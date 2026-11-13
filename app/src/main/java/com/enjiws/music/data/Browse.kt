package com.enjiws.music.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/** Satu kartu hasil jelajah: album, artis, atau playlist. */
data class BrowseItem(val kind: String, val name: String, val subtitle: String, val thumb: String, val url: String)

data class ArtistData(val songs: List<Track>, val albums: List<BrowseItem>)

/** Campuran lagu (mis. Daily Mix). */
data class Mix(val title: String, val subtitle: String, val tracks: List<Track>)

/** Argumen rute navigasi dipegang di sini supaya tidak perlu menyandikan URL ke dalam rute. */
object BrowseRegistry {
    private val items = mutableListOf<BrowseItem>()
    fun put(i: BrowseItem): Int {
        val idx = items.indexOfFirst { it.url == i.url && it.kind == i.kind }
        if (idx >= 0) return idx
        items += i
        return items.lastIndex
    }
    fun get(id: Int): BrowseItem? = items.getOrNull(id)
}

/** Bagian-bagian Beranda yang bisa dinyalakan atau dimatikan. Triple = (kunci, nama, keterangan). */
object HomeSections {
    val all = listOf(
        Triple("continue", "Lanjutkan mendengarkan", "Kartu besar lagu terakhir"),
        Triple("recent", "Terakhir diputar", "Riwayat putar terbaru"),
        Triple("mix", "Daily Mix", "Campuran dari artis yang sering kamu dengar (online)"),
        Triple("artists", "Artis favoritmu", "Dari riwayat putarmu (online)"),
        Triple("albums", "Album untukmu", "Dari artis favoritmu (online)"),
        Triple("popular", "Populer sekarang", "Dari pencarian YouTube Music (online)"),
        Triple("releases", "Rilis baru", "Dari pencarian YouTube Music (online)"),
        Triple("favorites", "Favorit", "Lagu yang kamu tandai"),
        Triple("playlists", "Playlist kamu", "Playlist buatanmu"),
        Triple("frequent", "Paling sering diputar", "Dari riwayat putarmu"),
        Triple("newest", "Baru di perangkat", "Musik yang baru masuk ke HP")
    )
}

/** Jelajah YouTube Music: pencarian per kategori, halaman album dan artis. Hasil di-cache 6 jam. */
object Browse {
    private const val TTL = 6 * 60 * 60_000L
    private val cache = ConcurrentHashMap<String, Pair<Any, Long>>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, block: suspend () -> T): T {
        cache[key]?.takeIf { it.second > System.currentTimeMillis() }?.let { return it.first as T }
        val v = block()
        cache[key] = v to System.currentTimeMillis() + TTL
        return v
    }

    private fun thumb(images: List<Image>): String =
        images.maxByOrNull { it.getHeight() }?.getUrl() ?: images.firstOrNull()?.getUrl() ?: ""

    private fun filterFor(kind: String): String = when (kind) {
        "album" -> YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS
        "artist" -> YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS
        "playlist" -> YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS
        else -> YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
    }

    private fun subs(n: Long): String = when {
        n < 0 -> ""
        n >= 1_000_000 -> "%.1f jt pelanggan".format(n / 1_000_000.0)
        n >= 1_000 -> "${n / 1_000} rb pelanggan"
        else -> "$n pelanggan"
    }

    private fun toItem(kind: String, i: InfoItem): BrowseItem? = when (i) {
        is PlaylistInfoItem -> BrowseItem(
            kind = kind,
            name = i.getName().orEmpty(),
            subtitle = listOfNotNull(
                i.getUploaderName()?.removeSuffix(" - Topic"),
                i.getStreamCount().takeIf { it > 0 }?.let { "$it lagu" }
            ).joinToString(" · "),
            thumb = thumb(i.getThumbnails()),
            url = i.getUrl()
        )
        is ChannelInfoItem -> BrowseItem("artist", i.getName().orEmpty(), subs(i.getSubscriberCount()), thumb(i.getThumbnails()), i.getUrl())
        else -> null
    }

    /** Cari album / artis / playlist (halaman pertama). */
    suspend fun search(kind: String, query: String): List<BrowseItem> = cached("s|$kind|${query.lowercase()}") {
        withContext(Dispatchers.IO) {
            runInterruptible {
                val service = ServiceList.YouTube
                val handler = service.getSearchQHFactory().fromQuery(query, listOf(filterFor(kind)), "")
                val ex = service.getSearchExtractor(handler)
                ex.fetchPage()
                ex.getInitialPage().getItems().mapNotNull { toItem(kind, it) }.filter { it.name.isNotBlank() }
            }
        }
    }

    /** Cari lagu di katalog YouTube Music (halaman pertama). */
    suspend fun songs(query: String): List<Track> = cached("songs|${query.lowercase()}") {
        withContext(Dispatchers.IO) {
            runInterruptible {
                val service = ServiceList.YouTube
                val handler = service.getSearchQHFactory().fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "")
                val ex = service.getSearchExtractor(handler)
                ex.fetchPage()
                ex.getInitialPage().getItems().filterIsInstance<StreamInfoItem>().map { it.toYtTrack() }.filter { it.title.isNotBlank() }
            }
        }
    }

    /** Isi sebuah album / playlist. Semua lagu diberi sampul dan nama album yang sama. */
    suspend fun album(url: String): List<Track> = cached("album|$url") {
        withContext(Dispatchers.IO) {
            runInterruptible {
                val ex = ServiceList.YouTube.getPlaylistExtractor(url)
                ex.fetchPage()
                val cover = thumb(ex.getThumbnails())
                val name = ex.getName().orEmpty()
                ex.getInitialPage().getItems().filterIsInstance<StreamInfoItem>().map { it.toYtTrack() }
                    .filter { it.title.isNotBlank() }
                    .map { it.copy(artworkUrl = cover.ifBlank { it.artworkUrl }, album = name) }
            }
        }
    }

    /** Halaman artis: lagu populer dan album, dicari lewat katalog YouTube Music. */
    suspend fun artist(name: String): ArtistData = coroutineScope {
        val s = async { runCatching { songs(name) }.getOrDefault(emptyList()) }
        val a = async { runCatching { search("album", name) }.getOrDefault(emptyList()) }
        val allSongs = s.await()
        val allAlbums = a.await()
        val mine = allSongs.filter { it.artist.contains(name, true) }
        ArtistData(
            songs = mine.ifEmpty { allSongs }.take(20),
            albums = allAlbums.filter { it.subtitle.contains(name, true) }.ifEmpty { allAlbums }.take(12)
        )
    }
}

/**
 * Isi Beranda yang diambil online: Daily Mix, artis, album, populer, rilis baru.
 * Personalisasi memakai riwayat putarmu. "Populer" dan "Rilis baru" berasal dari pencarian YouTube Music,
 * bukan data tangga lagu resmi (YouTube tidak menyediakan API chart publik).
 */
object HomeFeed {
    var mixes by mutableStateOf<List<Mix>>(emptyList()); private set
    var albums by mutableStateOf<List<BrowseItem>>(emptyList()); private set
    var artists by mutableStateOf<List<BrowseItem>>(emptyList()); private set
    var popular by mutableStateOf<List<Track>>(emptyList()); private set
    var releases by mutableStateOf<List<BrowseItem>>(emptyList()); private set
    var loading by mutableStateOf(false); private set

    private var loadedKey = ""
    private var loadedAt = 0L

    /** Artis yang paling sering didengar, dari riwayat putar. */
    fun topArtists(limit: Int = 6): List<String> {
        val skip = setOf("", "YouTube", "Artis tidak dikenal")
        return Store.history
            .filter { it.track.artist !in skip }
            .groupBy { it.track.artist }
            .mapValues { e -> e.value.sumOf { it.plays } }
            .entries.sortedByDescending { it.value }
            .take(limit).map { it.key }
    }

    fun refresh(force: Boolean = false) {
        val top = topArtists()
        val key = top.joinToString("|") + "#" + Prefs.hiddenSections.sorted().joinToString(",")
        val now = System.currentTimeMillis()
        if (!force && key == loadedKey && now - loadedAt < 30 * 60_000L) return
        loadedKey = key
        loadedAt = now
        loading = true
        AppScope.launch {
            try {
                coroutineScope {
                    val year = Calendar.getInstance().get(Calendar.YEAR)
                    if (Prefs.sectionOn("popular")) launch {
                        runCatching { Browse.songs("lagu terpopuler $year") }.onSuccess { popular = it.take(20) }
                    }
                    if (Prefs.sectionOn("releases")) launch {
                        runCatching { Browse.search("album", "album baru $year") }.onSuccess { releases = it.take(12) }
                    }
                    if (top.isNotEmpty()) {
                        if (Prefs.sectionOn("mix")) launch { mixes = buildMixes(top.take(3)) }
                        if (Prefs.sectionOn("artists")) launch { artists = findArtists(top) }
                        if (Prefs.sectionOn("albums")) launch { albums = findAlbums(top.take(2)) }
                    }
                }
            } finally {
                loading = false
            }
        }
    }

    private suspend fun buildMixes(names: List<String>): List<Mix> = coroutineScope {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        names.mapIndexed { i, artist ->
            async {
                runCatching {
                    val all = Browse.songs(artist)
                    val songs = all.filter { it.artist.contains(artist, true) }.ifEmpty { all }
                    val seed = songs.firstOrNull()
                    val related = if (seed != null) runCatching { Catalog.related(seed) }.getOrDefault(emptyList()) else emptyList()
                    // Acak yang stabil per hari: isi mix berganti setiap hari, bukan setiap dibuka.
                    val tracks = (songs.take(8) + related.take(20)).distinctBy { it.key }
                        .shuffled(kotlin.random.Random(day * 31 + i)).take(25)
                    if (tracks.size < 5) null else {
                        val others = tracks.map { it.artist }.filter { it != artist }.distinct().take(2)
                        Mix("Daily Mix ${i + 1}", (listOf(artist) + others).joinToString(", "), tracks)
                    }
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun findArtists(names: List<String>): List<BrowseItem> = coroutineScope {
        names.map { n -> async { runCatching { Browse.search("artist", n).firstOrNull() }.getOrNull() } }
            .awaitAll().filterNotNull().distinctBy { it.url }
    }

    private suspend fun findAlbums(names: List<String>): List<BrowseItem> = coroutineScope {
        names.map { n ->
            async { runCatching { Browse.search("album", n).filter { it.subtitle.contains(n, true) }.take(6) }.getOrDefault(emptyList()) }
        }.awaitAll().flatten().distinctBy { it.url }
    }
}
