"""
Media Downloader Module.
Handles inspection, validation, and safe downloading of permitted/direct media files.
Complies strictly with legal, safety, and platform standards:
- Does NOT bypass DRM, paywalls, authentication, or access controls.
- Validates file sizes against Telegram's 50MB bot upload limit.
- Employs async streaming with chunked I/O to maintain low memory footprint.
"""

import os
import re
import uuid
import logging
from dataclasses import dataclass
from typing import Optional, Tuple
from urllib.parse import urlparse, unquote

import httpx
import aiofiles

from config import config
from utils import sanitize_filename, format_bytes

logger = logging.getLogger("telegram_video_bot.downloader")

# Common direct media content-types
VIDEO_MIME_TYPES = {
    "video/mp4",
    "video/webm",
    "video/ogg",
    "video/quicktime",
    "video/x-matroska",
    "video/x-msvideo",
}

AUDIO_MIME_TYPES = {
    "audio/mpeg",
    "audio/mp3",
    "audio/ogg",
    "audio/wav",
    "audio/aac",
    "audio/x-m4a",
    "audio/mp4",
    "audio/flac",
}

# Known DRM or protected streaming formats that must be rejected gracefully
PROTECTED_STREAMING_TYPES = {
    "application/x-mpegurl",
    "application/vnd.apple.mpegurl",
    "application/dash+xml",
    "application/vnd.ms-sstr+xml",
}


@dataclass
class MediaInspectionResult:
    """Information gathered about a media URL prior to downloading."""
    is_supported: bool
    media_type: str  # 'video', 'audio', or 'unknown'
    mime_type: str
    file_size_bytes: int
    filename: str
    error_message: Optional[str] = None


@dataclass
class DownloadResult:
    """Result of a media download operation."""
    success: bool
    file_path: Optional[str] = None
    filename: Optional[str] = None
    media_type: Optional[str] = None
    file_size_bytes: int = 0
    error_message: Optional[str] = None


def _derive_filename_and_ext(url: str, mime_type: str) -> Tuple[str, str]:
    """Derives a clean filename and extension from the URL path or MIME type."""
    parsed = urlparse(url)
    path = unquote(parsed.path)
    base_name = os.path.basename(path.rstrip("/"))

    ext = ""
    if "." in base_name:
        name_parts = base_name.rsplit(".", 1)
        base_name = name_parts[0]
        ext = f".{name_parts[1].lower()[:5]}"

    # Fallback to extension based on MIME type if extension is missing
    if not ext:
        mime_map = {
            "video/mp4": ".mp4",
            "video/webm": ".webm",
            "video/quicktime": ".mov",
            "video/x-matroska": ".mkv",
            "audio/mpeg": ".mp3",
            "audio/mp3": ".mp3",
            "audio/ogg": ".ogg",
            "audio/wav": ".wav",
            "audio/aac": ".aac",
            "audio/x-m4a": ".m4a",
            "audio/flac": ".flac",
        }
        ext = mime_map.get(mime_type, ".bin")

    clean_name = sanitize_filename(base_name) or f"media_{uuid.uuid4().hex[:6]}"
    return f"{clean_name}{ext}", ext


async def inspect_media_url(url: str) -> MediaInspectionResult:
    """
    Inspects a URL using HTTP HEAD (or targeted GET) to verify:
    1. If the resource exists and is accessible without authentication.
    2. If it is a direct/permitted video or audio stream.
    3. If its size complies with bot limits.
    """
    max_bytes = config.max_file_size_mb * 1024 * 1024
    headers = {
        "User-Agent": "Mozilla/5.0 (compatible; DirectMediaBot/1.0; +https://github.com/)",
        "Accept": "*/*",
    }

    try:
        async with httpx.AsyncClient(timeout=config.request_timeout_seconds, follow_redirects=True) as client:
            try:
                response = await client.head(url, headers=headers)
            except (httpx.RequestError, httpx.HTTPStatusError):
                # Some servers reject HEAD requests; fallback to a Range GET for first 1KB
                range_headers = {**headers, "Range": "bytes=0-1024"}
                response = await client.get(url, headers=range_headers)

            status_code = response.status_code

            # Handle access restrictions and paywalls
            if status_code in (401, 403):
                return MediaInspectionResult(
                    is_supported=False,
                    media_type="unknown",
                    mime_type="",
                    file_size_bytes=0,
                    filename="",
                    error_message=(
                        "🔒 Access restricted (HTTP 401/403).\n"
                        "This media requires login credentials, private authorization, "
                        "or a paywall. This bot does not bypass security or access controls."
                    )
                )

            if status_code == 404:
                return MediaInspectionResult(
                    is_supported=False,
                    media_type="unknown",
                    mime_type="",
                    file_size_bytes=0,
                    filename="",
                    error_message="❌ The requested media was not found on the server (HTTP 404)."
                )

            if status_code >= 400:
                return MediaInspectionResult(
                    is_supported=False,
                    media_type="unknown",
                    mime_type="",
                    file_size_bytes=0,
                    filename="",
                    error_message=f"❌ Server returned error status: {status_code}."
                )

            content_type = response.headers.get("content-type", "").split(";")[0].strip().lower()
            content_length_str = response.headers.get("content-length")
            content_length = int(content_length_str) if content_length_str and content_length_str.isdigit() else 0

            # Reject DRM / protected streaming protocols
            if content_type in PROTECTED_STREAMING_TYPES or ".m3u8" in url.lower() or ".mpd" in url.lower():
                return MediaInspectionResult(
                    is_supported=False,
                    media_type="unknown",
                    mime_type=content_type,
                    file_size_bytes=content_length,
                    filename="",
                    error_message=(
                        "⚠️ Protected or segmented streaming format detected (HLS/DASH/DRM).\n"
                        "This bot only supports permitted direct video/audio files (e.g., MP4, MP3, WEBM), "
                        "and does not bypass DRM or stream protections."
                    )
                )

            # Classify media
            url_path_lower = urlparse(url).path.lower()
            is_video = (
                content_type in VIDEO_MIME_TYPES
                or any(url_path_lower.endswith(ext) for ext in [".mp4", ".webm", ".mov", ".mkv"])
            )
            is_audio = (
                content_type in AUDIO_MIME_TYPES
                or any(url_path_lower.endswith(ext) for ext in [".mp3", ".m4a", ".aac", ".ogg", ".wav", ".flac"])
            )

            if not is_video and not is_audio:
                # If content-type is HTML, this is a web page, not a direct media link
                if "html" in content_type:
                    return MediaInspectionResult(
                        is_supported=False,
                        media_type="unknown",
                        mime_type=content_type,
                        file_size_bytes=content_length,
                        filename="",
                        error_message=(
                            "ℹ️ This URL leads to a web page rather than a direct downloadable media file.\n"
                            "Please provide a direct URL ending in .mp4, .mp3, etc., or an authorized direct link."
                        )
                    )
                return MediaInspectionResult(
                    is_supported=False,
                    media_type="unknown",
                    mime_type=content_type,
                    file_size_bytes=content_length,
                    filename="",
                    error_message=(
                        f"Unsupported media format ({content_type or 'unknown'}).\n"
                        "Supported media: direct MP4, WEBM, MOV, MKV videos, and MP3, M4A, OGG, WAV audio."
                    )
                )

            # Check file size limit
            if content_length > max_bytes:
                return MediaInspectionResult(
                    is_supported=False,
                    media_type="video" if is_video else "audio",
                    mime_type=content_type,
                    file_size_bytes=content_length,
                    filename="",
                    error_message=(
                        f"⚠️ File is too large ({format_bytes(content_length)}).\n"
                        f"The maximum allowed size is {config.max_file_size_mb} MB due to Telegram bot limits."
                    )
                )

            media_type = "video" if is_video else "audio"
            filename, _ = _derive_filename_and_ext(url, content_type)

            return MediaInspectionResult(
                is_supported=True,
                media_type=media_type,
                mime_type=content_type,
                file_size_bytes=content_length,
                filename=filename,
                error_message=None
            )

    except httpx.TimeoutException:
        return MediaInspectionResult(
            is_supported=False,
            media_type="unknown",
            mime_type="",
            file_size_bytes=0,
            filename="",
            error_message="⏱️ Connection timed out while inspecting the media URL. Server took too long to reply."
        )
    except httpx.RequestError as exc:
        logger.warning("Network failure during inspection: %s", type(exc).__name__)
        return MediaInspectionResult(
            is_supported=False,
            media_type="unknown",
            mime_type="",
            file_size_bytes=0,
            filename="",
            error_message="🌐 Network error occurred while connecting to the host server."
        )


async def download_media(url: str, inspection: MediaInspectionResult) -> DownloadResult:
    """
    Safely downloads the inspected media to a temporary file using chunked streaming.
    Enforces the file size limit continuously during stream.
    """
    os.makedirs(config.temp_dir, exist_ok=True)
    temp_filename = f"{uuid.uuid4().hex}_{inspection.filename}"
    file_path = os.path.join(config.temp_dir, temp_filename)

    max_bytes = config.max_file_size_mb * 1024 * 1024
    total_downloaded = 0

    headers = {
        "User-Agent": "Mozilla/5.0 (compatible; DirectMediaBot/1.0)",
        "Accept": "*/*",
    }

    try:
        async with httpx.AsyncClient(timeout=config.request_timeout_seconds, follow_redirects=True) as client:
            async with client.stream("GET", url, headers=headers) as response:
                if response.status_code != 200 and response.status_code != 206:
                    return DownloadResult(
                        success=False,
                        error_message=f"Server returned HTTP {response.status_code} during download."
                    )

                async with aiofiles.open(file_path, "wb") as f:
                    async for chunk in response.aiter_bytes(chunk_size=config.download_chunk_size):
                        total_downloaded += len(chunk)
                        if total_downloaded > max_bytes:
                            # Abort download immediately to protect storage
                            await f.close()
                            cleanup_temp_file(file_path)
                            return DownloadResult(
                                success=False,
                                error_message=(
                                    f"File exceeded the maximum limit of {config.max_file_size_mb} MB "
                                    "during download and was aborted."
                                )
                            )
                        await f.write(chunk)

        return DownloadResult(
            success=True,
            file_path=file_path,
            filename=inspection.filename,
            media_type=inspection.media_type,
            file_size_bytes=total_downloaded,
            error_message=None
        )

    except httpx.TimeoutException:
        cleanup_temp_file(file_path)
        return DownloadResult(
            success=False,
            error_message="⏱️ The download timed out before completing."
        )
    except Exception as exc:
        cleanup_temp_file(file_path)
        logger.error("Download failed unexpectedly: %s", type(exc).__name__)
        return DownloadResult(
            success=False,
            error_message="⚠️ Failed to complete media download due to a network or server issue."
        )


def cleanup_temp_file(file_path: Optional[str]):
    """Safely removes temporary download file."""
    if file_path and os.path.isfile(file_path):
        try:
            os.remove(file_path)
        except OSError as e:
            logger.warning("Could not delete temporary file %s: %s", file_path, e)
