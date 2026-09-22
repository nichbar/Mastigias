package now.link.mastigias.core.logging

import now.link.mastigias.domain.logging.AppLogger
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
        LogManager.resetForTesting(enabled = true)
    }

    @After
    fun tearDown() {
        LogManager.resetForTesting(enabled = false)
    }

    @Test
    fun `resetForTesting defaults to disabled`() {
        LogManager.resetForTesting()
        assertFalse(LogManager.isLogEnabled())
        assertFalse(LogManager.isLogEnabledFlow.value)
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

    @Test
    fun `LogManager implements AppLogger contract correctly`() {
        val logger: AppLogger = LogManager
        logger.v("AppLoggerTag", "Verbose message via interface")
        logger.d("AppLoggerTag", "Debug message via interface")
        logger.i("AppLoggerTag", "Info message via interface")
        logger.w("AppLoggerTag", "Warn message via interface")
        logger.e("AppLoggerTag", "Error message via interface")

        val entries = LogManager.getAllLogEntries()
        assertEquals(5, entries.size)
        assertEquals("Verbose message via interface", entries[0].message)
        assertEquals("Debug message via interface", entries[1].message)
        assertEquals("Info message via interface", entries[2].message)
        assertEquals("Warn message via interface", entries[3].message)
        assertEquals("Error message via interface", entries[4].message)
    }

    @Test
    fun `isLogEnabledFlow reflects state changes and returns 0 when disabled`() {
        assertTrue(LogManager.isLogEnabledFlow.value)

        LogManager.setLogEnabled(false)
        assertFalse(LogManager.isLogEnabledFlow.value)

        val logger: AppLogger = LogManager
        val retV = logger.v("Tag", "Should return 0")
        val retD = logger.d("Tag", "Should return 0")
        val retI = logger.i("Tag", "Should return 0")
        val retW = logger.w("Tag", "Should return 0")
        val retE = logger.e("Tag", "Should return 0")

        assertEquals(0, retV)
        assertEquals(0, retD)
        assertEquals(0, retI)
        assertEquals(0, retW)
        assertEquals(0, retE)

        LogManager.setLogEnabled(true)
        assertTrue(LogManager.isLogEnabledFlow.value)
    }
}
