package com.enjiws.music.data

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.annotation.RequiresApi
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cover lagu lokal langsung dari file (Android 10+). URI albumart lama sering mengembalikan
 * gambar default yang sama untuk semua lagu; loadThumbnail membaca cover tertanam yang asli.
 */
@RequiresApi(29)
class LocalArtFetcher(private val context: Context, private val uri: Uri) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val bmp = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(uri, Size(512, 512), null) }.getOrNull()
        } ?: return null
        return DrawableResult(BitmapDrawable(context.resources, bmp), false, DataSource.DISK)
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (Build.VERSION.SDK_INT >= 29 && data.scheme == "content" && data.authority == "media" &&
                data.path?.contains("/audio/media/") == true
            ) LocalArtFetcher(context, data) else null
    }
}
