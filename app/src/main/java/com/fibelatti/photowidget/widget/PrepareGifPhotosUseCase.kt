package com.fibelatti.photowidget.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.toColorInt
import com.fibelatti.photowidget.model.PhotoWidget
import com.fibelatti.photowidget.model.PhotoWidgetAspectRatio
import com.fibelatti.photowidget.model.PhotoWidgetBorder
import com.fibelatti.photowidget.model.borderPercent
import com.fibelatti.photowidget.platform.PhotoDecoder
import com.fibelatti.photowidget.platform.colorForType
import com.fibelatti.photowidget.platform.getColorPalette
import com.fibelatti.photowidget.platform.getDynamicAttributeColor
import com.fibelatti.photowidget.platform.getMaxBitmapWidgetDimension
import com.fibelatti.photowidget.platform.runWithFileOutputStream
import com.fibelatti.photowidget.platform.withPolygonalShape
import com.fibelatti.photowidget.platform.withRoundedCorners
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.measureTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

class PrepareGifPhotosUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val decoder: PhotoDecoder,
) {

    suspend operator fun invoke(
        appWidgetId: Int,
        photoWidget: PhotoWidget,
        gifIds: Set<String>? = null,
    ) {
        Timber.i(
            "Preparing gif photos %s",
            mapOf("appWidgetId" to appWidgetId, "gifIds" to gifIds),
        )

        val maxDimension: Int = context.getMaxBitmapWidgetDimension()
        val borderPercent: Float = photoWidget.border.borderPercent()
        val photos = photoWidget.photos.filter { gifIds == null || it.photoId in gifIds }

        coroutineScope {
            val totalTime: Duration = measureTime {
                val deferred = photos.flatMap { gif ->
                    val gifDir: File? = gif.gifSourcePath?.let { File(it).parentFile }
                        ?: gif.croppedPhotoPath?.let { File(it).parentFile?.parentFile }
                    val originalDir = gifDir?.let { File(it, "original") }
                    val framesDir = gifDir?.let { File(it, "frames") }

                    if (originalDir == null || framesDir == null || !originalDir.isDirectory) {
                        // Legacy single-path photo — prepare cropped path only.
                        return@flatMap listOf(
                            async {
                                prepareFrame(
                                    originalPath = gif.originalPhotoPath ?: return@async,
                                    outputPath = gif.croppedPhotoPath ?: return@async,
                                    photoWidget = photoWidget,
                                    maxDimension = maxDimension,
                                    borderPercent = borderPercent,
                                )
                            },
                        )
                    }

                    framesDir.mkdirs()
                    originalDir.listFiles()
                        .orEmpty()
                        .filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
                        .sortedBy { it.name }
                        .map { originalFrame ->
                            async {
                                prepareFrame(
                                    originalPath = originalFrame.path,
                                    outputPath = File(framesDir, originalFrame.name).path,
                                    photoWidget = photoWidget,
                                    maxDimension = maxDimension,
                                    borderPercent = borderPercent,
                                )
                            }
                        }
                }

                deferred.awaitAll()

                photos.forEach { gif ->
                    val gifDir: File? = gif.gifSourcePath?.let { File(it).parentFile }
                        ?: gif.croppedPhotoPath?.let { File(it).parentFile?.parentFile }
                    gifDir?.let { File(it, "prepared").writeText("1") }
                }
            }

            Timber.d("Prepared gif photos in $totalTime")
        }
    }

    private suspend fun prepareFrame(
        originalPath: String,
        outputPath: String,
        photoWidget: PhotoWidget,
        maxDimension: Int,
        borderPercent: Float,
    ) {
        val bitmap: Bitmap = decoder.decode(
            data = originalPath,
            maxDimension = maxDimension,
        ) ?: return

        val borderColor: Int? = when (photoWidget.border) {
            is PhotoWidgetBorder.None -> null

            is PhotoWidgetBorder.Color -> "#${photoWidget.border.colorHex}".toColorInt()

            is PhotoWidgetBorder.Dynamic -> context.getDynamicAttributeColor(
                photoWidget.border.type.colorAttr,
            )

            is PhotoWidgetBorder.MatchPhoto -> getColorPalette(bitmap).colorForType(
                photoWidget.border.type,
            )
        }

        Timber.d("Transforming the bitmap")
        val transformedBitmap: Bitmap = if (photoWidget.aspectRatio == PhotoWidgetAspectRatio.SQUARE) {
            bitmap.withPolygonalShape(
                context = context,
                shapeId = photoWidget.shapeId,
                colors = photoWidget.colors,
                borderColor = borderColor,
                borderPercent = borderPercent,
            )
        } else {
            bitmap.withRoundedCorners(
                radius = photoWidget.cornerRadius * context.resources.displayMetrics.density,
                aspectRatio = photoWidget.aspectRatio,
                colors = photoWidget.colors,
                borderColor = borderColor,
                borderPercent = borderPercent,
            )
        }

        File(/* pathname = */ "$outputPath.tmp").runWithFileOutputStream { fos ->
            transformedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        val tempFile = File("$outputPath.tmp")
        val outputFile = File(outputPath)
        if (!tempFile.renameTo(outputFile)) {
            tempFile.copyTo(outputFile, overwrite = true)
            tempFile.delete()
        }
    }
}
