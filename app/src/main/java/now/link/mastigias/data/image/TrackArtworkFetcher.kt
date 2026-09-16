package now.link.mastigias.data.image

import android.graphics.BitmapFactory
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import now.link.mastigias.domain.engine.TagEngine
import now.link.mastigias.domain.model.Track
import javax.inject.Inject

data class TrackArtworkData(
    val id: Long,
    val path: String,
    val dateModified: Long = 0L
)

fun Track.toArtworkData(): TrackArtworkData = TrackArtworkData(
    id = id,
    path = path,
    dateModified = dateModified
)

class TrackArtworkFetcher(
    private val data: TrackArtworkData,
    private val tagEngine: TagEngine,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val artworkBytes = tagEngine.readArtwork(data.path).getOrNull()
            ?: return null

        if (artworkBytes.isEmpty()) {
            return null
        }

        // Two-pass decoding: read image bounds first
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(artworkBytes, 0, artworkBytes.size, boundsOptions)

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            return null
        }

        // Downsample to requested size or default to 120x120 px
        val reqWidth = options.size.width.pxOrElse { 120 }
        val reqHeight = options.size.height.pxOrElse { 120 }

        var inSampleSize = 1
        if (boundsOptions.outHeight > reqHeight || boundsOptions.outWidth > reqWidth) {
            val halfHeight = boundsOptions.outHeight / 2
            val halfWidth = boundsOptions.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }

        val bitmap = BitmapFactory.decodeByteArray(artworkBytes, 0, artworkBytes.size, decodeOptions)
            ?: return null

        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = inSampleSize > 1,
            dataSource = DataSource.DISK
        )
    }

    class Factory @Inject constructor(
        private val tagEngine: TagEngine
    ) : Fetcher.Factory<TrackArtworkData> {
        override fun create(
            data: TrackArtworkData,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return TrackArtworkFetcher(data, tagEngine, options)
        }
    }

    class TrackFactory @Inject constructor(
        private val tagEngine: TagEngine
    ) : Fetcher.Factory<Track> {
        override fun create(
            data: Track,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return TrackArtworkFetcher(data.toArtworkData(), tagEngine, options)
        }
    }
}
