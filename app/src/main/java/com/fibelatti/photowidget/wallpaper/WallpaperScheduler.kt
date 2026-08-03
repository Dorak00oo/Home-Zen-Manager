package com.fibelatti.photowidget.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.fibelatti.photowidget.platform.KeepAliveService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class WallpaperScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: WallpaperStorage,
) {

    fun reschedule() {
        val seconds = storage.intervalSeconds(WallpaperSurface.HOME)
        WallpaperCycleWorker.cancel(context)

        when {
            seconds == null -> {
                Timber.d("Wallpaper cycling disabled")
            }

            seconds < KEEP_ALIVE_THRESHOLD_SECONDS -> {
                KeepAliveService.tryStart(context)
                Timber.d("Wallpaper short interval ($seconds s): Keep Alive")
            }

            else -> {
                val minutes = (seconds / 60).coerceAtLeast(15)
                WallpaperCycleWorker.enqueue(context, minutes)
                Timber.d("Wallpaper long interval: WorkManager every $minutes min")
            }
        }
    }

    fun openLiveWallpaperPicker() {
        val component = ComponentName(context, ZenWallpaperService::class.java)
        val changeIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(changeIntent)
        }.recoverCatching {
            val fallback = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }.onFailure {
            Timber.e(it, "Unable to open live wallpaper picker")
        }
    }

    suspend fun applyLockFallbackIfNeeded() {
        val hub = storage.currentConfig()
        val surface = if (hub.linked) WallpaperSurface.HOME else WallpaperSurface.LOCK
        val media = storage.currentMedia(surface) ?: return
        val bitmap = decodeStill(media) ?: return
        runCatching {
            WallpaperManager.getInstance(context).setBitmap(
                bitmap,
                null,
                true,
                WallpaperManager.FLAG_LOCK,
            )
        }.onFailure {
            Timber.w(it, "Lock wallpaper fallback not supported on this device")
        }.also {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun decodeStill(media: WallpaperMedia): Bitmap? {
        return runCatching {
            when (media.type) {
                WallpaperMediaType.VIDEO -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        if (media.path.startsWith("content:")) {
                            retriever.setDataSource(context, Uri.parse(media.path))
                        } else {
                            retriever.setDataSource(media.path)
                        }
                        retriever.frameAtTime
                    } finally {
                        retriever.release()
                    }
                }

                WallpaperMediaType.PHOTO,
                WallpaperMediaType.GIF,
                -> {
                    if (media.path.startsWith("content:")) {
                        context.contentResolver.openInputStream(Uri.parse(media.path))?.use {
                            BitmapFactory.decodeStream(it)
                        }
                    } else {
                        BitmapFactory.decodeFile(media.path)
                    }
                }
            }
        }.getOrNull()
    }

    companion object {
        const val KEEP_ALIVE_THRESHOLD_SECONDS: Long = 15 * 60
    }
}
