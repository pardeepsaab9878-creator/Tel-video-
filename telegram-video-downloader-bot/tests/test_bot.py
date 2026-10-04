"""
Unit tests for Telegram Video Downloader Bot.
Tests URL validation, SSRF protection, RateLimiter, and Config loading.
Does not require a live Telegram Bot Token or network calls.
"""

import os
import pytest
from unittest.mock import patch

from utils import is_valid_url, RateLimiter, format_bytes, sanitize_filename
from config import Config, mask_token


def test_url_validation_valid():
    """Verify that normal public HTTP/HTTPS URLs pass validation."""
    valid_urls = [
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        "http://example.com/audio/sample.mp3",
        "https://cdn.example.org/videos/clip.webm?token=abc",
    ]
    for url in valid_urls:
        valid, msg = is_valid_url(url)
        assert valid is True, f"Failed for {url}: {msg}"


def test_url_validation_invalid_and_ssrf():
    """Verify that private IPs, localhost, and non-http schemes are blocked."""
    invalid_urls = [
        ("ftp://example.com/file.mp4", "Only HTTP and HTTPS URLs are supported."),
        ("file:///etc/passwd", "Only HTTP and HTTPS URLs are supported."),
        ("http://localhost:8080/video.mp4", "Internal addresses are not permitted."),
        ("http://127.0.0.1/secret.mp4", "Internal addresses are not permitted."),
        ("http://192.168.1.1/video.mp4", "Private or local network IP addresses are not permitted."),
        ("http://10.0.0.1/video.mp4", "Private or local network IP addresses are not permitted."),
        ("not_a_url", "Only HTTP and HTTPS URLs are supported."),
    ]
    for url, expected_error in invalid_urls:
        valid, msg = is_valid_url(url)
        assert valid is False, f"Expected {url} to be invalid"
        assert expected_error in msg


def test_rate_limiter():
    """Test that RateLimiter enforces cooldowns between calls."""
    limiter = RateLimiter(cooldown_seconds=5)
    user_id = 99999

    # First request should be allowed
    allowed, wait = limiter.check(user_id)
    assert allowed is True
    assert wait == 0

    # Immediate second request should be blocked
    allowed, wait = limiter.check(user_id)
    assert allowed is False
    assert wait > 0


def test_format_bytes():
    """Test byte formatting helper."""
    assert format_bytes(500) == "500 B"
    assert format_bytes(2048) == "2.0 KB"
    assert format_bytes(10 * 1024 * 1024) == "10.0 MB"


def test_sanitize_filename():
    """Test filename sanitization."""
    bad_name = '../../etc/passwd.mp4?arg=1'
    clean = sanitize_filename(bad_name)
    assert '/' not in clean
    assert '\\' not in clean
    assert '?' not in clean


def test_mask_token():
    """Test token masking for logs and debug output."""
    assert mask_token(None) == "[NOT SET]"
    assert mask_token("") == "[NOT SET]"
    assert mask_token("123456789:ABCDEF1234567890abcdef") == "123456...cdef"
