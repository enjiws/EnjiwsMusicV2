package com.enjiws.music.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Penyimpanan lokal: favorit, playlist, riwayat putar, riwayat pencarian.
 * Nama prefs & kunci favorit/playlist sama dengan versi 1.x, jadi data lama otomatis terbaca.
 */
object Store {
    private lateinit var prefs: SharedPreferences
    const val FAV_ID = "fav"

    var favorites by mutableStateOf<List<Track>>(emptyList()); private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList()); private set
    var history by mutableStateOf<List<HistoryEntry>>(emptyList()); private set
    var searchHistory by mutableStateOf<List<String>>(emptyList()); private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("enjiws_playlists", Context.MODE_PRIVATE)
        favorites = readTracks(prefs.getString("favorites", null))
        playlists = readPlaylists(prefs.getString("playlists", null))
        history = readHistory(prefs.getString("history", null))
        searchHistory = prefs.getString("search_history", null)?.let { raw ->
            runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrNull()
        } ?: emptyList()
    }

    // ---------- parsing ----------
    private fun readTracks(raw: String?): List<Track> {
        if (raw == null) return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(Track::fromJson) }
        } catch (e: Exception) { emptyList() }
    }

    private fun tracksJson(list: List<Track>) = JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }

    private fun playlistJson(p: Playlist) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("tracks", tracksJson(p.tracks))
    }

    private fun readPlaylists(raw: String?): List<Playlist> {
        if (raw == null) return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val t = o.optJSONArray("tracks")
                Playlist(
                    o.optString("id"), o.optString("name"),
                    (0 until (t?.length() ?: 0)).mapNotNull { j -> t?.optJSONObject(j)?.let(Track::fromJson) }
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun readHistory(raw: String?): List<HistoryEntry> {
        if (raw == null) return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                HistoryEntry(Track.fromJson(o.getJSONObject("track")), o.optLong("at"), o.optInt("plays", 1))
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun saveFavorites() = prefs.edit().putString("favorites", tracksJson(favorites).toString()).apply()
    private fun savePlaylists() = prefs.edit().putString("playlists", JSONArray().also { a -> playlists.forEach { a.put(playlistJson(it)) } }.toString()).apply()
    private fun saveHistory() = prefs.edit().putString("history", JSONArray().also { a ->
        history.forEach { h -> a.put(JSONObject().put("track", h.track.toJson()).put("at", h.playedAt).put("plays", h.plays)) }
    }.toString()).apply()

    // ---------- favorit ----------
    fun isFavorite(t: Track) = favorites.any { it.key == t.key }

    fun toggleFavorite(t: Track): Boolean {
        favorites = if (isFavorite(t)) favorites.filter { it.key != t.key } else listOf(t) + favorites
        saveFavorites()
        return isFavorite(t)
    }

    // ---------- playlist ----------
    fun playlist(id: String): Playlist? = playlists.firstOrNull { it.id == id }

    fun tracksOf(id: String): List<Track> = if (id == FAV_ID) favorites else playlist(id)?.tracks.orEmpty()

    fun createPlaylist(name: String, tracks: List<Track> = emptyList()): Playlist {
        val p = Playlist(System.currentTimeMillis().toString(), name.trim().ifBlank { "Playlist" }, tracks.distinctBy { it.key })
        playlists = playlists + p
        savePlaylists()
        return p
    }

    fun renamePlaylist(id: String, name: String) {
        playlists = playlists.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it }
        savePlaylists()
    }

    fun deletePlaylist(id: String) { playlists = playlists.filter { it.id != id }; savePlaylists() }

    fun addToPlaylist(id: String, t: Track) {
        playlists = playlists.map { p ->
            if (p.id == id && p.tracks.none { it.key == t.key }) p.copy(tracks = p.tracks + t) else p
        }
        savePlaylists()
    }

    fun removeFrom(id: String, t: Track) {
        if (id == FAV_ID) { if (isFavorite(t)) toggleFavorite(t); return }
        playlists = playlists.map { p -> if (p.id == id) p.copy(tracks = p.tracks.filter { it.key != t.key }) else p }
        savePlaylists()
    }

    /** Geser satu lagu naik (-1) / turun (+1) di dalam playlist. */
    fun moveInPlaylist(id: String, t: Track, delta: Int) {
        if (id == FAV_ID) return
        playlists = playlists.map { p ->
            if (p.id != id) return@map p
            val list = p.tracks.toMutableList()
            val from = list.indexOfFirst { it.key == t.key }
            val to = from + delta
            if (from < 0 || to !in list.indices) return@map p
            list.add(to, list.removeAt(from))
            p.copy(tracks = list)
        }
        savePlaylists()
    }

    // ---------- riwayat ----------
    fun recordPlay(t: Track) {
        val old = history.firstOrNull { it.track.key == t.key }
        val entry = HistoryEntry(t, System.currentTimeMillis(), (old?.plays ?: 0) + 1)
        history = (listOf(entry) + history.filter { it.track.key != t.key }).take(200)
        saveHistory()
    }

    fun clearHistory() { history = emptyList(); saveHistory() }

    fun addSearch(q: String) {
        val s = q.trim(); if (s.isBlank()) return
        searchHistory = (listOf(s) + searchHistory.filter { !it.equals(s, true) }).take(15)
        persistSearch()
    }

    fun removeSearch(q: String) { searchHistory = searchHistory - q; persistSearch() }
    fun clearSearch() { searchHistory = emptyList(); persistSearch() }
    private fun persistSearch() = prefs.edit().putString("search_history", JSONArray(searchHistory).toString()).apply()

    // ---------- cadangan ----------
    fun exportJson(): String = JSONObject().apply {
        put("app", "enjiws-music"); put("version", 2)
        put("favorites", tracksJson(favorites))
        put("playlists", JSONArray().also { a -> playlists.forEach { a.put(playlistJson(it)) } })
    }.toString(2)

    /** Gabungkan isi cadangan ke data sekarang (tidak menimpa). Mengembalikan jumlah lagu/playlist baru. */
    fun importJson(text: String): Int {
        val o = JSONObject(text)
        var added = 0
        val favs = readTracks(o.optJSONArray("favorites")?.toString())
        favs.filter { f -> favorites.none { it.key == f.key } }.also { added += it.size; favorites = favorites + it }
        val pls = readPlaylists(o.optJSONArray("playlists")?.toString())
        pls.forEach { p ->
            val existing = playlists.firstOrNull { it.name == p.name }
            if (existing == null) { playlists = playlists + p; added += p.tracks.size }
            else {
                val extra = p.tracks.filter { t -> existing.tracks.none { it.key == t.key } }
                playlists = playlists.map { if (it.id == existing.id) it.copy(tracks = it.tracks + extra) else it }
                added += extra.size
            }
        }
        saveFavorites(); savePlaylists()
        return added
    }
}
