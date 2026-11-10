package com.enjiws.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.enjiws.music.data.UiBus
import kotlinx.coroutines.launch

private data class TopDest(val route: String, val label: String, val icon: ImageVector)

private val topDests = listOf(
    TopDest("home", "Beranda", Icons.Rounded.Home),
    TopDest("search", "Cari", Icons.Rounded.Search),
    TopDest("library", "Perangkat", Icons.Rounded.LibraryMusic),
    TopDest("playlists", "Playlist", Icons.AutoMirrored.Rounded.PlaylistPlay)
)

@Composable
fun MainApp(onExport: () -> Unit, onImport: () -> Unit) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val topLevel = topDests.any { it.route == route }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        UiBus.messages.collect { msg ->
            launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(msg, duration = SnackbarDuration.Short)
            }
        }
    }

    fun go(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    TrackMenuHost {
        val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
        Box(Modifier.fillMaxSize().then(if (darkTheme) Modifier.aurora() else Modifier.background(MaterialTheme.colorScheme.background))) {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column {
                        MiniPlayer(onOpen = { showPlayer = true })
                        if (topLevel) {
                            Box(Modifier.navigationBarsPadding()) { PremiumNavBar(route) { go(it) } }
                        } else {
                            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                        }
                    }
                }
            ) { padding ->
                NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                    composable("home") {
                        HomeScreen(
                            onOpenSearch = { go("search") },
                            onOpenSettings = { nav.navigate("settings") },
                            onOpenPlaylist = { nav.navigate("playlist/$it") },
                            onOpenHistory = { nav.navigate("collection/${CollectionRegistry.put("history", "")}") }
                        )
                    }
                    composable("search") { SearchScreen() }
                    composable("library") {
                        LibraryScreen(onOpenCollection = { k, n -> nav.navigate("collection/${CollectionRegistry.put(k, n)}") })
                    }
                    composable("playlists") { PlaylistsScreen(onOpenPlaylist = { nav.navigate("playlist/$it") }) }
                    composable("playlist/{id}") { entry ->
                        PlaylistDetailScreen(entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                    }
                    composable("collection/{cid}", arguments = listOf(navArgument("cid") { type = NavType.IntType })) { entry ->
                        val pair = CollectionRegistry.get(entry.arguments?.getInt("cid") ?: -1)
                        if (pair == null) {
                            LaunchedEffect(Unit) { nav.popBackStack() }
                        } else {
                            val (kind, name) = pair
                            val tracks = resolveCollection(kind, name)
                            CollectionScreen(
                                title = if (kind == "history") "Riwayat putar" else name,
                                subtitle = "${tracks.size} lagu", tracks = tracks, onBack = { nav.popBackStack() }
                            )
                        }
                    }
                    composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }, onExport = onExport, onImport = onImport) }
                }
            }

            AnimatedVisibility(
                visible = showPlayer,
                enter = slideInVertically { it }, exit = slideOutVertically { it }
            ) {
                PlayerScreen(onClose = { showPlayer = false }, onQueue = { showQueue = true })
            }
            if (showQueue) QueueSheet { showQueue = false }
            BackHandler(enabled = showPlayer) { showPlayer = false }
        }
    }
}

/** Bilah navigasi melayang: pil kaca, tab aktif berubah jadi pil bergradien dengan label. */
@Composable
private fun PremiumNavBar(current: String?, onSelect: (String) -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, GlassBorder),
        shadowElevation = 16.dp
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            topDests.forEach { d ->
                val selected = current == d.route
                Row(
                    Modifier.pressable { onSelect(d.route) }
                        .clip(RoundedCornerShape(24.dp))
                        .then(if (selected) Modifier.background(AccentGradient) else Modifier)
                        .padding(horizontal = if (selected) 18.dp else 14.dp, vertical = 12.dp)
                        .animateContentSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(d.icon, d.label, tint = if (selected) Color(0xFF0B1500) else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Text(d.label, color = Color(0xFF0B1500), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
