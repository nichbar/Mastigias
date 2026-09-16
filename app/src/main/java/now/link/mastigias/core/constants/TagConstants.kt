package now.link.mastigias.core.constants

/**
 * Constants used across metadata reading, writing, and artwork handling.
 */
object TagConstants {
    /**
     * Isolated work copy directory inside cacheDir for Scoped Storage atomic writes.
     */
    const val TAG_WORK_DIR = "tag_work"

    /**
     * Temporary audio preview directory for external playback.
     */
    const val OPEN_EXTERNAL_TEMP_DIR = "open_external_temp"

    /**
     * Target dimension for downsampled album artwork thumbnails in library list views.
     */
    const val THUMBNAIL_SIZE_PX = 120

    /**
     * Maximum dimension when decoding and scaling user-picked artwork images.
     */
    const val MAX_ARTWORK_DIMENSION = 2048

    /**
     * Standard 256KB NIO buffer size for streaming file I/O operations.
     */
    const val BUFFER_SIZE_BYTES = 256 * 1024

    /**
     * Default fallback MIME type for album artwork images.
     */
    const val DEFAULT_ARTWORK_MIME = "image/jpeg"
}
