package now.link.mastigias.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagFieldTest {

    @Test
    fun `verify TagField has more than 50 entries`() {
        val count = TagField.entries.size
        assertTrue("Expected at least 50 TagFields, found $count", count >= 50)
        assertEquals(62, count)
    }

    @Test
    fun `verify all TagField keys are unique`() {
        val keys = TagField.entries.map { it.key }
        val uniqueKeys = keys.toSet()
        assertEquals("TagField keys must be unique", keys.size, uniqueKeys.size)
    }

    @Test
    fun `verify fromKey lookup resolves every TagField`() {
        for (field in TagField.entries) {
            val resolved = TagField.fromKey(field.key)
            assertEquals("Lookup for key ${field.key} should resolve correctly", field, resolved)
        }
    }

    @Test
    fun `verify basicFields contains expected basic tags`() {
        val basicFields = TagField.basicFields
        assertEquals(12, basicFields.size)
        assertTrue(basicFields.contains(TagField.TITLE))
        assertTrue(basicFields.contains(TagField.ARTIST))
        assertTrue(basicFields.contains(TagField.ALBUM))
        assertTrue(basicFields.contains(TagField.YEAR))
        assertTrue(basicFields.contains(TagField.TRACK_NUMBER))
        assertTrue(basicFields.contains(TagField.TRACK_TOTAL))
        assertTrue(basicFields.contains(TagField.GENRE))
        assertTrue(basicFields.contains(TagField.ALBUM_ARTIST))
        assertTrue(basicFields.contains(TagField.COMPOSER))
        assertTrue(basicFields.contains(TagField.DISC_NUMBER))
        assertTrue(basicFields.contains(TagField.DISC_TOTAL))
        assertTrue(basicFields.contains(TagField.COMMENT))
    }

    @Test
    fun `verify advancedFields contains all non-basic categories`() {
        val advanced = TagField.advancedFields
        assertEquals(TagField.entries.size - 12, advanced.size)
        for (field in advanced) {
            assertTrue(field.category != TagCategory.BASIC)
        }
    }

    @Test
    fun `verify all TagCategories are represented`() {
        val representedCategories = TagField.entries.map { it.category }.toSet()
        assertEquals(TagCategory.entries.toSet(), representedCategories)
    }

    @Test
    fun `verify batchBasicFields excludes track-specific fields`() {
        val batchBasic = TagField.batchBasicFields
        assertEquals(10, batchBasic.size)
        assertTrue(batchBasic.contains(TagField.ALBUM))
        assertTrue(batchBasic.contains(TagField.ARTIST))
        assertTrue(batchBasic.contains(TagField.YEAR))
        assertTrue(batchBasic.contains(TagField.TRACK_TOTAL))
        assertTrue(batchBasic.contains(TagField.GENRE))
        assertTrue(batchBasic.contains(TagField.ALBUM_ARTIST))
        assertTrue(batchBasic.contains(TagField.COMPOSER))
        assertTrue(batchBasic.contains(TagField.DISC_NUMBER))
        assertTrue(batchBasic.contains(TagField.DISC_TOTAL))
        assertTrue(batchBasic.contains(TagField.COMMENT))
        // Track-specific fields must NOT be in batchBasicFields
        assertTrue(!batchBasic.contains(TagField.TITLE))
        assertTrue(!batchBasic.contains(TagField.TRACK_NUMBER))
    }

    @Test
    fun `verify track specific fields are not batch editable`() {
        val excludedFields = listOf(
            TagField.TITLE,
            TagField.TRACK_NUMBER,
            TagField.LYRICS,
            TagField.TITLE_SORT,
            TagField.BPM,
            TagField.INITIAL_KEY,
            TagField.REPLAYGAIN_TRACK_GAIN,
            TagField.REPLAYGAIN_TRACK_PEAK,
            TagField.ACOUSTID_FINGERPRINT,
            TagField.ACOUSTID_ID,
            TagField.MUSICBRAINZ_TRACK_ID,
            TagField.MUSICBRAINZ_RECORDING_ID,
            TagField.MUSICBRAINZ_WORK_ID,
            TagField.ISRC
        )
        for (field in excludedFields) {
            assertTrue("Field $field should not be batch editable", !field.isBatchEditable)
        }
    }
}
