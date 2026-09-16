package now.link.mastigias.data.image

import coil3.key.Keyer
import coil3.request.Options
import now.link.mastigias.domain.model.Track
import javax.inject.Inject

class TrackArtworkKeyer @Inject constructor() : Keyer<TrackArtworkData> {
    override fun key(data: TrackArtworkData, options: Options): String {
        return createKey(data.id, data.dateModified)
    }

    companion object {
        fun createKey(id: Long, dateModified: Long): String {
            return "track_artwork_${id}_${dateModified}"
        }
    }
}

class TrackKeyer @Inject constructor() : Keyer<Track> {
    override fun key(data: Track, options: Options): String {
        return TrackArtworkKeyer.createKey(data.id, data.dateModified)
    }
}
