package com.fibelatti.photowidget.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fibelatti.photowidget.di.PhotoWidgetEntryPoint
import com.fibelatti.photowidget.di.entryPoint
import java.io.FileInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@Suppress("DEPRECATION")
class ZenWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ZenEngine()

    private inner class ZenEngine : Engine() {

        private val storage: WallpaperStorage by lazy {
            entryPoint<PhotoWidgetEntryPoint>(applicationContext).wallpaperStorage()
        }
        private val scheduler: WallpaperScheduler by lazy {
            entryPoint<PhotoWidgetEntryPoint>(applicationContext).wallpaperScheduler()
        }
        private val scope: CoroutineScope by lazy {
            entryPoint<PhotoWidgetEntryPoint>(applicationContext).coroutineScope()
        }

        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private var mediaPlayer: MediaPlayer? = null
        private var movie: Movie? = null
        private var stillBitmap: Bitmap? = null
        private var movieStart: Long = 0L
        private var visible = false
        private var currentMediaId: String? = null

        private val gestureDetector = GestureDetector(
            this@ZenWallpaperService,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    scope.launch {
                        storage.advanceCurrent(WallpaperSurface.HOME)
                        if (!storage.currentConfig().linked) {
                            storage.advanceCurrent(WallpaperSurface.LOCK)
                        }
                        scheduler.applyLockFallbackIfNeeded()
                        handler.post { reloadAndDraw() }
                    }
                    return true
                }
            },
        )

        private val redrawReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                reloadAndDraw()
            }
        }

        private val cycleTick = object : Runnable {
            override fun run() {
                if (!visible) return
                scope.launch {
                    storage.advanceCurrent(WallpaperSurface.HOME)
                    if (!storage.currentConfig().linked) {
                        storage.advanceCurrent(WallpaperSurface.LOCK)
                    }
                    scheduler.applyLockFallbackIfNeeded()
                    handler.post {
                        reloadAndDraw()
                        scheduleCycleTick()
                    }
                }
            }
        }

        private val animationTick = object : Runnable {
            override fun run() {
                if (!visible || movie == null) return
                drawFrame()
                handler.postDelayed(this, 33L)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            LocalBroadcastManager.getInstance(this@ZenWallpaperService)
                .registerReceiver(redrawReceiver, IntentFilter(ACTION_REDRAW))
        }

        override fun onDestroy() {
            handler.removeCallbacksAndMessages(null)
            LocalBroadcastManager.getInstance(this@ZenWallpaperService)
                .unregisterReceiver(redrawReceiver)
            releasePlayers()
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                reloadAndDraw()
                scheduleCycleTick()
            } else {
                handler.removeCallbacks(cycleTick)
                handler.removeCallbacks(animationTick)
                mediaPlayer?.pause()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (visible) drawFrame()
        }

        override fun onTouchEvent(event: MotionEvent?) {
            if (event != null) gestureDetector.onTouchEvent(event)
            super.onTouchEvent(event)
        }

        private fun scheduleCycleTick() {
            handler.removeCallbacks(cycleTick)
            val seconds = storage.intervalSeconds(WallpaperSurface.HOME) ?: return
            if (seconds >= WallpaperScheduler.KEEP_ALIVE_THRESHOLD_SECONDS) return
            handler.postDelayed(cycleTick, seconds * 1000L)
        }

        private fun reloadAndDraw() {
            scope.launch {
                val media = storage.currentMedia(WallpaperSurface.HOME)
                handler.post {
                    if (media?.id != currentMediaId) {
                        currentMediaId = media?.id
                        loadMedia(media)
                    }
                    drawFrame()
                    if (movie != null) {
                        handler.removeCallbacks(animationTick)
                        handler.post(animationTick)
                    }
                }
            }
        }

        private fun loadMedia(media: WallpaperMedia?) {
            releasePlayers()
            if (media == null) return

            when (media.type) {
                WallpaperMediaType.VIDEO -> {
                    runCatching {
                        mediaPlayer = MediaPlayer().apply {
                            if (media.path.startsWith("content:")) {
                                setDataSource(applicationContext, Uri.parse(media.path))
                            } else {
                                setDataSource(media.path)
                            }
                            isLooping = true
                            setSurface(surfaceHolder.surface)
                            setOnPreparedListener { player ->
                                if (visible) player.start()
                            }
                            prepareAsync()
                        }
                    }.onFailure { Timber.e(it, "Failed to play wallpaper video") }
                }

                WallpaperMediaType.GIF -> {
                    movie = runCatching {
                        if (media.path.startsWith("content:")) {
                            applicationContext.contentResolver.openInputStream(Uri.parse(media.path))
                                ?.use { Movie.decodeStream(it) }
                        } else {
                            FileInputStream(media.path).use { Movie.decodeStream(it) }
                        }
                    }.getOrNull()
                    movieStart = 0L
                }

                WallpaperMediaType.PHOTO -> {
                    stillBitmap = decodeBitmap(media)
                }
            }
        }

        private fun decodeBitmap(media: WallpaperMedia): Bitmap? {
            return runCatching {
                if (media.path.startsWith("content:")) {
                    applicationContext.contentResolver.openInputStream(Uri.parse(media.path))
                        ?.use { BitmapFactory.decodeStream(it) }
                } else {
                    BitmapFactory.decodeFile(media.path)
                }
            }.getOrNull()
        }

        private fun drawFrame() {
            if (mediaPlayer != null) return

            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas() ?: return
                canvas.drawColor(Color.BLACK)

                val gif = movie
                if (gif != null) {
                    val now = SystemClock.uptimeMillis()
                    if (movieStart == 0L) movieStart = now
                    val duration = gif.duration().takeIf { it > 0 } ?: 1000
                    gif.setTime(((now - movieStart) % duration).toInt())
                    drawCentered(canvas, gif.width().toFloat(), gif.height().toFloat()) { c, matrix ->
                        c.concat(matrix)
                        gif.draw(c, 0f, 0f)
                    }
                    return
                }

                val bitmap = stillBitmap ?: return
                drawCentered(canvas, bitmap.width.toFloat(), bitmap.height.toFloat()) { c, matrix ->
                    c.drawBitmap(bitmap, matrix, paint)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to draw wallpaper frame")
            } finally {
                if (canvas != null) {
                    runCatching { surfaceHolder.unlockCanvasAndPost(canvas) }
                }
            }
        }

        private fun drawCentered(
            canvas: Canvas,
            srcW: Float,
            srcH: Float,
            draw: (Canvas, Matrix) -> Unit,
        ) {
            if (srcW <= 0f || srcH <= 0f) return
            val dst = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
            val scale = maxOf(dst.width() / srcW, dst.height() / srcH)
            val dx = (dst.width() - srcW * scale) / 2f
            val dy = (dst.height() - srcH * scale) / 2f
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(dx, dy)
            }
            draw(canvas, matrix)
        }

        private fun releasePlayers() {
            handler.removeCallbacks(animationTick)
            mediaPlayer?.run {
                runCatching {
                    stop()
                    release()
                }
            }
            mediaPlayer = null
            movie = null
            stillBitmap?.recycle()
            stillBitmap = null
        }
    }

    companion object {
        const val ACTION_REDRAW = "com.fibelatti.photowidget.wallpaper.REDRAW"

        fun requestRedraw(context: Context) {
            LocalBroadcastManager.getInstance(context)
                .sendBroadcast(Intent(ACTION_REDRAW))
        }
    }
}
