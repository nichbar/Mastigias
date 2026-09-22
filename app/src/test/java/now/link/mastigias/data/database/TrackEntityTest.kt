package now.link.mastigias.data.database

import now.link.mastigias.data.database.entity.TrackEntity
import now.link.mastigias.data.database.entity.toDomain
import now.link.mastigias.data.database.entity.toEntity
import now.link.mastigias.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackEntityTest {

    @Test
    fun `TrackEntity toDomain maps all properties accurately`() {
        val entity = TrackEntity(
            id = 42L,
            path = "/storage/emulated/0/Music/track.flac",
            title = "Starlight",
            artist = "Muse",
            album = "Black Holes and Revelations",
            trackNumber = 2,
            durationMs = 240000L,
            hasArtwork = true,
            isTagged = true,
            dateModified = 1700000000L,
            mimeType = "audio/flac",
            sizeBytes = 25000000L,
            dateAdded = 1695000000L
        )

        val domain = entity.toDomain()

        assertEquals(42L, domain.id)
        assertEquals("/storage/emulated/0/Music/track.flac", domain.path)
        assertEquals("Starlight", domain.title)
        assertEquals("Muse", domain.artist)
        assertEquals("Black Holes and Revelations", domain.album)
        assertEquals(2, domain.trackNumber)
        assertEquals(240000L, domain.durationMs)
        assertEquals(true, domain.hasArtwork)
        assertEquals(true, domain.isTagged)
        assertEquals(1700000000L, domain.dateModified)
        assertEquals(1695000000L, domain.dateAdded)
        assertEquals(1695000000L, domain.dateCreated)
    }

    @Test
    fun `Track toEntity maps all properties accurately`() {
        val domain = Track(
            id = 100L,
            path = "/music/song.mp3",
            title = "Time",
            artist = "Pink Floyd",
            album = "The Dark Side of the Moon",
            trackNumber = 4,
            durationMs = 425000L,
            hasArtwork = false,
            isTagged = true,
            dateModified = 1690000000L,
            dateAdded = 1680000000L
        )

        val entity = domain.toEntity(mimeType = "audio/mpeg", sizeBytes = 10000000L)

        assertEquals(100L, entity.id)
        assertEquals("/music/song.mp3", entity.path)
        assertEquals("Time", entity.title)
        assertEquals("Pink Floyd", entity.artist)
        assertEquals("The Dark Side of the Moon", entity.album)
        assertEquals(4, entity.trackNumber)
        assertEquals(425000L, entity.durationMs)
        assertEquals(false, entity.hasArtwork)
        assertEquals(true, entity.isTagged)
        assertEquals(1690000000L, entity.dateModified)
        assertEquals(1680000000L, entity.dateAdded)
        assertEquals("audio/mpeg", entity.mimeType)
        assertEquals(10000000L, entity.sizeBytes)
    }

    @Test
    fun `TrackEntity supports nullable artwork`() {
        val entity = TrackEntity(
            id = 1L,
            path = "/music/unknown.opus",
            title = "Track 1",
            artist = "<unknown>",
            album = "<unknown>",
            trackNumber = 0,
            durationMs = 0L,
            hasArtwork = null,
            isTagged = false,
            dateModified = 0L,
            mimeType = "audio/opus",
            sizeBytes = 1024L
        )

        val domain = entity.toDomain()
        assertNull(domain.hasArtwork)
        assertFalse(domain.isTagged)
    }
}
