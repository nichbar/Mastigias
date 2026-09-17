package now.link.mastigias.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents a single log entry with timestamp, level, tag, and message.
 */
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
    val id: Long = idGenerator.incrementAndGet()
) {

    private val formattedTime: String = formatTimestamp(timestamp)

    enum class LogLevel(val priority: Int, val shortName: String) {
        VERBOSE(2, "V"),
        DEBUG(3, "D"),
        INFO(4, "I"),
        WARN(5, "W"),
        ERROR(6, "E"),
        ASSERT(7, "A")
    }

    /**
     * Format timestamp to readable string (HH:mm:ss.SSS).
     */
    fun getFormattedTime(): String = formattedTime

    /**
     * Get the full formatted log line.
     */
    fun getFormattedMessage(): String {
        val baseMessage = "$formattedTime ${level.shortName}/$tag: $message"
        return if (throwable != null) {
            "$baseMessage\n${throwable.stackTraceToString()}"
        } else {
            baseMessage
        }
    }

    companion object {
        private val idGenerator = AtomicLong(0L)
        private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault())

        private fun formatTimestamp(timestamp: Long): String {
            return timeFormatter.format(Instant.ofEpochMilli(timestamp))
        }
    }
}
