package now.link.mastigias.domain.model

data class AudioMetadata(
    val trackId: Long,
    val path: String,
    val fields: Map<TagField, String>,
    val artwork: ArtworkData?,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val channels: Int,
    val durationMs: Long,
    val fileSizeBytes: Long = 0L
)
