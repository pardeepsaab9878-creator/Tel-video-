package com.example.ui

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class InspectionReport(
    val statusCode: Int = 0,
    val contentType: String = "",
    val contentLengthBytes: Long = 0,
    val isSupported: Boolean = false,
    val verdict: String = "",
    val mediaType: String = "",
    val reason: String = "",
    val isError: Boolean = false
)

@Composable
fun UrlInspectorScreen(modifier: Modifier = Modifier) {
    var urlInput by remember { mutableStateOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4") }
    var isLoading by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<InspectionReport?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val client = remember {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun inspectUrl(targetUrl: String) {
        if (targetUrl.isBlank()) return
        isLoading = true
        report = null

        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", "Mozilla/5.0 (compatible; DirectMediaBot/1.0)")
                        .head()
                        .build()

                    var response = try {
                        client.newCall(request).execute()
                    } catch (e: Exception) {
                        // Fallback to GET with range
                        val rangeReq = Request.Builder()
                            .url(targetUrl)
                            .header("User-Agent", "Mozilla/5.0 (compatible; DirectMediaBot/1.0)")
                            .header("Range", "bytes=0-1024")
                            .get()
                            .build()
                        client.newCall(rangeReq).execute()
                    }

                    val code = response.code
                    val type = (response.header("Content-Type") ?: "").split(";")[0].trim().lowercase()
                    val length = response.header("Content-Length")?.toLongOrNull() ?: 0L
                    val maxBytes = 50 * 1024 * 1024

                    if (code in 401..403) {
                        InspectionReport(
                            statusCode = code,
                            contentType = type,
                            contentLengthBytes = length,
                            isSupported = false,
                            verdict = "ACCESS RESTRICTED",
                            reason = "Media requires authorization or is behind a paywall. The bot strictly does not bypass access restrictions.",
                            isError = true
                        )
                    } else if (code == 404) {
                        InspectionReport(
                            statusCode = code,
                            isSupported = false,
                            verdict = "NOT FOUND",
                            reason = "Server returned 404 Not Found. Please check the URL.",
                            isError = true
                        )
                    } else if (type.contains("mpegurl") || type.contains("dash") || targetUrl.contains(".m3u8") || targetUrl.contains(".mpd")) {
                        InspectionReport(
                            statusCode = code,
                            contentType = type,
                            contentLengthBytes = length,
                            isSupported = false,
                            verdict = "PROTECTED STREAM DETECTED",
                            reason = "HLS/DASH/DRM format detected. The bot processes standalone permitted files (MP4, MP3, etc.) and does not decrypt segmented streams.",
                            isError = true
                        )
                    } else if (type.contains("html")) {
                        InspectionReport(
                            statusCode = code,
                            contentType = type,
                            contentLengthBytes = length,
                            isSupported = false,
                            verdict = "WEB PAGE (NOT DIRECT MEDIA)",
                            reason = "This link points to an HTML webpage instead of a direct video or audio stream.",
                            isError = true
                        )
                    } else if (length > maxBytes) {
                        InspectionReport(
                            statusCode = code,
                            contentType = type,
                            contentLengthBytes = length,
                            isSupported = false,
                            verdict = "EXCEEDS SIZE LIMIT",
                            reason = "File size (${length / (1024 * 1024)} MB) exceeds Telegram's 50 MB standard bot limit.",
                            isError = true
                        )
                    } else if (type.startsWith("video/") || targetUrl.endsWith(".mp4") || targetUrl.endsWith(".webm")) {
                        InspectionReport(
                            statusCode = code,
                            contentType = type,
                            contentLengthBytes = length,
                            isSupported = true,
                            mediaType = "Video (🎬)",
                            verdict = "SUPPORTED DIRECT VIDEO",
                            reason = "Direct permitted video file under 50 MB. The bot will download and upload it natively.",
                            isError = false
                        )
                    } else if (type.startsWith("audio/") || targetUrl.endsWith(".mp3") || targetUrl.endsWith(".m4a")) {
                        InspectionReport(
                            statusCode = code,
                            contentType = type,
                            contentLengthBytes = length,
                            isSupported = true,
                            mediaType = "Audio (🎵)",
                            verdict = "SUPPORTED DIRECT AUDIO",
                            reason = "Direct permitted audio file under 50 MB. The bot will deliver it as a Telegram audio track.",
                            isError = false
                        )
                    } else {
                        InspectionReport(
                            statusCode = code,
                            contentType = type,
                            contentLengthBytes = length,
                            isSupported = false,
                            verdict = "UNSUPPORTED MIME TYPE",
                            reason = "Content-Type '$type' is not recognized as direct video/audio.",
                            isError = true
                        )
                    }
                } catch (e: Exception) {
                    InspectionReport(
                        isSupported = false,
                        verdict = "NETWORK ERROR",
                        reason = "Could not connect to URL host: ${e.message ?: "Connection failure"}",
                        isError = true
                    )
                }
            }
            report = result
            isLoading = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.TravelExplore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Live URL Inspector & Tester",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Test URLs live on your device to verify how the bot will inspect & process them.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Enter direct media URL") },
                placeholder = { Text("https://example.com/video.mp4") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
                trailingIcon = {
                    if (urlInput.isNotBlank()) {
                        IconButton(onClick = { urlInput = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )
        }

        // Quick sample chips
        item {
            Text("Try sample URLs:", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(
                    onClick = {
                        urlInput = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
                        inspectUrl(urlInput)
                    },
                    label = { Text("MP4 Video") }
                )
                SuggestionChip(
                    onClick = {
                        urlInput = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
                        inspectUrl(urlInput)
                    },
                    label = { Text("MP3 Audio") }
                )
                SuggestionChip(
                    onClick = {
                        urlInput = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                        inspectUrl(urlInput)
                    },
                    label = { Text("DRM / HLS") }
                )
            }
        }

        item {
            Button(
                onClick = { inspectUrl(urlInput) },
                enabled = !isLoading && urlInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Inspecting Headers...")
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Inspect URL")
                }
            }
        }

        // Report display
        item {
            report?.let { rep ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (rep.isSupported) Color(0xFF1B382B) else Color(0xFF381E24)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (rep.isSupported) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (rep.isSupported) Color(0xFF00E676) else Color(0xFFFF5252),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                rep.verdict,
                                fontWeight = FontWeight.Bold,
                                color = if (rep.isSupported) Color(0xFF00E676) else Color(0xFFFF5252),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(rep.reason, color = Color.White, style = MaterialTheme.typography.bodyMedium)

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                        Spacer(Modifier.height(8.dp))

                        if (rep.statusCode > 0) {
                            Text("HTTP Status: ${rep.statusCode}", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                        }
                        if (rep.contentType.isNotBlank()) {
                            Text("Content-Type: ${rep.contentType}", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                        }
                        if (rep.contentLengthBytes > 0) {
                            val mb = rep.contentLengthBytes.toDouble() / (1024 * 1024)
                            Text("Content-Length: ${"%.2f".format(mb)} MB (${rep.contentLengthBytes} bytes)", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                        }
                        if (rep.mediaType.isNotBlank()) {
                            Text("Detected Media: ${rep.mediaType}", color = Color(0xFF80D4FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
