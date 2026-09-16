package now.link.mastigias.data.taglib

import kotlinx.coroutines.runBlocking
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.domain.model.ArtworkData
import now.link.mastigias.domain.model.AudioMetadata
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.domain.model.TagPatch
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TagLibEngineImplTest {

    private lateinit var engine: TagLibEngineImpl

    @Before
    fun setUp() {
        engine = TagLibEngineImpl(AppDispatchers())
        engine.clearFallbackStore()
    }

    @Test
    fun `verify TagLibBridge safely reports availability without throwing in JVM`() {
        // On standard JVM without mastigias-native.so, isAvailable should return false safely
        val available = TagLibBridge.isAvailable()
        assertEquals(TagLibBridge.isLoaded, available)
    }

    @Test
    fun `verify NativeTagBundle constructors`() {
        val bundleLong = NativeTagBundle(
            keys = arrayOf("TITLE", "ARTIST"),
            values = arrayOf("Test Title", "Test Artist"),
            bitrateKbps = 320,
            sampleRateHz = 44100,
            channels = 2,
            durationMs = 180000L
        )
        assertEquals("TITLE", bundleLong.keys[0])
        assertEquals("Test Title", bundleLong.values[0])
        assertEquals(320, bundleLong.bitrateKbps)
        assertEquals(44100, bundleLong.sampleRateHz)
        assertEquals(2, bundleLong.channels)
        assertEquals(180000L, bundleLong.durationMs)

        val bundleInt = NativeTagBundle(
            keys = arrayOf("ALBUM"),
            values = arrayOf("Test Album"),
            bitrateKbps = 256,
            sampleRateHz = 48000,
            channels = 1,
            durationMs = 90000
        )
        assertEquals(90000L, bundleInt.durationMs)
    }

    @Test
    fun `verify mapTagsToFields resolves Vorbis keys`() {
        val keys = arrayOf("TITLE", "ARTIST", "ALBUM", "DATE", "GENRE", "TRACKNUMBER", "DISCNUMBER")
        val values = arrayOf("Song A", "Artist B", "Album C", "2024", "Rock", "3", "1")

        val result = TagLibEngineImpl.mapTagsToFields(keys, values)

        assertEquals("Song A", result[TagField.TITLE])
        assertEquals("Artist B", result[TagField.ARTIST])
        assertEquals("Album C", result[TagField.ALBUM])
        assertEquals("2024", result[TagField.YEAR])
        assertEquals("Rock", result[TagField.GENRE])
        assertEquals("3", result[TagField.TRACK_NUMBER])
        assertEquals("1", result[TagField.DISC_NUMBER])
    }

    @Test
    fun `verify mapTagsToFields resolves ID3v2 frames`() {
        val keys = arrayOf("TIT2", "TPE1", "TALB", "TDRC", "TCON", "TRCK", "TPOS")
        val values = arrayOf("Song ID3", "Artist ID3", "Album ID3", "2023", "Jazz", "5", "2")

        val result = TagLibEngineImpl.mapTagsToFields(keys, values)

        assertEquals("Song ID3", result[TagField.TITLE])
        assertEquals("Artist ID3", result[TagField.ARTIST])
        assertEquals("Album ID3", result[TagField.ALBUM])
        assertEquals("2023", result[TagField.YEAR])
        assertEquals("Jazz", result[TagField.GENRE])
        assertEquals("5", result[TagField.TRACK_NUMBER])
        assertEquals("2", result[TagField.DISC_NUMBER])
    }

    @Test
    fun `verify mapTagsToFields resolves standard keys`() {
        val keys = arrayOf("title", "artist", "album", "year", "genre", "composer", "bpm")
        val values = arrayOf("Song Std", "Artist Std", "Album Std", "2022", "Classical", "Bach", "120")

        val result = TagLibEngineImpl.mapTagsToFields(keys, values)

        assertEquals("Song Std", result[TagField.TITLE])
        assertEquals("Artist Std", result[TagField.ARTIST])
        assertEquals("Album Std", result[TagField.ALBUM])
        assertEquals("2022", result[TagField.YEAR])
        assertEquals("Classical", result[TagField.GENRE])
        assertEquals("Bach", result[TagField.COMPOSER])
        assertEquals("120", result[TagField.BPM])
    }

    @Test
    fun `verify mapTagsToFields resolves ReplayGain and MusicBrainz identifiers`() {
        val keys = arrayOf(
            "REPLAYGAIN_TRACK_GAIN",
            "REPLAYGAIN_TRACK_PEAK",
            "MUSICBRAINZ_TRACKID",
            "MUSICBRAINZ_ALBUMID",
            "ISRC"
        )
        val values = arrayOf("-6.5 dB", "0.988", "mb-track-123", "mb-album-456", "US-ABC-24-00001")

        val result = TagLibEngineImpl.mapTagsToFields(keys, values)

        assertEquals("-6.5 dB", result[TagField.REPLAYGAIN_TRACK_GAIN])
        assertEquals("0.988", result[TagField.REPLAYGAIN_TRACK_PEAK])
        assertEquals("mb-track-123", result[TagField.MUSICBRAINZ_TRACK_ID])
        assertEquals("mb-album-456", result[TagField.MUSICBRAINZ_RELEASE_ID])
        assertEquals("US-ABC-24-00001", result[TagField.ISRC])
    }

    @Test
    fun `verify mapTagsToFields ignores unknown keys safely`() {
        val keys = arrayOf("UNKNOWN_CUSTOM_HEADER", "X-SOME-NONEXISTENT-TAG", "TITLE")
        val values = arrayOf("Foo", "Bar", "Valid Title")

        val result = TagLibEngineImpl.mapTagsToFields(keys, values)

        assertEquals(1, result.size)
        assertEquals("Valid Title", result[TagField.TITLE])
    }

    @Test
    fun `verify parseBundleToAudioMetadata creates valid AudioMetadata object`() {
        val bundle = NativeTagBundle(
            keys = arrayOf("TITLE", "ARTIST", "ALBUM"),
            values = arrayOf("Track One", "Artist One", "Album One"),
            bitrateKbps = 320,
            sampleRateHz = 96000,
            channels = 2,
            durationMs = 245000L
        )

        val metadata = TagLibEngineImpl.parseBundleToAudioMetadata("/music/test.flac", bundle)

        assertEquals(0L, metadata.trackId)
        assertEquals("/music/test.flac", metadata.path)
        assertEquals(320, metadata.bitrateKbps)
        assertEquals(96000, metadata.sampleRateHz)
        assertEquals(2, metadata.channels)
        assertEquals(245000L, metadata.durationMs)
        assertNull(metadata.artwork)
        assertEquals("Track One", metadata.fields[TagField.TITLE])
        assertEquals("Artist One", metadata.fields[TagField.ARTIST])
        assertEquals("Album One", metadata.fields[TagField.ALBUM])
    }

    @Test
    fun `verify fallback readMetadata returns default metadata when file not in store`() = runBlocking {
        val result = engine.readMetadata("/path/to/virtual/song.mp3")

        assertTrue("readMetadata should succeed in fallback", result.isSuccess)
        val metadata = result.getOrNull()
        assertNotNull(metadata)
        assertEquals("/path/to/virtual/song.mp3", metadata!!.path)
        assertTrue(metadata.fields.isEmpty())
        assertNull(metadata.artwork)
    }

    @Test
    fun `verify fallback readArtwork returns null when no artwork present`() = runBlocking {
        val result = engine.readArtwork("/path/to/virtual/song.mp3")

        assertTrue("readArtwork should succeed", result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `verify fallback writeMetadata updates fields and can be read back`() = runBlocking {
        val path = "/path/to/virtual/test_song.flac"
        val patch = TagPatch(
            updatedFields = mapOf(
                TagField.TITLE to "New Title",
                TagField.ARTIST to "New Artist",
                TagField.GENRE to "Electronic"
            ),
            deletedFields = emptySet(),
            updatedArtwork = null,
            removeArtwork = false
        )

        val writeResult = engine.writeMetadata(path, patch)
        assertTrue("writeMetadata should succeed", writeResult.isSuccess)

        val readResult = engine.readMetadata(path)
        assertTrue("readMetadata should succeed", readResult.isSuccess)

        val metadata = readResult.getOrThrow()
        assertEquals("New Title", metadata.fields[TagField.TITLE])
        assertEquals("New Artist", metadata.fields[TagField.ARTIST])
        assertEquals("Electronic", metadata.fields[TagField.GENRE])
    }

    @Test
    fun `verify fallback writeMetadata handles field deletion`() = runBlocking {
        val path = "/path/to/virtual/song_delete.ogg"

        // Initial write
        val initialPatch = TagPatch(
            updatedFields = mapOf(
                TagField.TITLE to "Song Title",
                TagField.COMMENT to "Temporary Comment"
            ),
            deletedFields = emptySet(),
            updatedArtwork = null,
            removeArtwork = false
        )
        engine.writeMetadata(path, initialPatch)

        // Delete COMMENT
        val deletePatch = TagPatch(
            updatedFields = emptyMap(),
            deletedFields = setOf(TagField.COMMENT),
            updatedArtwork = null,
            removeArtwork = false
        )
        engine.writeMetadata(path, deletePatch)

        val readResult = engine.readMetadata(path).getOrThrow()
        assertEquals("Song Title", readResult.fields[TagField.TITLE])
        assertFalse(readResult.fields.containsKey(TagField.COMMENT))
    }

    @Test
    fun `verify fallback write and read artwork`() = runBlocking {
        val path = "/path/to/virtual/song_art.m4a"
        val dummyBytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
        val artwork = ArtworkData(
            binaryData = dummyBytes,
            mimeType = "image/png",
            width = 500,
            height = 500
        )

        val patch = TagPatch(
            updatedFields = mapOf(TagField.TITLE to "Artwork Test"),
            deletedFields = emptySet(),
            updatedArtwork = artwork,
            removeArtwork = false
        )

        val writeResult = engine.writeMetadata(path, patch)
        assertTrue(writeResult.isSuccess)

        val artResult = engine.readArtwork(path)
        assertTrue(artResult.isSuccess)
        assertArrayEquals(dummyBytes, artResult.getOrNull())

        // Remove artwork
        val removePatch = TagPatch(
            updatedFields = emptyMap(),
            deletedFields = emptySet(),
            updatedArtwork = null,
            removeArtwork = true
        )
        engine.writeMetadata(path, removePatch)

        val artAfterRemove = engine.readArtwork(path).getOrThrow()
        assertNull(artAfterRemove)
    }

    @Test
    fun `verify fallback store manual population via setFallbackMetadata`() = runBlocking {
        val path = "/music/custom.opus"
        val customMeta = AudioMetadata(
            trackId = 42L,
            path = path,
            fields = mapOf(TagField.TITLE to "Custom Opus"),
            artwork = ArtworkData(byteArrayOf(1, 2, 3), "image/jpeg"),
            bitrateKbps = 128,
            sampleRateHz = 48000,
            channels = 2,
            durationMs = 120000L
        )

        engine.setFallbackMetadata(path, customMeta)

        val readResult = engine.readMetadata(path).getOrThrow()
        assertEquals("Custom Opus", readResult.fields[TagField.TITLE])
        assertEquals(128, readResult.bitrateKbps)

        val artResult = engine.readArtwork(path).getOrThrow()
        assertArrayEquals(byteArrayOf(1, 2, 3), artResult)
    }
}
