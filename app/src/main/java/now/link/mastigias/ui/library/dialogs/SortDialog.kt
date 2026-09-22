package now.link.mastigias.ui.library.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import now.link.mastigias.ui.library.LibrarySortOrder
import now.link.mastigias.ui.library.LibraryViewMode
import now.link.mastigias.ui.library.SortDirection

@Composable
fun SortDialog(
    currentOrder: LibrarySortOrder,
    currentDirection: SortDirection,
    viewMode: LibraryViewMode = LibraryViewMode.TRACKS,
    onApply: (LibrarySortOrder, SortDirection) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dialogTitle = if (viewMode == LibraryViewMode.ALBUMS) "Sort Albums" else "Sort Tracks"
    val availableOrders = if (viewMode == LibraryViewMode.ALBUMS) {
        listOf(LibrarySortOrder.TITLE, LibrarySortOrder.ARTIST)
    } else {
        listOf(
            LibrarySortOrder.TITLE,
            LibrarySortOrder.DATE_MODIFIED,
            LibrarySortOrder.DATE_CREATED
        )
    }

    val initialOrder = if (currentOrder !in availableOrders) {
        availableOrders.first()
    } else {
        currentOrder
    }
    var selectedOrder by remember { mutableStateOf(initialOrder) }
    var selectedDirection by remember { mutableStateOf(currentDirection) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = dialogTitle,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = "Sort by",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                availableOrders.forEach { order ->
                    val label = if (viewMode == LibraryViewMode.ALBUMS) {
                        when (order) {
                            LibrarySortOrder.TITLE -> "Album Title"
                            LibrarySortOrder.ARTIST -> "Artist"
                            else -> "Album Title"
                        }
                    } else {
                        when (order) {
                            LibrarySortOrder.TITLE -> "Title"
                            LibrarySortOrder.DATE_MODIFIED -> "Last modified date"
                            LibrarySortOrder.DATE_CREATED -> "Created date"
                            else -> "Title"
                        }
                    }
                    val onSelectOrder = {
                        if (selectedOrder != order) {
                            selectedOrder = order
                            selectedDirection = if (order == LibrarySortOrder.DATE_MODIFIED || order == LibrarySortOrder.DATE_CREATED) {
                                SortDirection.DESCENDING
                            } else {
                                SortDirection.ASCENDING
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSelectOrder)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOrder == order,
                            onClick = onSelectOrder
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Direction",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                val isDateSort = selectedOrder == LibrarySortOrder.DATE_MODIFIED || selectedOrder == LibrarySortOrder.DATE_CREATED
                SortDirection.entries.forEach { direction ->
                    val label = when (direction) {
                        SortDirection.ASCENDING -> if (isDateSort) "Ascending (Oldest first)" else "Ascending (A → Z)"
                        SortDirection.DESCENDING -> if (isDateSort) "Descending (Newest first)" else "Descending (Z → A)"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDirection = direction }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedDirection == direction,
                            onClick = { selectedDirection = direction }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(selectedOrder, selectedDirection)
                    onDismiss()
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}
