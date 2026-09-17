package now.link.mastigias.domain.logging

/**
 * Pure Kotlin logging contract for domain and application layers.
 * Keeps domain logic decoupled from Android platform logging APIs.
 */
interface AppLogger {
    fun v(tag: String, msg: String): Int = 0
    fun v(tag: String, msg: String, tr: Throwable): Int = 0
    fun d(tag: String, msg: String): Int = 0
    fun d(tag: String, msg: String, tr: Throwable): Int = 0
    fun i(tag: String, msg: String): Int = 0
    fun i(tag: String, msg: String, tr: Throwable): Int = 0
    fun w(tag: String, msg: String): Int = 0
    fun w(tag: String, msg: String, tr: Throwable): Int = 0
    fun w(tag: String, tr: Throwable): Int = 0
    fun e(tag: String, msg: String): Int = 0
    fun e(tag: String, msg: String, tr: Throwable): Int = 0
}

object NoOpAppLogger : AppLogger
