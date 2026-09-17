package now.link.mastigias.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogEntryTest {

    @Test
    fun `log entry has default timestamp and properties set correctly`() {
        val before = System.currentTimeMillis()
        val entry = LogEntry(
            level = LogEntry.LogLevel.DEBUG,
            tag = "TestTag",
            message = "Test message"
        )
        val after = System.currentTimeMillis()

        assertTrue(entry.timestamp in before..after)
        assertEquals(LogEntry.LogLevel.DEBUG, entry.level)
        assertEquals("TestTag", entry.tag)
        assertEquals("Test message", entry.message)
        assertEquals(null, entry.throwable)
    }

    @Test
    fun `log levels have correct priorities and short names`() {
        assertEquals(2, LogEntry.LogLevel.VERBOSE.priority)
        assertEquals("V", LogEntry.LogLevel.VERBOSE.shortName)

        assertEquals(3, LogEntry.LogLevel.DEBUG.priority)
        assertEquals("D", LogEntry.LogLevel.DEBUG.shortName)

        assertEquals(4, LogEntry.LogLevel.INFO.priority)
        assertEquals("I", LogEntry.LogLevel.INFO.shortName)

        assertEquals(5, LogEntry.LogLevel.WARN.priority)
        assertEquals("W", LogEntry.LogLevel.WARN.shortName)

        assertEquals(6, LogEntry.LogLevel.ERROR.priority)
        assertEquals("E", LogEntry.LogLevel.ERROR.shortName)

        assertEquals(7, LogEntry.LogLevel.ASSERT.priority)
        assertEquals("A", LogEntry.LogLevel.ASSERT.shortName)
    }

    @Test
    fun `getFormattedTime returns valid time format`() {
        val entry = LogEntry(
            timestamp = 1700000000000L,
            level = LogEntry.LogLevel.INFO,
            tag = "Tag",
            message = "Msg"
        )
        val time = entry.getFormattedTime()
        assertTrue(time.matches(Regex("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")))
    }

    @Test
    fun `getFormattedMessage without throwable includes level tag and message`() {
        val entry = LogEntry(
            timestamp = 1700000000000L,
            level = LogEntry.LogLevel.WARN,
            tag = "AudioScanner",
            message = "File skipped"
        )
        val formatted = entry.getFormattedMessage()
        assertTrue(formatted.contains("W/AudioScanner: File skipped"))
        assertTrue(formatted.startsWith(entry.getFormattedTime()))
    }

    @Test
    fun `getFormattedMessage with throwable appends stack trace`() {
        val exception = IllegalStateException("Corrupt header")
        val entry = LogEntry(
            timestamp = 1700000000000L,
            level = LogEntry.LogLevel.ERROR,
            tag = "TagLibEngine",
            message = "Read failed",
            throwable = exception
        )
        val formatted = entry.getFormattedMessage()
        assertTrue(formatted.contains("E/TagLibEngine: Read failed"))
        assertTrue(formatted.contains("IllegalStateException: Corrupt header"))
        assertTrue(formatted.contains("at "))
    }
}
