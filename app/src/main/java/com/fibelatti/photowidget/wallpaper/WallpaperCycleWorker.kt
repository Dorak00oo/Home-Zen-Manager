package com.fibelatti.photowidget.wallpaper

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import timber.log.Timber

@HiltWorker
class WallpaperCycleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val storage: WallpaperStorage,
    private val scheduler: WallpaperScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("WallpaperCycleWorker: advancing playlist")
        storage.advanceCurrent(WallpaperSurface.HOME)
        if (!storage.currentConfig().linked) {
            storage.advanceCurrent(WallpaperSurface.LOCK)
        }
        scheduler.applyLockFallbackIfNeeded()
        ZenWallpaperService.requestRedraw(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "wallpaper_cycle"

        fun enqueue(context: Context, intervalMinutes: Long) {
            val request = PeriodicWorkRequestBuilder<WallpaperCycleWorker>(
                repeatInterval = intervalMinutes.coerceAtLeast(15),
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
