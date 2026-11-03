package com.enjiws.music

import okhttp3.OkHttpClient

data class OnlineTrack(
    val id: String,
    val title: String,
    val artist: String,
    val streamUrl: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = 0
)

object OnlineHttpClient {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }
}
