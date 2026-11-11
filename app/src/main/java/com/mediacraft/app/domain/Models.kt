package com.mediacraft.app.domain

import java.io.InputStream

enum class MediaKind { PHOTO, VIDEO }
enum class DuplicateKind { EXACT, PHOTO_VERSION, VIDEO_VERSION }

data class VideoMetadata(
    val codec: String? = null,
    val bitrate: Long? = null,
    val frameRate: Float? = null,
    val dynamicRange: String? = null,
    val bitDepth: Int? = null,
    val audioSignature: String? = null,
)

data class MediaItem(
    val id: String,
    val displayName: String,
    val kind: MediaKind,
    val mimeType: String,
    val relativePath: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val dateTakenMillis: Long,
    val dateAddedMillis: Long,
    val dateModifiedSeconds: Long,
    val generation: Long = 0,
    val durationMillis: Long = 0,
    val favorite: Boolean = false,
    val video: VideoMetadata? = null,
) {
    val pixels: Long get() = width.toLong() * height
    val directoryKey: String
        get() = normalizedMediaName(id.substringBefore("/images/").substringBefore("/video/")) + "|" +
            normalizedMediaName(relativePath.replace('\\', '/').trim('/'))
}

data class ScanScope(
    val photos: Boolean = true,
    val videos: Boolean = true,
    val album: String? = null,
    /** Persisted SAF tree URI. When set, scanning is limited to this folder tree. */
    val folderUri: String? = null,
)
data class MediaIssue(val mediaId: String?, val message: String)
data class MediaListing(val items: List<MediaItem>, val issues: List<MediaIssue> = emptyList())
data class ScanProgress(val phase: String, val processed: Int, val total: Int, val skipped: Int = 0)

/** Small normalized samples only; callers must never retain decoded full-resolution video. */
data class FrameFingerprint(
    val hash: Long,
    val luminance: List<Int>,
    val sharpness: Double = 0.0,
)
data class ContentFingerprint(val frames: List<FrameFingerprint>, val hasMotion: Boolean = false)

interface MediaSource {
    suspend fun list(scope: ScanScope): MediaListing
    fun open(item: MediaItem): InputStream
    suspend fun fingerprint(item: MediaItem): ContentFingerprint?
}

data class DuplicateGroup(
    val id: String,
    val kind: DuplicateKind,
    val items: List<MediaItem>,
    val recommendedId: String?,
    val reason: String,
    val warnings: List<String> = emptyList(),
)
data class ScanResult(val groups: List<DuplicateGroup>, val items: List<MediaItem>, val issues: List<MediaIssue>)

enum class RenameMode { CUSTOM, RANDOM }
data class RenameOptions(
    val mode: RenameMode = RenameMode.CUSTOM,
    val template: String = "旅行_{date}_{seq:3}",
    val start: Int = 1,
)
data class RenamePlanItem(
    val media: MediaItem,
    val newName: String,
    val error: String? = null,
    val warnings: List<String> = emptyList(),
) {
    val unchanged: Boolean get() = media.displayName == newName
}
data class RenamePlan(val items: List<RenamePlanItem>, val error: String? = null) {
    val canExecute: Boolean get() = error == null && items.isNotEmpty() && items.none { it.error != null } && items.any { !it.unchanged }
}

enum class OperationType { RENAME, TRASH, RESTORE, UNDO_RENAME }
enum class OperationStatus { PENDING, SUCCESS, FAILED, SKIPPED, CANCELLED }
data class OperationSpec(
    val media: MediaItem,
    val targetName: String? = null,
    val keeper: MediaItem? = null,
    val duplicateKind: DuplicateKind? = null,
    val digest: String? = null,
    val validationError: String? = null,
)
data class OperationItemResult(val spec: OperationSpec, val status: OperationStatus, val message: String)
data class OperationRecord(
    val id: String,
    val type: OperationType,
    val createdAtMillis: Long,
    val items: List<OperationItemResult>,
    val sourceId: String? = null,
)
