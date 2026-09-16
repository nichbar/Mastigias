package now.link.mastigias.data.taglib

import kotlinx.coroutines.withContext
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.core.common.ImageUtils
import now.link.mastigias.domain.engine.TagEngine
import now.link.mastigias.domain.model.AudioMetadata
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.domain.model.TagPatch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagLibEngineImpl @Inject constructor(
    private val dispatchers: AppDispatchers
) : TagEngine {

    // In-memory store used when running in JVM unit tests or environments where the NDK library is not loaded
    private val fallbackStore = ConcurrentHashMap<String, AudioMetadata>()
    private val fallbackArtwork = ConcurrentHashMap<String, ByteArray>()

    override suspend fun readMetadata(path: String): Result<AudioMetadata> =
        withContext(dispatchers.io) {
            runCatching {
                if (!TagLibBridge.isAvailable()) {
                    return@runCatching readMetadataFallback(path)
                }

                val bundle = TagLibBridge.nativeReadMetadata(path)
                    ?: error("TagLib failed to read metadata from $path")

                parseBundleToAudioMetadata(path, bundle)
            }
        }

    override suspend fun readArtwork(path: String): Result<ByteArray?> =
        withContext(dispatchers.io) {
            runCatching {
                if (!TagLibBridge.isAvailable()) {
                    return@runCatching readArtworkFallback(path)
                }
                TagLibBridge.nativeReadArtwork(path)
            }
        }

    override suspend fun writeMetadata(path: String, patch: TagPatch): Result<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                if (!TagLibBridge.isAvailable()) {
                    writeMetadataFallback(path, patch)
                    return@runCatching
                }

                val setKeys = patch.updatedFields.keys.map { it.vorbisKey }.toTypedArray()
                val setValues = patch.updatedFields.values.toTypedArray()
                val delKeys = patch.deletedFields.map { it.vorbisKey }.toTypedArray()

                var artWidth = patch.updatedArtwork?.width ?: 0
                var artHeight = patch.updatedArtwork?.height ?: 0
                if (patch.updatedArtwork != null && (artWidth <= 0 || artHeight <= 0)) {
                    val (w, h) = ImageUtils.decodeDimensions(patch.updatedArtwork.binaryData)
                    if (w > 0) artWidth = w
                    if (h > 0) artHeight = h
                }

                val success = TagLibBridge.nativeWriteMetadata(
                    filePath = path,
                    setKeys = setKeys,
                    setValues = setValues,
                    deleteKeys = delKeys,
                    artworkBytes = patch.updatedArtwork?.binaryData,
                    removeArtwork = patch.removeArtwork,
                    artworkMime = patch.updatedArtwork?.mimeType ?: "image/jpeg",
                    artworkWidth = artWidth,
                    artworkHeight = artHeight
                )

                if (!success) error("TagLib failed to write tags to $path")
            }
        }

    private fun readMetadataFallback(path: String): AudioMetadata {
        return fallbackStore[path] ?: AudioMetadata(
            trackId = 0L,
            path = path,
            fields = emptyMap(),
            artwork = null,
            bitrateKbps = 320,
            sampleRateHz = 44100,
            channels = 2,
            durationMs = 0L
        )
    }

    private fun readArtworkFallback(path: String): ByteArray? {
        return fallbackArtwork[path] ?: fallbackStore[path]?.artwork?.binaryData
    }

    private fun writeMetadataFallback(path: String, patch: TagPatch) {
        val existing = readMetadataFallback(path)
        val updatedFields = existing.fields.toMutableMap()

        for (deleted in patch.deletedFields) {
            updatedFields.remove(deleted)
        }
        for ((field, value) in patch.updatedFields) {
            updatedFields[field] = value
        }

        val updatedArtwork = when {
            patch.removeArtwork -> {
                fallbackArtwork.remove(path)
                null
            }
            patch.updatedArtwork != null -> {
                fallbackArtwork[path] = patch.updatedArtwork.binaryData
                patch.updatedArtwork
            }
            else -> existing.artwork
        }

        fallbackStore[path] = existing.copy(
            fields = updatedFields,
            artwork = updatedArtwork
        )
    }

    // JVM test and internal helpers
    internal fun setFallbackMetadata(path: String, metadata: AudioMetadata) {
        fallbackStore[path] = metadata
        metadata.artwork?.let { fallbackArtwork[path] = it.binaryData }
    }

    internal fun clearFallbackStore() {
        fallbackStore.clear()
        fallbackArtwork.clear()
    }

    companion object {
        fun mapTagsToFields(keys: Array<String>, values: Array<String>): Map<TagField, String> {
            val fieldMap = mutableMapOf<TagField, String>()
            val count = minOf(keys.size, values.size)
            for (i in 0 until count) {
                val key = keys[i]
                val value = values[i]
                // Match by canonical vorbisKey, standard key, or id3v2Frame
                TagField.entries.firstOrNull {
                    it.vorbisKey.equals(key, ignoreCase = true) ||
                    it.key.equals(key, ignoreCase = true) ||
                    it.id3v2Frame.equals(key, ignoreCase = true) ||
                    it.asfAttribute.equals(key, ignoreCase = true) ||
                    it.mp4Atom.equals(key, ignoreCase = true)
                }?.let { fieldMap[it] = value }
            }
            return fieldMap
        }

        fun parseBundleToAudioMetadata(path: String, bundle: NativeTagBundle): AudioMetadata {
            val fieldMap = mapTagsToFields(bundle.keys, bundle.values)
            return AudioMetadata(
                trackId = 0L,
                path = path,
                fields = fieldMap,
                artwork = null,
                bitrateKbps = bundle.bitrateKbps,
                sampleRateHz = bundle.sampleRateHz,
                channels = bundle.channels,
                durationMs = bundle.durationMs
            )
        }
    }
}
