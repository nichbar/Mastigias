package now.link.mastigias.data.taglib

object TagLibBridge {
    val isLoaded: Boolean = try {
        System.loadLibrary("mastigias-native")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    } catch (e: SecurityException) {
        false
    }

    fun isAvailable(): Boolean = isLoaded

    external fun nativeReadMetadata(filePath: String): NativeTagBundle?

    external fun nativeReadArtwork(filePath: String): ByteArray?

    external fun nativeWriteMetadata(
        filePath: String,
        setKeys: Array<String>,
        setValues: Array<String>,
        deleteKeys: Array<String>,
        artworkBytes: ByteArray?,
        removeArtwork: Boolean,
        artworkMime: String,
        artworkWidth: Int,
        artworkHeight: Int
    ): Boolean
}
