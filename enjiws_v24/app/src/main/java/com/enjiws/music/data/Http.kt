package com.enjiws.music.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Scope level aplikasi untuk pekerjaan yang tidak boleh ikut batal saat layar berganti. */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

object Http {
    const val UA = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    /** GET sederhana; null kalau gagal / bukan 2xx. */
    fun getString(url: String): String? = try {
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    } catch (e: Exception) {
        null
    }
}

/** Satu-satunya jalur pesan singkat (snackbar) ke UI, juga dari service. */
object UiBus {
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    fun toast(msg: String) { messages.tryEmit(msg) }
}
