package com.fibelatti.photowidget.model

data class GifFrames(
    /**
     * Playlist entry for one imported GIF (not individual animation frames).
     */
    val gif: LocalPhoto?,
    val interval: Long,
) {

    companion object {

        val EMPTY = GifFrames(gif = null, interval = 0)

        const val MIN_INTERVAL_MS: Long = 20
        const val MAX_INTERVAL_MS: Long = 200
    }
}
