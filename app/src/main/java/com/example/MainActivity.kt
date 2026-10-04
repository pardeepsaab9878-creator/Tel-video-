package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BotSimulatorScreen
import com.example.ui.CodeExplorerScreen
import com.example.ui.PhoneSetupScreen
import com.example.ui.UrlInspectorScreen
import com.example.ui.theme.MyApplicationTheme

enum class Screen(val title: String, val icon: ImageVector) {
    CODE("Code", Icons.Default.Terminal),
    PHONE_SETUP("Phone Setup", Icons.Default.PhoneAndroid),
    URL_INSPECTOR("URL Tester", Icons.Default.TravelExplore),
    SIMULATOR("Simulator", Icons.Default.SmartToy)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                TeleBotApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeleBotApp() {
    var currentScreen by remember { mutableStateOf(Screen.CODE) }

    // Enable hardware back button navigation to return to first tab if on another screen
    BackHandler(enabled = currentScreen != Screen.CODE) {
        currentScreen = Screen.CODE
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "TeleBot Manager",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "telegram-video-downloader-bot",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    AssistChip(
                        onClick = { currentScreen = Screen.PHONE_SETUP },
                        label = { Text("Termux Ready", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.values().forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.CODE -> CodeExplorerScreen()
                Screen.PHONE_SETUP -> PhoneSetupScreen()
                Screen.URL_INSPECTOR -> UrlInspectorScreen()
                Screen.SIMULATOR -> BotSimulatorScreen()
            }
        }
    }
}
