"""
Utility helpers for Telegram Video Downloader Bot.
Includes URL validation, SSRF protection, safe logging, rate limiting, and stats tracking.
"""

import re
import time
import socket
import ipaddress
import logging
from urllib.parse import urlparse
from typing import Dict, Tuple, Optional, Set
from datetime import datetime


class TokenFilter(logging.Filter):
    """
    Logging filter that sanitizes and masks any sensitive bot tokens or tokens in strings.
    Prevents accidental leakage in logs.
    """
    def __init__(self, token_to_mask: Optional[str] = None):
        super().__init__()
        self.token_to_mask = token_to_mask

    def filter(self, record: logging.LogRecord) -> bool:
        msg = record.getMessage()
        if self.token_to_mask and len(self.token_to_mask) > 6 and self.token_to_mask in msg:
            record.msg = msg.replace(self.token_to_mask, "[PROTECTED_BOT_TOKEN]")
            record.args = ()
        # Mask generic Telegram bot token patterns like 123456789:ABCdef...
        record.msg = re.sub(r"\b\d{8,10}:[A-Za-z0-9_-]{35}\b", "[PROTECTED_BOT_TOKEN]", str(record.msg))
        return True


def setup_logging(bot_token: Optional[str] = None) -> logging.Logger:
    """Configures structured, safe logging without exposing secrets."""
    logger = logging.getLogger("telegram_video_bot")
    logger.setLevel(logging.INFO)

    if not logger.handlers:
        handler = logging.StreamHandler()
        formatter = logging.Formatter(
            fmt="%(asctime)s [%(levelname)s] [%(name)s] %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S"
        )
        handler.setFormatter(formatter)
        handler.addFilter(TokenFilter(bot_token))
        logger.addHandler(handler)

    return logger


def is_valid_url(url: str) -> Tuple[bool, str]:
    """
    Validates that a URL is a well-formed HTTP/HTTPS URL and does not target
    internal/private networks (SSRF prevention).
    Returns (is_valid, reason_or_message).
    """
    if not url or not isinstance(url, str):
        return False, "URL cannot be empty."

    url = url.strip()
    parsed = urlparse(url)

    if parsed.scheme.lower() not in ("http", "https"):
        return False, "Only HTTP and HTTPS URLs are supported."

    hostname = parsed.hostname
    if not hostname:
        return False, "Invalid URL: Missing domain or host."

    # Prevent internal localhost and loopback access (SSRF protection)
    if hostname.lower() in ("localhost", "127.0.0.1", "::1", "0.0.0.0"):
        return False, "Internal addresses are not permitted."

    # Check for private IPv4 / IPv6 addresses
    try:
        ip = ipaddress.ip_address(hostname)
        if ip.is_private or ip.is_loopback or ip.is_reserved or ip.is_link_local:
            return False, "Private or local network IP addresses are not permitted."
    except ValueError:
        # Hostname is a domain name, which is normal
        pass

    # Ensure hostname has at least one dot or is a valid FQDN
    if "." not in hostname:
        return False, "Invalid domain name in URL."

    return True, "Valid"


class RateLimiter:
    """Simple in-memory per-user rate limiter."""
    def __init__(self, cooldown_seconds: int = 10):
        self.cooldown_seconds = cooldown_seconds
        self._user_last_action: Dict[int, float] = {}

    def check(self, user_id: int) -> Tuple[bool, int]:
        """
        Check if user is allowed to perform a download.
        Returns (is_allowed, wait_seconds).
        """
        now = time.time()
        last_time = self._user_last_action.get(user_id, 0.0)
        elapsed = now - last_time

        if elapsed < self.cooldown_seconds:
            wait_time = int(self.cooldown_seconds - elapsed) + 1
            return False, wait_time

        self._user_last_action[user_id] = now
        return True, 0


class BotStats:
    """In-memory tracking of bot operational statistics."""
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
        days = diff.days
        hours, remainder = divmod(diff.seconds, 3600)
        minutes, seconds = divmod(remainder, 60)
        if days > 0:
            return f"{days}d {hours}h {minutes}m {seconds}s"
        return f"{hours}h {minutes}m {seconds}s"


def format_bytes(size_bytes: int) -> str:
    """Format bytes into human-readable string (KB, MB, GB)."""
    if size_bytes < 0:
        return "Unknown size"
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    elif size_bytes < 1024 * 1024 * 1024:
        return f"{size_bytes / (1024 * 1024):.1f} MB"
    else:
        return f"{size_bytes / (1024 * 1024 * 1024):.2f} GB"


def sanitize_filename(name: str) -> str:
    """Remove unsafe characters from filenames to avoid path traversal."""
    cleaned = re.sub(r'[/\\?%*:|"<>]', '_', name)
    cleaned = cleaned.strip(" ._")
    return cleaned[:100] or "media_file"
