package now.link.mastigias.domain.model

data class BatchFieldInfo(
    val value: String,
    val isMixed: Boolean
)

data class BatchMetadata(
    val fields: Map<TagField, BatchFieldInfo>,
    val artwork: ArtworkData?,
    val hasMixedArtwork: Boolean = false
)
