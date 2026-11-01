package com.enjiws.music

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class OkHttpDownloader : Downloader() {
    private val client = OkHttpClient()

    override fun execute(request: Request): Response {
        val data = request.dataToSend()
        val body = when {
            data != null -> data.toRequestBody(null)
            request.httpMethod() == "POST" -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        val reqBuilder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
        request.headers()?.forEach { (name, values) ->
            values?.forEach { value -> reqBuilder.addHeader(name, value) }
        }
        val resp = client.newCall(reqBuilder.build()).execute()
        val respBody = resp.body?.string() ?: ""
        return Response(resp.code, resp.message, resp.headers.toMultimap(), respBody, resp.request.url.toString())
    }
}
