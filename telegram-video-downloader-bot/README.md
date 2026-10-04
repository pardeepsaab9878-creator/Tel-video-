# 📥 Telegram Video Downloader Bot

A reliable, safe, and modular Telegram bot built with **Python 3.11+**, `python-telegram-bot` (v21+ async), and `httpx`. Designed to process authorized and direct downloadable media files (MP4, WEBM, MP3, etc.) with built-in per-user rate limiting, SSRF security guards, and administrator controls.

> 📱 **Phone & Android Friendly:** Simple to configure, clone, and manage directly from an Android device using **Termux**, GitHub Mobile, or cloud hosts.

---

## ⚖️ Legal & Safety Policy

This bot strictly adheres to platform terms of service and security standards:
- **No DRM or Encryption Bypass:** Does not decrypt or download Widevine, FairPlay, or segmented protected streams (e.g. DRM HLS/DASH).
- **No Paywall or Authentication Bypasses:** Does not access private accounts, subscription paywalls, or protected platforms.
- **Direct Permitted Content Only:** Processes direct media URLs that are publicly accessible and authorized for download.
- **Telegram Size Constraints:** Enforces Telegram's **50 MB** maximum file limit for standard bots.

---

## 🚀 Quick Setup (From Android or PC)

### Option A: Running from an Android Phone (using Termux)

1. **Install Termux** from [F-Droid](https://f-droid.org/packages/com.termux/) (do not use Google Play Termux as it is deprecated).
2. Open Termux and update packages:
   ```bash
   pkg update && pkg upgrade -y
   pkg install python git -y
   ```
3. Clone your repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/telegram-video-downloader-bot.git
   cd telegram-video-downloader-bot
   ```
4. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```
5. Create your `.env` configuration:
   ```bash
   cp .env.example .env
   nano .env
   ```
   *(Fill in your `BOT_TOKEN` and `ADMIN_USER_ID`, then press `Ctrl + O`, `Enter`, and `Ctrl + X` to save).*
6. Start the bot:
   ```bash
   python bot.py
   ```

---

### Option B: Running on Linux / macOS / Windows PC

1. **Clone repository:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/telegram-video-downloader-bot.git
   cd telegram-video-downloader-bot
   ```
2. **Create and activate virtual environment:**
   ```bash
   python3 -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```
3. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   ```
4. **Setup `.env`:**
   ```bash
   cp .env.example .env
   # Edit .env with your actual tokens
   ```
5. **Run bot:**
   ```bash
   python bot.py
   ```

---

## 🔐 Environment Variables (`.env`)

| Variable | Description | Default |
|---|---|---|
| `BOT_TOKEN` | Token provided by `@BotFather` | *Required* |
| `ADMIN_USER_ID` | Your numeric Telegram ID for admin commands | *Optional* |
| `MAX_FILE_SIZE_MB` | Maximum allowed media download size (Telegram limit is 50MB) | `50` |
| `RATE_LIMIT_SECONDS` | Cooldown period between user download requests | `10` |
| `REQUEST_TIMEOUT_SECONDS` | Timeout for media server connections | `60` |

---

## 🛠️ Bot Commands

### For All Users:
- `/start` — Welcome message and instructions.
- `/help` — Guidelines on permitted links and technical limitations.
- `<Any Direct URL>` — Validates, inspects, streams, and delivers the media file.

### For Bot Admin:
- `/stats` — Uptime, total requests, success/fail counts, registered users.
- `/status` — System memory, Python runtime version, online health check.
- `/users` — Registered unique user count.
- `/broadcast <msg>` — Send an announcement message to all users.

---

## 🧪 Testing Your Bot

1. **Test `/start`:**
   Open Telegram, search for your bot username, and press **Start** or send `/start`. You should receive the greeting message.

2. **Test Direct Media Download:**
   Send this public domain sample test video link to your bot:
   ```
   https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4
   ```
   The bot will inspect headers, download the file into temporary storage, upload it as a native playable Telegram video, and clean up the storage immediately.

---

## 🛡️ Finding Your Telegram User ID Safely

Never give your bot token or credentials to third-party bots! To find your own Telegram User ID:
1. Open Telegram and search for official utility bots such as `@userinfobot` or `@raw_data_bot`.
2. Tap **Start**.
3. It will reply with your numeric `Id` (e.g. `123456789`).
4. Copy this number and paste it as `ADMIN_USER_ID` in your `.env`.

---

## 🤖 GitHub Actions & 24/7 Hosting Note

The `.github/workflows/bot.yml` file is configured for **Continuous Integration (CI)**:
- Checks code formatting and syntax.
- Runs automated tests via `pytest`.
- **Important:** GitHub Actions is **NOT** meant to run bots 24/7 (doing so will violate GitHub's Terms of Service and cause account suspension).
- **Recommended Free/Low-Cost 24/7 Hosting:**
  - **Render.com** (Free background worker tier)
  - **Railway.app**
  - **Fly.io**
  - **Oracle Cloud Free Tier** or a $3-5/mo VPS
  - Or simply keep it running on your Android phone using **Termux + termux-wake-lock**!
