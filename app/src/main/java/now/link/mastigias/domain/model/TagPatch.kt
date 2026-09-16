package now.link.mastigias.domain.model

data class TagPatch(
    val updatedFields: Map<TagField, String>,
    val deletedFields: Set<TagField>,
    val updatedArtwork: ArtworkData?,
    val removeArtwork: Boolean
)
