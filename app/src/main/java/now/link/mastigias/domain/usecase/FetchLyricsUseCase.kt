package now.link.mastigias.domain.usecase

import now.link.mastigias.domain.logging.AppLogger
import now.link.mastigias.domain.model.LyricsCandidate
import now.link.mastigias.domain.repository.LyricsRepository
import javax.inject.Inject
import kotlin.math.abs

class FetchLyricsUseCase @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val logger: AppLogger
) {
    suspend operator fun invoke(
        trackName: String,
        artistName: String? = null,
        albumName: String? = null,
        targetDurationMs: Long? = null
    ): Result<List<LyricsCandidate>> {
        val trimmedTrack = trackName.trim()
        if (trimmedTrack.isBlank()) {
            return Result.failure(IllegalArgumentException("Track name cannot be blank"))
        }

        val trimmedArtist = artistName?.trim()?.ifBlank { null }
        val trimmedAlbum = albumName?.trim()?.ifBlank { null }

        logger.d(TAG, "Fetching lyrics for track='$trimmedTrack', artist='$trimmedArtist', album='$trimmedAlbum'")

        return lyricsRepository.searchLyrics(trimmedTrack, trimmedArtist, trimmedAlbum).map { candidates ->
            logger.d(TAG, "Received ${candidates.size} candidate(s) from repository")
            val targetDurationSec = targetDurationMs?.takeIf { it > 0 }?.let { it / 1000.0 }
            candidates
                .map { candidate -> candidate to calculateScore(candidate, trimmedTrack, trimmedArtist, trimmedAlbum, targetDurationSec) }
                .sortedWith(
                    compareByDescending<Pair<LyricsCandidate, Int>> { it.second }
                        .thenByDescending { it.first.hasSyncedLyrics }
                        .thenBy { candidatePair ->
                            val diff = targetDurationSec?.let { abs(it - candidatePair.first.durationSeconds) } ?: Double.MAX_VALUE
                            diff
                        }
                )
                .map { it.first }
        }
    }

    private fun calculateScore(
        candidate: LyricsCandidate,
        targetTrack: String,
        targetArtist: String?,
        targetAlbum: String?,
        targetDurationSec: Double?
    ): Int {
        var score = 0

        // 1. Duration closeness
        if (targetDurationSec != null && candidate.durationSeconds > 0) {
            val diff = abs(targetDurationSec - candidate.durationSeconds)
            score += when {
                diff <= 2.0 -> 50
                diff <= 5.0 -> 30
                diff <= 10.0 -> 10
                diff <= 20.0 -> 0
                else -> -30
            }
        }

        // 2. Track name match
        val normTargetTrack = targetTrack.lowercase()
        val normCandTrack = candidate.trackName.trim().lowercase()
        if (normCandTrack == normTargetTrack) {
            score += 40
        } else if (normCandTrack.contains(normTargetTrack) || normTargetTrack.contains(normCandTrack)) {
            score += 20
        }

        // 3. Artist match
        if (targetArtist != null) {
            val normTargetArtist = targetArtist.lowercase()
            val normCandArtist = candidate.artistName.trim().lowercase()
            if (normCandArtist == normTargetArtist) {
                score += 30
            } else if (normCandArtist.contains(normTargetArtist) || normTargetArtist.contains(normCandArtist)) {
                score += 15
            }
        }

        // 4. Album match
        if (targetAlbum != null && !candidate.albumName.isNullOrBlank()) {
            val normTargetAlbum = targetAlbum.lowercase()
            val normCandAlbum = candidate.albumName.trim().lowercase()
            if (normCandAlbum == normTargetAlbum) {
                score += 15
            } else if (normCandAlbum.contains(normTargetAlbum) || normTargetAlbum.contains(normCandAlbum)) {
                score += 5
            }
        }

        // 5. Synced vs Plain lyrics availability
        if (candidate.hasSyncedLyrics) {
            score += 25
        } else if (candidate.hasPlainLyrics) {
            score += 10
        }

        // 6. Instrumental penalty
        if (candidate.instrumental || (!candidate.hasSyncedLyrics && !candidate.hasPlainLyrics)) {
            score -= 20
        }

        return score
    }

    companion object {
        private const val TAG = "FetchLyricsUseCase"
    }
}
