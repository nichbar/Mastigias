package now.link.mastigias.domain.model

data class Album(
    val title: String,
    val artist: String,
    val tracks: List<Track>,
    val coverTrackId: Long?
) {
    val key: String
        get() {
            val id = coverTrackId ?: tracks.firstOrNull()?.id ?: 0L
            return "${title}_${artist}_$id"
        }
}
