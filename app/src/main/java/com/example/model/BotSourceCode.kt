package com.example.model

data class ProjectFile(
    val filename: String,
    val description: String,
    val category: String, // "Core Bot", "Configuration", "CI/CD", "Documentation"
    val content: String
)

object BotCodeRepository {
    val files: List<ProjectFile> = listOf(
        ProjectFile(
            filename = "bot.py",
            description = "Main Telegram bot application with async command & URL handlers",
            category = "Core Bot",
            content = """${"\"\"\""}
Telegram Video Downloader Bot.
Accepts permitted/direct downloadable media URLs and processes them for the user.
Built with python-telegram-bot (v21+ async), httpx, and clean modular architecture.
${"\"\"\""}

import sys
import platform
from typing import Optional

from telegram import (
    Update,
    InlineKeyboardMarkup,
    InlineKeyboardButton,
)
from telegram.constants import ParseMode, ChatAction
from telegram.ext import (
    Application,
    CommandHandler,
    MessageHandler,
    CallbackQueryHandler,
    ContextTypes,
    filters,
)

from config import config, mask_token
from utils import (
    setup_logging,
    is_valid_url,
    RateLimiter,
    BotStats,
    format_bytes,
)
from downloader import (
    inspect_media_url,
    download_media,
    cleanup_temp_file,
    MediaInspectionResult,
)

logger = setup_logging(config.bot_token)
rate_limiter = RateLimiter(cooldown_seconds=config.rate_limit_seconds)
stats = BotStats()

async def start_command(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.effective_user or not update.message:
        return
    user_id = update.effective_user.id
    stats.record_request(user_id)
    welcome_text = (
        "👋 Welcome to My Video Downloader Bot!\n\n"
        "📥 Send a permitted/direct media URL and I will process it.\n\n"
        "💡 *Tips:*\n"
        "• Direct links ending in `.mp4`, `.webm`, `.mp3`, etc.\n"
        "• Maximum file size: *50 MB* (Telegram Bot standard limit)\n"
        "• Send /help for usage rules and supported formats."
    )
    await update.message.reply_text(welcome_text, parse_mode=ParseMode.MARKDOWN)

async def help_command(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.message:
        return
    help_text = (
        "📖 *Bot Usage & Guidelines*\n\n"
        "*What is supported:*\n"
        "• Direct links to public video files (`.mp4`, `.webm`, `.mov`, `.mkv`)\n"
        "• Direct links to public audio files (`.mp3`, `.m4a`, `.ogg`, `.wav`)\n"
        "• Files up to *50 MB* in size\n\n"
        "*Important Legal & Safety Restrictions:*\n"
        "• ❌ No bypassing of DRM, paywalls, or encryption (e.g. Widevine, HLS `.m3u8`).\n"
        "• ❌ No private, authenticated, or login-protected accounts.\n"
        "• ❌ Only media where you have permission or authorized direct access.\n"
        "• ❌ Web pages with nested scripts/ads are not direct media files.\n\n"
        "*How to use:*\n"
        "Simply paste any direct media link into this chat!"
    )
    await update.message.reply_text(help_text, parse_mode=ParseMode.MARKDOWN)

async def handle_url_message(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.effective_user or not update.message or not update.message.text:
        return
    user_id = update.effective_user.id
    url = update.message.text.strip()
    stats.record_request(user_id)

    is_valid, validation_reason = is_valid_url(url)
    if not is_valid:
        await update.message.reply_text(
            f"❌ *Invalid URL*\n\n{validation_reason}\n\nPlease check the link and try again.",
            parse_mode=ParseMode.MARKDOWN
        )
        return

    allowed, wait_seconds = rate_limiter.check(user_id)
    if not allowed:
        await update.message.reply_text(
            f"⏳ *Please slow down!*\n\nPlease wait *{wait_seconds} seconds* before sending another request.",
            parse_mode=ParseMode.MARKDOWN
        )
        return

    status_msg = await update.message.reply_text("🔍 *Inspecting media URL...*", parse_mode=ParseMode.MARKDOWN)
    try:
        inspection = await inspect_media_url(url)
        if not inspection.is_supported or inspection.error_message:
            stats.record_failure()
            await status_msg.edit_text(inspection.error_message or "Unsupported URL or media format.")
            return

        size_display = format_bytes(inspection.file_size_bytes) if inspection.file_size_bytes > 0 else "streaming"
        await status_msg.edit_text(
            f"⬇️ *Downloading media...*\n\n"
            f"• Format: `{inspection.media_type.upper()}`\n"
            f"• Name: `{inspection.filename}`\n"
            f"• Size: *{size_display}*",
            parse_mode=ParseMode.MARKDOWN
        )

        if inspection.media_type == "video":
            await update.effective_chat.send_action(action=ChatAction.UPLOAD_VIDEO)
        else:
            await update.effective_chat.send_action(action=ChatAction.UPLOAD_VOICE)

        download_result = await download_media(url, inspection)
        if not download_result.success or not download_result.file_path:
            stats.record_failure()
            await status_msg.edit_text(f"❌ *Download Failed*\n\n{download_result.error_message}", parse_mode=ParseMode.MARKDOWN)
            return

        await status_msg.edit_text("📤 *Uploading media to Telegram...*", parse_mode=ParseMode.MARKDOWN)
        file_path = download_result.file_path
        try:
            with open(file_path, "rb") as f:
                caption = f"🎬 {download_result.filename}\n📦 Size: {format_bytes(download_result.file_size_bytes)}"
                if download_result.media_type == "video":
                    await update.message.reply_video(video=f, caption=caption, supports_streaming=True)
                elif download_result.media_type == "audio":
                    await update.message.reply_audio(audio=f, caption=caption)
                else:
                    await update.message.reply_document(document=f, caption=caption)
            stats.record_success()
            await status_msg.delete()
        except Exception as upload_err:
            stats.record_failure()
            await status_msg.edit_text("⚠️ *Upload Failed*\n\nTelegram was unable to receive this file.")
        finally:
            cleanup_temp_file(file_path)
    except Exception as exc:
        stats.record_failure()
        await status_msg.edit_text("⚠️ An unexpected server error occurred while processing your request.")

def _is_admin(user_id: int) -> bool:
    return config.admin_user_id is not None and user_id == config.admin_user_id

async def admin_stats(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.effective_user or not update.message:
        return
    if not _is_admin(update.effective_user.id):
        await update.message.reply_text("⛔ This command is restricted to bot administrators.")
        return
    stats_msg = (
        "📊 *Bot Operational Statistics*\n\n"
        f"• *Uptime:* {stats.uptime_str}\n"
        f"• *Total Requests:* {stats.total_requests}\n"
        f"• *Successful Downloads:* {stats.successful_downloads}\n"
        f"• *Failed Requests:* {stats.failed_downloads}\n"
        f"• *Unique Users Seen:* {len(stats.unique_users)}\n"
        f"• *Max File Size Limit:* {config.max_file_size_mb} MB"
    )
    await update.message.reply_text(stats_msg, parse_mode=ParseMode.MARKDOWN)

async def admin_status(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.effective_user or not update.message:
        return
    if not _is_admin(update.effective_user.id):
        await update.message.reply_text("⛔ This command is restricted to bot administrators.")
        return
    status_msg = (
        "⚙️ *System & Bot Status*\n\n"
        f"• *Status:* 🟢 Online and healthy\n"
        f"• *Python Version:* `{platform.python_version()}`\n"
        f"• *Admin ID Configured:* {'✅ Yes' if config.admin_user_id else '❌ No'}\n"
        f"• *Bot Token:* `{mask_token(config.bot_token)}`"
    )
    await update.message.reply_text(status_msg, parse_mode=ParseMode.MARKDOWN)

async def admin_users(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.effective_user or not update.message:
        return
    if not _is_admin(update.effective_user.id):
        await update.message.reply_text("⛔ This command is restricted to bot administrators.")
        return
    await update.message.reply_text(
        f"👥 *Registered User Count:* {len(stats.unique_users)} user(s) recorded.",
        parse_mode=ParseMode.MARKDOWN
    )

async def admin_broadcast(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.effective_user or not update.message:
        return
    if not _is_admin(update.effective_user.id):
        await update.message.reply_text("⛔ This command is restricted to bot administrators.")
        return
    if not context.args:
        await update.message.reply_text("⚠️ Usage: `/broadcast <message>`", parse_mode=ParseMode.MARKDOWN)
        return
    broadcast_content = " ".join(context.args)
    sent_count, fail_count = 0, 0
    status_msg = await update.message.reply_text("📢 Starting broadcast...")
    for user_id in list(stats.unique_users):
        try:
            await context.bot.send_message(
                chat_id=user_id,
                text=f"📢 *Announcement from Admin*\n\n{broadcast_content}",
                parse_mode=ParseMode.MARKDOWN
            )
            sent_count += 1
        except Exception:
            fail_count += 1
    await status_msg.edit_text(f"✅ *Broadcast Complete*\n\n• Delivered: {sent_count}\n• Failed: {fail_count}")

def main() -> None:
    if not config.bot_token:
        logger.critical("FATAL: BOT_TOKEN is empty in environment.")
        sys.exit(1)
    app = Application.builder().token(config.bot_token).build()
    app.add_handler(CommandHandler("start", start_command))
    app.add_handler(CommandHandler("help", help_command))
    app.add_handler(CommandHandler("stats", admin_stats))
    app.add_handler(CommandHandler("status", admin_status))
    app.add_handler(CommandHandler("users", admin_users))
    app.add_handler(CommandHandler("broadcast", admin_broadcast))
    app.add_handler(MessageHandler(filters.TEXT & (filters.Entity("url") | filters.Regex(r"^https?://")), handle_url_message))
    app.run_polling(drop_pending_updates=True)

if __name__ == "__main__":
    main()
"""
        ),
        ProjectFile(
            filename = "downloader.py",
            description = "Direct media inspector, chunked async streamer, and DRM protector",
            category = "Core Bot",
            content = """${"\"\"\""}
Media Downloader Module.
Handles inspection, validation, and safe downloading of permitted/direct media files.
Complies strictly with legal, safety, and platform standards:
- Does NOT bypass DRM, paywalls, authentication, or access controls.
- Validates file sizes against Telegram's 50MB bot upload limit.
- Employs async streaming with chunked I/O to maintain low memory footprint.
${"\"\"\""}

import os
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

VIDEO_MIME_TYPES = {
    "video/mp4", "video/webm", "video/ogg",
    "video/quicktime", "video/x-matroska", "video/x-msvideo"
}

AUDIO_MIME_TYPES = {
    "audio/mpeg", "audio/mp3", "audio/ogg",
    "audio/wav", "audio/aac", "audio/x-m4a",
    "audio/mp4", "audio/flac"
}

PROTECTED_STREAMING_TYPES = {
    "application/x-mpegurl", "application/vnd.apple.mpegurl",
    "application/dash+xml", "application/vnd.ms-sstr+xml"
}

@dataclass
class MediaInspectionResult:
    is_supported: bool
    media_type: str
    mime_type: str
    file_size_bytes: int
    filename: str
    error_message: Optional[str] = None

@dataclass
class DownloadResult:
    success: bool
    file_path: Optional[str] = None
    filename: Optional[str] = None
    media_type: Optional[str] = None
    file_size_bytes: int = 0
    error_message: Optional[str] = None

def _derive_filename_and_ext(url: str, mime_type: str) -> Tuple[str, str]:
    parsed = urlparse(url)
    path = unquote(parsed.path)
    base_name = os.path.basename(path.rstrip("/"))
    ext = ""
    if "." in base_name:
        name_parts = base_name.rsplit(".", 1)
        base_name = name_parts[0]
        ext = f".{name_parts[1].lower()[:5]}"
    if not ext:
        mime_map = {
            "video/mp4": ".mp4", "video/webm": ".webm",
            "audio/mpeg": ".mp3", "audio/mp3": ".mp3",
            "audio/ogg": ".ogg", "audio/wav": ".wav"
        }
        ext = mime_map.get(mime_type, ".bin")
    clean_name = sanitize_filename(base_name) or f"media_{uuid.uuid4().hex[:6]}"
    return f"{clean_name}{ext}", ext

async def inspect_media_url(url: str) -> MediaInspectionResult:
    max_bytes = config.max_file_size_mb * 1024 * 1024
    headers = {"User-Agent": "Mozilla/5.0 (compatible; DirectMediaBot/1.0)", "Accept": "*/*"}
    try:
        async with httpx.AsyncClient(timeout=config.request_timeout_seconds, follow_redirects=True) as client:
            try:
                response = await client.head(url, headers=headers)
            except Exception:
                range_headers = {**headers, "Range": "bytes=0-1024"}
                response = await client.get(url, headers=range_headers)

            if response.status_code in (401, 403):
                return MediaInspectionResult(
                    is_supported=False, media_type="unknown", mime_type="",
                    file_size_bytes=0, filename="",
                    error_message="🔒 Access restricted (HTTP 401/403). Requires login, private permission, or paywall."
                )

            if response.status_code == 404:
                return MediaInspectionResult(
                    is_supported=False, media_type="unknown", mime_type="",
                    file_size_bytes=0, filename="",
                    error_message="❌ The media file was not found on the server (HTTP 404)."
                )

            content_type = response.headers.get("content-type", "").split(";")[0].strip().lower()
            content_length_str = response.headers.get("content-length")
            content_length = int(content_length_str) if content_length_str and content_length_str.isdigit() else 0

            if content_type in PROTECTED_STREAMING_TYPES or ".m3u8" in url.lower() or ".mpd" in url.lower():
                return MediaInspectionResult(
                    is_supported=False, media_type="unknown", mime_type=content_type,
                    file_size_bytes=content_length, filename="",
                    error_message="⚠️ Protected streaming format detected (HLS/DASH/DRM). This bot only processes direct files."
                )

            url_path_lower = urlparse(url).path.lower()
            is_video = content_type in VIDEO_MIME_TYPES or any(url_path_lower.endswith(ext) for ext in [".mp4", ".webm", ".mov", ".mkv"])
            is_audio = content_type in AUDIO_MIME_TYPES or any(url_path_lower.endswith(ext) for ext in [".mp3", ".m4a", ".aac", ".ogg", ".wav"])

            if not is_video and not is_audio:
                if "html" in content_type:
                    return MediaInspectionResult(
                        is_supported=False, media_type="unknown", mime_type=content_type,
                        file_size_bytes=content_length, filename="",
                        error_message="ℹ️ This URL leads to a web page rather than a direct downloadable media file."
                    )
                return MediaInspectionResult(
                    is_supported=False, media_type="unknown", mime_type=content_type,
                    file_size_bytes=content_length, filename="",
                    error_message=f"Unsupported format ({content_type or 'unknown'}). Supported: MP4, WEBM, MP3, M4A, etc."
                )

            if content_length > max_bytes:
                return MediaInspectionResult(
                    is_supported=False, media_type="video" if is_video else "audio",
                    mime_type=content_type, file_size_bytes=content_length, filename="",
                    error_message=f"⚠️ File is too large ({format_bytes(content_length)}). Telegram bot limit is {config.max_file_size_mb} MB."
                )

            filename, _ = _derive_filename_and_ext(url, content_type)
            return MediaInspectionResult(
                is_supported=True, media_type="video" if is_video else "audio",
                mime_type=content_type, file_size_bytes=content_length, filename=filename
            )

    except httpx.TimeoutException:
        return MediaInspectionResult(False, "unknown", "", 0, "", "⏱️ Connection timed out inspecting the media URL.")
    except Exception:
        return MediaInspectionResult(False, "unknown", "", 0, "", "🌐 Network error connecting to host server.")

async def download_media(url: str, inspection: MediaInspectionResult) -> DownloadResult:
    os.makedirs(config.temp_dir, exist_ok=True)
    temp_filename = f"{uuid.uuid4().hex}_{inspection.filename}"
    file_path = os.path.join(config.temp_dir, temp_filename)
    max_bytes = config.max_file_size_mb * 1024 * 1024
    total_downloaded = 0
    headers = {"User-Agent": "Mozilla/5.0 (compatible; DirectMediaBot/1.0)", "Accept": "*/*"}

    try:
        async with httpx.AsyncClient(timeout=config.request_timeout_seconds, follow_redirects=True) as client:
            async with client.stream("GET", url, headers=headers) as response:
                if response.status_code not in (200, 206):
                    return DownloadResult(False, error_message=f"Server returned HTTP {response.status_code}.")

                async with aiofiles.open(file_path, "wb") as f:
                    async for chunk in response.aiter_bytes(chunk_size=config.download_chunk_size):
                        total_downloaded += len(chunk)
                        if total_downloaded > max_bytes:
                            await f.close()
                            cleanup_temp_file(file_path)
                            return DownloadResult(False, error_message="File exceeded size limit during download.")
                        await f.write(chunk)

        return DownloadResult(True, file_path=file_path, filename=inspection.filename,
                              media_type=inspection.media_type, file_size_bytes=total_downloaded)
    except Exception:
        cleanup_temp_file(file_path)
        return DownloadResult(False, error_message="Download aborted due to connection error.")

def cleanup_temp_file(file_path: Optional[str]):
    if file_path and os.path.isfile(file_path):
        try:
            os.remove(file_path)
        except OSError:
            pass
"""
        ),
        ProjectFile(
            filename = "config.py",
            description = "Loads environment variables safely with token masking and validation",
            category = "Configuration",
            content = """${"\"\"\""}
Configuration module for Telegram Video Downloader Bot.
Loads environment variables safely and validates startup configuration.
${"\"\"\""}

import os
import logging
from dataclasses import dataclass
from typing import Optional
from dotenv import load_dotenv

load_dotenv()

def mask_token(token: Optional[str]) -> str:
    if not token:
        return "[NOT SET]"
    if len(token) <= 10:
        return "***"
    return f"{token[:6]}...{token[-4:]}"

@dataclass(frozen=True)
class Config:
    bot_token: str
    admin_user_id: Optional[int]
    max_file_size_mb: int
    rate_limit_seconds: int
    request_timeout_seconds: int
    download_chunk_size: int = 64 * 1024
    temp_dir: str = "temp_downloads"

    @classmethod
    def load(cls) -> "Config":
        token = os.getenv("BOT_TOKEN", "").strip()
        admin_id_str = os.getenv("ADMIN_USER_ID", "").strip()
        admin_id = int(admin_id_str) if admin_id_str.isdigit() else None
        
        try:
            max_size_mb = min(50, max(1, int(os.getenv("MAX_FILE_SIZE_MB", "50"))))
        except ValueError:
            max_size_mb = 50

        try:
            rate_limit = max(1, int(os.getenv("RATE_LIMIT_SECONDS", "10")))
        except ValueError:
            rate_limit = 10

        try:
            timeout = max(10, int(os.getenv("REQUEST_TIMEOUT_SECONDS", "60")))
        except ValueError:
            timeout = 60

        return cls(
            bot_token=token,
            admin_user_id=admin_id,
            max_file_size_mb=max_size_mb,
            rate_limit_seconds=rate_limit,
            request_timeout_seconds=timeout,
        )

config = Config.load()
"""
        ),
        ProjectFile(
            filename = "utils.py",
            description = "SSRF guard, token sanitizer filter, rate limiter, and stats tracker",
            category = "Core Bot",
            content = """${"\"\"\""}
Utility helpers for Telegram Video Downloader Bot.
Includes URL validation, SSRF protection, safe logging, rate limiting, and stats tracking.
${"\"\"\""}

import re
import time
import ipaddress
import logging
from urllib.parse import urlparse
from typing import Dict, Tuple, Optional, Set
from datetime import datetime

class TokenFilter(logging.Filter):
    def __init__(self, token_to_mask: Optional[str] = None):
        super().__init__()
        self.token_to_mask = token_to_mask

    def filter(self, record: logging.LogRecord) -> bool:
        msg = record.getMessage()
        if self.token_to_mask and len(self.token_to_mask) > 6 and self.token_to_mask in msg:
            record.msg = msg.replace(self.token_to_mask, "[PROTECTED_BOT_TOKEN]")
            record.args = ()
        record.msg = re.sub(r"\b\d{8,10}:[A-Za-z0-9_-]{35}\b", "[PROTECTED_BOT_TOKEN]", str(record.msg))
        return True

def setup_logging(bot_token: Optional[str] = None) -> logging.Logger:
    logger = logging.getLogger("telegram_video_bot")
    logger.setLevel(logging.INFO)
    if not logger.handlers:
        handler = logging.StreamHandler()
        formatter = logging.Formatter(fmt="%(asctime)s [%(levelname)s] %(message)s")
        handler.setFormatter(formatter)
        handler.addFilter(TokenFilter(bot_token))
        logger.addHandler(handler)
    return logger

def is_valid_url(url: str) -> Tuple[bool, str]:
    if not url or not isinstance(url, str):
        return False, "URL cannot be empty."
    parsed = urlparse(url.strip())
    if parsed.scheme.lower() not in ("http", "https"):
        return False, "Only HTTP and HTTPS URLs are supported."
    hostname = parsed.hostname
    if not hostname:
        return False, "Invalid URL: Missing domain or host."
    if hostname.lower() in ("localhost", "127.0.0.1", "::1", "0.0.0.0"):
        return False, "Internal addresses are not permitted."
    try:
        ip = ipaddress.ip_address(hostname)
        if ip.is_private or ip.is_loopback:
            return False, "Private network IP addresses are not permitted."
    except ValueError:
        pass
    if "." not in hostname:
        return False, "Invalid domain name in URL."
    return True, "Valid"

class RateLimiter:
    def __init__(self, cooldown_seconds: int = 10):
        self.cooldown_seconds = cooldown_seconds
        self._user_last_action: Dict[int, float] = {}

    def check(self, user_id: int) -> Tuple[bool, int]:
        now = time.time()
        last_time = self._user_last_action.get(user_id, 0.0)
        elapsed = now - last_time
        if elapsed < self.cooldown_seconds:
            return False, int(self.cooldown_seconds - elapsed) + 1
        self._user_last_action[user_id] = now
        return True, 0

class BotStats:
    def __init__(self):
        self.start_time: datetime = datetime.now()
        self.total_requests: int = 0
        self.successful_downloads: int = 0
        self.failed_downloads: int = 0
        self.unique_users: Set[int] = set()

    def record_request(self, user_id: int):
        self.total_requests += 1
        self.unique_users.add(user_id)

    def record_success(self):
        self.successful_downloads += 1

    def record_failure(self):
        self.failed_downloads += 1

    @property
    def uptime_str(self) -> str:
        diff = datetime.now() - self.start_time
        return f"{diff.seconds // 3600}h {(diff.seconds // 60) % 60}m {diff.seconds % 60}s"

def format_bytes(size_bytes: int) -> str:
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    else:
        return f"{size_bytes / (1024 * 1024):.1f} MB"

def sanitize_filename(name: str) -> str:
    cleaned = re.sub(r'[/\\?%*:|"<>]', '_', name).strip(" ._")
    return cleaned[:100] or "media_file"
"""
        ),
        ProjectFile(
            filename = "requirements.txt",
            description = "Pinned dependencies for Python 3.11+",
            category = "Configuration",
            content = """python-telegram-bot[job-queue]>=21.0,<22.0
httpx[http2]>=0.27.0,<0.28.0
python-dotenv>=1.0.1,<2.0.0
aiofiles>=23.2.1,<24.0.0
pytest>=8.1.0,<9.0.0
pytest-asyncio>=0.23.5,<1.0.0
"""
        ),
        ProjectFile(
            filename = ".env.example",
            description = "Template for environment secrets",
            category = "Configuration",
            content = """# Telegram Bot Token from @BotFather
BOT_TOKEN=your_bot_token_here

# Telegram User ID for admin commands (/stats, /broadcast)
ADMIN_USER_ID=your_telegram_user_id_here

# Maximum file size in MB (Telegram max is 50MB)
MAX_FILE_SIZE_MB=50

# Cooldown between download requests in seconds
RATE_LIMIT_SECONDS=10

# Media download request timeout
REQUEST_TIMEOUT_SECONDS=60
"""
        ),
        ProjectFile(
            filename = ".gitignore",
            description = "Prevents committing .env, tokens, cache, and virtual environments",
            category = "Configuration",
            content = """__pycache__/
*.py[cod]
venv/
.venv/
.env
.env.local
temp_downloads/
media_cache/
bot.log
.pytest_cache/
.DS_Store
"""
        ),
        ProjectFile(
            filename = ".github/workflows/bot.yml",
            description = "GitHub Actions CI pipeline for syntax, imports, and tests",
            category = "CI/CD",
            content = """name: Telegram Bot CI

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  validate-and-test:
    name: Build & Test Validation
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up Python 3.11
        uses: actions/setup-python@v5
        with:
          python-version: "3.11"
          cache: "pip"

      - name: Install dependencies
        run: |
          python -m pip install --upgrade pip
          pip install -r requirements.txt

      - name: Code Syntax Check
        run: |
          python -m py_compile $(find . -name "*.py" -not -path "*/.*")

      - name: Run Test Suite
        env:
          BOT_TOKEN: "123456789:TESTING_MOCK_TOKEN_NOT_FOR_PRODUCTION_X"
          ADMIN_USER_ID: "123456789"
        run: |
          pytest -v
"""
        ),
        ProjectFile(
            filename = "README.md",
            description = "Complete beginner-friendly setup, Termux Android guide, and legal policy",
            category = "Documentation",
            content = """# 📥 Telegram Video Downloader Bot

A reliable, safe, and modular Telegram bot built with **Python 3.11+**, `python-telegram-bot` (v21+), and `httpx`.
Designed for authorized/direct downloadable media (MP4, WEBM, MP3) with rate limiting and SSRF guards.

## ⚖️ Legal & Safety Policy
- No DRM/Paywall bypass (Widevine, HLS .m3u8, private auth are rejected gracefully).
- 50MB maximum size limit (Telegram Bot API standard).

## 📱 Android Setup (Termux)
```bash
pkg update && pkg install python git -y
git clone https://github.com/YOUR_USERNAME/telegram-video-downloader-bot.git
cd telegram-video-downloader-bot
pip install -r requirements.txt
cp .env.example .env
nano .env # Paste BOT_TOKEN and ADMIN_USER_ID
python bot.py
```
"""
        )
    )
}
