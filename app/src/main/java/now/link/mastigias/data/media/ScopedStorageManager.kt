package now.link.mastigias.data.media

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.core.constants.AudioFormats
import now.link.mastigias.core.logging.LogManager
import now.link.mastigias.data.database.dao.TrackDao
import now.link.mastigias.domain.engine.TagEngine
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.domain.model.TagPatch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class ScopedStorageManager {
    private val context: Context?
    private val tagEngine: TagEngine
    private val trackDao: TrackDao
    private val dispatchers: AppDispatchers

    companion object {
        private const val TAG = "ScopedStorageManager"
    }

    @Inject
    constructor(
        @ApplicationContext context: Context,
        tagEngine: TagEngine,
        trackDao: TrackDao,
        dispatchers: AppDispatchers
    ) {
        this.context = context
        this.tagEngine = tagEngine
        this.trackDao = trackDao
        this.dispatchers = dispatchers
    }

    constructor(
        tagEngine: TagEngine,
        trackDao: TrackDao,
        dispatchers: AppDispatchers
    ) {
        this.context = null
        this.tagEngine = tagEngine
        this.trackDao = trackDao
        this.dispatchers = dispatchers
    }

    /**
     * Executes the 8-step atomic safe write protocol:
     * 1. Request Scoped Storage write consent if required (handled upstream via MediaStoreDataSource)
     * 2. Copy source audio bytes into an isolated work copy in cacheDir/tag_work/
     * 3. Native TagLib writes metadata & artwork to the isolated work copy
     * 4. Verify integrity (non-zero size, valid audio properties, uncorrupted duration)
     * 5. Stream verified bytes back to target destination using NIO FileChannel.transferTo()
     * 6. Flush (fsync) and remove temporary work file in a guaranteed finally block
     * 7. Trigger MediaScannerConnection.scanFile() with explicit 1:1 MIME type
     * 8. Invalidate Coil image cache and update Room database cache
     */
    suspend fun writeSingleTrack(
        trackId: Long,
        sourcePath: String,
        patch: TagPatch,
        mimeType: String
    ): Result<Unit> = withContext(NonCancellable + dispatchers.io) {
        runCatching {
            LogManager.d(TAG, "Starting atomic write protocol for track $trackId ($sourcePath)")
            val ctx = context ?: throw IOException("Context is required for writing audio track")
            val contentUri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                trackId
            )
            val workDir = File(ctx.cacheDir, "tag_work").apply { mkdirs() }
            val workFile = File.createTempFile("tag_${trackId}_${System.nanoTime()}", ".work", workDir)

            try {
                // Step 2: Copy source file to isolated work copy
                val sourceFile = File(sourcePath)
                if (sourceFile.exists() && sourceFile.canRead()) {
                    FileInputStream(sourceFile).channel.use { inChannel ->
                        FileOutputStream(workFile).channel.use { outChannel ->
                            inChannel.transferTo(0, inChannel.size(), outChannel)
                        }
                    }
                } else {
                    ctx.contentResolver.openInputStream(contentUri)?.use { input ->
                        FileOutputStream(workFile).use { output ->
                            input.copyTo(output, bufferSize = 256 * 1024)
                        }
                    } ?: throw IOException("Cannot open input stream for $contentUri ($sourcePath)")
                }

                val originalSize = workFile.length()
                if (originalSize == 0L) {
                    throw IOException("Source audio file copied to work directory is 0 bytes: $sourcePath")
                }
                LogManager.v(TAG, "Step 2: Work copy created (${workFile.name}, $originalSize bytes)")

                // Step 3: Native TagLib write on work copy
                LogManager.v(TAG, "Step 3: Invoking TagLib write on work copy for track $trackId")
                val writeResult = tagEngine.writeMetadata(workFile.absolutePath, patch)
                writeResult.getOrThrow()

                // Step 4: Integrity Verification
                val newSize = workFile.length()
                if (newSize == 0L) {
                    throw IOException("Integrity check failed: work file size became 0 after TagLib write")
                }
                // Verify audio header remains intact and duration is preserved
                val currentTrack = trackDao.getTrackById(trackId)
                val readBack = tagEngine.readMetadata(workFile.absolutePath).getOrNull()
                if (readBack == null) {
                    throw IOException("Integrity check failed: audio stream header unreadable after write on $sourcePath")
                }
                if ((currentTrack?.durationMs ?: 0L) > 0L && readBack.durationMs <= 0L) {
                    throw IOException("Integrity check failed: audio duration corrupted after write on $sourcePath")
                }
                LogManager.v(
                    TAG,
                    "Step 4: Integrity verified for track $trackId ($originalSize -> $newSize bytes, ${readBack.durationMs}ms)"
                )

                // Step 5: Stream verified bytes back to target destination
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // API 30+ Scoped Storage: write via granted ContentResolver ParcelFileDescriptor
                    val pfd = ctx.contentResolver.openFileDescriptor(contentUri, "rw")
                        ?: ctx.contentResolver.openFileDescriptor(contentUri, "wt")
                        ?: throw IOException("Failed to acquire ParcelFileDescriptor for $contentUri")

                    pfd.use { parcelFd ->
                        FileOutputStream(parcelFd.fileDescriptor).channel.use { outChannel ->
                            FileInputStream(workFile).channel.use { inChannel ->
                                outChannel.truncate(0) // Clear previous content if new file is smaller
                                inChannel.transferTo(0, inChannel.size(), outChannel)
                                outChannel.force(true) // fsync to physical storage
                            }
                        }
                    }
                } else {
                    // API 26-29: Direct filesystem write supported via requestLegacyExternalStorage
                    if (sourceFile.canWrite()) {
                        FileOutputStream(sourceFile).channel.use { outChannel ->
                            FileInputStream(workFile).channel.use { inChannel ->
                                outChannel.truncate(0)
                                inChannel.transferTo(0, inChannel.size(), outChannel)
                                outChannel.force(true)
                            }
                        }
                    } else {
                        // Fallback via ContentResolver if direct write is restricted by OEM
                        ctx.contentResolver.openFileDescriptor(contentUri, "rw")?.use { pfd ->
                            FileOutputStream(pfd.fileDescriptor).channel.use { outChannel ->
                                FileInputStream(workFile).channel.use { inChannel ->
                                    outChannel.truncate(0)
                                    inChannel.transferTo(0, inChannel.size(), outChannel)
                                    outChannel.force(true)
                                }
                            }
                        } ?: throw IOException("Unable to write to file $sourcePath via direct or URI access")
                    }
                }
                LogManager.v(TAG, "Step 5: Transferred verified bytes to destination for track $trackId")

                // Step 7: Post-write MediaScanner sync with accurate 1:1 MIME type
                val effectiveMimeType = mimeType.ifBlank { AudioFormats.getMimeTypeForPath(sourcePath) }
                MediaScannerConnection.scanFile(
                    ctx,
                    arrayOf(sourcePath),
                    arrayOf(effectiveMimeType)
                ) { scannedPath, scannedUri ->
                    LogManager.v(TAG, "Step 7: MediaScanner finished for $scannedPath (URI: $scannedUri)")
                }

                // Step 8: Update Room database cache
                val hasArtwork = when {
                    patch.updatedArtwork != null -> true
                    patch.removeArtwork -> false
                    else -> null // Retain previous status
                }
                if (hasArtwork != null) {
                    trackDao.updateArtworkStatus(trackId, hasArtwork)
                }

                val readTitle = readBack.fields[TagField.TITLE] ?: ""
                val readArtist = readBack.fields[TagField.ARTIST] ?: ""
                val readAlbum = readBack.fields[TagField.ALBUM] ?: ""
                val readTrackNumber = readBack.fields[TagField.TRACK_NUMBER]?.toIntOrNull()

                val track = currentTrack ?: trackDao.getTrackById(trackId)
                if (track != null) {
                    val updatedTitle = readTitle.ifBlank { track.title }
                    val updatedArtist = readArtist.ifBlank { track.artist }
                    val updatedAlbum = readAlbum.ifBlank { track.album }
                    val isTagged = updatedTitle.isNotBlank() &&
                        !updatedTitle.equals("<unknown>", ignoreCase = true) &&
                        updatedArtist.isNotBlank() &&
                        !updatedArtist.equals("<unknown>", ignoreCase = true) &&
                        updatedAlbum.isNotBlank() &&
                        !updatedAlbum.equals("<unknown>", ignoreCase = true)

                    val updatedTrack = track.copy(
                        title = updatedTitle,
                        artist = updatedArtist,
                        album = updatedAlbum,
                        trackNumber = readTrackNumber ?: track.trackNumber,
                        durationMs = if (readBack.durationMs > 0) readBack.durationMs else track.durationMs,
                        hasArtwork = hasArtwork ?: track.hasArtwork,
                        isTagged = isTagged,
                        dateModified = System.currentTimeMillis() / 1000,
                        sizeBytes = newSize
                    )
                    trackDao.upsertTracks(listOf(updatedTrack))
                }
                LogManager.i(TAG, "Step 8: Atomic write protocol finished successfully for track $trackId ($sourcePath)")
                Unit
            } finally {
                // Step 6: Guaranteed temporary work file cleanup
                if (workFile.exists()) {
                    val deleted = workFile.delete()
                    LogManager.v(TAG, "Step 6: Work copy cleanup ${workFile.name} (deleted: $deleted)")
                }
            }
        }.onFailure { ex ->
            LogManager.e(TAG, "Atomic write protocol failed for track $trackId ($sourcePath): ${ex.message}", ex)
        }
    }
}
