package now.link.mastigias.data.media

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import now.link.mastigias.core.constants.AudioFormats
import now.link.mastigias.core.logging.LogManager
import javax.inject.Inject
import javax.inject.Singleton

data class MediaStoreAudioItem(
    val id: Long,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: Int,
    val durationMs: Long,
    val dateModified: Long,
    val dateAdded: Long = 0L,
    val mimeType: String,
    val sizeBytes: Long
)

@Singleton
open class MediaStoreDataSource {
    private val context: Context?

    companion object {
        private const val TAG = "MediaStoreDataSource"
    }

    @Inject
    constructor(@ApplicationContext context: Context) {
        this.context = context
    }

    constructor() {
        this.context = null
    }

    fun getTrackUri(trackId: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)

    /**
     * Creates a batch write consent request for Android 11+ (API 30+).
     *
     * In Android Scoped Storage (API 30+), modifying media files owned by other apps
     * requires write access granted via [MediaStore.createWriteRequest].
     *
     * When [MediaStore.canManageMedia] is granted (API 31+), the system's PermissionActivity
     * automatically suppresses the confirmation dialog and silently approves the request.
     * We must still initiate the write request so that Android grants the URI write permission.
     *
     * Files that already have write permission granted (or when All Files Access is active)
     * are filtered out to avoid redundant requests.
     */
    fun createBatchWriteRequest(trackIds: List<Long>): IntentSender? {
        val ctx = context ?: return null
        if (trackIds.isEmpty()) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // All Files Access (MANAGE_EXTERNAL_STORAGE) bypasses Scoped Storage write restrictions
            if (Environment.isExternalStorageManager()) {
                LogManager.d(TAG, "All Files Access (isExternalStorageManager) granted, consent bypassed")
                return null
            }

            // Filter out tracks that already have write permission granted
            val urisToRequest = trackIds.map { getTrackUri(it) }.filter { uri ->
                ctx.checkUriPermission(
                    uri,
                    Process.myPid(),
                    Process.myUid(),
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                ) != PackageManager.PERMISSION_GRANTED
            }

            if (urisToRequest.isEmpty()) {
                LogManager.d(TAG, "Write permission already held for all ${trackIds.size} tracks")
                return null
            }

            LogManager.d(
                TAG,
                "Requesting write consent for ${urisToRequest.size} of ${trackIds.size} tracks (canManageMedia=${hasManageMediaPermission()})"
            )
            val pendingIntent: PendingIntent = MediaStore.createWriteRequest(ctx.contentResolver, urisToRequest)
            return pendingIntent.intentSender
        }
        return null
    }

    fun hasManageMediaPermission(): Boolean {
        val ctx = context ?: return false
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(ctx)
    }

    fun queryAudioTracks(): List<MediaStoreAudioItem> {
        val ctx = context ?: return emptyList()
        val items = mutableListOf<MediaStoreAudioItem>()
        val collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        val cursor: Cursor? = try {
            ctx.contentResolver.query(
                collectionUri,
                projection,
                selection,
                null,
                sortOrder
            )
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to query MediaStore: ${e.message}", e)
            null
        }

        cursor?.use { c ->
            val idCol = c.getColumnIndex(MediaStore.Audio.Media._ID)
            val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            val titleCol = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val trackCol = c.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val durationCol = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val dateModifiedCol = c.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val dateAddedCol = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val mimeTypeCol = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val sizeCol = c.getColumnIndex(MediaStore.Audio.Media.SIZE)

            while (c.moveToNext()) {
                val path = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                if (path.isBlank() || !AudioFormats.isSupportedPath(path)) {
                    continue
                }
                val id = if (idCol >= 0) c.getLong(idCol) else -1L
                if (id == -1L) continue

                val title = if (titleCol >= 0) c.getString(titleCol) ?: "" else ""
                val artist = if (artistCol >= 0) c.getString(artistCol) ?: "" else ""
                val album = if (albumCol >= 0) c.getString(albumCol) ?: "" else ""
                val trackNumber = if (trackCol >= 0) c.getInt(trackCol) else 0
                val durationMs = if (durationCol >= 0) c.getLong(durationCol) else 0L
                val dateModified = if (dateModifiedCol >= 0) c.getLong(dateModifiedCol) else 0L
                val dateAdded = if (dateAddedCol >= 0) c.getLong(dateAddedCol) else 0L
                val mimeType = if (mimeTypeCol >= 0) c.getString(mimeTypeCol) ?: "" else ""
                val sizeBytes = if (sizeCol >= 0) c.getLong(sizeCol) else 0L

                items.add(
                    MediaStoreAudioItem(
                        id = id,
                        path = path,
                        title = title,
                        artist = artist,
                        album = album,
                        trackNumber = trackNumber,
                        durationMs = durationMs,
                        dateModified = dateModified,
                        dateAdded = dateAdded,
                        mimeType = mimeType.ifBlank { AudioFormats.getMimeType(path) },
                        sizeBytes = sizeBytes
                    )
                )
            }
        }

        LogManager.i(TAG, "MediaStore query completed: found ${items.size} supported audio tracks")
        return items
    }

    fun deleteTrack(trackId: Long): Boolean {
        val ctx = context ?: return false
        return try {
            val uri = getTrackUri(trackId)
            val rows = ctx.contentResolver.delete(uri, null, null)
            val success = rows > 0
            LogManager.d(TAG, "Deleted track $trackId from MediaStore: success=$success (rows=$rows)")
            success
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to delete track $trackId from MediaStore: ${e.message}", e)
            false
        }
    }

    fun observeMediaStore(): Flow<Unit> = callbackFlow {
        val ctx = context
        if (ctx == null) {
            close()
            return@callbackFlow
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                LogManager.v(TAG, "MediaStore onChange fired: uri=$uri, selfChange=$selfChange")
                trySend(Unit)
            }
        }
        try {
            ctx.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (e: Exception) {
            // ContentObserver registration failure fallback
        }
        awaitClose {
            try {
                ctx.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                // Ignore unregister failure
            }
        }
    }
}
