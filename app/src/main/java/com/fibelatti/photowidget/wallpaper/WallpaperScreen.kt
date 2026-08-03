package com.fibelatti.photowidget.wallpaper

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fibelatti.photowidget.R
import com.fibelatti.photowidget.configure.PhotoWidgetCycleModeBottomSheet
import com.fibelatti.photowidget.model.PhotoWidgetCycleMode
import com.fibelatti.photowidget.ui.AsyncPhotoViewer
import com.fibelatti.photowidget.ui.BooleanListItem
import com.fibelatti.photowidget.ui.PickerListItem
import com.fibelatti.photowidget.ui.icons.AppIcons
import com.fibelatti.photowidget.ui.icons.Back
import com.fibelatti.photowidget.ui.icons.Trash
import com.fibelatti.ui.component.rememberAppSheetState
import com.fibelatti.ui.foundation.Shapes
import java.util.concurrent.TimeUnit

@Composable
fun WallpaperScreen(
    modifier: Modifier = Modifier,
    viewModel: WallpaperViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.showLibrary) {
        WallpaperLibraryScreen(
            state = state,
            onBack = viewModel::closeLibrary,
            onSelect = viewModel::selectMedia,
            onRemove = viewModel::removeMedia,
            modifier = modifier,
        )
    } else {
        WallpaperHubContent(
            state = state,
            onLinkedChange = viewModel::setLinked,
            onSelectSurface = viewModel::selectSurface,
            onImportUris = viewModel::importUris,
            onAddFolder = viewModel::addFolder,
            onRemoveFolder = viewModel::removeFolder,
            onOpenLibrary = viewModel::openLibrary,
            onShuffleChange = viewModel::setShuffle,
            onCycleModeChange = viewModel::setCycleMode,
            onActivate = viewModel::activate,
            modifier = modifier,
        )
    }
}

@Composable
private fun WallpaperHubContent(
    state: WallpaperUiState,
    onLinkedChange: (Boolean) -> Unit,
    onSelectSurface: (WallpaperSurface) -> Unit,
    onImportUris: (List<Uri>, WallpaperMediaType?) -> Unit,
    onAddFolder: (Uri) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onCycleModeChange: (PhotoWidgetCycleMode) -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cycleSheetState = rememberAppSheetState()

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris -> if (uris.isNotEmpty()) onImportUris(uris, null) },
    )
    val gifPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            if (uris.isNotEmpty()) onImportUris(uris, WallpaperMediaType.GIF)
        },
    )
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            if (uris.isNotEmpty()) onImportUris(uris, WallpaperMediaType.VIDEO)
        },
    )
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri -> if (uri != null) onAddFolder(uri) },
    )

    val hasContent = state.surfaceConfig.media.isNotEmpty() ||
        state.surfaceConfig.folderUris.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.wallpaper_hub_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )

        BooleanListItem(
            headlineText = stringResource(R.string.wallpaper_hub_use_same),
            currentValue = state.linked,
            onValueChange = onLinkedChange,
            shape = Shapes.StandaloneShape,
        )

        PrimaryTabRow(
            selectedTabIndex = if (state.selectedSurface == WallpaperSurface.HOME) 0 else 1,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Tab(
                selected = state.selectedSurface == WallpaperSurface.HOME,
                onClick = { onSelectSurface(WallpaperSurface.HOME) },
                text = { Text(stringResource(R.string.wallpaper_hub_tab_home)) },
            )
            Tab(
                selected = state.selectedSurface == WallpaperSurface.LOCK,
                onClick = { onSelectSurface(WallpaperSurface.LOCK) },
                enabled = !state.linked,
                text = { Text(stringResource(R.string.wallpaper_hub_tab_lock)) },
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { mediaPicker.launch("image/*") }) {
                    Text(stringResource(R.string.wallpaper_hub_add_photos))
                }
                TextButton(onClick = { gifPicker.launch("image/gif") }) {
                    Text(stringResource(R.string.wallpaper_hub_add_gifs))
                }
                TextButton(onClick = { videoPicker.launch("video/*") }) {
                    Text(stringResource(R.string.wallpaper_hub_add_videos))
                }
                TextButton(onClick = { folderPicker.launch(null) }) {
                    Text(stringResource(R.string.wallpaper_hub_add_folder))
                }
            }

            if (!hasContent) {
                Text(
                    text = stringResource(R.string.wallpaper_hub_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.wallpaper_hub_library_summary,
                        state.surfaceConfig.media.size,
                        state.surfaceConfig.folderUris.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedButton(
                    onClick = onOpenLibrary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.wallpaper_hub_open_library))
                }
            }

            if (state.surfaceConfig.folderUris.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.wallpaper_hub_folders),
                    style = MaterialTheme.typography.titleSmall,
                )
                state.surfaceConfig.folderUris.forEach { folder ->
                    PickerListItem(
                        headlineText = Uri.parse(folder).lastPathSegment ?: folder,
                        currentValue = stringResource(R.string.wallpaper_hub_remove_folder),
                        onClick = { onRemoveFolder(folder) },
                        shape = Shapes.StandaloneShape,
                    )
                }
            }

            Text(
                text = stringResource(R.string.wallpaper_hub_behavior),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )

            Text(
                text = stringResource(R.string.wallpaper_hub_double_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val cycleMode = state.surfaceConfig.cycleMode.toPhotoWidgetCycleMode()
            PickerListItem(
                headlineText = stringResource(R.string.widget_defaults_cycling),
                currentValue = cycleModeLabel(cycleMode),
                onClick = cycleSheetState::showBottomSheet,
                shape = Shapes.TopShape,
            )

            BooleanListItem(
                headlineText = stringResource(R.string.widget_defaults_shuffle),
                currentValue = state.surfaceConfig.shuffle,
                onValueChange = onShuffleChange,
                shape = Shapes.BottomShape,
                modifier = Modifier.padding(top = 2.dp),
            )

            Text(
                text = stringResource(R.string.wallpaper_hub_oem_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Button(
            onClick = onActivate,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            enabled = hasContent,
        ) {
            Text(stringResource(R.string.wallpaper_hub_activate))
        }
    }

    PhotoWidgetCycleModeBottomSheet(
        sheetState = cycleSheetState,
        cycleMode = state.surfaceConfig.cycleMode.toPhotoWidgetCycleMode(),
        canUseAdvancedSchedule = false,
        onApplyClick = onCycleModeChange,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WallpaperLibraryScreen(
    state: WallpaperUiState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(
                        R.string.wallpaper_hub_library_title,
                        state.libraryTotalCount,
                    ),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = rememberVectorPainter(AppIcons.Back),
                        contentDescription = null,
                    )
                }
            },
        )

        when {
            state.libraryLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.libraryItems.isEmpty() -> {
                Text(
                    text = stringResource(R.string.wallpaper_hub_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = state.libraryItems,
                        key = { it.id },
                        contentType = { it.type },
                    ) { media ->
                        WallpaperLibraryThumb(
                            media = media,
                            selected = media.id == state.surfaceConfig.currentId,
                            canRemove = !media.id.startsWith("folder:"),
                            onSelect = { onSelect(media.id) },
                            onRemove = { onRemove(media.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperLibraryThumb(
    media: WallpaperMedia,
    selected: Boolean,
    canRemove: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onSelect),
    ) {
        AsyncPhotoViewer(
            data = media.path,
            isLoading = false,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
            )
        }
        Text(
            text = media.type.name.take(1),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
        if (canRemove) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    painter = rememberVectorPainter(AppIcons.Trash),
                    contentDescription = stringResource(R.string.wallpaper_hub_remove),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Text(
            text = media.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun cycleModeLabel(cycleMode: PhotoWidgetCycleMode): String {
    return when (cycleMode) {
        is PhotoWidgetCycleMode.Interval -> {
            val interval = cycleMode.loopingInterval
            val unitRes = when (interval.timeUnit) {
                TimeUnit.SECONDS -> R.plurals.photo_widget_configure_interval_current_seconds
                TimeUnit.MINUTES -> R.plurals.photo_widget_configure_interval_current_minutes
                TimeUnit.HOURS -> R.plurals.photo_widget_configure_interval_current_hours
                else -> R.plurals.photo_widget_configure_interval_current_days
            }
            stringResource(
                R.string.photo_widget_configure_interval_current_label,
                pluralStringResource(
                    id = unitRes,
                    count = interval.repeatInterval.toInt(),
                    interval.repeatInterval,
                ),
            )
        }

        is PhotoWidgetCycleMode.Disabled -> {
            stringResource(R.string.photo_widget_configure_cycling_mode_disabled)
        }

        is PhotoWidgetCycleMode.Schedule,
        is PhotoWidgetCycleMode.AdvancedSchedule,
        -> stringResource(R.string.photo_widget_configure_cycling_mode_interval)
    }
}
