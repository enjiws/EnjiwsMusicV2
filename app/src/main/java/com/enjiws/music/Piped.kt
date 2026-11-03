package com.enjiws.music

import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

object PipedApi {
    private const val BASE_URL = "https://pipedapi.kavin.rocks"

    fun searchTracks(query: String): List<OnlineTrack> {
        val list = mutableListOf<OnlineTrack>()
        try {
            val url = "$BASE_URL/search?q=$query&filter=music_songs"
            val request = Request.Builder().url(url).build()
            OnlineHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val title = item.optString("title", "Unknown")
                    val uploader = item.optString("uploaderName", "Unknown")
                    val urlPath = item.optString("url", "")
                    val id = urlPath.replace("/watch?v=", "")
                    val thumbnail = item.optString("thumbnail", "")
                    val duration = item.optLong("duration", 0)
                    list.add(OnlineTrack(id, title, uploader, urlPath, thumbnail, duration))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
