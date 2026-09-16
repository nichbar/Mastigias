package now.link.mastigias.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import now.link.mastigias.domain.model.TagCategory
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.ui.editor.components.EditorArtworkSection
import now.link.mastigias.ui.editor.components.TagFieldInput

@Composable
fun BatchEditorContent(
    uiState: EditorUiState,
    trackCount: Int,
    onValueChange: (TagField, String) -> Unit,
    onToggleBatchEnabled: (TagField, Boolean) -> Unit,
    onDeleteField: (TagField) -> Unit,
    onAddFieldClick: () -> Unit,
    onArtworkSelected: (ByteArray, String, Int, Int) -> Unit,
    onArtworkRemoved: () -> Unit,
    onToggleArtworkBatch: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp)
    ) {
        // Informational banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Editing $trackCount Tracks",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Check the box next to any tag you wish to overwrite across all selected tracks. Unchecked tags will remain unchanged in individual files. (Lyrics are skipped in batch mode for safety).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        // Dedicated Artwork Batch section
        EditorArtworkSection(
            artwork = uiState.artwork,
            isBatchMode = true,
            isArtworkBatchEnabled = uiState.isArtworkBatchEnabled,
            onArtworkSelected = onArtworkSelected,
            onArtworkRemoved = onArtworkRemoved,
            onToggleBatchEnabled = onToggleArtworkBatch
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        // Batch Fields (excluding LYRICS per spec)
        val validFields = uiState.fields.filter { it.key.category != TagCategory.LYRICS }

        validFields.forEach { (field, state) ->
            TagFieldInput(
                field = field,
                editState = state,
                isBatchMode = true,
                onValueChange = { onValueChange(field, it) },
                onToggleBatchEnabled = { onToggleBatchEnabled(field, it) },
                onDeleteField = { onDeleteField(field) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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
            Text("Add Tag Field to Batch")
        }
    }
}
