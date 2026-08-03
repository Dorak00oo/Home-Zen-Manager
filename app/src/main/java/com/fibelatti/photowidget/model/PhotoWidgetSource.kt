package com.fibelatti.photowidget.model

import androidx.annotation.StringRes
import com.fibelatti.photowidget.R

/**
 * Describes where the photos from a widget are coming from.
 */
enum class PhotoWidgetSource(
    @StringRes val label: Int,
) {

    /**
     * The user selected each photo individually.
     */
    PHOTOS(label = R.string.photo_widget_source_photos),

    /**
     * The user selected a directory and all its photos are added automatically.
     */
    DIRECTORY(label = R.string.photo_widget_source_directory),

    /**
     * The user selected one or more GIF files. Each file is a playlist item (like a photo);
     * extracted frames are used only to animate the current GIF on the home screen.
     */
    GIF(label = R.string.photo_widget_source_gif),
}
