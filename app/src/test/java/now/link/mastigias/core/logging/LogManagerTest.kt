package now.link.mastigias.core.logging

import now.link.mastigias.domain.model.LogEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogManagerTest {

    @Before
    fun setUp() {
        LogManager.resetForTesting()
    }

    @After
    fun tearDown() {
        LogManager.resetForTesting()
    }

    @Test
    fun `logging methods add entries with appropriate levels`() {
        LogManager.v("TagV", "Verbose msg")
        LogManager.d("TagD", "Debug msg")
        LogManager.i("TagI", "Info msg")
        LogManager.w("TagW", "Warn msg")
        LogManager.e("TagE", "Error msg")
        LogManager.wtf("TagA", "Assert msg")

        val entries = LogManager.getAllLogEntries()
        assertEquals(6, entries.size)

        assertEquals(LogEntry.LogLevel.VERBOSE, entries[0].level)
        assertEquals("Verbose msg", entries[0].message)

        assertEquals(LogEntry.LogLevel.DEBUG, entries[1].level)
        assertEquals("Debug msg", entries[1].message)

        assertEquals(LogEntry.LogLevel.INFO, entries[2].level)
        assertEquals("Info msg", entries[2].message)

        assertEquals(LogEntry.LogLevel.WARN, entries[3].level)
        assertEquals("Warn msg", entries[3].message)

        assertEquals(LogEntry.LogLevel.ERROR, entries[4].level)
        assertEquals("Error msg", entries[4].message)

        assertEquals(LogEntry.LogLevel.ASSERT, entries[5].level)
        assertEquals("Assert msg", entries[5].message)
    }

    @Test
    fun `logging with throwable attaches the exception`() {
        val ex = RuntimeException("Disk full")
        LogManager.e("StorageTag", "Write failed", ex)

        val entries = LogManager.getAllLogEntries()
        assertEquals(1, entries.size)
        assertEquals("Write failed", entries[0].message)
        assertEquals(ex, entries[0].throwable)
    }

    @Test
    fun `flow emits updated logs on new entry`() {
        LogManager.i("Tag", "First")
        assertEquals(1, LogManager.logEntriesFlow.value.size)
        assertEquals("First", LogManager.logEntriesFlow.value[0].message)

        LogManager.d("Tag", "Second")
        assertEquals(2, LogManager.logEntriesFlow.value.size)
        assertEquals("Second", LogManager.logEntriesFlow.value[1].message)
    }

    @Test
    fun `clearLogs empties buffer and updates flow`() {
        LogManager.d("Tag", "Entry 1")
        LogManager.d("Tag", "Entry 2")
        assertEquals(2, LogManager.getLogCount())

        LogManager.clearLogs()

        // When logging is enabled, clearLogs records one "Log entries cleared" entry
        val entries = LogManager.getAllLogEntries()
        assertEquals(1, entries.size)
        assertEquals("Log entries cleared", entries[0].message)
    }

    @Test
    fun `disabling logging prevents new logs from being recorded`() {
        LogManager.setLogEnabled(false)
        LogManager.clearLogs()

        assertEquals(0, LogManager.getLogCount())
        assertFalse(LogManager.isLogEnabled())

        LogManager.d("Tag", "Should not be recorded")
        LogManager.e("Tag", "Should not be recorded")

        assertEquals(0, LogManager.getLogCount())
        assertTrue(LogManager.getAllLogEntries().isEmpty())
    }
}
