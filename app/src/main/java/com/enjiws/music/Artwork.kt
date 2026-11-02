package com.enjiws.music

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val artCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private fun decodeSampled(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
    val side = maxOf(bmp.width, bmp.height)
    if (side <= maxSide) return bmp
    val scale = maxSide.toFloat() / side
    return Bitmap.createScaledBitmap(
        bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true
    )
}

private fun download(url: String): ByteArray? {
    val c = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000
        readTimeout = 15000
        setRequestProperty("User-Agent", "EnjiwsMusic/1.8")
    }
    return try {
        if (c.responseCode in 200..299) c.inputStream.use { it.readBytes() } else null
    } finally {
        c.disconnect()
    }
}

/** Ambil cover: URL http(s) -> download; content:// -> stream; lalu cover tertanam di file; lalu thumbnail MediaStore. */
suspend fun loadArtwork(context: Context, artworkUri: String?, mediaId: String?, maxSide: Int = 640): Bitmap? =
    withContext(Dispatchers.IO) {
        val songUri = mediaId?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.let {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.toLong())
        }
        val key = (artworkUri ?: "") + "|" + (songUri?.toString() ?: "") + "|" + maxSide
        if (artworkUri.isNullOrBlank() && songUri == null) return@withContext null
        artCache.get(key)?.let { return@withContext it }

        var bmp: Bitmap? = null
        if (!artworkUri.isNullOrBlank()) {
            bmp = runCatching {
                if (artworkUri.startsWith("http")) {
                    download(artworkUri)?.let { decodeSampled(it, maxSide) }
                } else {
                    context.contentResolver.openInputStream(Uri.parse(artworkUri))
                        ?.use { it.readBytes() }?.let { decodeSampled(it, maxSide) }
                }
            }.getOrNull()
        }
        if (bmp == null && songUri != null) {
            bmp = runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, songUri)
                    r.embeddedPicture?.let { decodeSampled(it, maxSide) }
                } finally {
                    r.release()
                }
            }.getOrNull()
        }
        if (bmp == null && songUri != null && Build.VERSION.SDK_INT >= 29) {
            bmp = runCatching {
                context.contentResolver.loadThumbnail(songUri, Size(maxSide, maxSide), null)
            }.getOrNull()
        }
        if (bmp != null) artCache.put(key, bmp)
        bmp
    }

/** Versi sangat kecil (24x24). Di-upscale dengan filter halus = efek blur untuk background. */
fun tinyBlur(src: Bitmap): Bitmap {
    val mid = Bitmap.createScaledBitmap(src, 96, 96, true)
    return Bitmap.createScaledBitmap(mid, 24, 24, true)
}

@Composable
fun rememberArtwork(artworkUri: String?, mediaId: String?, maxSide: Int = 640): State<Bitmap?> {
    val context = LocalContext.current
    return produceState<Bitmap?>(initialValue = null, artworkUri, mediaId, maxSide) {
        value = loadArtwork(context, artworkUri, mediaId, maxSide)
    }
}

@Composable
fun ArtThumb(bitmap: Bitmap?, size: Dp, radius: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(listOf(Color(0xFF202D24), Color(0xFF426D4B)))),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("♫", color = Color(0xFF91D6A0), fontSize = (size.value * 0.45f).sp)
        }
    }
}

@Composable
fun RemoteArtThumb(url: String?, size: Dp, mediaId: String? = null) {
    val bmp by rememberArtwork(url?.ifBlank { null }, mediaId, 160)
    ArtThumb(bmp, size)
}
