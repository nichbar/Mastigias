package now.link.mastigias.core.common

import java.util.Locale

/**
 * Formats duration in milliseconds to mm:ss or hh:mm:ss format.
 */
fun Long.toFormattedDuration(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/**
 * Formats file size in bytes to human-readable string (B, KB, MB, GB).
 */
fun Long.toFormattedFileSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(this.toDouble()) / Math.log10(1024.0)).toInt()
    val groupIndex = digitGroups.coerceIn(0, units.size - 1)
    val size = this / Math.pow(1024.0, groupIndex.toDouble())
    return String.format(Locale.US, "%.1f %s", size, units[groupIndex])
}

/**
 * Extracts the file extension (without dot) in lowercase from a path or file name.
 */
fun String.getFileExtension(): String {
    val dotIndex = this.lastIndexOf('.')
    return if (dotIndex >= 0 && dotIndex < this.length - 1) {
        this.substring(dotIndex + 1).lowercase(Locale.ROOT)
    } else {
        ""
    }
}
