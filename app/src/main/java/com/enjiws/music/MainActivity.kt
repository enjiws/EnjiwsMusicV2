package com.enjiws.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.enjiws.music.ui.theme.AmoledBlack
import com.enjiws.music.ui.theme.EnjiwsTheme
import com.enjiws.music.ui.theme.NeonGreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EnjiwsTheme {
                var currentTab by remember { mutableIntStateOf(0) }
                
                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = AmoledBlack,
                            contentColor = Color.White
                        ) {
                            NavigationBarItem(
                                selected = currentTab == 0,
                                onClick = { currentTab = 0 },
                                label = { Text("Library") },
                                icon = { Text("🎵") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeonGreen,
                                    selectedTextColor = NeonGreen,
                                    indicatorColor = AmoledBlack
                                )
                            )
                            NavigationBarItem(
                                selected = currentTab == 1,
                                onClick = { currentTab = 1 },
                                label = { Text("Now Playing") },
                                icon = { Text("🎧") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeonGreen,
                                    selectedTextColor = NeonGreen,
                                    indicatorColor = AmoledBlack
                                )
                            )
                            NavigationBarItem(
                                selected = currentTab == 2,
                                onClick = { currentTab = 2 },
                                label = { Text("Online") },
                                icon = { Text("🌐") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeonGreen,
                                    selectedTextColor = NeonGreen,
                                    indicatorColor = AmoledBlack
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(AmoledBlack)
                    ) {
                        when (currentTab) {
                            0 -> LibraryScreen()
                            1 -> NowPlayingScreen(
                                trackName = "Midnight City Pulse",
                                artist = "dingekk",
                                isPlaying = true
                            )
                            2 -> OnlineScreen()
                        }
                    }
                }
            }
        }
    }
}
