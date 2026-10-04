"""
Configuration module for Telegram Video Downloader Bot.
Loads environment variables safely and validates startup configuration.
"""

import os
import sys
import logging
from dataclasses import dataclass
from typing import Optional
from dotenv import load_dotenv

# Load variables from .env file if it exists
load_dotenv()


def mask_token(token: Optional[str]) -> str:
    """Safely mask a token for display in logs or UI (e.g. 123456789:ABC...xyz)."""
    if not token:
        return "[NOT SET]"
    if len(token) <= 10:
        return "***"
    return f"{token[:6]}...{token[-4:]}"


@dataclass(frozen=True)
class Config:
    """Bot configuration settings loaded from environment variables."""
    bot_token: str
    admin_user_id: Optional[int]
    max_file_size_mb: int
    rate_limit_seconds: int
    request_timeout_seconds: int
    download_chunk_size: int = 64 * 1024  # 64 KB chunks for memory efficiency
    temp_dir: str = "temp_downloads"

    @classmethod
    def load(cls) -> "Config":
        """Load and validate configuration from the environment."""
        token = os.getenv("BOT_TOKEN", "").strip()

        # Bot token validation
        if not token:
            logging.critical(
                "CRITICAL: 'BOT_TOKEN' environment variable is missing!\n"
                "Please configure BOT_TOKEN in your .env file or environment."
            )
            # We don't call sys.exit here immediately so tests can import config with mock values,
            # but bot.py will abort on startup if token is empty.

        # Admin User ID parsing
        admin_id_str = os.getenv("ADMIN_USER_ID", "").strip()
        admin_id: Optional[int] = None
        if admin_id_str:
            try:
                admin_id = int(admin_id_str)
            except ValueError:
                logging.warning(
                    "WARNING: ADMIN_USER_ID '%s' is not a valid integer. Admin commands will be disabled.",
                    admin_id_str
                )

        # Max file size (Telegram Bot API has a 50MB upload limit for standard bots)
        max_size_mb = 50
        try:
            max_size_mb = int(os.getenv("MAX_FILE_SIZE_MB", "50"))
            if max_size_mb <= 0 or max_size_mb > 50:
                max_size_mb = 50  # Cap at Telegram's 50MB standard bot limit
        except ValueError:
            max_size_mb = 50

        # Rate limit in seconds
        rate_limit = 10
        try:
            rate_limit = max(1, int(os.getenv("RATE_LIMIT_SECONDS", "10")))
        except ValueError:
            rate_limit = 10

        # Request timeout in seconds
        timeout = 60
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


# Global config instance
config = Config.load()
