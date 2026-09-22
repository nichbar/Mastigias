package now.link.mastigias.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackTest {

    @Test
    fun `isUnknownOrBlank returns true for null or blank`() {
        assertTrue(Track.isUnknownOrBlank(null))
        assertTrue(Track.isUnknownOrBlank(""))
        assertTrue(Track.isUnknownOrBlank("   "))
    }

    @Test
    fun `isUnknownOrBlank returns true for unknown placeholders`() {
        assertTrue(Track.isUnknownOrBlank("<unknown>"))
        assertTrue(Track.isUnknownOrBlank("<Unknown>"))
        assertTrue(Track.isUnknownOrBlank("<Unknown Artist>"))
        assertTrue(Track.isUnknownOrBlank("<unknown artist>"))
        assertTrue(Track.isUnknownOrBlank("<Unknown Album>"))
        assertTrue(Track.isUnknownOrBlank("<unknown album>"))
        assertTrue(Track.isUnknownOrBlank("<Unknown Title>"))
        assertTrue(Track.isUnknownOrBlank("<unknown title>"))
        assertTrue(Track.isUnknownOrBlank("  <unknown>  "))
    }

    @Test
    fun `isUnknownOrBlank returns false for valid metadata`() {
        assertFalse(Track.isUnknownOrBlank("OK Computer"))
        assertFalse(Track.isUnknownOrBlank("Radiohead"))
        assertFalse(Track.isUnknownOrBlank("Paranoid Android"))
    }

    @Test
    fun `computeIsTagged returns true only when title, artist, and album are all valid`() {
        assertTrue(Track.computeIsTagged("Title", "Artist", "Album"))
        assertFalse(Track.computeIsTagged("", "Artist", "Album"))
        assertFalse(Track.computeIsTagged("Title", "", "Album"))
        assertFalse(Track.computeIsTagged("Title", "Artist", ""))
        assertFalse(Track.computeIsTagged("Title", "<unknown>", "Album"))
        assertFalse(Track.computeIsTagged("Title", "<Unknown Artist>", "Album"))
        assertFalse(Track.computeIsTagged("Title", "Artist", "<Unknown Album>"))
        assertFalse(Track.computeIsTagged("<unknown>", "<unknown>", "<unknown>"))
    }

    @Test
    fun `dateCreated returns dateAdded when dateAdded is greater than zero`() {
        val track = Track(
            id = 1L,
            path = "/music/song.mp3",
            title = "Song",
            artist = "Artist",
            album = "Album",
            trackNumber = 1,
            durationMs = 1000L,
            hasArtwork = false,
            isTagged = true,
            dateModified = 500L,
            dateAdded = 200L
        )
        org.junit.Assert.assertEquals(200L, track.dateCreated)
    }

    @Test
    fun `dateCreated falls back to dateModified when dateAdded is zero`() {
        val track = Track(
            id = 1L,
            path = "/music/song.mp3",
            title = "Song",
            artist = "Artist",
            album = "Album",
            trackNumber = 1,
            durationMs = 1000L,
            hasArtwork = false,
            isTagged = true,
            dateModified = 500L,
            dateAdded = 0L
        )
        org.junit.Assert.assertEquals(500L, track.dateCreated)
    }
}
