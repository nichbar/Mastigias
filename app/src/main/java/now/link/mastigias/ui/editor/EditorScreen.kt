package now.link.mastigias.ui.editor

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.ui.common.ConfirmationDialog
import now.link.mastigias.ui.editor.dialogs.AddFieldDialog
import now.link.mastigias.ui.editor.dialogs.LyricsBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBatchEditor: (LongArray) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var pendingBatchTrackIds by remember { mutableStateOf<LongArray?>(null) }
    var showAddFieldDialog by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var hasNavigatedBack by remember { mutableStateOf(false) }
    val lyricsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val safeNavigateBack: () -> Unit = {
        if (!hasNavigatedBack) {
            hasNavigatedBack = true
            onNavigateBack()
        }
    }

    // Android 11+ Scoped Storage write permission consent launcher
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onConsentGranted()
        } else {
            viewModel.onConsentDenied()
        }
    }

    // Collect one-off UI events (Toasts, Navigation, Consent Requests)
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is EditorUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is EditorUiEvent.NavigateBack -> {
                    safeNavigateBack()
                }
                is EditorUiEvent.RequestStorageConsent -> {
                    val request = IntentSenderRequest.Builder(event.intentSender).build()
                    consentLauncher.launch(request)
                }
                is EditorUiEvent.NavigateToBatchEditor -> {
                    if (uiState.isDirty) {
                        pendingBatchTrackIds = event.trackIds
                    } else {
                        onNavigateToBatchEditor(event.trackIds)
                    }
                }
            }
        }
    }

    // Intercept back navigation if dirty; disable back action while saving is in progress
    val handleBackPress: () -> Unit = {
        if (!uiState.isSaving && !hasNavigatedBack) {
            if (uiState.isDirty) {
                showDiscardConfirmation = true
            } else {
                safeNavigateBack()
            }
        }
    }

    BackHandler(enabled = uiState.isDirty || uiState.isSaving, onBack = handleBackPress)

    val isBatch = uiState.mode is EditorMode.Batch
    val titleText = when (val mode = uiState.mode) {
        is EditorMode.Single -> "Edit Track"
        is EditorMode.Batch -> "Batch Edit (${mode.trackIds.size})"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = handleBackPress,
                        enabled = !uiState.isSaving
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = uiState.isDirty,
                enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    scaleIn(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()) +
                    scaleOut(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec())
            ) {
                FloatingActionButton(
                    onClick = {
                        if (!uiState.isSaving) {
                            viewModel.saveMetadata()
                        }
                    },
                    shape = MaterialTheme.shapes.largeIncreased,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save changes"
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isBatch) {
                val batchTrackCount = (uiState.mode as EditorMode.Batch).trackIds.size
                BatchEditorContent(
                    uiState = uiState,
                    trackCount = batchTrackCount,
                    onValueChange = { field, value -> viewModel.updateField(field, value) },
                    onToggleBatchEnabled = { field, isEnabled -> viewModel.toggleFieldBatch(field, isEnabled) },
                    onDeleteField = { field -> viewModel.removeField(field) },
                    onAddFieldClick = { showAddFieldDialog = true },
                    onArtworkSelected = { bytes, mime, w, h -> viewModel.setArtwork(bytes, mime, w, h) },
                    onArtworkRemoved = { viewModel.removeArtwork() },
                    onToggleArtworkBatch = { isEnabled -> viewModel.toggleArtworkBatch(isEnabled) }
                )
            } else {
                SingleEditorContent(
                    uiState = uiState,
                    onValueChange = { field, value -> viewModel.updateField(field, value) },
                    onDeleteField = { field -> viewModel.removeField(field) },
                    onAddFieldClick = { showAddFieldDialog = true },
                    onOpenLyricsClick = { showLyricsSheet = true },
                    onArtworkSelected = { bytes, mime, w, h -> viewModel.setArtwork(bytes, mime, w, h) },
                    onArtworkRemoved = { viewModel.removeArtwork() }
                )
            }

            // Saving progress overlay
            if (uiState.isSaving) {
                AlertDialog(
                    onDismissRequest = { /* Prevent dismiss during save */ },
                    title = { Text("Saving Metadata") },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isBatch) {
                                LinearWavyProgressIndicator(
                                    progress = { uiState.saveProgress },
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                                Text("${(uiState.saveProgress * 100).toInt()}% completed")
                            } else {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .padding(8.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Writing tags safely to audio file...")
                            }
                        }
                    },
                    confirmButton = {}
                )
            }
        }
    }

    // Discard Confirmation Dialog
    if (showDiscardConfirmation) {
        ConfirmationDialog(
            title = "Discard Changes?",
            message = "You have unsaved changes. Are you sure you want to discard them and exit?",
            onConfirm = { safeNavigateBack() },
            onDismiss = { showDiscardConfirmation = false },
            confirmButtonText = "Discard",
            isDestructive = true
        )
    }

    // Discard confirmation before navigating to batch album editor
    if (pendingBatchTrackIds != null) {
        ConfirmationDialog(
            title = "Discard Changes?",
            message = "You have unsaved changes. Discard them and edit all songs in this album?",
            onConfirm = {
                val ids = pendingBatchTrackIds
                pendingBatchTrackIds = null
                if (ids != null) {
                    onNavigateToBatchEditor(ids)
                }
            },
            onDismiss = { pendingBatchTrackIds = null },
            confirmButtonText = "Discard & Edit Album",
            isDestructive = true
        )
    }

    // Add Field Dialog
    if (showAddFieldDialog) {
        AddFieldDialog(
            existingFields = uiState.fields.keys,
            isBatchMode = isBatch,
            onFieldSelected = { field ->
                viewModel.addField(field)
            },
            onDismiss = { showAddFieldDialog = false }
        )
    }

    // Lyrics Bottom Sheet
    if (showLyricsSheet) {
        val currentLyrics = uiState.fields[TagField.LYRICS]?.value ?: ""
        val title = uiState.fields[TagField.TITLE]?.value ?: ""
        val artist = uiState.fields[TagField.ARTIST]?.value ?: ""

        LyricsBottomSheet(
            initialLyrics = currentLyrics,
            trackTitle = title,
            artistName = artist,
            sheetState = lyricsSheetState,
            onSaveLyrics = { newLyrics ->
                viewModel.updateField(TagField.LYRICS, newLyrics)
            },
            onDismiss = {
                coroutineScope.launch {
                    lyricsSheetState.hide()
                    showLyricsSheet = false
                }
            }
        )
    }
}
