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
    val dateModified: Long
)
