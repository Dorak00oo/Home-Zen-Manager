package com.fibelatti.photowidget.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val AppIcons.Wallpaper: ImageVector
    get() {
        if (_Wallpaper != null) {
            return _Wallpaper!!
        }
        _Wallpaper = ImageVector.Builder(
            name = "Wallpaper",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 4f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(16f)
                horizontalLineTo(4f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 15f)
                lineTo(9f, 10f)
                lineTo(13f, 14f)
                lineTo(16f, 11f)
                lineTo(20f, 15f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9f, 9f)
                moveToRelative(-1.5f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, 3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, -3f, 0f)
            }
        }.build()
        return _Wallpaper!!
    }

@Suppress("ObjectPropertyName")
private var _Wallpaper: ImageVector? = null

val AppIcons.WallpaperSelected: ImageVector
    get() {
        if (_WallpaperSelected != null) {
            return _WallpaperSelected!!
        }
        _WallpaperSelected = ImageVector.Builder(
            name = "WallpaperSelected",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 4f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(16f)
                horizontalLineTo(4f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(4f, 15f)
                lineTo(9f, 10f)
                lineTo(13f, 14f)
                lineTo(16f, 11f)
                lineTo(20f, 15f)
                verticalLineToRelative(5f)
                horizontalLineTo(4f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(9f, 9f)
                moveToRelative(-1.5f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, 3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, -3f, 0f)
            }
        }.build()
        return _WallpaperSelected!!
    }

@Suppress("ObjectPropertyName")
private var _WallpaperSelected: ImageVector? = null
