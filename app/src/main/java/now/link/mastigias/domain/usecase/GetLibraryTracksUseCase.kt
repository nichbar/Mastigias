package now.link.mastigias.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import now.link.mastigias.domain.model.FilterMode
import now.link.mastigias.domain.model.FolderFilter
import now.link.mastigias.domain.model.Track
import now.link.mastigias.domain.repository.MusicRepository
import now.link.mastigias.domain.repository.PreferencesRepository
import now.link.mastigias.ui.library.LibrarySortOrder
import now.link.mastigias.ui.library.SortDirection
import java.net.URLDecoder
import java.util.Locale
import javax.inject.Inject

class GetLibraryTracksUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(
        query: String = "",
        untaggedOnly: Boolean = false,
        sortOrder: LibrarySortOrder? = null,
        sortDirection: SortDirection? = null,
        folderFilters: List<FolderFilter>? = null
    ): Flow<List<Track>> {
        val tracksFlow = when {
            query.isNotBlank() -> musicRepository.searchTracks(query)
            untaggedOnly -> musicRepository.observeUntaggedTracks()
            else -> musicRepository.observeTracks()
        }

        val sortOrderFlow = sortOrder?.let { flowOf(it) } ?: preferencesRepository.sortOrderFlow
        val sortDirectionFlow = sortDirection?.let { flowOf(it) } ?: preferencesRepository.sortDirectionFlow
        val filtersFlow = folderFilters?.let { flowOf(it) } ?: preferencesRepository.folderFiltersFlow

        return combine(tracksFlow, filtersFlow, sortOrderFlow, sortDirectionFlow) { tracks, filters, order, direction ->
            var result = tracks

            if (untaggedOnly) {
                result = result.filter { !it.isTagged }
            }

            if (filters.isNotEmpty()) {
                result = result.filter { track -> matchesFolderFilters(track.path, filters) }
            }

            val comparator = getComparator(order)
            val finalComparator = if (direction == SortDirection.DESCENDING) comparator.reversed() else comparator
            result.sortedWith(finalComparator)
        }
    }

    private fun getComparator(order: LibrarySortOrder): Comparator<Track> = when (order) {
        LibrarySortOrder.TITLE -> compareBy<Track> { it.title.lowercase(Locale.ROOT) }
            .thenBy { it.artist.lowercase(Locale.ROOT) }
            .thenBy { it.trackNumber }
        LibrarySortOrder.ARTIST -> compareBy<Track> { it.artist.lowercase(Locale.ROOT) }
            .thenBy { it.album.lowercase(Locale.ROOT) }
            .thenBy { it.trackNumber }
            .thenBy { it.title.lowercase(Locale.ROOT) }
        LibrarySortOrder.ALBUM -> compareBy<Track> { it.album.lowercase(Locale.ROOT) }
            .thenBy { it.trackNumber }
            .thenBy { it.title.lowercase(Locale.ROOT) }
        LibrarySortOrder.DATE_MODIFIED -> compareBy<Track> { it.dateModified }
            .thenBy { it.title.lowercase(Locale.ROOT) }
            .thenBy { it.artist.lowercase(Locale.ROOT) }
        LibrarySortOrder.DATE_CREATED -> compareBy<Track> { it.dateCreated }
            .thenBy { it.title.lowercase(Locale.ROOT) }
            .thenBy { it.artist.lowercase(Locale.ROOT) }
    }

    companion object {
        fun matchesFolderFilters(trackPath: String, filters: List<FolderFilter>): Boolean {
            if (filters.isEmpty()) return true

            val includeFilters = filters.filter { it.mode == FilterMode.INCLUDE }
            val excludeFilters = filters.filter { it.mode == FilterMode.EXCLUDE }

            if (includeFilters.isNotEmpty()) {
                if (includeFilters.none { isTrackUnderFilter(trackPath, it) }) {
                    return false
                }
            }

            if (excludeFilters.isNotEmpty()) {
                if (excludeFilters.any { isTrackUnderFilter(trackPath, it) }) {
                    return false
                }
            }

            return true
        }

        fun isTrackUnderFilter(trackPath: String, filter: FolderFilter): Boolean {
            // 1. Direct absolute path check (e.g. if filter.path is an absolute path from tests or legacy)
            if (filter.path.startsWith("/")) {
                val normalizedFilter = normalizeStorageAliases(filter.path).trimEnd('/')
                val normalizedTrack = normalizeStorageAliases(trackPath).trimEnd('/')
                if (normalizedTrack.equals(normalizedFilter, ignoreCase = true) ||
                    normalizedTrack.startsWith("$normalizedFilter/", ignoreCase = true)
                ) {
                    return true
                }
            }

            // 2. Resolve from SAF Uri if present and valid
            if (filter.uri.isNotBlank()) {
                val safMatchResult = isPathUnderSafUri(trackPath, filter.uri)
                if (safMatchResult != null) {
                    return safMatchResult
                }
            }

            // 3. Fallback: match filter.path as relative path when URI is not a SAF URI
            if (filter.path.isNotBlank()) {
                val filterRel = filter.path.trim('/')
                if (filterRel.isEmpty()) {
                    return true
                }
                val storagePrefixRegex = Regex(
                    "^/(?:storage/emulated/\\d+|sdcard|storage/self/primary|storage/[^/]+|mnt/media_rw/[^/]+)/",
                    RegexOption.IGNORE_CASE
                )
                val trackRel = trackPath.replaceFirst(storagePrefixRegex, "").trim('/')
                if (trackRel.equals(filterRel, ignoreCase = true) ||
                    trackRel.startsWith("$filterRel/", ignoreCase = true)
                ) {
                    return true
                }
            }

            return false
        }

        private fun normalizeStorageAliases(path: String): String = when {
            path.startsWith("/sdcard/", ignoreCase = true) ->
                "/storage/emulated/0/" + path.substring(8)
            path.equals("/sdcard", ignoreCase = true) ->
                "/storage/emulated/0"
            path.startsWith("/storage/self/primary/", ignoreCase = true) ->
                "/storage/emulated/0/" + path.substring(22)
            path.equals("/storage/self/primary", ignoreCase = true) ->
                "/storage/emulated/0"
            else -> path
        }

        private fun isPathUnderSafUri(trackPath: String, uriString: String): Boolean? {
            val pathPart = uriString.substringBefore("?").substringBefore("#")
            val decoded = runCatching {
                URLDecoder.decode(pathPart, "UTF-8")
            }.getOrDefault(pathPart)

            val docId = when {
                decoded.contains("/tree/") -> decoded.substringAfter("/tree/").substringBefore("/document/")
                decoded.contains("/document/") -> decoded.substringAfter("/document/")
                else -> null
            }?.trim('/')

            if (docId == null) {
                return null
            }

            if (docId.startsWith("raw:", ignoreCase = true)) {
                val rawPath = normalizeStorageAliases(docId.substringAfter("raw:").trimEnd('/'))
                val normalizedTrack = normalizeStorageAliases(trackPath).trimEnd('/')
                return normalizedTrack.equals(rawPath, ignoreCase = true) ||
                    normalizedTrack.startsWith("$rawPath/", ignoreCase = true)
            }

            if (docId.equals("primary", ignoreCase = true)) {
                val regexPattern = "^/(?:storage/emulated/\\d+|sdcard|storage/self/primary)(?:/.*)?$"
                return Regex(regexPattern, RegexOption.IGNORE_CASE).matches(trackPath)
            }

            if (docId.contains(":")) {
                val volumeId = docId.substringBefore(":")
                val relPath = docId.substringAfter(":").trim('/')

                if (volumeId.equals("primary", ignoreCase = true)) {
                    val regexPattern = if (relPath.isEmpty()) {
                        "^/(?:storage/emulated/\\d+|sdcard|storage/self/primary)(?:/.*)?$"
                    } else {
                        "^/(?:storage/emulated/\\d+|sdcard|storage/self/primary)/${Regex.escape(relPath)}(?:/.*)?$"
                    }
                    return Regex(regexPattern, RegexOption.IGNORE_CASE).matches(trackPath)
                } else if (volumeId.isNotBlank()) {
                    val regexPattern = if (relPath.isEmpty()) {
                        "^/(?:storage|mnt/media_rw)/${Regex.escape(volumeId)}(?:/.*)?$"
                    } else {
                        "^/(?:storage|mnt/media_rw)/${Regex.escape(volumeId)}/${Regex.escape(relPath)}(?:/.*)?$"
                    }
                    return Regex(regexPattern, RegexOption.IGNORE_CASE).matches(trackPath)
                }
            }

            return null
        }
    }
}
