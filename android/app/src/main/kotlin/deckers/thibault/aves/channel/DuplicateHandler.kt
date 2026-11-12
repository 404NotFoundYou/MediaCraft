package deckers.thibault.aves.channel

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
    @Volatile
    private var scanJob: Job? = null

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "scan" -> startScan(call, result)
            "cancel" -> {
                scanJob?.cancel()
                result.success(null)
            }
            "verifySelection" -> verifySelection(call, result)
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
                val outcome = engine.scan(candidates) { processed, total, stage ->
                    mainHandler.post {
                        channel.invokeMethod("scanProgress", mapOf("processed" to processed, "total" to total, "stage" to stage))
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

    private fun parseCandidates(call: MethodCall): List<DuplicateCandidate> {
        val maps = call.argument<List<Map<String, Any?>>>("members") ?: emptyList()
        return maps.map { map ->
            DuplicateCandidate(
                entryId = (map["entryId"] as Number).toLong(),
                uri = map["uri"] as String,
                sizeBytes = (map["sizeBytes"] as Number).toLong(),
                dateModifiedMillis = (map["dateModifiedMillis"] as Number?)?.toLong(),
            )
        }
    }

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

    private fun DuplicateCandidate.toMap() = mapOf(
        "entryId" to entryId,
        "uri" to uri,
        "sizeBytes" to sizeBytes,
        "dateModifiedMillis" to dateModifiedMillis,
    )

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
