package now.link.mastigias.ui.editor.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import now.link.mastigias.domain.model.TagCategory
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.ui.editor.FieldEditState

@Composable
fun TagFieldInput(
    field: TagField,
    editState: FieldEditState,
    isBatchMode: Boolean,
    onValueChange: (String) -> Unit,
    onToggleBatchEnabled: (Boolean) -> Unit,
    onDeleteField: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnabled = if (isBatchMode) editState.isEnabledInBatch else true
    val isNumberField = field == TagField.TRACK_NUMBER ||
        field == TagField.TRACK_TOTAL ||
        field == TagField.DISC_NUMBER ||
        field == TagField.DISC_TOTAL ||
        field == TagField.YEAR ||
        field == TagField.BPM

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isBatchMode) {
            Checkbox(
                checked = editState.isEnabledInBatch,
                onCheckedChange = onToggleBatchEnabled
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        OutlinedTextField(
            value = if (isBatchMode && !editState.isEnabledInBatch) "" else editState.value,
            onValueChange = onValueChange,
            enabled = isEnabled,
            label = { Text(field.displayName) },
            placeholder = {
                Text(
                    text = if (isBatchMode && !editState.isEnabledInBatch) "<unchanged>" else "Enter ${field.displayName.lowercase()}"
                )
            },
            trailingIcon = if (isEnabled && editState.value.isNotEmpty()) {
                {
                    IconButton(onClick = onDeleteField) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear field",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isNumberField) KeyboardType.Number else KeyboardType.Text
            ),
            singleLine = field.category != TagCategory.LYRICS && field != TagField.COMMENT,
            maxLines = if (field == TagField.COMMENT) 3 else 1,
            supportingText = if (editState.isDirty) {
                { Text("Modified", color = MaterialTheme.colorScheme.primary) }
            } else null,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (field == TagField.COMMENT) {
                        Modifier.heightIn(min = 100.dp)
                    } else if (editState.isDirty) {
                        Modifier.height(84.dp)
                    } else {
                        Modifier.height(64.dp)
                    }
                )
        )
    }
}
