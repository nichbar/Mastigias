package now.link.mastigias.core

import now.link.mastigias.core.common.ImageUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageUtilsTest {

    @Test
    fun `sniffMimeType identifies JPEG, PNG, and WebP`() {
        val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        assertEquals("image/jpeg", ImageUtils.sniffMimeType(jpegHeader))

        val pngHeader = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        )
        assertEquals("image/png", ImageUtils.sniffMimeType(pngHeader))

        val webpHeader = "RIFF1234WEBP".toByteArray()
        assertEquals("image/webp", ImageUtils.sniffMimeType(webpHeader))

        val unknownHeader = byteArrayOf(0x00, 0x01, 0x02)
        assertEquals("image/jpeg", ImageUtils.sniffMimeType(unknownHeader))
    }

    @Test
    fun `decodeDimensions parses valid PNG dimensions from header`() {
        // Construct minimum PNG header with IHDR: 8-byte signature + 4-byte chunk len + "IHDR" + 4-byte width + 4-byte height
        val pngBytes = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
            0x00, 0x00, 0x00, 0x0D, // Length: 13
            0x49, 0x48, 0x44, 0x52, // "IHDR"
            0x00, 0x00, 0x01, 0xF4.toByte(), // Width: 500
            0x00, 0x00, 0x01, 0x2C.toByte()  // Height: 300
        )

        val (width, height) = ImageUtils.decodeDimensions(pngBytes)
        assertEquals(500, width)
        assertEquals(300, height)
    }

    @Test
    fun `decodeDimensions parses valid JPEG dimensions from SOF marker`() {
        // Construct minimum JPEG with SOF0 marker
        val jpegBytes = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // SOI
            0xFF.toByte(), 0xC0.toByte(), // SOF0
            0x00, 0x11,                   // Marker length: 17
            0x08,                         // Precision: 8
            0x01, 0x90.toByte(),          // Height: 400
            0x02, 0x58.toByte()           // Width: 600
        )

        val (width, height) = ImageUtils.decodeDimensions(jpegBytes)
        assertEquals(600, width)
        assertEquals(400, height)
    }

    @Test
    fun `decodeDimensions handles empty or corrupted arrays safely`() {
        val (emptyW, emptyH) = ImageUtils.decodeDimensions(byteArrayOf())
        assertEquals(0, emptyW)
        assertEquals(0, emptyH)

        val (corruptW, corruptH) = ImageUtils.decodeDimensions(byteArrayOf(1, 2, 3, 4))
        assertEquals(0, corruptW)
        assertEquals(0, corruptH)
    }
}
