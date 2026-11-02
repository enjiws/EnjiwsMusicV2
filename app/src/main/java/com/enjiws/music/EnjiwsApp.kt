package com.enjiws.music

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

class EnjiwsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(AppDownloader(), Localization("id", "ID"))
    }
}
