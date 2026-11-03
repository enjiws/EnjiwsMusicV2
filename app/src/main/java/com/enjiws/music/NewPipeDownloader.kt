package com.enjiws.music

import org.schabi.newpipe.extractor.NewPipe

object NewPipeDownloader {
    fun init() {
        try {
            NewPipe.init(AppDownloader())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
