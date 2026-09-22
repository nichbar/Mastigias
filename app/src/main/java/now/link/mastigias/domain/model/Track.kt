package now.link.mastigias.domain.model

data class Track(
    val id: Long,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: Int,
    val durationMs: Long,
    val hasArtwork: Boolean?,
    val isTagged: Boolean,
    val dateModified: Long,
    val dateAdded: Long = 0L
) {
    val dateCreated: Long
        get() = if (dateAdded > 0L) dateAdded else dateModified

    companion object {
        const val UNKNOWN_VALUE = "<unknown>"
        const val UNKNOWN_ARTIST = "<Unknown Artist>"
        const val UNKNOWN_ALBUM = "<Unknown Album>"
        const val UNKNOWN_TITLE = "<Unknown Title>"

        fun isUnknownOrBlank(value: String?): Boolean {
            if (value.isNullOrBlank()) return true
            val trimmed = value.trim()
            return trimmed.equals(UNKNOWN_VALUE, ignoreCase = true) ||
                trimmed.equals(UNKNOWN_ARTIST, ignoreCase = true) ||
                trimmed.equals(UNKNOWN_ALBUM, ignoreCase = true) ||
                trimmed.equals(UNKNOWN_TITLE, ignoreCase = true) ||
                trimmed.equals("<unknown artist>", ignoreCase = true) ||
                trimmed.equals("<unknown album>", ignoreCase = true) ||
                trimmed.equals("<unknown title>", ignoreCase = true)
        }

        fun computeIsTagged(title: String?, artist: String?, album: String?): Boolean {
            return !isUnknownOrBlank(title) && !isUnknownOrBlank(artist) && !isUnknownOrBlank(album)
        }
    }
}
