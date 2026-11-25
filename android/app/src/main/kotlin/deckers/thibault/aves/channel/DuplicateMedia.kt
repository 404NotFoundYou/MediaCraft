package deckers.thibault.aves.channel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.bumptech.glide.Glide
import deckers.thibault.aves.glide.AvesAppGlideModule
import deckers.thibault.aves.model.ContentAddress
import deckers.thibault.aves.storage.StorageUtils
import deckers.thibault.aves.utils.BitmapUtils.applyExifOrientation
import deckers.thibault.aves.utils.MimeTypes.needRotationAfterGlide

internal class DuplicateMedia(context: Context) {
    val engine = DuplicateEngine(source = DuplicateInputSource { candidate ->
        context.contentResolver.openInputStream(Uri.parse(candidate.uri))
            ?: throw IllegalStateException("无法打开媒体")
    })
    val similarEngine = SimilarEngine(
        imageLoader = SimilarImageLoader { candidate -> loadImage(context, candidate) },
        videoLoader = SimilarVideoLoader { candidate ->
            StorageUtils.openMetadataRetriever(
                context,
                ContentAddress(
                    mimeType = candidate.mimeType ?: "video/*",
                    uri = Uri.parse(candidate.uri),
                    path = candidate.path,
                    pageId = candidate.pageId,
                ),
            ) ?: throw IllegalStateException("无法打开视频")
        },
    )
    private fun loadImage(context: Context, candidate: DuplicateCandidate): Bitmap {
        val address = ContentAddress(
            mimeType = candidate.mimeType ?: "image/*",
            uri = Uri.parse(candidate.uri),
            path = candidate.path,
            pageId = candidate.pageId,
        )
        val target = Glide.with(context)
            .asBitmap()
            .apply(AvesAppGlideModule.uncachedFullImageOptions)
            .load(AvesAppGlideModule.getModel(context, address, sizeBytes = candidate.sizeBytes))
            .submit(64, 64)
        return try {
            var bitmap = target.get()?.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IllegalStateException("无法读取图片")
            if (needRotationAfterGlide(candidate.mimeType ?: "", candidate.pageId)) {
                bitmap = applyExifOrientation(context, bitmap, candidate.rotationDegrees, candidate.isFlipped)
                    ?: throw IllegalStateException("无法调整图片方向")
            }
            bitmap
        } finally {
            Glide.with(context).clear(target)
        }
    }

}

internal fun parseCandidate(map: Map<*, *>): DuplicateCandidate =
    DuplicateCandidate(
        entryId = (map["entryId"] as Number).toLong(),
        uri = map["uri"] as String,
        sizeBytes = (map["sizeBytes"] as Number).toLong(),
        dateModifiedMillis = (map["dateModifiedMillis"] as Number?)?.toLong(),
        mimeType = map["mimeType"] as String?,
        path = map["path"] as String?,
        pageId = (map["pageId"] as Number?)?.toInt(),
        width = (map["width"] as Number?)?.toInt() ?: 0,
        height = (map["height"] as Number?)?.toInt() ?: 0,
        durationMillis = (map["durationMillis"] as Number?)?.toLong(),
        rotationDegrees = (map["rotationDegrees"] as Number?)?.toInt() ?: 0,
        isFlipped = map["isFlipped"] as Boolean? ?: false,
        isVideo = map["isVideo"] as Boolean? ?: (map["mimeType"] as String?)?.startsWith("video") == true,
    )

internal fun parseFingerprint(map: Map<*, *>): SimilarFingerprint {
    val candidate = parseCandidate(map).copy(isVideo = map["kind"] == "video")
    return SimilarFingerprint(
        candidate = candidate,
        algorithmVersion = (map["algorithmVersion"] as Number).toInt(),
        kind = map["kind"] as String,
        imagePHash = map["imagePHash"] as String?,
        imageDHash = map["imageDHash"] as String?,
        clipPHashes = (map["clipPHashes"] as List<*>?).orEmpty().map { it as String },
        clipDHashes = (map["clipDHashes"] as List<*>?).orEmpty().map { it as String },
        videoPHashes = (map["videoPHashes"] as List<*>? ?: emptyList<Any>()).map { it as String },
        videoDHashes = (map["videoDHashes"] as List<*>? ?: emptyList<Any>()).map { it as String },
    )
}

internal fun DuplicateScanOutcome.toMap(cancelled: Boolean): Map<String, Any?> = mapOf(
    "groups" to groups.mapIndexed { index, group ->
        mapOf(
            "id" to "${group.digest.take(16)}-${group.sizeBytes}-$index",
            "sizeBytes" to group.sizeBytes,
            "digest" to group.digest,
            "members" to group.members.map { it.toMap() },
        )
    },
    "issues" to issues.map { it.toMap() },
    "cancelled" to cancelled,
)

internal fun SimilarScanOutcome.toMap(cancelled: Boolean): Map<String, Any?> = mapOf(
    "groups" to groups.mapIndexed { index, group ->
        mapOf(
            "id" to "${group.kind}-${group.members.firstOrNull()?.entryId ?: -1}-$index",
            "sizeBytes" to group.members.firstOrNull()?.sizeBytes,
            "digest" to "${group.kind}-${group.members.firstOrNull()?.entryId ?: -1}",
            "kind" to group.kind,
            "highConfidence" to group.highConfidence,
            "similarityScore" to group.score,
            "fragment" to group.fragment,
            "frames" to group.evidence.mapValues { (_, frames) -> frames.map { listOf(it.referenceMillis, it.memberMillis, it.matched) } },
            "matchedFrames" to group.matchedFrames,
            "totalFrames" to group.totalFrames,
            "durationDeltaMillis" to group.durationDeltaMillis,
            "members" to group.members.map { it.toMap() },
        )
    },
    "issues" to issues.map { it.toMap() },
    "fingerprints" to fingerprints.map { it.toMap() },
    "cancelled" to cancelled,
)

internal fun DuplicateCandidate.toMap() = mapOf(
    "entryId" to entryId,
    "uri" to uri,
    "sizeBytes" to sizeBytes,
    "dateModifiedMillis" to dateModifiedMillis,
    "mimeType" to mimeType,
    "path" to path,
    "pageId" to pageId,
    "width" to width,
    "height" to height,
    "durationMillis" to durationMillis,
    "rotationDegrees" to rotationDegrees,
    "isFlipped" to isFlipped,
    "isVideo" to isVideo,
)

internal fun SimilarFingerprint.toMap() = mapOf(
    "entryId" to candidate.entryId,
    "uri" to candidate.uri,
    "sizeBytes" to candidate.sizeBytes,
    "dateModifiedMillis" to candidate.dateModifiedMillis,
    "algorithmVersion" to algorithmVersion,
    "kind" to kind,
    "width" to candidate.width,
    "height" to candidate.height,
    "durationMillis" to candidate.durationMillis,
    "rotationDegrees" to candidate.rotationDegrees,
    "isFlipped" to candidate.isFlipped,
    "mimeType" to candidate.mimeType,
    "pageId" to candidate.pageId,
    "clipPHashes" to clipPHashes,
    "clipDHashes" to clipDHashes,
    "imagePHash" to imagePHash,
    "imageDHash" to imageDHash,
    "videoPHashes" to videoPHashes,
    "videoDHashes" to videoDHashes,
)

internal fun DuplicateIssue.toMap() = mapOf(
    "entryId" to candidate.entryId,
    "uri" to candidate.uri,
    "code" to code,
    "message" to message,
)
