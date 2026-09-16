package now.link.mastigias.core.common

import android.graphics.BitmapFactory

/**
 * Image decoding utility functions ensuring memory efficiency and proper dimension sniffing.
 * Uses BitmapFactory.Options.inJustDecodeBounds to avoid Java heap exhaustion.
 */
object ImageUtils {

    /**
     * Decodes the width and height of an image byte array without allocating full bitmap pixels.
     * Guaranteed to return (0, 0) if unparseable, or (width, height) on success.
     */
    fun decodeDimensions(bytes: ByteArray): Pair<Int, Int> {
        if (bytes.isEmpty()) return 0 to 0

        // 1. Android runtime standard decoding with inJustDecodeBounds
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                return options.outWidth to options.outHeight
            }
        } catch (_: Throwable) {
            // Fallback to binary header parsing on JVM unit tests
        }

        // 2. Binary header inspection fallback (for pure JVM testing environments)
        return parseDimensionsFromHeader(bytes)
    }

    /**
     * Sniffs the image MIME type from the binary file magic numbers.
     */
    fun sniffMimeType(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
        ) {
            return "image/webp"
        }
        return "image/jpeg"
    }

    private fun parseDimensionsFromHeader(bytes: ByteArray): Pair<Int, Int> {
        // Check PNG signature: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.size >= 24 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            val width = readInt32BigEndian(bytes, 16)
            val height = readInt32BigEndian(bytes, 20)
            if (width > 0 && height > 0) return width to height
        }

        // Check JPEG signature: FF D8
        if (bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            var offset = 2
            while (offset + 4 < bytes.size) {
                if (bytes[offset] != 0xFF.toByte()) {
                    offset++
                    continue
                }
                val marker = bytes[offset + 1].toInt() and 0xFF
                // SOF0 (0xC0) or SOF2 (0xC2) markers contain dimensions
                if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2) {
                    if (offset + 8 < bytes.size) {
                        val height = readInt16BigEndian(bytes, offset + 5)
                        val width = readInt16BigEndian(bytes, offset + 7)
                        if (width > 0 && height > 0) return width to height
                    }
                    break
                }
                val length = readInt16BigEndian(bytes, offset + 2)
                if (length < 2) break
                offset += 2 + length
            }
        }

        return 0 to 0
    }

    private fun readInt16BigEndian(bytes: ByteArray, offset: Int): Int {
        if (offset + 1 >= bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun readInt32BigEndian(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 >= bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }
}
