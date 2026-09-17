package now.link.mastigias

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import now.link.mastigias.core.logging.LogManager
import now.link.mastigias.data.image.TrackArtworkFetcher
import now.link.mastigias.data.image.TrackArtworkKeyer
import now.link.mastigias.data.image.TrackKeyer
import now.link.mastigias.domain.engine.TagEngine
import javax.inject.Inject

@HiltAndroidApp
class MastigiasApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var tagEngine: TagEngine

    override fun onCreate() {
        super.onCreate()
        LogManager.initialize(this)
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(TrackArtworkFetcher.Factory(tagEngine))
                add(TrackArtworkFetcher.TrackFactory(tagEngine))
                add(TrackArtworkKeyer())
                add(TrackKeyer())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .build()
    }
}
