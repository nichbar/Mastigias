package now.link.mastigias.domain.model

data class Album(
    val title: String,
    val artist: String,
    val tracks: List<Track>,
    val coverTrackId: Long?
)
