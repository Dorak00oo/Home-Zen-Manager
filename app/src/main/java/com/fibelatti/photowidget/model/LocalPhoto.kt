package com.fibelatti.photowidget.model

import android.net.Uri
import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

@Parcelize
@Immutable
data class LocalPhoto(
    val photoId: String,
    val croppedPhotoPath: String? = null,
    val originalPhotoPath: String? = null,
    val externalUri: Uri? = null,
    val launcherUri: Uri? = null,
    val cropping: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    /**
     * When set, this photo is a GIF playlist item. Frame PNGs live under the sibling `frames/`
     * directory; [croppedPhotoPath] is the poster still used for thumbs / RemoteViews seed.
     */
    val gifSourcePath: String? = null,
    /** Frame delay for this GIF's home-screen animation, in milliseconds. */
    val gifFrameInterval: Long = 0,
) : Parcelable {
    val isGifItem: Boolean get() = !gifSourcePath.isNullOrEmpty()
}

/**
 * Returns the path of the photo to be displayed to the user.
 *
 * If the photo has been cropped and [viewOriginalPhoto] is false, the cropped photo path is
 * returned. Otherwise, the original photo path is returned (`originalPhotoPath` and
 * `externalUri` are mutually exclusive and only one of them is expected to be not null).
 * For GIF playlist items, prefers [gifSourcePath] when viewing the original.
 */
fun LocalPhoto.getPhotoPath(viewOriginalPhoto: Boolean = false): String? {
    return when {
        viewOriginalPhoto && gifSourcePath != null -> gifSourcePath
        !croppedPhotoPath.isNullOrEmpty() && !viewOriginalPhoto -> croppedPhotoPath
        gifSourcePath != null -> gifSourcePath
        originalPhotoPath != null -> originalPhotoPath
        externalUri != null -> externalUri.toString()
        else -> null
    }
}
