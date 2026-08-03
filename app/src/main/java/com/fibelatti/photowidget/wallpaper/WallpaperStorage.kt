package com.fibelatti.photowidget.wallpaper

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber

@Singleton
class WallpaperStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val rootDir: File by lazy {
        File(context.filesDir, "wallpapers").apply { mkdirs() }
    }

    private val _config: MutableStateFlow<WallpaperHubConfig> = MutableStateFlow(loadConfig())
    val config: StateFlow<WallpaperHubConfig> = _config.asStateFlow()

    @Volatile
    private var playlistCacheKey: String? = null

    @Volatile
    private var playlistCache: List<WallpaperMedia> = emptyList()

    fun currentConfig(): WallpaperHubConfig = _config.value

    fun updateConfig(transform: (WallpaperHubConfig) -> WallpaperHubConfig) {
        val next = transform(_config.value).sanitized()
        persist(next)
        _config.value = next
        invalidatePlaylistCache()
    }

    fun setLinked(linked: Boolean) {
        updateConfig { current ->
            if (linked) {
                current.copy(linked = true, lock = current.home)
            } else {
                current.copy(linked = false)
            }
        }
    }

    fun updateSurface(
        surface: WallpaperSurface,
        transform: (WallpaperSurfaceConfig) -> WallpaperSurfaceConfig,
    ) {
        updateConfig { hub ->
            if (hub.linked || surface == WallpaperSurface.HOME) {
                val home = transform(hub.home)
                hub.copy(home = home, lock = if (hub.linked) home else hub.lock)
            } else {
                hub.copy(lock = transform(hub.lock))
            }
        }
    }

    suspend fun importMedia(
        surface: WallpaperSurface,
        uris: List<Uri>,
        forcedType: WallpaperMediaType? = null,
    ): List<WallpaperMedia> = withContext(Dispatchers.IO) {
        val dir = surfaceDir(surface)
        val imported = uris.mapNotNull { uri ->
            runCatching {
                val type = forcedType ?: detectType(uri)
                val id = UUID.randomUUID().toString()
                val extension = when (type) {
                    WallpaperMediaType.VIDEO -> "mp4"
                    WallpaperMediaType.GIF -> "gif"
                    WallpaperMediaType.PHOTO -> "jpg"
                }
                val dest = File(dir, "$id.$extension")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null

                WallpaperMedia(
                    id = id,
                    type = type,
                    path = dest.absolutePath,
                    displayName = uri.lastPathSegment.orEmpty(),
                )
            }.onFailure { Timber.e(it, "Failed to import wallpaper media") }
                .getOrNull()
        }

        if (imported.isNotEmpty()) {
            updateSurface(surface) { current ->
                current.copy(
                    media = current.media + imported,
                    currentId = current.currentId ?: imported.first().id,
                )
            }
        }
        imported
    }

    fun addFolder(surface: WallpaperSurface, folderUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                folderUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { Timber.w(it, "Could not persist folder permission") }

        updateSurface(surface) { current ->
            current.copy(
                folderUris = (current.folderUris + folderUri.toString()).distinct(),
                currentId = current.currentId ?: "folder-pending",
            )
        }
    }

    fun removeMedia(surface: WallpaperSurface, mediaId: String) {
        if (mediaId.startsWith(FOLDER_ID_PREFIX)) return
        updateSurface(surface) { current ->
            val target = current.media.firstOrNull { it.id == mediaId }
            target?.path?.let { File(it).delete() }
            val remaining = current.media.filterNot { it.id == mediaId }
            current.copy(
                media = remaining,
                currentId = when (current.currentId) {
                    mediaId -> remaining.firstOrNull()?.id
                    else -> current.currentId
                },
            )
        }
    }

    fun removeFolder(surface: WallpaperSurface, folderUri: String) {
        updateSurface(surface) { current ->
            val nextFolders = current.folderUris - folderUri
            current.copy(
                folderUris = nextFolders,
                currentId = when {
                    current.currentId?.startsWith(FOLDER_ID_PREFIX) == true ->
                        current.media.firstOrNull()?.id
                    else -> current.currentId
                },
            )
        }
    }

    fun setCurrent(surface: WallpaperSurface, mediaId: String) {
        setCurrentKeepingCache(surface, mediaId)
    }

    /**
     * Imported picks + folder children resolved lazily (not persisted).
     * Safe to call from IO; caches until config changes.
     */
    suspend fun resolvePlaylist(surface: WallpaperSurface): List<WallpaperMedia> =
        withContext(Dispatchers.IO) {
            val config = currentConfig().configFor(surface)
            val key = playlistKey(surface, config)
            if (key == playlistCacheKey) return@withContext playlistCache

            val folderItems = config.folderUris.flatMap { listFolderMedia(it) }
            val resolved = config.media + folderItems
            playlistCacheKey = key
            playlistCache = resolved

            val currentMissing = config.currentId == null ||
                config.currentId == "folder-pending" ||
                resolved.none { it.id == config.currentId }
            if (currentMissing) {
                resolved.firstOrNull()?.let { first ->
                    setCurrentKeepingCache(surface, first.id)
                }
            }
            resolved
        }

    fun playlistSummary(surface: WallpaperSurface): WallpaperPlaylistSummary {
        val config = currentConfig().configFor(surface)
        return WallpaperPlaylistSummary(
            importedCount = config.media.size,
            folderCount = config.folderUris.size,
            currentId = config.currentId,
        )
    }

    suspend fun advanceCurrent(surface: WallpaperSurface = WallpaperSurface.HOME): WallpaperMedia? {
        val playlist = resolvePlaylist(surface)
        if (playlist.isEmpty()) return null
        val config = currentConfig().configFor(surface)
        val ordered = if (config.shuffle) playlist.shuffled() else playlist
        val index = ordered.indexOfFirst { it.id == config.currentId }.coerceAtLeast(0)
        val next = ordered[(index + 1) % ordered.size]
        setCurrentKeepingCache(surface, next.id)
        return next
    }

    suspend fun currentMedia(surface: WallpaperSurface = WallpaperSurface.HOME): WallpaperMedia? {
        val playlist = resolvePlaylist(surface)
        val config = currentConfig().configFor(surface)
        return playlist.firstOrNull { it.id == config.currentId } ?: playlist.firstOrNull()
    }

    private fun setCurrentKeepingCache(surface: WallpaperSurface, mediaId: String) {
        val hub = _config.value
        val next = if (hub.linked || surface == WallpaperSurface.HOME) {
            val home = hub.home.copy(currentId = mediaId)
            hub.copy(home = home, lock = if (hub.linked) home else hub.lock)
        } else {
            hub.copy(lock = hub.lock.copy(currentId = mediaId))
        }
        persist(next)
        _config.value = next
    }

    fun intervalSeconds(surface: WallpaperSurface = WallpaperSurface.HOME): Long? {
        return when (val mode = currentConfig().configFor(surface).cycleMode) {
            is SerializedCycleMode.Disabled -> null
            is SerializedCycleMode.Interval -> {
                val unit = runCatching { TimeUnit.valueOf(mode.timeUnit) }
                    .getOrDefault(TimeUnit.HOURS)
                unit.toSeconds(mode.repeatInterval)
            }
        }
    }

    private fun listFolderMedia(folderUriString: String): List<WallpaperMedia> {
        val treeUri = runCatching { Uri.parse(folderUriString) }.getOrNull() ?: return emptyList()
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return emptyList()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val result = mutableListOf<WallpaperMedia>()

        runCatching {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val mime = cursor.getString(mimeIdx)
                    val type = mimeToType(mime) ?: continue
                    val childId = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx).orEmpty()
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    result += WallpaperMedia(
                        id = "$FOLDER_ID_PREFIX$fileUri",
                        type = type,
                        path = fileUri.toString(),
                        displayName = name,
                    )
                }
            }
        }.onFailure { Timber.e(it, "Failed to list wallpaper folder") }

        return result
    }

    private fun playlistKey(surface: WallpaperSurface, config: WallpaperSurfaceConfig): String {
        return buildString {
            append(surface.name)
            append('|')
            append(config.media.joinToString(",") { it.id })
            append('|')
            append(config.folderUris.joinToString(","))
        }
    }

    private fun invalidatePlaylistCache() {
        playlistCacheKey = null
        playlistCache = emptyList()
    }

    private fun surfaceDir(surface: WallpaperSurface): File {
        return File(rootDir, surface.name.lowercase()).apply { mkdirs() }
    }

    private fun detectType(uri: Uri): WallpaperMediaType {
        return mimeToType(context.contentResolver.getType(uri)) ?: WallpaperMediaType.PHOTO
    }

    private fun mimeToType(mime: String?): WallpaperMediaType? {
        val value = mime.orEmpty()
        return when {
            value.startsWith("video/") -> WallpaperMediaType.VIDEO
            value.equals("image/gif", ignoreCase = true) -> WallpaperMediaType.GIF
            value.startsWith("image/") -> WallpaperMediaType.PHOTO
            else -> null
        }
    }

    private fun loadConfig(): WallpaperHubConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return WallpaperHubConfig()
        return runCatching { json.decodeFromString(WallpaperHubConfig.serializer(), raw) }
            .getOrElse {
                Timber.e(it, "Failed to decode wallpaper config")
                WallpaperHubConfig()
            }
            .sanitized()
    }

    private fun persist(config: WallpaperHubConfig) {
        prefs.edit {
            putString(KEY_CONFIG, json.encodeToString(WallpaperHubConfig.serializer(), config))
        }
    }

    private fun WallpaperHubConfig.sanitized(): WallpaperHubConfig {
        return copy(
            home = home.copy(media = home.media.filterNot { it.id.startsWith(FOLDER_ID_PREFIX) }),
            lock = lock.copy(media = lock.media.filterNot { it.id.startsWith(FOLDER_ID_PREFIX) }),
        )
    }

    private companion object {
        const val PREFS_NAME = "com.fibelatti.photowidget.Wallpaper"
        const val KEY_CONFIG = "wallpaper_hub_config"
        const val FOLDER_ID_PREFIX = "folder:"
    }
}

data class WallpaperPlaylistSummary(
    val importedCount: Int,
    val folderCount: Int,
    val currentId: String?,
)
