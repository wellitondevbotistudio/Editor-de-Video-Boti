package com.example

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.example.data.db.AppDatabase
import com.example.data.media.MediaMetadataExtractor
import com.example.data.media.MediaStorageManager
import com.example.data.repository.ProjectRepository

class BotiApplication : Application(), ImageLoaderFactory {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val projectRepository: ProjectRepository by lazy { ProjectRepository(database) }
    val mediaStorageManager: MediaStorageManager by lazy { MediaStorageManager(this) }
    val mediaMetadataExtractor: MediaMetadataExtractor by lazy { MediaMetadataExtractor(this) }

    companion object {
        lateinit var instance: BotiApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }
}
