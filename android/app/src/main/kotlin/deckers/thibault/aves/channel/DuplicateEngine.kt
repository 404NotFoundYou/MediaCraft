package deckers.thibault.aves.channel

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.security.MessageDigest

internal data class DuplicateCandidate(
    val entryId: Long,
    val uri: String,
    val sizeBytes: Long,
    val dateModifiedMillis: Long?,
)

internal data class DuplicateIssue(
    val candidate: DuplicateCandidate,
    val code: String,
    val message: String,
)

internal data class DuplicateGroup(
    val sizeBytes: Long,
    val digest: String,
    val members: List<DuplicateCandidate>,
)

internal data class DuplicateScanOutcome(
    val groups: List<DuplicateGroup>,
    val issues: List<DuplicateIssue>,
)

internal fun interface DuplicateInputSource {
    fun open(candidate: DuplicateCandidate): InputStream
}

internal fun interface DuplicateDigester {
    fun digest(input: InputStream, checkCancelled: () -> Unit): String
}

internal class Sha256Digester : DuplicateDigester {
    override fun digest(input: InputStream, checkCancelled: () -> Unit): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            checkCancelled()
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) md.update(buffer, 0, count)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

internal class DuplicateEngine(
    private val source: DuplicateInputSource,
    private val digester: DuplicateDigester = Sha256Digester(),
) {
    suspend fun scan(
        candidates: List<DuplicateCandidate>,
        onProgress: (processed: Int, total: Int, stage: String) -> Unit,
    ): DuplicateScanOutcome {
        val issues = mutableListOf<DuplicateIssue>()
        val seenIds = mutableSetOf<Long>()
        val seenUris = mutableSetOf<String>()
        val uniqueCandidates = candidates.filter { candidate ->
            when {
                candidate.sizeBytes < 0 -> {
                    issues += DuplicateIssue(candidate, "invalid-size", "媒体大小未知")
                    false
                }
                !seenIds.add(candidate.entryId) || !seenUris.add(candidate.uri) -> {
                    issues += DuplicateIssue(candidate, "duplicate-input", "同一媒体被重复提交")
                    false
                }
                else -> true
            }
        }
        val sizeCandidates = uniqueCandidates
            .groupBy { it.sizeBytes }
            .values
            .filter { it.size > 1 }
            .flatten()
        val digests = mutableMapOf<DuplicateCandidate, String>()

        sizeCandidates.forEachIndexed { index, candidate ->
            val coroutineContext = currentCoroutineContext()
            coroutineContext.ensureActive()
            onProgress(index, sizeCandidates.size, "hashing")
            try {
                digests[candidate] = source.open(candidate).use { input ->
                    digester.digest(input) { coroutineContext.ensureActive() }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                issues += DuplicateIssue(candidate, "read-failed", e.message ?: e.javaClass.simpleName)
            }
        }

        val groups = mutableListOf<DuplicateGroup>()
        val digestBuckets = digests.entries.groupBy { it.key.sizeBytes to it.value }.values.filter { it.size > 1 }
        var compared = 0
        val comparisonTotal = digestBuckets.sumOf { bucket -> bucket.size * (bucket.size - 1) / 2 }
        for (bucket in digestBuckets) {
            val pending = bucket.map { it.key }.toMutableList()
            while (pending.size > 1) {
                currentCoroutineContext().ensureActive()
                val representative = pending.removeAt(0)
                val equal = mutableListOf(representative)
                val iterator = pending.iterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    onProgress(compared++, comparisonTotal, "verifying")
                    val matches = try {
                        contentEquals(representative, candidate)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        issues += DuplicateIssue(candidate, "verify-read-failed", e.message ?: e.javaClass.simpleName)
                        false
                    }
                    if (matches) {
                        equal += candidate
                        iterator.remove()
                    }
                }
                if (equal.size > 1) {
                    groups += DuplicateGroup(representative.sizeBytes, digests.getValue(representative), equal)
                }
            }
        }
        onProgress(sizeCandidates.size, sizeCandidates.size, "completed")
        return DuplicateScanOutcome(groups, issues)
    }

    suspend fun verify(
        candidates: List<DuplicateCandidate>,
        expectedSizeBytes: Long,
        expectedDigest: String,
    ): List<DuplicateIssue> {
        if (candidates.size < 2) {
            val candidate = candidates.firstOrNull() ?: DuplicateCandidate(-1, "", -1, null)
            return listOf(DuplicateIssue(candidate, "invalid-selection", "至少需要一个保留项和一个待删除项"))
        }
        if (candidates.map { it.entryId }.toSet().size != candidates.size || candidates.map { it.uri }.toSet().size != candidates.size) {
            return listOf(DuplicateIssue(candidates.first(), "invalid-selection", "保留项和待删除项不能重复"))
        }
        val issues = mutableListOf<DuplicateIssue>()
        for (candidate in candidates) {
            val coroutineContext = currentCoroutineContext()
            coroutineContext.ensureActive()
            if (candidate.sizeBytes != expectedSizeBytes) {
                issues += DuplicateIssue(candidate, "changed", "媒体大小已变化")
                continue
            }
            try {
                val digest = source.open(candidate).use { input ->
                    digester.digest(input) { coroutineContext.ensureActive() }
                }
                if (digest != expectedDigest) issues += DuplicateIssue(candidate, "changed", "媒体内容已变化")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                issues += DuplicateIssue(candidate, "read-failed", e.message ?: e.javaClass.simpleName)
            }
        }
        if (issues.isNotEmpty()) return issues
        val keeper = candidates.first()
        for (candidate in candidates.drop(1)) {
            try {
                if (!contentEquals(keeper, candidate)) {
                    issues += DuplicateIssue(candidate, "content-mismatch", "媒体不再与保留项完全相同")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                issues += DuplicateIssue(candidate, "verify-read-failed", e.message ?: e.javaClass.simpleName)
            }
        }
        return issues
    }

    private suspend fun contentEquals(first: DuplicateCandidate, second: DuplicateCandidate): Boolean {
        source.open(first).use { left ->
            source.open(second).use { right ->
                val leftBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
                val rightBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val leftCount = readChunk(left, leftBuffer)
                    val rightCount = readChunk(right, rightBuffer)
                    if (leftCount != rightCount) return false
                    if (leftCount < 0) return true
                    for (i in 0 until leftCount) {
                        if (leftBuffer[i] != rightBuffer[i]) return false
                    }
                }
            }
        }
    }

    private suspend fun readChunk(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer, total, buffer.size - total)
            if (count < 0) return if (total == 0) -1 else total
            if (count == 0) continue
            total += count
        }
        return total
    }
}
