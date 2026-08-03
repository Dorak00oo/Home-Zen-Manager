package com.fibelatti.photowidget.wallpaper

import android.net.Uri
import com.fibelatti.photowidget.model.PhotoWidgetCycleMode
import com.fibelatti.photowidget.model.PhotoWidgetLoopingInterval
import kotlinx.serialization.Serializable

enum class WallpaperSurface {
    HOME,
    LOCK,
}

@Serializable
enum class WallpaperMediaType {
    PHOTO,
    VIDEO,
    GIF,
}

@Serializable
data class WallpaperMedia(
    val id: String,
    val type: WallpaperMediaType,
    val path: String,
    val displayName: String = "",
)

@Serializable
data class WallpaperSurfaceConfig(
    val media: List<WallpaperMedia> = emptyList(),
    val folderUris: List<String> = emptyList(),
    val shuffle: Boolean = false,
    val cycleMode: SerializedCycleMode = SerializedCycleMode.Interval(),
    val currentId: String? = null,
)

/**
 * Serializable stand-in for [PhotoWidgetCycleMode] for prefs persistence.
 */
@Serializable
sealed interface SerializedCycleMode {
    @Serializable
    data class Interval(
        val repeatInterval: Long = 24,
        val timeUnit: String = "HOURS",
    ) : SerializedCycleMode

    @Serializable
    data object Disabled : SerializedCycleMode
}

fun SerializedCycleMode.toPhotoWidgetCycleMode(): PhotoWidgetCycleMode = when (this) {
    is SerializedCycleMode.Interval -> {
        val unit = runCatching { java.util.concurrent.TimeUnit.valueOf(timeUnit) }
            .getOrDefault(java.util.concurrent.TimeUnit.DAYS)
        PhotoWidgetCycleMode.Interval(
            loopingInterval = PhotoWidgetLoopingInterval(
                repeatInterval = repeatInterval,
                timeUnit = unit,
            ),
        )
    }

    is SerializedCycleMode.Disabled -> PhotoWidgetCycleMode.Disabled
}

fun PhotoWidgetCycleMode.toSerialized(): SerializedCycleMode = when (this) {
    is PhotoWidgetCycleMode.Interval -> SerializedCycleMode.Interval(
        repeatInterval = loopingInterval.repeatInterval,
        timeUnit = loopingInterval.timeUnit.name,
    )

    is PhotoWidgetCycleMode.Disabled -> SerializedCycleMode.Disabled

    // Schedule modes collapse to a 1-hour interval for wallpaper MVP persistence.
    is PhotoWidgetCycleMode.Schedule,
    is PhotoWidgetCycleMode.AdvancedSchedule,
    -> SerializedCycleMode.Interval(repeatInterval = 1, timeUnit = "HOURS")
}

@Serializable
data class WallpaperHubConfig(
    val linked: Boolean = true,
    val home: WallpaperSurfaceConfig = WallpaperSurfaceConfig(),
    val lock: WallpaperSurfaceConfig = WallpaperSurfaceConfig(),
) {
    fun configFor(surface: WallpaperSurface): WallpaperSurfaceConfig {
        return if (linked) home else when (surface) {
            WallpaperSurface.HOME -> home
            WallpaperSurface.LOCK -> lock
        }
    }
}

fun WallpaperSurfaceConfig.resolvedFolderUris(): Set<Uri> =
    folderUris.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }.toSet()
