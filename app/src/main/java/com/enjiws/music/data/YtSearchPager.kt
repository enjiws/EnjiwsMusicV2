package com.enjiws.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Pencarian YouTube bertahap, selengkap di aplikasi YouTube. Dua umpan jalan bersamaan:
 *  - "lagu"  : katalog YouTube Music (lagu resmi)
 *  - "semua" : pencarian YouTube biasa (cover, live, remix, upload penggemar)
 * Hasilnya digabung selang-seling, duplikat dibuang. Halaman berikutnya diambil lewat [more].
 */
class YtSearchPager(private val query: String) {

    private class Feed(val filters: List<String>) {
        var extractor: SearchExtractor? = null
        var next: Page? = null
        var started = false
        var done = false
    }

    private val feeds = listOf(
        Feed(listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS)),
        Feed(emptyList())
    )

    /** True selama salah satu umpan masih punya halaman berikutnya. */
    val hasMore: Boolean get() = feeds.any { !it.done }

    suspend fun first(): List<Track> = coroutineScope {
        val results = feeds.map { f ->
            async(Dispatchers.IO) { runCatching { runInterruptible { fetch(f) } }.onFailure { f.done = true } }
        }.awaitAll()
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull() ?: IllegalStateException("Pencarian gagal")
        merge(results.map { it.getOrDefault(emptyList()) })
    }

    suspend fun more(): List<Track> = coroutineScope {
        val results = feeds.filter { !it.done }.map { f ->
            async(Dispatchers.IO) { runCatching { runInterruptible { fetch(f) } }.onFailure { f.done = true } }
        }.awaitAll()
        merge(results.map { it.getOrDefault(emptyList()) })
    }

    private fun fetch(f: Feed): List<Track> {
        val service = ServiceList.YouTube
        val page = if (!f.started) {
            val handler = service.getSearchQHFactory().fromQuery(query, f.filters, "")
            val ex = service.getSearchExtractor(handler)
            ex.fetchPage()
            f.extractor = ex
            f.started = true
            ex.getInitialPage()
        } else {
            val np = f.next
            if (np == null) { f.done = true; return emptyList() }
            f.extractor!!.getPage(np)
        }
        f.next = if (page.hasNextPage()) page.getNextPage() else null
        if (f.next == null) f.done = true
        return page.getItems().filterIsInstance<StreamInfoItem>().map { it.toYtTrack() }
    }

    // Selang-seling antar umpan supaya lagu resmi dan hasil biasa bercampur; duplikat dibuang.
    private fun merge(lists: List<List<Track>>): List<Track> {
        val max = lists.maxOfOrNull { it.size } ?: 0
        return buildList<Track> {
            for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let { add(it) } }
        }.distinctBy { it.key }.filter { it.title.isNotBlank() }
    }
}

internal fun StreamInfoItem.toYtTrack(): Track {
    val thumbs = getThumbnails()
    val thumb = thumbs.maxByOrNull { it.getHeight() }?.getUrl() ?: thumbs.firstOrNull()?.getUrl() ?: ""
    return Track(
        title = getName() ?: "",
        artist = (getUploaderName() ?: "YouTube").removeSuffix(" - Topic"),
        artworkUrl = thumb,
        durationSeconds = getDuration().coerceAtLeast(0),
        source = "YouTube",
        youtubeUrl = getUrl()
    )
}
