package deckers.thibault.aves.storage.apis

import deckers.thibault.aves.utils.MimeTypes
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaStorePermissionsTest {
    @Test
    fun videoCanBeInsertedInMovies() {
        assertTrue(MediaStoreInsertionRules.allows(MimeTypes.MP4, "movies"))
    }

    @Test
    fun imageCannotBeInsertedInMovies() {
        assertFalse(MediaStoreInsertionRules.allows(MimeTypes.JPEG, "movies"))
    }

    @Test
    fun imageCanBeInsertedInPictures() {
        assertTrue(MediaStoreInsertionRules.allows(MimeTypes.JPEG, "pictures"))
    }

    @Test
    fun videoCanBeInsertedInPictures() {
        assertTrue(MediaStoreInsertionRules.allows(MimeTypes.MP4, "pictures"))
    }

    @Test
    fun mediaCannotBeInsertedInArbitraryPrimaryDirectory() {
        assertFalse(MediaStoreInsertionRules.allows(MimeTypes.MP4, "documents"))
        assertFalse(MediaStoreInsertionRules.allows(MimeTypes.JPEG, "documents"))
    }

    @Test
    fun nonMediaTypeDoesNotUseMimeAwareOverride() {
        assertFalse(MediaStoreInsertionRules.allows(MimeTypes.ZIP, "downloads"))
    }
}
