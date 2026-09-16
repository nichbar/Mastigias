package now.link.mastigias.domain.usecase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncMediaStoreUseCaseTest {

    private lateinit var musicRepository: FakeMusicRepository
    private lateinit var useCase: SyncMediaStoreUseCase

    @Before
    fun setUp() {
        musicRepository = FakeMusicRepository()
        useCase = SyncMediaStoreUseCase(musicRepository)
    }

    @Test
    fun `invoke calls syncMediaStore on repository`() = runBlocking {
        val result = useCase()
        assertTrue(result.isSuccess)
        assertTrue(musicRepository.syncCalled)
    }
}
