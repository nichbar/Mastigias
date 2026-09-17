package now.link.mastigias.core.logging

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import now.link.mastigias.domain.model.LogEntry
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Manages in-memory logging for the application:
 * - Wraps Android Log.* calls to capture logs in memory
 * - Maintains a thread-safe circular buffer of logs (up to 10,000 entries)
 * - Exposes real-time updates via StateFlow
 */
object LogManager {

    private const val TAG = "LogManager"
    private const val PREF_NAME = "mastigias_log_prefs"
    private const val PREF_LOG_ENABLED = "log_enabled"
    const val MAX_LOG_ENTRIES = 10000

    private val logEntries = ConcurrentLinkedQueue<LogEntry>()
    private val _logEntriesFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntriesFlow: StateFlow<List<LogEntry>> = _logEntriesFlow.asStateFlow()

    private var sharedPreferences: SharedPreferences? = null

    @Volatile
    private var isLogEnabled: Boolean = true

    /**
     * Initialize the LogManager with application context
     */
    fun initialize(context: Context) {
        try {
            sharedPreferences = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            isLogEnabled = sharedPreferences?.getBoolean(PREF_LOG_ENABLED, true) ?: true
        } catch (_: Exception) {
            isLogEnabled = true
        }

        if (isLogEnabled) {
            addLogEntry(LogEntry.LogLevel.INFO, TAG, "LogManager initialized, logging enabled")
        }
    }

    /**
     * Enable or disable logging
     */
    fun setLogEnabled(enabled: Boolean) {
        isLogEnabled = enabled
        sharedPreferences?.edit()?.putBoolean(PREF_LOG_ENABLED, enabled)?.apply()

        if (enabled) {
            addLogEntry(LogEntry.LogLevel.INFO, TAG, "In-memory logging enabled")
        } else {
            addLogEntry(LogEntry.LogLevel.INFO, TAG, "In-memory logging disabled")
        }
    }

    /**
     * Check if logging is enabled
     */
    fun isLogEnabled(): Boolean = isLogEnabled

    /**
     * Add a log entry to the in-memory store
     */
    private fun addLogEntry(
        level: LogEntry.LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (!isLogEnabled) return

        val entry = LogEntry(
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )

        synchronized(logEntries) {
            logEntries.offer(entry)
            while (logEntries.size > MAX_LOG_ENTRIES) {
                logEntries.poll()
            }
            _logEntriesFlow.value = logEntries.toList()
        }
    }

    /**
     * Get all current log entries
     */
    fun getAllLogEntries(): List<LogEntry> {
        return synchronized(logEntries) {
            logEntries.toList()
        }
    }

    /**
     * Clear all log entries
     */
    fun clearLogs() {
        synchronized(logEntries) {
            logEntries.clear()
            _logEntriesFlow.value = emptyList()
        }
        if (isLogEnabled) {
            addLogEntry(LogEntry.LogLevel.INFO, TAG, "Log entries cleared")
        }
    }

    /**
     * Reset buffer completely for testing
     */
    fun resetForTesting() {
        isLogEnabled = true
        synchronized(logEntries) {
            logEntries.clear()
            _logEntriesFlow.value = emptyList()
        }
    }

    /**
     * Get log entries count
     */
    fun getLogCount(): Int = synchronized(logEntries) { logEntries.size }

    private fun safeAndroidLog(priority: Int, tag: String, msg: String, tr: Throwable? = null): Int {
        return try {
            when (priority) {
                Log.VERBOSE -> if (tr != null) Log.v(tag, msg, tr) else Log.v(tag, msg)
                Log.DEBUG -> if (tr != null) Log.d(tag, msg, tr) else Log.d(tag, msg)
                Log.INFO -> if (tr != null) Log.i(tag, msg, tr) else Log.i(tag, msg)
                Log.WARN -> if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
                Log.ERROR -> if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
                Log.ASSERT -> if (tr != null) Log.wtf(tag, msg, tr) else Log.wtf(tag, msg)
                else -> 0
            }
        } catch (_: RuntimeException) {
            0
        }
    }

    // Wrapper methods for Android Log.* calls

    fun v(tag: String, msg: String): Int {
        val result = safeAndroidLog(Log.VERBOSE, tag, msg)
        addLogEntry(LogEntry.LogLevel.VERBOSE, tag, msg)
        return result
    }

    fun v(tag: String, msg: String, tr: Throwable): Int {
        val result = safeAndroidLog(Log.VERBOSE, tag, msg, tr)
        addLogEntry(LogEntry.LogLevel.VERBOSE, tag, msg, tr)
        return result
    }

    fun d(tag: String, msg: String): Int {
        val result = safeAndroidLog(Log.DEBUG, tag, msg)
        addLogEntry(LogEntry.LogLevel.DEBUG, tag, msg)
        return result
    }

    fun d(tag: String, msg: String, tr: Throwable): Int {
        val result = safeAndroidLog(Log.DEBUG, tag, msg, tr)
        addLogEntry(LogEntry.LogLevel.DEBUG, tag, msg, tr)
        return result
    }

    fun i(tag: String, msg: String): Int {
        val result = safeAndroidLog(Log.INFO, tag, msg)
        addLogEntry(LogEntry.LogLevel.INFO, tag, msg)
        return result
    }

    fun i(tag: String, msg: String, tr: Throwable): Int {
        val result = safeAndroidLog(Log.INFO, tag, msg, tr)
        addLogEntry(LogEntry.LogLevel.INFO, tag, msg, tr)
        return result
    }

    fun w(tag: String, msg: String): Int {
        val result = safeAndroidLog(Log.WARN, tag, msg)
        addLogEntry(LogEntry.LogLevel.WARN, tag, msg)
        return result
    }

    fun w(tag: String, msg: String, tr: Throwable): Int {
        val result = safeAndroidLog(Log.WARN, tag, msg, tr)
        addLogEntry(LogEntry.LogLevel.WARN, tag, msg, tr)
        return result
    }

    fun w(tag: String, tr: Throwable): Int {
        val msg = tr.message ?: "Exception"
        val result = safeAndroidLog(Log.WARN, tag, msg, tr)
        addLogEntry(LogEntry.LogLevel.WARN, tag, msg, tr)
        return result
    }

    fun e(tag: String, msg: String): Int {
        val result = safeAndroidLog(Log.ERROR, tag, msg)
        addLogEntry(LogEntry.LogLevel.ERROR, tag, msg)
        return result
    }

    fun e(tag: String, msg: String, tr: Throwable): Int {
        val result = safeAndroidLog(Log.ERROR, tag, msg, tr)
        addLogEntry(LogEntry.LogLevel.ERROR, tag, msg, tr)
        return result
    }

    fun wtf(tag: String, msg: String): Int {
        val result = safeAndroidLog(Log.ASSERT, tag, msg)
        addLogEntry(LogEntry.LogLevel.ASSERT, tag, msg)
        return result
    }

    fun wtf(tag: String, tr: Throwable): Int {
        val msg = tr.message ?: "WTF Exception"
        val result = safeAndroidLog(Log.ASSERT, tag, msg, tr)
        addLogEntry(LogEntry.LogLevel.ASSERT, tag, msg, tr)
        return result
    }

    fun wtf(tag: String, msg: String, tr: Throwable): Int {
        val result = safeAndroidLog(Log.ASSERT, tag, msg, tr)
        addLogEntry(LogEntry.LogLevel.ASSERT, tag, msg, tr)
        return result
    }
}
