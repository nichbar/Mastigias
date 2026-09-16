package now.link.mastigias.core

import now.link.mastigias.core.common.getFileExtension
import now.link.mastigias.core.common.toFormattedDuration
import now.link.mastigias.core.common.toFormattedFileSize
import now.link.mastigias.core.constants.AudioFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorePrimitivesTest {

    @Test
    fun `verify 16 audio containers are supported`() {
        assertEquals(16, AudioFormats.SUPPORTED_EXTENSIONS.size)
        val expected = listOf(
            "mp3", "wav", "wave", "dsf", "aiff", "aif", "aifc",
            "wma", "ogg", "ogx", "mp4", "m4a", "m4p", "flac", "aac", "opus"
        )
        for (ext in expected) {
            assertTrue("Extension $ext should be supported", AudioFormats.isSupported(ext))
            assertTrue("Path track.$ext should be supported", AudioFormats.isSupportedPath("/music/track.$ext"))
        }
        assertFalse(AudioFormats.isSupported("txt"))
        assertFalse(AudioFormats.isSupported("avi"))
    }

    @Test
    fun `verify ReplayGain format gating`() {
        val replayGainFormats = listOf("mp3", "wav", "dsf", "wma", "ogg", "flac")
        for (ext in replayGainFormats) {
            assertTrue("Format $ext should support ReplayGain", AudioFormats.supportsReplayGain(ext))
        }
        assertFalse(AudioFormats.supportsReplayGain("m4a"))
        assertFalse(AudioFormats.supportsReplayGain("aac"))
    }

    @Test
    fun `verify MIME mappings`() {
        assertEquals("audio/mpeg", AudioFormats.getMimeType("song.mp3"))
        assertEquals("audio/flac", AudioFormats.getMimeType("song.flac"))
        assertEquals("audio/ogg", AudioFormats.getMimeType("song.ogg"))
        assertEquals("audio/opus", AudioFormats.getMimeType("song.opus"))
        assertEquals("audio/mp4", AudioFormats.getMimeType("song.m4a"))
        assertEquals("audio/x-wav", AudioFormats.getMimeType("song.wav"))
        assertEquals("audio/x-wav", AudioFormats.getMimeType("song.wave"))
        assertEquals("audio/x-dsf", AudioFormats.getMimeType("song.dsf"))
        assertEquals("audio/x-aiff", AudioFormats.getMimeType("song.aiff"))
        assertEquals("audio/x-aiff", AudioFormats.getMimeType("song.aif"))
        assertEquals("audio/x-aiff", AudioFormats.getMimeType("song.aifc"))
        assertEquals("audio/x-ms-wma", AudioFormats.getMimeType("song.wma"))
        assertEquals("audio/ogg", AudioFormats.getMimeType("song.ogx"))
        assertEquals("audio/mp4", AudioFormats.getMimeType("song.mp4"))
        assertEquals("audio/mp4", AudioFormats.getMimeType("song.m4p"))
        assertEquals("audio/aac", AudioFormats.getMimeType("song.aac"))

        // Test getMimeTypeForPath
        assertEquals("audio/flac", AudioFormats.getMimeTypeForPath("/storage/emulated/0/Music/song.flac"))
        assertEquals("audio/opus", AudioFormats.getMimeTypeForPath("/storage/emulated/0/Music/song.opus"))
    }

    @Test
    fun `verify duration formatting`() {
        assertEquals("00:00", 0L.toFormattedDuration())
        assertEquals("01:05", 65000L.toFormattedDuration())
        assertEquals("10:00", 600000L.toFormattedDuration())
        assertEquals("1:01:05", 3665000L.toFormattedDuration())
    }

    @Test
    fun `verify file size formatting`() {
        assertEquals("0 B", 0L.toFormattedFileSize())
        assertEquals("1.0 KB", 1024L.toFormattedFileSize())
        assertEquals("5.0 MB", (5 * 1024 * 1024L).toFormattedFileSize())
    }

    @Test
    fun `verify file extension extraction`() {
        assertEquals("mp3", "/storage/emulated/0/Music/song.mp3".getFileExtension())
        assertEquals("flac", "file.FLAC".getFileExtension())
        assertEquals("", "file_without_ext".getFileExtension())
    }
}
