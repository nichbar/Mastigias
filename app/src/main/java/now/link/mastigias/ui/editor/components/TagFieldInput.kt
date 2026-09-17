package now.link.mastigias.ui.editor.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
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
    onToggleBatchEnabled: (Boolean) -> Unit = {},
    onDeleteField: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isNumberField = field == TagField.TRACK_NUMBER ||
        field == TagField.TRACK_TOTAL ||
        field == TagField.DISC_NUMBER ||
        field == TagField.DISC_TOTAL ||
        field == TagField.YEAR ||
        field == TagField.BPM

    val placeholderText = if (isBatchMode && editState.isMixed) {
        "<multiple values>"
    } else {
        "Enter ${field.displayName.lowercase()}"
    }

    val supportingMessage: String? = when {
        editState.isDirty && editState.value.isEmpty() -> if (isBatchMode) "Cleared (will delete tag across tracks)" else "Cleared"
        editState.isDirty -> "Modified"
        isBatchMode && editState.isMixed -> "Tracks have different values"
        else -> null
    }

    val supportingColor = when {
        editState.isDirty && editState.value.isEmpty() -> MaterialTheme.colorScheme.error
        editState.isDirty -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = editState.value,
            onValueChange = onValueChange,
            enabled = true,
            label = { Text(field.displayName) },
            placeholder = { Text(text = placeholderText) },
            trailingIcon = if (editState.value.isNotEmpty()) {
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
            supportingText = supportingMessage?.let { msg ->
                { Text(msg, color = supportingColor) }
            },
            modifier = Modifier
                .weight(1f)
                .then(
                    if (field == TagField.COMMENT) {
                        Modifier.heightIn(min = 100.dp)
                    } else if (supportingMessage != null) {
                        Modifier.height(84.dp)
                    } else {
                        Modifier.height(64.dp)
                    }
                )
        )
    }
}
