package com.enjiws.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persists queue metadata only. Stream URLs may expire, so they are resolved again on playback. */
object QueuePersistence {
    private const val PREFS = "enjiws_playback_queue"
    private const val KEY_ITEMS = "online_items"
    private const val KEY_INDEX = "online_index"

    data class Snapshot(val tracks: List<OnlineTrack>, val index: Int)

    fun save(context: Context, tracks: List<OnlineTrack>, index: Int) {
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(JSONObject().apply {
                put("title", track.title)
                put("artist", track.artist)
                put("artworkUrl", track.artworkUrl)
                put("durationSeconds", track.durationSeconds)
                put("source", track.source)
                put("audioUrl", track.audioUrl)
                put("youtubeUrl", track.youtubeUrl)
                put("soundcloudUrl", track.soundcloudUrl)
                put("storeUrl", track.storeUrl)
                put("pipedId", track.pipedId)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ITEMS, array.toString())
            .putInt(KEY_INDEX, index.coerceIn(-1, (tracks.size - 1).coerceAtLeast(-1)))
            .apply()
    }

    fun restore(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ITEMS, null) ?: return Snapshot(emptyList(), -1)
        return try {
            val array = JSONArray(raw)
            val tracks = (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                OnlineTrack(
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
            }
            Snapshot(tracks, prefs.getInt(KEY_INDEX, -1).coerceIn(-1, (tracks.size - 1).coerceAtLeast(-1)))
        } catch (_: Exception) {
            Snapshot(emptyList(), -1)
        }
    }
}
