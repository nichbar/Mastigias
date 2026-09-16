package now.link.mastigias.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp)
    ) {
        // Artwork section
        EditorArtworkSection(
            artwork = uiState.artwork,
            isBatchMode = false,
            isArtworkBatchEnabled = false,
            onArtworkSelected = onArtworkSelected,
            onArtworkRemoved = onArtworkRemoved,
            onToggleBatchEnabled = {}
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        // Basic fields
        val basicFields = uiState.fields.filter { it.key.category == TagCategory.BASIC }
        basicFields.forEach { (field, state) ->
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

        // Non-basic fields (Advanced, Sorting, Musical, URLs, etc.)
        val nonBasicFields = uiState.fields.filter {
            it.key.category != TagCategory.BASIC && it.key.category != TagCategory.LYRICS
        }

        if (nonBasicFields.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Advanced Tags",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))

            nonBasicFields.forEach { (field, state) ->
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

        Spacer(modifier = Modifier.height(12.dp))

        // "+ Add Tag Field" button
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

        // Technical Audio Info Card
        uiState.initialMetadata?.let { metadata ->
            Spacer(modifier = Modifier.height(16.dp))
            TechnicalInfoCard(metadata = metadata)
        }
    }
}
