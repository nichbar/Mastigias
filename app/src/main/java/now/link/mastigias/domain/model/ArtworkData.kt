package now.link.mastigias.domain.model

data class ArtworkData(
    val binaryData: ByteArray,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArtworkData

        if (!binaryData.contentEquals(other.binaryData)) return false
        if (mimeType != other.mimeType) return false
        if (width != other.width) return false
        if (height != other.height) return false

        return true
    }

    override fun hashCode(): Int {
        var result = binaryData.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}
