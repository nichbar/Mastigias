package now.link.mastigias.data.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.domain.engine.TagEngine
import now.link.mastigias.domain.model.AudioMetadata
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.domain.model.TagPatch
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ScopedStorageManagerTest {

    private val testDispatchers = AppDispatchers(
        default = Dispatchers.Unconfined,
        io = Dispatchers.Unconfined,
        main = Dispatchers.Unconfined,
        unconfined = Dispatchers.Unconfined
    )

    private lateinit var fakeTagEngine: FakeTagEngine
    private lateinit var fakeTrackDao: FakeTrackDao
    private lateinit var scopedStorageManager: ScopedStorageManager

    @Before
    fun setup() {
        fakeTagEngine = FakeTagEngine()
        fakeTrackDao = FakeTrackDao()
        scopedStorageManager = ScopedStorageManager(
            fakeTagEngine,
            fakeTrackDao,
            testDispatchers
        )
    }

    @Test
    fun `writeSingleTrack without context returns failure with descriptive IOException`() = runBlocking {
        val patch = TagPatch(
            updatedFields = mapOf(TagField.TITLE to "New Title"),
            deletedFields = emptySet(),
            updatedArtwork = null,
            removeArtwork = false
        )

        val result = scopedStorageManager.writeSingleTrack(
            trackId = 100L,
            sourcePath = "/storage/emulated/0/Music/song.mp3",
            patch = patch,
            mimeType = "audio/mpeg"
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IOException)
        assertTrue(exception?.message?.contains("Context is required") == true)
    }

    private class FakeTagEngine : TagEngine {
        var failWrite = false
        var returnNullMetadata = false
        var durationMsToReturn = 180000L

        override suspend fun readMetadata(path: String): Result<AudioMetadata> {
            if (returnNullMetadata) {
                return Result.failure(IOException("Failed to read"))
            }
            return Result.success(
                AudioMetadata(
                    trackId = 0L,
                    path = path,
                    fields = mapOf(
                        TagField.TITLE to "Read Title",
                        TagField.ARTIST to "Read Artist",
                        TagField.ALBUM to "Read Album"
                    ),
                    artwork = null,
                    bitrateKbps = 320,
                    sampleRateHz = 44100,
                    channels = 2,
                    durationMs = durationMsToReturn
                )
            )
        }

        override suspend fun readArtwork(path: String): Result<ByteArray?> = Result.success(null)

        override suspend fun writeMetadata(path: String, patch: TagPatch): Result<Unit> {
            if (failWrite) {
                return Result.failure(IOException("TagLib native write failed"))
            }
            return Result.success(Unit)
        }
    }
}
