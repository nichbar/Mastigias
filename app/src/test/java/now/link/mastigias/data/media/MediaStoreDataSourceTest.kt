package now.link.mastigias.data.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MediaStoreDataSourceTest {

    private lateinit var mediaStoreDataSource: MediaStoreDataSource

    @Before
    fun setup() {
        mediaStoreDataSource = MediaStoreDataSource()
    }

    @Test
    fun `createBatchWriteRequest with null context returns null`() {
        val result = mediaStoreDataSource.createBatchWriteRequest(listOf(1L, 2L))
        assertNull(result)
    }

    @Test
    fun `createBatchWriteRequest with empty trackIds returns null`() {
        val result = mediaStoreDataSource.createBatchWriteRequest(emptyList())
        assertNull(result)
    }

    @Test
    fun `hasManageMediaPermission with null context returns false`() {
        assertFalse(mediaStoreDataSource.hasManageMediaPermission())
    }
}
