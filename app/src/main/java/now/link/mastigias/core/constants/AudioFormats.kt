package now.link.mastigias.core.constants

import java.util.Locale

/**
 * Supported audio formats, MIME type mappings, and container format capabilities.
 */
object AudioFormats {

    /**
     * 16 supported audio file extensions.
     */
    val SUPPORTED_EXTENSIONS: Set<String> = setOf(
        "mp3",
        "wav",
        "wave",
        "dsf",
        "aiff",
        "aif",
        "aifc",
        "wma",
        "ogg",
        "ogx",
        "mp4",
        "m4a",
        "m4p",
        "flac",
        "aac",
        "opus"
    )

    /**
     * Formats that natively support ReplayGain tags.
     */
    val REPLAYGAIN_SUPPORTED_EXTENSIONS: Set<String> = setOf(
        "mp3",
        "wav",
        "dsf",
        "wma",
        "ogg",
        "flac"
    )

    /**
     * Exact 1:1 MIME type mappings for all supported extensions.
     */
    private val EXTENSION_TO_MIME: Map<String, String> = mapOf(
        "mp3" to "audio/mpeg",
        "wav" to "audio/x-wav",
        "wave" to "audio/x-wav",
        "dsf" to "audio/x-dsf",
        "aiff" to "audio/x-aiff",
        "aif" to "audio/x-aiff",
        "aifc" to "audio/x-aiff",
        "wma" to "audio/x-ms-wma",
        "ogg" to "audio/ogg",
        "ogx" to "audio/ogg",
        "mp4" to "audio/mp4",
        "m4a" to "audio/mp4",
        "m4p" to "audio/mp4",
        "flac" to "audio/flac",
        "aac" to "audio/aac",
        "opus" to "audio/opus"
    )

    fun isSupported(extension: String): Boolean =
        SUPPORTED_EXTENSIONS.contains(extension.lowercase(Locale.ROOT))

    fun isSupportedPath(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return isSupported(ext)
    }

    fun getMimeType(extensionOrPath: String): String {
        val ext = extensionOrPath.substringAfterLast('.', extensionOrPath).lowercase(Locale.ROOT)
        return EXTENSION_TO_MIME[ext] ?: "audio/*"
    }

    fun getMimeTypeForPath(path: String): String = getMimeType(path)

    fun supportsReplayGain(extensionOrPath: String): Boolean {
        val ext = extensionOrPath.substringAfterLast('.', extensionOrPath).lowercase(Locale.ROOT)
        return REPLAYGAIN_SUPPORTED_EXTENSIONS.contains(ext)
    }
}
