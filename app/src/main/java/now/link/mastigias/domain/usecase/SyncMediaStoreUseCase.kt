package now.link.mastigias.domain.usecase

import now.link.mastigias.domain.repository.MusicRepository
import javax.inject.Inject

class SyncMediaStoreUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return musicRepository.syncMediaStore()
    }
}
