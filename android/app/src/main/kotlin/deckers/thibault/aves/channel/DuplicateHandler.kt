package deckers.thibault.aves.channel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.bumptech.glide.Glide
import deckers.thibault.aves.glide.AvesAppGlideModule
import deckers.thibault.aves.model.ContentAddress
import deckers.thibault.aves.storage.StorageUtils
import deckers.thibault.aves.utils.BitmapUtils.applyExifOrientation
import deckers.thibault.aves.utils.MimeTypes.needRotationAfterGlide
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class DuplicateHandler(
    context: Context,
    private val channel: MethodChannel,
) : MethodChannel.MethodCallHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val engine = DuplicateEngine(source = DuplicateInputSource { candidate ->
        context.contentResolver.openInputStream(Uri.parse(candidate.uri))
            ?: throw IllegalStateException("无法打开媒体")
    })
    private val similarEngine = SimilarEngine(
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
    @Volatile
    private var scanJob: Job? = null

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "scan" -> startScan(call, result)
            "scanSimilar" -> startSimilarScan(call, result)
            "cancel" -> {
                scanJob?.cancel()
                result.success(null)
            }
            "verifySelection" -> verifySelection(call, result)
            "verifySimilarSelection" -> verifySimilarSelection(call, result)
            else -> result.notImplemented()
        }
    }

    private fun startScan(call: MethodCall, result: MethodChannel.Result) {
        if (scanJob?.isActive == true) {
            result.error("scan-in-progress", "重复扫描正在进行", null)
            return
        }
        val candidates = try {
            parseCandidates(call)
        } catch (e: Exception) {
            result.error("invalid-arguments", e.message ?: "媒体参数无效", null)
            return
        }
        scanJob = scope.launch {
            try {
                val progressThrottle = DuplicateScanProgressThrottle(SystemClock::elapsedRealtime)
                val outcome = engine.scan(candidates) { processed, total, stage ->
                    if (progressThrottle.shouldEmit(stage)) {
                        mainHandler.post {
                            channel.invokeMethod("scanProgress", mapOf("processed" to processed, "total" to total, "stage" to stage))
                        }
                    }
                }
                replySuccess(result, outcome.toMap(cancelled = false))
            } catch (_: CancellationException) {
                replySuccess(result, mapOf("groups" to emptyList<Any>(), "issues" to emptyList<Any>(), "cancelled" to true))
            } catch (e: Exception) {
                replyError(result, "scan-failed", e.message ?: e.javaClass.simpleName)
            } finally {
                scanJob = null
            }
        }
    }

    private fun verifySelection(call: MethodCall, result: MethodChannel.Result) {
        val candidates = try {
            parseCandidates(call)
        } catch (e: Exception) {
            result.error("invalid-arguments", e.message ?: "媒体参数无效", null)
            return
        }
        val expectedSizeBytes = call.argument<Number>("expectedSizeBytes")?.toLong()
        val expectedDigest = call.argument<String>("expectedDigest")
        if (expectedSizeBytes == null || expectedDigest == null) {
            result.error("invalid-arguments", "缺少预期大小或摘要", null)
            return
        }
        scope.launch {
            try {
                val issues = engine.verify(candidates, expectedSizeBytes, expectedDigest)
                replySuccess(result, mapOf("valid" to issues.isEmpty(), "issues" to issues.map { it.toMap() }))
            } catch (e: Exception) {
                replyError(result, "verify-failed", e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun startSimilarScan(call: MethodCall, result: MethodChannel.Result) {
        if (scanJob?.isActive == true) {
            result.error("scan-in-progress", "相似媒体扫描正在进行", null)
            return
        }
        val candidates = try {
            parseCandidates(call)
        } catch (e: Exception) {
            result.error("invalid-arguments", e.message ?: "媒体参数无效", null)
            return
        }
        val cached = try {
            parseCachedFingerprints(call)
        } catch (e: Exception) {
            result.error("invalid-arguments", e.message ?: "相似媒体缓存无效", null)
            return
        }
        scanJob = scope.launch {
            try {
                val progressThrottle = DuplicateScanProgressThrottle(SystemClock::elapsedRealtime)
                val exactOutcome = engine.scan(candidates) { _, _, _ -> }
                val exactEntryIds = exactOutcome.groups
                    .asSequence()
                    .flatMap { it.members.asSequence() }
                    .map { it.entryId }
                    .toSet()
                val similarOutcome = similarEngine.scan(candidates, cached, exactEntryIds) { processed, total, stage ->
                    if (progressThrottle.shouldEmit(stage)) {
                        mainHandler.post {
                            channel.invokeMethod("scanProgress", mapOf("processed" to processed, "total" to total, "stage" to stage))
                        }
                    }
                }
                val outcome = similarOutcome.copy(issues = exactOutcome.issues + similarOutcome.issues)
                replySuccess(result, outcome.toMap(cancelled = false))
            } catch (_: CancellationException) {
                replySuccess(result, mapOf("groups" to emptyList<Any>(), "issues" to emptyList<Any>(), "fingerprints" to emptyList<Any>(), "cancelled" to true))
            } catch (e: Exception) {
                replyError(result, "scan-failed", e.message ?: e.javaClass.simpleName)
            } finally {
                scanJob = null
            }
        }
    }

    private fun verifySimilarSelection(call: MethodCall, result: MethodChannel.Result) {
        val candidates = try {
            parseCandidates(call)
        } catch (e: Exception) {
            result.error("invalid-arguments", e.message ?: "媒体参数无效", null)
            return
        }
        val expectedKind = call.argument<String>("expectedKind")
        val expectedHighConfidence = call.argument<Boolean>("expectedHighConfidence") ?: false
        val expectedScore = call.argument<Number>("expectedScore")?.toDouble()
        val expectedMatchedFrames = call.argument<Number>("expectedMatchedFrames")?.toInt()
        val expectedTotalFrames = call.argument<Number>("expectedTotalFrames")?.toInt()
        if (expectedKind == null || expectedScore == null) {
            result.error("invalid-arguments", "缺少预期相似度信息", null)
            return
        }
        scope.launch {
            try {
                val issues = similarEngine.verify(
                    candidates = candidates,
                    expectedKind = expectedKind,
                    expectedHighConfidence = expectedHighConfidence,
                    expectedScore = expectedScore,
                    expectedMatchedFrames = expectedMatchedFrames,
                    expectedTotalFrames = expectedTotalFrames,
                )
                replySuccess(result, mapOf("valid" to issues.isEmpty(), "issues" to issues.map { it.toMap() }))
            } catch (e: Exception) {
                replyError(result, "verify-failed", e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun parseCandidates(call: MethodCall): List<DuplicateCandidate> {
        val maps = call.argument<List<Map<String, Any?>>>("members") ?: emptyList()
        return maps.map { map ->
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
        }
    }

    private fun parseCachedFingerprints(call: MethodCall): Map<Long, SimilarFingerprint> =
        (call.argument<List<Map<String, Any?>>>("cachedFingerprints") ?: emptyList())
            .map { map ->
                val candidate = DuplicateCandidate(
                    entryId = (map["entryId"] as Number).toLong(),
                    uri = map["uri"] as String,
                    sizeBytes = (map["sizeBytes"] as Number).toLong(),
                    dateModifiedMillis = (map["dateModifiedMillis"] as Number?)?.toLong(),
                    width = (map["width"] as Number?)?.toInt() ?: 0,
                    height = (map["height"] as Number?)?.toInt() ?: 0,
                    durationMillis = (map["durationMillis"] as Number?)?.toLong(),
                    isVideo = map["kind"] as String == "video",
                )
                SimilarFingerprint(
                    candidate = candidate,
                    algorithmVersion = (map["algorithmVersion"] as Number).toInt(),
                    kind = map["kind"] as String,
                    imagePHash = map["imagePHash"] as String?,
                    imageDHash = map["imageDHash"] as String?,
                    videoPHashes = (map["videoPHashes"] as List<*>? ?: emptyList<Any>()).map { it as String },
                    videoDHashes = (map["videoDHashes"] as List<*>? ?: emptyList<Any>()).map { it as String },
                )
            }
            .associateBy { it.candidate.entryId }

    private fun DuplicateScanOutcome.toMap(cancelled: Boolean) = mapOf(
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

    private fun SimilarScanOutcome.toMap(cancelled: Boolean) = mapOf(
        "groups" to groups.mapIndexed { index, group ->
            mapOf(
                "id" to "${group.kind}-${group.members.firstOrNull()?.entryId ?: -1}-$index",
                "sizeBytes" to group.members.firstOrNull()?.sizeBytes,
                "digest" to "${group.kind}-${group.members.firstOrNull()?.entryId ?: -1}",
                "kind" to group.kind,
                "highConfidence" to group.highConfidence,
                "similarityScore" to group.score,
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

    private fun DuplicateCandidate.toMap() = mapOf(
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

    private fun SimilarFingerprint.toMap() = mapOf(
        "entryId" to candidate.entryId,
        "uri" to candidate.uri,
        "sizeBytes" to candidate.sizeBytes,
        "dateModifiedMillis" to candidate.dateModifiedMillis,
        "algorithmVersion" to algorithmVersion,
        "kind" to kind,
        "width" to candidate.width,
        "height" to candidate.height,
        "durationMillis" to candidate.durationMillis,
        "imagePHash" to imagePHash,
        "imageDHash" to imageDHash,
        "videoPHashes" to videoPHashes,
        "videoDHashes" to videoDHashes,
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

    private fun DuplicateIssue.toMap() = mapOf(
        "entryId" to candidate.entryId,
        "uri" to candidate.uri,
        "code" to code,
        "message" to message,
    )

    private fun replySuccess(result: MethodChannel.Result, value: Any?) = mainHandler.post { result.success(value) }

    private fun replyError(result: MethodChannel.Result, code: String, message: String) = mainHandler.post { result.error(code, message, null) }

    fun dispose() {
        scope.cancel()
    }

    companion object {
        const val CHANNEL = "deckers.thibault/aves/duplicates"
    }
}

internal class DuplicateScanProgressThrottle(private val nowMillis: () -> Long) {
    private var lastStage: String? = null
    private var lastEmissionMillis = 0L

    fun shouldEmit(stage: String): Boolean {
        val now = nowMillis()
        if (stage == lastStage && now - lastEmissionMillis < 100L) return false
        lastStage = stage
        lastEmissionMillis = now
        return true
    }
}
