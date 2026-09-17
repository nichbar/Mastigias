package now.link.mastigias.domain.usecase

import now.link.mastigias.domain.logging.AppLogger
import now.link.mastigias.domain.logging.NoOpAppLogger
import now.link.mastigias.domain.repository.MusicRepository
import javax.inject.Inject

class SyncMediaStoreUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    private val logger: AppLogger = NoOpAppLogger
) {
    companion object {
        private const val TAG = "SyncMediaStoreUseCase"
    }

    suspend operator fun invoke(): Result<Unit> {
        logger.d(TAG, "Starting MediaStore sync")
        val startTime = System.currentTimeMillis()
        val result = musicRepository.syncMediaStore()
        val duration = System.currentTimeMillis() - startTime
        result.onSuccess {
            logger.i(TAG, "MediaStore sync completed successfully in ${duration}ms")
        }.onFailure { ex ->
            logger.e(TAG, "MediaStore sync failed after ${duration}ms: ${ex.message}", ex)
        }
        return result
    }
}
