package now.link.mastigias.domain.model

data class LyricsCandidate(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String? = null,
    val durationSeconds: Double = 0.0,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
) {
    val hasSyncedLyrics: Boolean
        get() = !syncedLyrics.isNullOrBlank()

    val hasPlainLyrics: Boolean
        get() = !plainLyrics.isNullOrBlank()

    val bestLyrics: String?
        get() = if (hasSyncedLyrics) syncedLyrics else plainLyrics
}
