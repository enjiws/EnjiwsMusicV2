package com.enjiws.music

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

class AppDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val data = request.dataToSend()
        val body = when {
            data != null -> data.toRequestBody()
            method == "POST" || method == "PUT" -> ByteArray(0).toRequestBody()
            else -> null
        }
        val b = okhttp3.Request.Builder()
            .method(method, body)
            .url(request.url())
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0")
        request.headers().forEach { (k, values) ->
            b.removeHeader(k)
            values.forEach { b.addHeader(k, it) }
        }
        client.newCall(b.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("Rate limited", request.url())
            return Response(
                r.code, r.message, r.headers.toMultimap(),
                r.body?.string(), r.request.url.toString()
            )
        }
    }
}
