package com.fibelatti.photowidget.wallpaper

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fibelatti.photowidget.model.PhotoWidgetCycleMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WallpaperUiState(
    val linked: Boolean = true,
    val selectedSurface: WallpaperSurface = WallpaperSurface.HOME,
    val surfaceConfig: WallpaperSurfaceConfig = WallpaperSurfaceConfig(),
    val showLibrary: Boolean = false,
    val libraryItems: List<WallpaperMedia> = emptyList(),
    val libraryLoading: Boolean = false,
    val libraryTotalCount: Int = 0,
)

@HiltViewModel
class WallpaperViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: WallpaperStorage,
    private val scheduler: WallpaperScheduler,
) : ViewModel() {

    private val selectedSurface = MutableStateFlow(WallpaperSurface.HOME)
    private val showLibrary = MutableStateFlow(false)
    private val libraryItems = MutableStateFlow<List<WallpaperMedia>>(emptyList())
    private val libraryLoading = MutableStateFlow(false)

    val state: StateFlow<WallpaperUiState> = combine(
        storage.config,
        selectedSurface,
        showLibrary,
        libraryItems,
        libraryLoading,
    ) { hub, surface, libraryVisible, items, loading ->
        WallpaperUiState(
            linked = hub.linked,
            selectedSurface = surface,
            surfaceConfig = hub.configFor(surface),
            showLibrary = libraryVisible,
            libraryItems = items,
            libraryLoading = loading,
            libraryTotalCount = items.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WallpaperUiState(
            linked = storage.currentConfig().linked,
            surfaceConfig = storage.currentConfig().home,
        ),
    )

    fun selectSurface(surface: WallpaperSurface) {
        selectedSurface.value = surface
        if (showLibrary.value) loadLibrary()
    }

    fun setLinked(linked: Boolean) {
        storage.setLinked(linked)
        if (linked) selectedSurface.value = WallpaperSurface.HOME
        scheduler.reschedule()
    }

    fun setShuffle(shuffle: Boolean) {
        storage.updateSurface(selectedSurface.value) { it.copy(shuffle = shuffle) }
    }

    fun setCycleMode(mode: PhotoWidgetCycleMode) {
        storage.updateSurface(selectedSurface.value) {
            it.copy(cycleMode = mode.toSerialized())
        }
        scheduler.reschedule()
    }

    fun importUris(uris: List<Uri>, forcedType: WallpaperMediaType? = null) {
        viewModelScope.launch {
            storage.importMedia(selectedSurface.value, uris, forcedType)
            if (showLibrary.value) loadLibrary()
            ZenWallpaperService.requestRedraw(context)
        }
    }

    fun addFolder(uri: Uri) {
        storage.addFolder(selectedSurface.value, uri)
        if (showLibrary.value) loadLibrary()
        ZenWallpaperService.requestRedraw(context)
    }

    fun removeMedia(id: String) {
        storage.removeMedia(selectedSurface.value, id)
        if (showLibrary.value) loadLibrary()
        ZenWallpaperService.requestRedraw(context)
    }

    fun removeFolder(uri: String) {
        storage.removeFolder(selectedSurface.value, uri)
        if (showLibrary.value) loadLibrary()
        ZenWallpaperService.requestRedraw(context)
    }

    fun openLibrary() {
        showLibrary.value = true
        loadLibrary()
    }

    fun closeLibrary() {
        showLibrary.value = false
        libraryItems.value = emptyList()
    }

    fun selectMedia(id: String) {
        storage.setCurrent(selectedSurface.value, id)
        ZenWallpaperService.requestRedraw(context)
    }

    fun activate() {
        viewModelScope.launch {
            storage.resolvePlaylist(WallpaperSurface.HOME)
            if (!storage.currentConfig().linked) {
                storage.resolvePlaylist(WallpaperSurface.LOCK)
            }
            scheduler.reschedule()
            scheduler.applyLockFallbackIfNeeded()
            scheduler.openLiveWallpaperPicker()
        }
    }

    private fun loadLibrary() {
        viewModelScope.launch {
            libraryLoading.value = true
            libraryItems.value = storage.resolvePlaylist(selectedSurface.value)
            libraryLoading.value = false
        }
    }
}
