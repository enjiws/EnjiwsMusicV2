package com.enjiws.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.services.soundcloud.linkHandler.SoundcloudSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.URLEncoder

data class SearchOutcome(
    val tracks: List<Track>,
    val counts: Map<Source, Int>,
    val failed: List<Source>
)

/** Pencarian & rekomendasi dari semua sumber. Semua fungsi aman dipanggil dari coroutine mana pun. */
object Catalog {

    suspend fun search(query: String, sources: Set<Source>): SearchOutcome = supervisorScope {
        val q = query.trim()
        val responses = sources.map { s ->
            async(Dispatchers.IO) {
                s to runCatching { withTimeout(20_000L) { runInterruptible { searchOne(s, q) } } }
            }
        }.awaitAll()

        val found = responses.flatMap { (_, r) -> r.getOrDefault(emptyList()) }
            .filter { it.title.isNotBlank() }
            .distinctBy { it.key }
        // Judul yang persis sama dengan query naik ke atas; sisanya urutan asli sumber.
        val sorted = found.sortedWith(
            compareByDescending<Track> { it.title.equals(q, true) }
                .thenByDescending { it.title.contains(q, true) }
                .thenByDescending { it.canStream }
        )
        SearchOutcome(
            tracks = sorted,
            counts = responses.mapNotNull { (s, r) -> r.getOrNull()?.let { s to it.size } }.toMap(),
            failed = responses.filter { it.second.isFailure }.map { it.first }
        )
    }

    private val searchCache = java.util.concurrent.ConcurrentHashMap<String, Pair<List<Track>, Long>>()

    /** Cari di satu sumber (timeout 8 dtk, cache 5 mnt). Melempar exception kalau gagal. */
    suspend fun searchSource(source: Source, query: String): List<Track> {
        val q = query.trim()
        val key = source.name + "|" + q.lowercase()
        searchCache[key]?.takeIf { it.second > System.currentTimeMillis() }?.let { return it.first }
        val r = withTimeout(8_000L) { runInterruptible(Dispatchers.IO) { searchOne(source, q) } }
        searchCache[key] = r to System.currentTimeMillis() + 5 * 60_000L
        return r
    }

    fun sortResults(tracks: List<Track>, q: String): List<Track> = tracks
        .filter { it.title.isNotBlank() }
        .sortedWith(
            compareByDescending<Track> { it.title.equals(q, true) }
                .thenByDescending { it.title.contains(q, true) }
                .thenByDescending { it.canStream }
        )

    /** Memanaskan NewPipe di latar belakang supaya pencarian pertama tidak lambat. */
    fun warmUp() {
        runCatching {
            searchNewPipe(ServiceList.YouTube, "musik", listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "YouTube")
        }
    }

    private fun searchOne(source: Source, q: String): List<Track> = when (source) {
        Source.YouTube -> {
            val songs = runCatching {
                searchNewPipe(ServiceList.YouTube, q, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "YouTube")
            }.getOrDefault(emptyList())
            songs.ifEmpty { searchNewPipe(ServiceList.YouTube, q, emptyList(), "YouTube") }
        }
        Source.SoundCloud -> searchNewPipe(
            ServiceList.SoundCloud, q, listOf(SoundcloudSearchQueryHandlerFactory.TRACKS), "SoundCloud"
        )
        Source.Audius -> searchAudius(q)
        Source.Apple -> searchApple(q)
        Source.Piped -> Piped.search(q)
    }

    private fun searchNewPipe(service: StreamingService, q: String, filters: List<String>, label: String): List<Track> {
        val handler = service.getSearchQHFactory().fromQuery(q, filters, "")
        val extractor = service.getSearchExtractor(handler)
        extractor.fetchPage()
        return extractor.getInitialPage().getItems().filterIsInstance<StreamInfoItem>().map { it.toTrack(label) }
    }

    private fun StreamInfoItem.toTrack(label: String): Track {
        val thumbs = getThumbnails()
        val thumb = thumbs.maxByOrNull { it.getHeight() }?.getUrl() ?: thumbs.firstOrNull()?.getUrl() ?: ""
        return Track(
            title = getName() ?: "",
            artist = getUploaderName() ?: label,
            artworkUrl = thumb,
            durationSeconds = getDuration().coerceAtLeast(0),
            source = label,
            youtubeUrl = if (label == "YouTube") getUrl() else "",
            soundcloudUrl = if (label == "SoundCloud") getUrl() else ""
        )
    }

    private fun searchApple(q: String): List<Track> {
        val url = "https://itunes.apple.com/search?term=" + URLEncoder.encode(q, "UTF-8") + "&media=music&entity=song&limit=25"
        val arr = JSONObject(Http.getString(url) ?: error("Apple gagal")).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Track(
                title = o.optString("trackName"), artist = o.optString("artistName"),
                album = o.optString("collectionName"),
                artworkUrl = o.optString("artworkUrl100").replace("100x100bb.jpg", "600x600bb.jpg"),
                durationSeconds = o.optLong("trackTimeMillis") / 1000,
                source = "Apple Music", audioUrl = o.optString("previewUrl"), storeUrl = o.optString("trackViewUrl")
            )
        }
    }

    private fun searchAudius(q: String): List<Track> {
        val url = "https://api.audius.co/v1/tracks/search?query=" + URLEncoder.encode(q, "UTF-8") + "&app_name=EnjiwsMusic"
        val arr = JSONObject(Http.getString(url) ?: error("Audius gagal")).optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isBlank() || o.optBoolean("is_stream_gated", false)) return@mapNotNull null
            val art = o.optJSONObject("artwork")
            Track(
                title = o.optString("title"),
                artist = o.optJSONObject("user")?.optString("name").orEmpty(),
                artworkUrl = art?.optString("480x480").orEmpty().ifBlank { art?.optString("150x150").orEmpty() },
                durationSeconds = o.optLong("duration"),
                source = "Audius",
                audioUrl = "https://api.audius.co/v1/tracks/$id/stream?app_name=EnjiwsMusic"
            )
        }
    }

    /** Saran pencarian (YouTube). Kosong kalau gagal. */
    suspend fun suggestions(q: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { ServiceList.YouTube.getSuggestionExtractor().suggestionList(q).take(6) }
            .getOrDefault(emptyList())
    }

    /** Lagu terkait untuk radio otomatis. Lagu lokal / preview tidak punya rekomendasi. */
    suspend fun related(track: Track): List<Track> = withContext(Dispatchers.IO) {
        if (track.pipedId.isNotBlank()) return@withContext Piped.related(track.pipedId)
        if (track.soundcloudUrl.isNotBlank()) {
            return@withContext relatedViaNewPipe(ServiceList.SoundCloud, track.soundcloudUrl, "SoundCloud")
        }
        val videoId = Piped.videoIdOf(track.youtubeUrl)
        if (videoId.isBlank()) return@withContext emptyList()
        relatedViaNewPipe(ServiceList.YouTube, track.youtubeUrl, "YouTube").ifEmpty { Piped.related(videoId) }
    }

    private fun relatedViaNewPipe(service: StreamingService, url: String, label: String): List<Track> =
        runCatching {
            val extractor = service.getStreamExtractor(url)
            extractor.fetchPage()
            StreamInfo.getInfo(extractor).getRelatedItems()
                ?.filterIsInstance<StreamInfoItem>().orEmpty()
                .map { it.toTrack(label) }.filter { it.title.isNotBlank() }
        }.getOrDefault(emptyList())
}

/**
 * Piped = frontend YouTube pihak ketiga. Instance publiknya sering mati, jadi hanya dipakai
 * sebagai sumber opsional dan cadangan; instance yang gagal diistirahatkan sementara.
 */
object Piped {
    private val instances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://pipedapi.leptons.xyz"
    )
    private val coolDown = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun <T> firstWorking(block: (String) -> T?): T? {
        val now = System.currentTimeMillis()
        for (base in instances.sortedBy { coolDown[it] ?: 0L }) {
            if ((coolDown[base] ?: 0L) > now) continue
            val r = runCatching { block(base) }.getOrNull()
            if (r != null) return r
            coolDown[base] = now + 5 * 60_000L
        }
        return null
    }

    private fun videoId(url: String) = url.substringAfter("v=", "").substringBefore("&")

    private fun parseItems(arr: org.json.JSONArray?): List<Track> {
        arr ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val type = o.optString("type")
            if (type.isNotBlank() && type != "stream") return@mapNotNull null
            val id = videoId(o.optString("url"))
            if (id.isBlank()) return@mapNotNull null
            Track(
                title = o.optString("title"), artist = o.optString("uploaderName"),
                artworkUrl = o.optString("thumbnail"), durationSeconds = o.optLong("duration"),
                source = "Piped", pipedId = id
            )
        }.filter { it.title.isNotBlank() }
    }

    fun search(q: String): List<Track> {
        val r = firstWorking { base ->
            Http.getString("$base/search?q=" + URLEncoder.encode(q, "UTF-8") + "&filter=music_songs")
                ?.let { parseItems(JSONObject(it).optJSONArray("items")) }
        }
        return r ?: error("Semua server Piped gagal dihubungi")
    }

    fun related(videoId: String): List<Track> =
        firstWorking { base ->
            Http.getString("$base/streams/$videoId")?.let { parseItems(JSONObject(it).optJSONArray("relatedStreams")) }
        } ?: emptyList()

    /** (url, ekstensi) audio bitrate tertinggi. */
    fun stream(videoId: String): Pair<String, String> {
        val r = firstWorking { base ->
            val arr = Http.getString("$base/streams/$videoId")?.let { JSONObject(it).optJSONArray("audioStreams") }
                ?: return@firstWorking null
            var bestUrl = ""; var bestRate = -1L; var ext = "m4a"
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val u = o.optString("url")
                val rate = o.optLong("bitrate")
                if (u.isNotBlank() && rate > bestRate) {
                    bestRate = rate; bestUrl = u
                    ext = if (o.optString("mimeType").contains("webm")) "webm" else "m4a"
                }
            }
            if (bestUrl.isBlank()) null else bestUrl to ext
        }
        return r ?: error("Semua server Piped gagal dihubungi")
    }

    fun videoIdOf(youtubeUrl: String) = videoId(youtubeUrl)
}
