package deckers.thibault.aves.channel

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class DuplicateHandler(
    context: Context,
) : MethodChannel.MethodCallHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val app = context.applicationContext
    private val media = DuplicateMedia(app)
    private val scans = DuplicateScanFiles(app)

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "verifySelection" -> verifySelection(call, result)
            "verifySimilarSelection" -> verifySimilarSelection(call, result)
            "scan", "scanSimilar", "scanStatus", "scanHistory", "cancel", "samplePreview" -> scope.launch {
                try {
                    val value: Any? = when (call.method) {
                        "scan", "scanSimilar" -> scans.enqueue(call.method, call.arguments as Map<*, *>)
                        "scanStatus" -> scans.status(call.argument<String>("id")!!)
                        "scanHistory" -> scans.history()
                        "samplePreview" -> media.similarEngine.preview(parseCandidate(call.argument<Map<*, *>>("member")!!), call.argument<Number>("timeMillis")!!.toLong())
                        else -> { scans.cancel(call.argument<String>("id")!!); null }
                    }
                    replySuccess(result, value)
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    replyError(result, "scan-failed", e.message ?: e.javaClass.simpleName)
                }
            }
            else -> result.notImplemented()
        }
    }

    private fun parseCandidates(call: MethodCall) = call.argument<List<Map<*, *>>>("members").orEmpty().map(::parseCandidate)

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
                val issues = media.engine.verify(candidates, expectedSizeBytes, expectedDigest)
                replySuccess(result, mapOf("valid" to issues.isEmpty(), "issues" to issues.map { it.toMap() }))
            } catch (e: Exception) {
                replyError(result, "verify-failed", e.message ?: e.javaClass.simpleName)
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
                val issues = media.similarEngine.verify(
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

    private fun replySuccess(result: MethodChannel.Result, value: Any?) = mainHandler.post { result.success(value) }

    private fun replyError(result: MethodChannel.Result, code: String, message: String) = mainHandler.post { result.error(code, message, null) }

    fun dispose() {
        scope.cancel()
    }

    companion object {
        const val CHANNEL = "deckers.thibault/aves/duplicates"
    }
}

internal class DuplicateScanProgressThrottle(private val intervalMillis: Long = 100, private val nowMillis: () -> Long) {
    private var lastStage: String? = null
    private var lastEmissionMillis = 0L

    fun shouldEmit(stage: String): Boolean {
        val now = nowMillis()
        if (stage == lastStage && now - lastEmissionMillis < intervalMillis) return false
        lastStage = stage
        lastEmissionMillis = now
        return true
    }
}
