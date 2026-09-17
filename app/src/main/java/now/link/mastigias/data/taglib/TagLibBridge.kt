package now.link.mastigias.data.taglib

import now.link.mastigias.core.logging.LogManager

object TagLibBridge {
    private const val TAG = "TagLibBridge"

    val isLoaded: Boolean = try {
        System.loadLibrary("mastigias-native")
        LogManager.i(TAG, "Native library 'mastigias-native' loaded successfully")
        true
    } catch (e: UnsatisfiedLinkError) {
        LogManager.w(TAG, "Failed to load 'mastigias-native' (UnsatisfiedLinkError): ${e.message}")
        false
    } catch (e: SecurityException) {
        LogManager.w(TAG, "Failed to load 'mastigias-native' (SecurityException): ${e.message}")
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
