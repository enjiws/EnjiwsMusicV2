package com.enjiws.music.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column {
                        MiniPlayer(onOpen = { showPlayer = true })
                        if (topLevel) {
                            NavigationBar {
                                topDests.forEach { d ->
                                    NavigationBarItem(
                                        selected = route == d.route, onClick = { go(d.route) },
                                        icon = { Icon(d.icon, d.label) }, label = { Text(d.label) }
                                    )
                                }
                            }
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
