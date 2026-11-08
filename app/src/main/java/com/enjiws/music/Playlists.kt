package com.enjiws.music

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class Playlist(val id: String, val name: String, val tracks: List<OnlineTrack>)

// Identitas satu lagu: sumber + ID sumber + judul + artis. Dipakai untuk
// favorit, tambah/hapus playlist, dan dedupe antrean.
fun trackKey(track: OnlineTrack): String {
    val id = track.youtubeUrl.ifBlank { track.pipedId.ifBlank { track.audioUrl } }
    return track.source + "|" + id + "|" + track.title + "|" + track.artist
}

// Dua lagu dianggap sama kalau identitasnya sama, atau judul+artisnya sama
// (menangani kembaran YouTube vs Piped dari lagu yang sama).
fun isSameSong(a: OnlineTrack, b: OnlineTrack): Boolean {
    if (trackKey(a) == trackKey(b)) return true
    return a.title.isNotBlank() &&
        a.title.trim().equals(b.title.trim(), ignoreCase = true) &&
        a.artist.trim().equals(b.artist.trim(), ignoreCase = true)
}

private fun trackToJson(t: OnlineTrack): JSONObject = JSONObject().apply {
    put("title", t.title)
    put("artist", t.artist)
    put("artworkUrl", t.artworkUrl)
    put("durationSeconds", t.durationSeconds)
    put("source", t.source)
    put("audioUrl", t.audioUrl)
    put("youtubeUrl", t.youtubeUrl)
    put("soundcloudUrl", t.soundcloudUrl)
    put("storeUrl", t.storeUrl)
    put("pipedId", t.pipedId)
}

private fun trackFromJson(o: JSONObject): OnlineTrack = OnlineTrack(
    title = o.optString("title"),
    artist = o.optString("artist"),
    artworkUrl = o.optString("artworkUrl"),
    durationSeconds = o.optLong("durationSeconds"),
    source = o.optString("source"),
    audioUrl = o.optString("audioUrl"),
    youtubeUrl = o.optString("youtubeUrl"),
    soundcloudUrl = o.optString("soundcloudUrl"),
    storeUrl = o.optString("storeUrl"),
    pipedId = o.optString("pipedId")
)

private fun playlistToJson(p: Playlist): JSONObject = JSONObject().apply {
    put("id", p.id)
    put("name", p.name)
    val arr = JSONArray()
    p.tracks.forEach { arr.put(trackToJson(it)) }
    put("tracks", arr)
}

private fun playlistFromJson(o: JSONObject): Playlist {
    val arr = o.optJSONArray("tracks")
    val tracks = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
        arr?.optJSONObject(i)?.let { trackFromJson(it) }
    }
    return Playlist(o.optString("id"), o.optString("name"), tracks)
}

// Penyimpanan playlist & favorit (lokal di HP, SharedPreferences + JSON).
// State-nya Compose (mutableStateOf) jadi semua layar yang membacanya
// ikut berubah saat favorit/playlist berubah. init() dipanggil sekali
// dari MainActivity.onCreate sebelum layar mana pun tampil.
object PlaylistStore {
    private const val PREFS = "enjiws_playlists"
    private const val KEY_FAV = "favorites"
    private const val KEY_LISTS = "playlists"

    private var prefs: SharedPreferences? = null
    var favorites by mutableStateOf<List<OnlineTrack>>(emptyList())
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        favorites = loadFavorites()
        playlists = loadPlaylists()
    }

    private fun loadFavorites(): List<OnlineTrack> {
        val raw = prefs?.getString(KEY_FAV, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { trackFromJson(it) } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadPlaylists(): List<Playlist> {
        val raw = prefs?.getString(KEY_LISTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { playlistFromJson(it) } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveFavorites() {
        val arr = JSONArray()
        favorites.forEach { arr.put(trackToJson(it)) }
        prefs?.edit()?.putString(KEY_FAV, arr.toString())?.apply()
    }

    private fun savePlaylists() {
        val arr = JSONArray()
        playlists.forEach { arr.put(playlistToJson(it)) }
        prefs?.edit()?.putString(KEY_LISTS, arr.toString())?.apply()
    }

    fun isFavorite(track: OnlineTrack): Boolean =
        favorites.any { trackKey(it) == trackKey(track) }

    // Toggle favorit, mengembalikan status baru (true = sekarang favorit).
    fun toggleFavorite(track: OnlineTrack): Boolean {
        val key = trackKey(track)
        favorites = if (favorites.any { trackKey(it) == key }) {
            favorites.filter { trackKey(it) != key }
        } else {
            favorites + track
        }
        saveFavorites()
        return isFavorite(track)
    }

    fun createPlaylist(name: String): Playlist {
        val p = Playlist(id = System.currentTimeMillis().toString(), name = name, tracks = emptyList())
        playlists = playlists + p
        savePlaylists()
        return p
    }

    fun deletePlaylist(id: String) {
        playlists = playlists.filter { it.id != id }
        savePlaylists()
    }

    fun addToPlaylist(id: String, track: OnlineTrack) {
        val key = trackKey(track)
        playlists = playlists.map { p ->
            if (p.id == id && p.tracks.none { trackKey(it) == key }) p.copy(tracks = p.tracks + track) else p
        }
        savePlaylists()
    }

    fun removeFromPlaylist(id: String, track: OnlineTrack) {
        val key = trackKey(track)
        playlists = playlists.map { p ->
            if (p.id == id) p.copy(tracks = p.tracks.filter { trackKey(it) != key }) else p
        }
        savePlaylists()
    }
}
