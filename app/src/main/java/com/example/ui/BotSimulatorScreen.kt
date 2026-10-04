package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String,
    val sender: String, // "user" or "bot"
    val text: String,
    val timestamp: String = "Just now"
)

@Composable
fun BotSimulatorScreen(modifier: Modifier = Modifier) {
    var isAdminMode by remember { mutableStateOf(true) }
    var inputText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(
                    id = "1",
                    sender = "bot",
                    text = "👋 Welcome to My Video Downloader Bot!\n\n" +
                           "📥 Send a permitted/direct media URL and I will process it.\n\n" +
                           "💡 Tips:\n" +
                           "• Direct links ending in .mp4, .webm, .mp3, etc.\n" +
                           "• Maximum file size: 50 MB (Telegram Bot limit)\n" +
                           "• Send /help for usage rules and supported formats."
                )
            )
        )
    }

    fun handleSend(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val userMsg = ChatMessage(id = System.currentTimeMillis().toString(), sender = "user", text = trimmed)
        val botReplyText: String = when {
            trimmed == "/start" -> {
                "👋 Welcome to My Video Downloader Bot!\n\n" +
                "📥 Send a permitted/direct media URL and I will process it.\n\n" +
                "💡 Tips:\n" +
                "• Direct links ending in .mp4, .webm, .mp3, etc.\n" +
                "• Maximum file size: 50 MB (Telegram Bot limit)\n" +
                "• Send /help for usage rules and supported formats."
            }
            trimmed == "/help" -> {
                "📖 Bot Usage & Guidelines\n\n" +
                "What is supported:\n" +
                "• Direct links to public video files (.mp4, .webm, .mov, .mkv)\n" +
                "• Direct links to public audio files (.mp3, .m4a, .ogg, .wav)\n" +
                "• Files up to 50 MB in size\n\n" +
                "Important Legal & Safety Restrictions:\n" +
                "• ❌ No bypassing of DRM, paywalls, or encryption (e.g. Widevine, HLS .m3u8).\n" +
                "• ❌ No private, authenticated, or login-protected accounts.\n" +
                "• ❌ Only media where you have permission or authorized direct access.\n" +
                "• ❌ Web pages with nested scripts/ads are not direct media files."
            }
            trimmed == "/stats" -> {
                if (isAdminMode) {
                    "📊 Bot Operational Statistics\n\n" +
                    "• Uptime: 4h 12m 30s\n" +
                    "• Total Requests: 42\n" +
                    "• Successful Downloads: 38\n" +
                    "• Failed Requests: 4\n" +
                    "• Unique Users Seen: 15\n" +
                    "• Max File Size Limit: 50 MB\n" +
                    "• Rate Limit: 10s cooldown"
                } else {
                    "⛔ This command is restricted to bot administrators."
                }
            }
            trimmed == "/status" -> {
                if (isAdminMode) {
                    "⚙️ System & Bot Status\n\n" +
                    "• Status: 🟢 Online and healthy\n" +
                    "• Python Version: 3.11.8\n" +
                    "• OS: Linux (Android Termux / VPS)\n" +
                    "• Admin ID Configured: ✅ Yes (987654321)\n" +
                    "• Bot Token: 123456...wxyz"
                } else {
                    "⛔ This command is restricted to bot administrators."
                }
            }
            trimmed == "/users" -> {
                if (isAdminMode) {
                    "👥 Registered User Count: 15 user(s) recorded since last start."
                } else {
                    "⛔ This command is restricted to bot administrators."
                }
            }
            trimmed.startsWith("/broadcast") -> {
                if (isAdminMode) {
                    val msg = trimmed.removePrefix("/broadcast").trim()
                    if (msg.isEmpty()) {
                        "⚠️ Usage: /broadcast <your message here>"
                    } else {
                        "✅ Broadcast Complete\n\n• Delivered: 15\n• Failed/Blocked: 0\n• Message: $msg"
                    }
                } else {
                    "⛔ This command is restricted to bot administrators."
                }
            }
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                if (trimmed.contains(".m3u8") || trimmed.contains(".mpd")) {
                    "⚠️ Protected or segmented streaming format detected (HLS/DASH/DRM).\n\n" +
                    "This bot only supports permitted direct video/audio files (e.g. MP4, MP3, WEBM), " +
                    "and does not bypass DRM or stream protections."
                } else if (trimmed.endsWith(".mp4") || trimmed.endsWith(".webm")) {
                    "⬇️ Downloading media...\n\n" +
                    "• Format: VIDEO\n" +
                    "• Name: sample_video.mp4\n" +
                    "• Size: 12.4 MB\n\n" +
                    "📤 Uploaded as playable native video to Telegram!"
                } else if (trimmed.endsWith(".mp3") || trimmed.endsWith(".m4a")) {
                    "⬇️ Downloading media...\n\n" +
                    "• Format: AUDIO\n" +
                    "• Name: audio_track.mp3\n" +
                    "• Size: 4.8 MB\n\n" +
                    "🎵 Uploaded as audio track to Telegram!"
                } else {
                    "ℹ️ URL inspected. Please make sure this is a direct media file (MP4, MP3, etc.) " +
                    "under 50 MB without login requirements."
                }
            }
            else -> {
                "❓ Unrecognized input. Please send a valid HTTP/HTTPS direct media link or type /help."
            }
        }

        val botMsg = ChatMessage(id = (System.currentTimeMillis() + 1).toString(), sender = "bot", text = botReplyText)
        messages = messages + userMsg + botMsg
        inputText = ""

        coroutineScope.launch {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Mode toggle header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = if (isAdminMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isAdminMode) "Simulating: Admin User" else "Simulating: Regular User",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Switch(
                    checked = isAdminMode,
                    onCheckedChange = { isAdminMode = it }
                )
            }
        }

        // Quick command chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(onClick = { handleSend("/start") }, label = { Text("/start") })
            AssistChip(onClick = { handleSend("/help") }, label = { Text("/help") })
            AssistChip(onClick = { handleSend("/stats") }, label = { Text("/stats") })
            AssistChip(onClick = { handleSend("/status") }, label = { Text("/status") })
        }

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isUser = msg.sender == "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 290.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomStart = if (isUser) 12.dp else 2.dp,
                                    bottomEnd = if (isUser) 2.dp else 12.dp
                                )
                            )
                            .background(
                                if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = msg.text,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Type command or paste URL...", fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { handleSend(inputText) },
                enabled = inputText.isNotBlank(),
                colors = IconButtonDefaults.filledIconButtonColors()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
