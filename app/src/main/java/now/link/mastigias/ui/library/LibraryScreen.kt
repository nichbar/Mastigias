package now.link.mastigias.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import now.link.mastigias.ui.common.ConfirmationDialog
import now.link.mastigias.ui.common.MastigiasSearchBar
import now.link.mastigias.ui.library.components.AlbumAccordionItem
import now.link.mastigias.ui.library.components.LibraryFab
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
    var showViewModeMenu by remember { mutableStateOf(false) }
    var showMultiAlbumWarningDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.clearSelection()
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

    var lastSortOrder by remember { mutableStateOf(uiState.sortOrder) }
    var lastSortDirection by remember { mutableStateOf(uiState.sortDirection) }
    var lastViewMode by remember { mutableStateOf(uiState.viewMode) }

    LaunchedEffect(uiState.tracks, uiState.albums) {
        if (uiState.sortOrder != lastSortOrder ||
            uiState.sortDirection != lastSortDirection ||
            uiState.viewMode != lastViewMode
        ) {
            lastSortOrder = uiState.sortOrder
            lastSortDirection = uiState.sortDirection
            lastViewMode = uiState.viewMode
            lazyListState.scrollToItem(0)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${uiState.selectedCount} selected",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear selection"
                            )
                        }
                    },
                    actions = {
                        // Select All
                        IconButton(onClick = { viewModel.toggleSelectAll() }) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Select all"
                            )
                        }
                        // Batch Edit
                        IconButton(
                            onClick = {
                                if (viewModel.isMultiAlbumSelected()) {
                                    showMultiAlbumWarningDialog = true
                                } else {
                                    val ids = uiState.selectedTrackIds.toLongArray()
                                    viewModel.clearSelection()
                                    onNavigateToEditor(ids)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Batch edit"
                            )
                        }
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
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
            LibraryFab(
                lazyListState = lazyListState,
                isSyncing = uiState.isSyncing,
                onRefresh = { viewModel.sync() }
            )
        }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val bottomNavPadding = innerPadding.calculateBottomPadding()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection)
                )
        ) {
            // Sync progress indicator (shown at top when library already has items)
            if (uiState.isSyncing && !uiState.isEmpty) {
                LinearWavyProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val searchPlaceholder = if (uiState.viewMode == LibraryViewMode.ALBUMS) {
                "Search albums, artists..."
            } else {
                "Search tracks, artists, albums..."
            }

            // Search Bar
            MastigiasSearchBar(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                onClearClick = { viewModel.onClearSearch() },
                placeholderText = searchPlaceholder
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
                // View mode chip with DropdownMenu
                Box {
                    val viewLabel = when (uiState.viewMode) {
                        LibraryViewMode.TRACKS -> "View: Tracks ▾"
                        LibraryViewMode.ALBUMS -> "View: Albums ▾"
                    }
                    FilterChip(
                        selected = true,
                        onClick = { showViewModeMenu = true },
                        label = { Text(viewLabel) }
                    )

                    DropdownMenu(
                        expanded = showViewModeMenu,
                        onDismissRequest = { showViewModeMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tracks (flat list)") },
                            onClick = {
                                viewModel.onViewModeChanged(LibraryViewMode.TRACKS)
                                showViewModeMenu = false
                            },
                            trailingIcon = {
                                if (uiState.viewMode == LibraryViewMode.TRACKS) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Albums (accordion)") },
                            onClick = {
                                viewModel.onViewModeChanged(LibraryViewMode.ALBUMS)
                                showViewModeMenu = false
                            },
                            trailingIcon = {
                                if (uiState.viewMode == LibraryViewMode.ALBUMS) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                    }
                }

                // Current sort chip (clickable to open dialog)
                val sortLabel = if (uiState.viewMode == LibraryViewMode.ALBUMS) {
                    when (uiState.sortOrder) {
                        LibrarySortOrder.ARTIST -> "Artist"
                        else -> "Album Title"
                    }
                } else {
                    when (uiState.sortOrder) {
                        LibrarySortOrder.TITLE -> "Title"
                        LibrarySortOrder.DATE_MODIFIED -> "Last modified date"
                        LibrarySortOrder.DATE_CREATED -> "Created date"
                        else -> "Title"
                    }
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

                // Untagged filter chip
                FilterChip(
                    selected = uiState.isUntaggedFilterActive,
                    onClick = { viewModel.onToggleUntaggedFilter() },
                    label = { Text("Untagged only") }
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
                            isUntaggedFilterActive = uiState.isUntaggedFilterActive,
                            onSync = { viewModel.sync() },
                            modifier = Modifier.padding(bottom = bottomNavPadding)
                        )
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = bottomNavPadding + 80.dp)
                        ) {
                            items(
                                items = uiState.albums,
                                key = { album -> "${uiState.sortOrder}_${uiState.sortDirection}_${album.key}" },
                                contentType = { "album_accordion" }
                            ) { album ->
                                AlbumAccordionItem(
                                    album = album,
                                    isExpanded = album.key in uiState.expandedAlbumKeys,
                                    onToggleExpand = { viewModel.toggleAlbumExpanded(album.key) },
                                    onTrackClick = { track ->
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleTrackSelection(track.id)
                                        } else {
                                            onNavigateToEditor(longArrayOf(track.id))
                                        }
                                    },
                                    onEditAlbumClick = { albumToEdit ->
                                        viewModel.editAlbum(albumToEdit, onNavigateToEditor)
                                    },
                                    selectedTrackIds = uiState.selectedTrackIds,
                                    isSelectionMode = uiState.isSelectionMode,
                                    onTrackLongClick = { track ->
                                        viewModel.toggleTrackSelection(track.id)
                                    },
                                    onToggleTrackSelect = { track ->
                                        viewModel.toggleTrackSelection(track.id)
                                    },
                                    onToggleAlbumSelect = { albumToSelect ->
                                        viewModel.toggleAlbumSelection(albumToSelect)
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
                            isUntaggedFilterActive = uiState.isUntaggedFilterActive,
                            onSync = { viewModel.sync() },
                            modifier = Modifier.padding(bottom = bottomNavPadding)
                        )
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = bottomNavPadding + 80.dp)
                        ) {
                            items(
                                items = uiState.tracks,
                                key = { "${uiState.sortOrder}_${uiState.sortDirection}_${it.id}" },
                                contentType = { "track_item" }
                            ) { track ->
                                TrackListItem(
                                    track = track,
                                    onClick = {
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleTrackSelection(track.id)
                                        } else {
                                            onNavigateToEditor(longArrayOf(track.id))
                                        }
                                    },
                                    isSelected = track.id in uiState.selectedTrackIds,
                                    isSelectionMode = uiState.isSelectionMode,
                                    onLongClick = {
                                        viewModel.toggleTrackSelection(track.id)
                                    },
                                    onToggleSelect = {
                                        viewModel.toggleTrackSelection(track.id)
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
            viewMode = uiState.viewMode,
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

    if (showMultiAlbumWarningDialog) {
        ConfirmationDialog(
            title = "Different Albums Selected",
            message = "The selected tracks belong to different albums. Batch editing might overwrite existing tag values across these distinct albums. Do you want to proceed?",
            confirmButtonText = "Proceed",
            dismissButtonText = "Cancel",
            onConfirm = {
                val ids = uiState.selectedTrackIds.toLongArray()
                viewModel.clearSelection()
                onNavigateToEditor(ids)
            },
            onDismiss = { showMultiAlbumWarningDialog = false }
        )
    }
}

@Composable
private fun EmptyLibraryView(
    isSyncing: Boolean,
    searchQuery: String,
    isUntaggedFilterActive: Boolean,
    onSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = isSyncing,
            label = "EmptyOrLoadingTransition"
        ) { syncing ->
            if (syncing) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Scanning for audio files...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Indexing tags and album artwork across your storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when {
                            searchQuery.isNotBlank() -> Icons.Default.Info
                            isUntaggedFilterActive -> Icons.Default.CheckCircle
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> "No music matching \"$searchQuery\""
                            isUntaggedFilterActive -> "No untagged tracks found"
                            else -> "No music tracks found in your library"
                        },
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> "Try searching with a different title, artist, or album keyword."
                            isUntaggedFilterActive -> "All tracks in your library have title, artist, and album tags."
                            else -> "Ensure storage permission is granted and your audio files are indexed."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isBlank() && !isUntaggedFilterActive) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onSync
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Media")
                        }
                    }
                }
            }
        }
    }
}
