package com.metaverse.gallery

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import java.io.File

/** Video thumbnails for Coil + a tiny crash recorder (the trace is shown on the next launch). */
class GalleryApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try { File(filesDir, "crash.txt").writeText(Log.getStackTraceString(error)) } catch (_: Throwable) {}
            previous?.uncaughtException(thread, error)
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).components { add(VideoFrameDecoder.Factory()) }.crossfade(true).build()
}
