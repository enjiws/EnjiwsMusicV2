package com.enjiws.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.enjiws.music.data.AppScope
import com.enjiws.music.data.Catalog
import com.enjiws.music.data.Http
import com.enjiws.music.data.LocalArtFetcher
import com.enjiws.music.data.NewPipeDownloader
import com.enjiws.music.data.Prefs
import com.enjiws.music.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

class EnjiwsApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Store.init(this)
        // Satu-satunya tempat NewPipe diinisialisasi.
        NewPipe.init(NewPipeDownloader(), Localization("id", "ID"))
        AppScope.launch(Dispatchers.IO) { Catalog.warmUp() }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(LocalArtFetcher.Factory(this@EnjiwsApp)) }
        .okHttpClient(Http.client)
        .crossfade(true)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(100L * 1024 * 1024).build() }
        .build()
}
