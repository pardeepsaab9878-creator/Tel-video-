package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PhoneSetupScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied: $label", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Android & Phone Setup Guide",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Run & manage your bot directly from Termux without a PC!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Section 1: Bot Token & Admin ID
        item {
            GuideSectionCard(
                stepNumber = "1",
                title = "Get Bot Token & Telegram User ID",
                icon = Icons.Default.Key
            ) {
                Text(
                    "1. Open Telegram and search for @BotFather.\n" +
                    "2. Send /newbot and choose a display name and unique username ending in 'bot'.\n" +
                    "3. Copy your HTTP API token (format: 123456789:ABCdef...). Keep it secret!",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Finding your Telegram ADMIN_USER_ID safely:\n" +
                    "• Open @userinfobot or @raw_data_bot in Telegram.\n" +
                    "• Tap Start; it displays your numeric Id (e.g. 987654321).\n" +
                    "• Never give your Bot Token to third-party ID checker bots!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section 2: Termux setup
        item {
            GuideSectionCard(
                stepNumber = "2",
                title = "Install & Run on Phone (Termux)",
                icon = Icons.Default.Terminal
            ) {
                Text(
                    "Download Termux from F-Droid (do not use Google Play Termux). Then run these commands:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))

                CommandBox(
                    title = "Update packages and install Python & Git",
                    command = "pkg update && pkg install python git -y",
                    onCopy = { copyToClipboard("Install packages", "pkg update && pkg install python git -y") }
                )

                Spacer(Modifier.height(8.dp))
                CommandBox(
                    title = "Clone your bot repository",
                    command = "git clone https://github.com/YOUR_USER/telegram-video-downloader-bot.git\ncd telegram-video-downloader-bot",
                    onCopy = { copyToClipboard("Clone repo", "git clone https://github.com/YOUR_USER/telegram-video-downloader-bot.git && cd telegram-video-downloader-bot") }
                )

                Spacer(Modifier.height(8.dp))
                CommandBox(
                    title = "Install Python dependencies",
                    command = "pip install -r requirements.txt",
                    onCopy = { copyToClipboard("Install dependencies", "pip install -r requirements.txt") }
                )

                Spacer(Modifier.height(8.dp))
                CommandBox(
                    title = "Configure .env file",
                    command = "cp .env.example .env && nano .env",
                    onCopy = { copyToClipboard("Setup .env", "cp .env.example .env && nano .env") }
                )

                Spacer(Modifier.height(8.dp))
                CommandBox(
                    title = "Start the bot!",
                    command = "python bot.py",
                    onCopy = { copyToClipboard("Run bot", "python bot.py") }
                )

                Spacer(Modifier.height(8.dp))
                CommandBox(
                    title = "Keep running when phone screen is locked",
                    command = "termux-wake-lock",
                    onCopy = { copyToClipboard("Wake lock", "termux-wake-lock") }
                )
            }
        }

        // Section 3: Testing Guide
        item {
            GuideSectionCard(
                stepNumber = "3",
                title = "Testing Your Bot",
                icon = Icons.Default.CheckCircle
            ) {
                Text(
                    "Testing /start:\n" +
                    "• Open your bot in Telegram and send /start.\n" +
                    "• The bot responds with the welcome banner and /help guide.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Testing Permitted/Direct Media URL:\n" +
                    "• Copy and send this public test video URL to your bot:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                CommandBox(
                    title = "Sample Direct MP4 Video URL (Google Cloud Public Bucket)",
                    command = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    onCopy = { copyToClipboard("Test URL", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4") }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Testing Admin Commands:\n" +
                    "• As Admin, test: /stats, /status, /users\n" +
                    "• From any other account, ensure admin commands are rejected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section 4: GitHub Secrets Setup
        item {
            GuideSectionCard(
                stepNumber = "4",
                title = "GitHub Secrets Setup",
                icon = Icons.Default.Security
            ) {
                Text(
                    "How to safely store secrets in GitHub without committing them:\n\n" +
                    "1. Open your repository on GitHub.\n" +
                    "2. Navigate to: Settings > Secrets and variables > Actions.\n" +
                    "3. Click 'New repository secret'.\n" +
                    "4. Add Secret: BOT_TOKEN (value: your bot token from @BotFather).\n" +
                    "5. Add Secret: ADMIN_USER_ID (value: your numeric ID).\n\n" +
                    "💡 CI Note: The included .github/workflows/bot.yml uses mock tokens for tests so real tokens are never exposed in build logs.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Section 5: Troubleshooting Common Errors
        item {
            GuideSectionCard(
                stepNumber = "5",
                title = "Troubleshooting Common Errors",
                icon = Icons.Default.BugReport
            ) {
                TroubleItem(
                    error = "InvalidToken: The token `...` was rejected by Telegram",
                    fix = "Double check the token copied from @BotFather. Ensure there are no accidental spaces or linebreaks in .env."
                )
                TroubleItem(
                    error = "Access Restricted (HTTP 401/403)",
                    fix = "The requested URL requires login credentials, cookies, or is paywalled. The bot strictly rejects restricted content."
                )
                TroubleItem(
                    error = "Telegram API error: Request Entity Too Large",
                    fix = "Telegram standard bots cannot upload files larger than 50MB. Downloader automatically rejects files exceeding 50MB."
                )
                TroubleItem(
                    error = "Protected or Segmented format detected (.m3u8, HLS)",
                    fix = "Segmented streaming URLs with DRM cannot be downloaded directly. Only standalone .mp4, .webm, .mp3 links are supported."
                )
                TroubleItem(
                    error = "Bot dies when switching apps on Android phone",
                    fix = "Run `termux-wake-lock` in Termux, and disable Android battery optimization for the Termux app."
                )
            }
        }
    }
}

@Composable
fun GuideSectionCard(
    stepNumber: String,
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stepNumber, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun CommandBox(
    title: String,
    command: String,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E2836))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 11.sp, color = Color(0xFF90CAF9), fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy command", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            command,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color(0xFF80D4FF)
        )
    }
}

@Composable
fun TroubleItem(error: String, fix: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("⚠️ $error", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        Text("💡 Fix: $fix", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
    }
}
