package now.link.mastigias.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import now.link.mastigias.ui.common.FloatingScrollToTop
import now.link.mastigias.ui.common.MastigiasSearchBar
import now.link.mastigias.ui.library.components.AlbumAccordionItem
import now.link.mastigias.ui.library.components.MultiSelectTopBar
import now.link.mastigias.ui.library.components.TrackListItem
import now.link.mastigias.ui.library.dialogs.FolderFilterDialog
import now.link.mastigias.ui.library.dialogs.SortDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToEditor: (LongArray) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToFolderManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val folderFilters by viewModel.folderFilters.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSortDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.isMultiSelectMode) {
        viewModel.onClearSelection()
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.onClearError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isMultiSelectMode) {
                MultiSelectTopBar(
                    selectedCount = uiState.selectedTrackIds.size,
                    onCloseClick = { viewModel.onClearSelection() },
                    onSelectAllClick = { viewModel.onSelectAll() },
                    onInvertClick = { viewModel.onInvertSelection() },
                    onEditClick = {
                        val ids = uiState.selectedTrackIds.toLongArray()
                        if (ids.isNotEmpty()) {
                            onNavigateToEditor(ids)
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = "Mastigias",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        // Sync button
                        IconButton(
                            onClick = { viewModel.sync() },
                            enabled = !uiState.isSyncing
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan media"
                            )
                        }
                        // Sort button
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Sort library",
                                modifier = Modifier.rotate(-90f)
                            )
                        }
                        // Settings button
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingScrollToTop(lazyListState = lazyListState)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Sync progress indicator
            if (uiState.isSyncing) {
                LinearWavyProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Search Bar
            MastigiasSearchBar(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                onClearClick = { viewModel.onClearSearch() }
            )

            // Filter & Sort chips row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Untagged filter chip
                FilterChip(
                    selected = uiState.isUntaggedFilterActive,
                    onClick = { viewModel.onToggleUntaggedFilter() },
                    label = { Text("Untagged only") }
                )

                // Current sort chip (clickable to open dialog)
                val sortLabel = when (uiState.sortOrder) {
                    LibrarySortOrder.TITLE -> "Title"
                    LibrarySortOrder.ARTIST -> "Artist"
                    LibrarySortOrder.ALBUM -> "Album (Accordion)"
                }
                val dirSymbol = if (uiState.sortDirection == SortDirection.ASCENDING) "↑" else "↓"
                FilterChip(
                    selected = true,
                    onClick = { showSortDialog = true },
                    label = { Text("Sort: $sortLabel $dirSymbol") }
                )

                // Folders filter chip
                FilterChip(
                    selected = folderFilters.isNotEmpty(),
                    onClick = { showFolderDialog = true },
                    label = {
                        val count = folderFilters.size
                        Text(if (count > 0) "Folders ($count)" else "Folders")
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Content: Accordion vs Flat
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (uiState.isAccordionView) {
                    if (uiState.albums.isEmpty()) {
                        EmptyLibraryView(
                            isSyncing = uiState.isSyncing,
                            searchQuery = uiState.searchQuery,
                            onSync = { viewModel.sync() }
                        )
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = uiState.albums,
                                key = { album ->
                                    val id = album.coverTrackId ?: album.tracks.firstOrNull()?.id ?: 0L
                                    "${album.title}_${album.artist}_$id"
                                },
                                contentType = { "album_accordion" }
                            ) { album ->
                                AlbumAccordionItem(
                                    album = album,
                                    selectedTrackIds = uiState.selectedTrackIds,
                                    isMultiSelectMode = uiState.isMultiSelectMode,
                                    onTrackClick = { track ->
                                        viewModel.onTrackClicked(track) { id ->
                                            onNavigateToEditor(longArrayOf(id))
                                        }
                                    },
                                    onTrackLongClick = { track ->
                                        viewModel.onTrackLongClicked(track)
                                    },
                                    onHeaderLongClick = {
                                        viewModel.onAlbumHeaderLongClicked(album)
                                    },
                                    onEditAlbumClick = { albumToEdit ->
                                        viewModel.editAlbum(albumToEdit, onNavigateToEditor)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    if (uiState.tracks.isEmpty()) {
                        EmptyLibraryView(
                            isSyncing = uiState.isSyncing,
                            searchQuery = uiState.searchQuery,
                            onSync = { viewModel.sync() }
                        )
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = uiState.tracks,
                                key = { it.id },
                                contentType = { "track_item" }
                            ) { track ->
                                TrackListItem(
                                    track = track,
                                    isSelected = uiState.selectedTrackIds.contains(track.id),
                                    isMultiSelectMode = uiState.isMultiSelectMode,
                                    onClick = {
                                        viewModel.onTrackClicked(track) { id ->
                                            onNavigateToEditor(longArrayOf(id))
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.onTrackLongClicked(track)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showSortDialog) {
        SortDialog(
            currentOrder = uiState.sortOrder,
            currentDirection = uiState.sortDirection,
            onApply = { order, direction ->
                viewModel.onSortOrderChanged(order)
                viewModel.onSortDirectionChanged(direction)
            },
            onDismiss = { showSortDialog = false }
        )
    }

    if (showFolderDialog) {
        FolderFilterDialog(
            filters = folderFilters,
            onAddFilter = { viewModel.addFolderFilter(it) },
            onRemoveFilter = { viewModel.removeFolderFilter(it) },
            onToggleFilterMode = { viewModel.toggleFolderFilterMode(it) },
            onDismiss = { showFolderDialog = false }
        )
    }
}

@Composable
private fun EmptyLibraryView(
    isSyncing: Boolean,
    searchQuery: String,
    onSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (searchQuery.isNotBlank()) Icons.Default.Info else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (searchQuery.isNotBlank()) {
                    "No music matching \"$searchQuery\""
                } else if (isSyncing) {
                    "Scanning for audio files..."
                } else {
                    "No music tracks found in your library"
                },
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (searchQuery.isBlank() && !isSyncing) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ensure storage permission is granted and your audio files are indexed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
