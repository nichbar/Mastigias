package now.link.mastigias.ui.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import now.link.mastigias.domain.model.AudioMetadata
import java.io.File
import java.util.Locale

@Composable
fun TechnicalInfoCard(
    metadata: AudioMetadata,
    modifier: Modifier = Modifier
) {
    val file = File(metadata.path)
    val fileSizeFormatted = if (file.exists()) {
        formatFileSize(file.length())
    } else {
        "--"
    }

    val containerFormat = file.extension.uppercase(Locale.ROOT).ifBlank { "AUDIO" }

    val channelsFormatted = when (metadata.channels) {
        1 -> "Mono (1 channel)"
        2 -> "Stereo (2 channels)"
        6 -> "5.1 Surround (6 channels)"
        8 -> "7.1 Surround (8 channels)"
        else -> "${metadata.channels} channels"
    }

    val sampleRateFormatted = if (metadata.sampleRateHz > 0) {
        String.format(Locale.ROOT, "%,d Hz", metadata.sampleRateHz)
    } else {
        "--"
    }

    val bitrateFormatted = if (metadata.bitrateKbps > 0) {
        "${metadata.bitrateKbps} kbps"
    } else {
        "--"
    }

    val durationFormatted = formatDuration(metadata.durationMs)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Technical File Info",
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(10.dp))

            InfoRow(label = "Format", value = containerFormat)
            InfoRow(label = "Bitrate", value = bitrateFormatted)
            InfoRow(label = "Sample Rate", value = sampleRateFormatted)
            InfoRow(label = "Channels", value = channelsFormatted)
            InfoRow(label = "Duration", value = durationFormatted)
            InfoRow(label = "File Size", value = fileSizeFormatted)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Text(
                text = "File Location",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = metadata.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var digitGroups = 0
    var size = bytes.toDouble()
    while (size >= 1024 && digitGroups < units.size - 1) {
        size /= 1024
        digitGroups++
    }
    return String.format(Locale.ROOT, "%.1f %s", size, units[digitGroups])
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}
