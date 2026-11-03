package com.enjiws.music

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class AppDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val client = OnlineHttpClient.client
        val reqBuilder = okhttp3.Request.Builder().url(request.url())
        request.headers().forEach { (key, values) ->
            values.forEach { reqBuilder.addHeader(key, it) }
        }
        val response = client.newCall(reqBuilder.build()).execute()
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            response.body?.string() ?: "",
            request.url()
        )
    }
}
