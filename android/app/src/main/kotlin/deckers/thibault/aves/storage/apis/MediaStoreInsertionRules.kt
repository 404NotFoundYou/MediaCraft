package deckers.thibault.aves.storage.apis

import deckers.thibault.aves.utils.MimeTypes

internal object MediaStoreInsertionRules {
    private val imageDirectories = setOf("dcim", "pictures")
    private val videoDirectories = setOf("dcim", "movies", "pictures")

    fun allows(mimeType: String, primaryDirectoryLower: String?): Boolean = when {
        MimeTypes.isImage(mimeType) -> primaryDirectoryLower in imageDirectories
        MimeTypes.isVideo(mimeType) -> primaryDirectoryLower in videoDirectories
        else -> false
    }
}
