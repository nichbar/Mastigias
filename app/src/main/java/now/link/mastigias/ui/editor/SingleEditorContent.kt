package now.link.mastigias.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import now.link.mastigias.domain.model.TagCategory
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.ui.editor.components.EditorArtworkSection
import now.link.mastigias.ui.editor.components.TagFieldInput
import now.link.mastigias.ui.editor.components.TechnicalInfoCard

@Composable
fun SingleEditorContent(
    uiState: EditorUiState,
    onValueChange: (TagField, String) -> Unit,
    onDeleteField: (TagField) -> Unit,
    onAddFieldClick: () -> Unit,
    onOpenLyricsClick: () -> Unit,
    onArtworkSelected: (ByteArray, String, Int, Int) -> Unit,
    onArtworkRemoved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val basicFields = remember(uiState.fields) {
        uiState.fields.filter { it.key.category == TagCategory.BASIC }.toList()
    }
    val nonBasicFields = remember(uiState.fields) {
        uiState.fields.filter {
            it.key.category != TagCategory.BASIC && it.key.category != TagCategory.LYRICS
        }.toList()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Artwork section
        item(key = "single_artwork_section") {
            EditorArtworkSection(
                artwork = uiState.artwork,
                isBatchMode = false,
                isArtworkBatchEnabled = false,
                onArtworkSelected = onArtworkSelected,
                onArtworkRemoved = onArtworkRemoved,
                onToggleBatchEnabled = {}
            )
        }

        item(key = "single_divider_basic") {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        // Basic fields
        items(
            items = basicFields,
            key = { (field, _) -> "single_field_${field.name}" }
        ) { (field, state) ->
            TagFieldInput(
                field = field,
                editState = state,
                isBatchMode = false,
                onValueChange = { onValueChange(field, it) },
                onToggleBatchEnabled = {},
                onDeleteField = { onDeleteField(field) }
            )
        }

        // Lyrics button
        item(key = "single_lyrics_button") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lyricsField = uiState.fields[TagField.LYRICS]
                val hasLyrics = lyricsField?.value?.isNotBlank() == true

                OutlinedButton(
                    onClick = onOpenLyricsClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (hasLyrics) "Edit Lyrics (Set)" else "Add Lyrics")
                }
            }
        }

        // Non-basic fields (Advanced, Sorting, Musical, URLs, etc.)
        if (nonBasicFields.isNotEmpty()) {
            item(key = "single_header_advanced") {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Advanced Tags",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            items(
                items = nonBasicFields,
                key = { (field, _) -> "single_field_${field.name}" }
            ) { (field, state) ->
                TagFieldInput(
                    field = field,
                    editState = state,
                    isBatchMode = false,
                    onValueChange = { onValueChange(field, it) },
                    onToggleBatchEnabled = {},
                    onDeleteField = { onDeleteField(field) }
                )
            }
        }

        // "+ Add Tag Field" button
        item(key = "single_add_field_button") {
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onAddFieldClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Tag Field")
            }
        }

        // Technical Audio Info Card
        uiState.initialMetadata?.let { metadata ->
            item(key = "single_technical_info") {
                Spacer(modifier = Modifier.height(16.dp))
                TechnicalInfoCard(metadata = metadata)
            }
        }
    }
}
