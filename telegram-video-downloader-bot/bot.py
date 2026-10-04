"""
Telegram Video Downloader Bot.
Accepts permitted/direct downloadable media URLs and processes them for the user.
Built with python-telegram-bot (v21+ async), httpx, and clean modular architecture.
"""

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

# Initialize logger and in-memory state
logger = setup_logging(config.bot_token)
rate_limiter = RateLimiter(cooldown_seconds=config.rate_limit_seconds)
stats = BotStats()


# -------------------------------------------------------------------
# User Handlers
# -------------------------------------------------------------------

async def start_command(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Handles the /start command."""
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
    """Handles the /help command explaining usage and restrictions."""
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
    """Handles incoming text messages containing URLs."""
    if not update.effective_user or not update.message or not update.message.text:
        return

    user_id = update.effective_user.id
    url = update.message.text.strip()
    stats.record_request(user_id)

    # 1. URL validation
    is_valid, validation_reason = is_valid_url(url)
    if not is_valid:
        await update.message.reply_text(
            f"❌ *Invalid URL*\n\n{validation_reason}\n\nPlease check the link and try again.",
            parse_mode=ParseMode.MARKDOWN
        )
        return

    # 2. Rate limiting check
    allowed, wait_seconds = rate_limiter.check(user_id)
    if not allowed:
        await update.message.reply_text(
            f"⏳ *Please slow down!*\n\nPlease wait *{wait_seconds} seconds* before sending another request.",
            parse_mode=ParseMode.MARKDOWN
        )
        return

    # 3. Status notification
    status_msg = await update.message.reply_text("🔍 *Inspecting media URL...*", parse_mode=ParseMode.MARKDOWN)

    try:
        # 4. Media inspection
        inspection = await inspect_media_url(url)

        if not inspection.is_supported or inspection.error_message:
            stats.record_failure()
            error_text = inspection.error_message or "Unsupported URL or media format."
            await status_msg.edit_text(error_text)
            return

        # 5. Process media download
        size_display = format_bytes(inspection.file_size_bytes) if inspection.file_size_bytes > 0 else "streaming"
        await status_msg.edit_text(
            f"⬇️ *Downloading media...*\n\n"
            f"• Format: `{inspection.media_type.upper()}`\n"
            f"• Name: `{inspection.filename}`\n"
            f"• Size: *{size_display}*",
            parse_mode=ParseMode.MARKDOWN
        )

        # Trigger chat action (e.g. typing or recording video)
        if inspection.media_type == "video":
            await update.effective_chat.send_action(action=ChatAction.UPLOAD_VIDEO)
        else:
            await update.effective_chat.send_action(action=ChatAction.UPLOAD_VOICE)

        # 6. Execute safe download
        download_result = await download_media(url, inspection)

        if not download_result.success or not download_result.file_path:
            stats.record_failure()
            await status_msg.edit_text(f"❌ *Download Failed*\n\n{download_result.error_message}", parse_mode=ParseMode.MARKDOWN)
            return

        # 7. Upload to user via Telegram
        await status_msg.edit_text("📤 *Uploading media to Telegram...*", parse_mode=ParseMode.MARKDOWN)

        file_path = download_result.file_path
        try:
            with open(file_path, "rb") as f:
                caption = f"🎬 {download_result.filename}\n📦 Size: {format_bytes(download_result.file_size_bytes)}"
                if download_result.media_type == "video":
                    await update.effective_chat.send_action(action=ChatAction.UPLOAD_VIDEO)
                    await update.message.reply_video(
                        video=f,
                        caption=caption,
                        supports_streaming=True
                    )
                elif download_result.media_type == "audio":
                    await update.effective_chat.send_action(action=ChatAction.UPLOAD_VOICE)
                    await update.message.reply_audio(
                        audio=f,
                        caption=caption
                    )
                else:
                    await update.effective_chat.send_action(action=ChatAction.UPLOAD_DOCUMENT)
                    await update.message.reply_document(
                        document=f,
                        caption=caption
                    )

            stats.record_success()
            await status_msg.delete()

        except Exception as upload_err:
            stats.record_failure()
            logger.error("Telegram upload failed: %s", type(upload_err).__name__)
            await status_msg.edit_text(
                "⚠️ *Upload Failed*\n\nTelegram was unable to receive this file. "
                "The media might exceed Telegram's size or format constraints."
            )
        finally:
            # Always clean up temporary disk storage
            cleanup_temp_file(file_path)

    except Exception as exc:
        stats.record_failure()
        logger.error("Unhandled error processing URL: %s", type(exc).__name__)
        await status_msg.edit_text(
            "⚠️ An unexpected server error occurred while processing your request. Please try again later."
        )


# -------------------------------------------------------------------
# Admin Handlers
# -------------------------------------------------------------------

def _is_admin(user_id: int) -> bool:
    """Checks if the user matches the configured ADMIN_USER_ID."""
    return config.admin_user_id is not None and user_id == config.admin_user_id


async def admin_stats(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Shows operational statistics (Admin only)."""
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
        f"• *Max File Size Limit:* {config.max_file_size_mb} MB\n"
        f"• *Rate Limit:* {config.rate_limit_seconds}s cooldown"
    )
    await update.message.reply_text(stats_msg, parse_mode=ParseMode.MARKDOWN)


async def admin_status(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Shows system and runtime status (Admin only)."""
    if not update.effective_user or not update.message:
        return

    if not _is_admin(update.effective_user.id):
        await update.message.reply_text("⛔ This command is restricted to bot administrators.")
        return

    status_msg = (
        "⚙️ *System & Bot Status*\n\n"
        f"• *Status:* 🟢 Online and healthy\n"
        f"• *Python Version:* `{platform.python_version()}`\n"
        f"• *OS:* `{platform.system()} {platform.release()}`\n"
        f"• *Admin ID Configured:* {'✅ Yes' if config.admin_user_id else '❌ No'}\n"
        f"• *Bot Token:* `{mask_token(config.bot_token)}`"
    )
    await update.message.reply_text(status_msg, parse_mode=ParseMode.MARKDOWN)


async def admin_users(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Shows active user counts (Admin only)."""
    if not update.effective_user or not update.message:
        return

    if not _is_admin(update.effective_user.id):
        await update.message.reply_text("⛔ This command is restricted to bot administrators.")
        return

    total = len(stats.unique_users)
    await update.message.reply_text(
        f"👥 *Registered User Count:* {total} user(s) recorded since last start.",
        parse_mode=ParseMode.MARKDOWN
    )


async def admin_broadcast(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Broadcasts a message to all users who have interacted with the bot (Admin only)."""
    if not update.effective_user or not update.message:
        return

    if not _is_admin(update.effective_user.id):
        await update.message.reply_text("⛔ This command is restricted to bot administrators.")
        return

    if not context.args:
        await update.message.reply_text("⚠️ Usage: `/broadcast <your message here>`", parse_mode=ParseMode.MARKDOWN)
        return

    broadcast_content = " ".join(context.args)
    sent_count = 0
    fail_count = 0

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

    await status_msg.edit_text(
        f"✅ *Broadcast Complete*\n\n"
        f"• Delivered: {sent_count}\n"
        f"• Failed/Blocked: {fail_count}",
        parse_mode=ParseMode.MARKDOWN
    )


# -------------------------------------------------------------------
# Main Bot Initialization
# -------------------------------------------------------------------

def main() -> None:
    """Starts the Telegram bot application."""
    if not config.bot_token:
        logger.critical(
            "FATAL: BOT_TOKEN is empty. "
            "Please configure your BOT_TOKEN environment variable in .env or your host environment."
        )
        sys.exit(1)

    logger.info("Initializing Telegram Video Downloader Bot...")
    logger.info("Admin User ID configured: %s", "Yes" if config.admin_user_id else "None")
    logger.info("Max file size limit: %d MB", config.max_file_size_mb)

    # Build the Application instance
    app = Application.builder().token(config.bot_token).build()

    # User command handlers
    app.add_handler(CommandHandler("start", start_command))
    app.add_handler(CommandHandler("help", help_command))

    # Admin command handlers
    app.add_handler(CommandHandler("stats", admin_stats))
    app.add_handler(CommandHandler("status", admin_status))
    app.add_handler(CommandHandler("users", admin_users))
    app.add_handler(CommandHandler("broadcast", admin_broadcast))

    # URL / message handler (handles any message that looks like a URL or starts with http)
    app.add_handler(MessageHandler(filters.TEXT & (filters.Entity("url") | filters.Regex(r"^https?://")), handle_url_message))

    logger.info("Bot started successfully. Listening for updates...")
    # Run the polling loop
    app.run_polling(drop_pending_updates=True)


if __name__ == "__main__":
    main()
