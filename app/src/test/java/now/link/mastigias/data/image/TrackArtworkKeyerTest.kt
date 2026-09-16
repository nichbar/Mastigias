package now.link.mastigias.data.image

import now.link.mastigias.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackArtworkKeyerTest {

    @Test
    fun `createKey generates expected cache key`() {
        val key = TrackArtworkKeyer.createKey(id = 12345L, dateModified = 1690000000L)
        assertEquals("track_artwork_12345_1690000000", key)
    }

    @Test
    fun `Track toArtworkData maps properties correctly`() {
        val track = Track(
            id = 99L,
            path = "/storage/emulated/0/Music/song.flac",
            title = "FLAC Song",
            artist = "Artist",
            album = "Album",
            trackNumber = 5,
            durationMs = 240000L,
            hasArtwork = true,
            isTagged = true,
            dateModified = 1700000000L
        )

        val artworkData = track.toArtworkData()
        assertEquals(99L, artworkData.id)
        assertEquals("/storage/emulated/0/Music/song.flac", artworkData.path)
        assertEquals(1700000000L, artworkData.dateModified)

        val key = TrackArtworkKeyer.createKey(artworkData.id, artworkData.dateModified)
        assertEquals("track_artwork_99_1700000000", key)
    }
}
