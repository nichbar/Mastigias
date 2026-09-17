package now.link.mastigias.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import now.link.mastigias.R
import now.link.mastigias.domain.model.FilterMode
import now.link.mastigias.domain.model.FolderFilter
import now.link.mastigias.domain.repository.ThemeMode
import now.link.mastigias.ui.common.AppLayoutBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLogs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // Refresh permissions when app returns to foreground from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val treePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Ignore if unable to take persistable permission
            }

            val decodedPath = uri.path ?: uri.toString()
            val simplifiedPath = if (decodedPath.contains(":")) {
                decodedPath.substringAfter(":")
            } else {
                decodedPath
            }

            val newFilter = FolderFilter(
                uri = uri.toString(),
                path = simplifiedPath,
                mode = FilterMode.INCLUDE
            )
            viewModel.addFolderFilter(newFilter)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        AppLayoutBody(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // --- Appearance ---
                SettingsSectionHeader(title = "Appearance")
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val themeOptions = listOf(
                        ThemeMode.SYSTEM to "System",
                        ThemeMode.LIGHT to "Light",
                        ThemeMode.DARK to "Dark"
                    )
                    themeOptions.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = uiState.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                            label = { Text(label) }
                        )
                    }
                }

                HorizontalDivider()

                // --- Storage & Permissions (API 31+) ---
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsSectionHeader(title = "Storage & Permissions")
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "Manage Media Permission",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Column {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (uiState.hasManageMediaPermission) {
                                        "Manage Media permission is active. Batch and single track writes modify files seamlessly without individual system confirmation dialogs."
                                    } else {
                                        "Granting this special permission allows Mastigias to update audio metadata without showing Android system consent dialogs for each track."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!uiState.hasManageMediaPermission) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                context.startActivity(fallback)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Grant Manage Media Permission")
                                    }
                                }
                            }
                        },
                        trailingContent = {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (uiState.hasManageMediaPermission) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (uiState.hasManageMediaPermission) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = if (uiState.hasManageMediaPermission) "Granted" else "Not Granted",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (uiState.hasManageMediaPermission) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    )

                    HorizontalDivider()
                }

                // --- Music Folders & Filters ---
                SettingsSectionHeader(title = "Music Folders & Filters")
                Text(
                    text = "Restrict library scanning to specific folders, or exclude folders containing non-music audio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { treePickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Music Folder")
                    }
                }

                if (uiState.folderFilters.isNotEmpty()) {
                    uiState.folderFilters.forEach { filter ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = filter.path.ifBlank { filter.uri },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = if (filter.isInclude) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.errorContainer
                                        },
                                        modifier = Modifier.clickable { viewModel.toggleFilterMode(filter) }
                                    ) {
                                        Text(
                                            text = if (filter.isInclude) "Include" else "Exclude",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (filter.isInclude) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    IconButton(onClick = { viewModel.removeFolderFilter(filter) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove folder",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }

                HorizontalDivider()

                // --- About ---
                SettingsSectionHeader(title = "About")
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Mastigias",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "High-performance native audio tag editor powered by TagLib C++ via JNI. Features 8-step Scoped Storage atomic write protocol, FLAC picture block encoding, multi-file batch editing, and SongSync lyrics integration.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(id = R.string.enable_logging),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(id = R.string.enable_logging_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.isLoggingEnabled,
                            onCheckedChange = { viewModel.setLoggingEnabled(it) }
                        )
                    }
                )
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(id = R.string.view_logs),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(id = R.string.in_app_diagnostic_buffer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.clickable {
                        onNavigateToLogs()
                    }
                )
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Version",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "1.0.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Source code",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "github.com/nichbar/Mastigias",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Open in browser",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.clickable {
                        val browserIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/nichbar/Mastigias")
                        )
                        try {
                            context.startActivity(browserIntent)
                        } catch (_: Exception) {
                            // Fallback if browser is not installed
                        }
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
    )
}
