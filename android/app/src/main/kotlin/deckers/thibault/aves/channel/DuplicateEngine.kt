package deckers.thibault.aves.channel

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32

private const val PREFIX_SAMPLE_BYTES = 16 * 1024
private const val MIN_PREFIX_SAMPLE_FILE_BYTES = 1024 * 1024L

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
        val sizeBuckets = uniqueCandidates.groupBy { it.sizeBytes }.values.filter { it.size > 1 }
        val sample = ByteArray(PREFIX_SAMPLE_BYTES)
        val hashCandidates = mutableListOf<DuplicateCandidate>()
        var processed = 0
        for (bucket in sizeBuckets) {
            if (bucket.first().sizeBytes < MIN_PREFIX_SAMPLE_FILE_BYTES) {
                hashCandidates.addAll(bucket)
                continue
            }
            val byPrefix = mutableMapOf<Long, MutableList<DuplicateCandidate>>()
            for (candidate in bucket) {
                currentCoroutineContext().ensureActive()
                onProgress(processed++, 0, "hashing")
                val fingerprint = readCandidate(candidate, issues) { input ->
                    val count = readChunk(input, sample).coerceAtLeast(0)
                    CRC32().apply { update(sample, 0, count) }.value
                } ?: continue
                byPrefix.getOrPut(fingerprint) { mutableListOf() }.add(candidate)
            }
            // A prefix match only qualifies a file for full hashing and byte comparison.
            byPrefix.values.filter { it.size > 1 }.forEach(hashCandidates::addAll)
        }

        val digests = mutableMapOf<DuplicateCandidate, String>()
        val hashingWork = processed + hashCandidates.size
        for (candidate in hashCandidates) {
            val coroutineContext = currentCoroutineContext()
            coroutineContext.ensureActive()
            onProgress(processed++, hashingWork, "hashing")
            val digest = readCandidate(candidate, issues) { input ->
                digester.digest(input) { coroutineContext.ensureActive() }
            } ?: continue
            digests[candidate] = digest
        }

        val groups = mutableListOf<DuplicateGroup>()
        val digestBuckets = digests.entries.groupBy { it.key.sizeBytes to it.value }.values.filter { it.size > 1 }
        for (bucket in digestBuckets) {
            val pending = bucket.map { it.key }.toMutableList()
            while (pending.size > 1) {
                currentCoroutineContext().ensureActive()
                val representative = pending.removeAt(0)
                val equal = mutableListOf(representative)
                val iterator = pending.iterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    onProgress(processed++, 0, "verifying")
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
        onProgress(processed, processed, "completed")
        return DuplicateScanOutcome(groups, issues)
    }

    private inline fun <T> readCandidate(candidate: DuplicateCandidate, issues: MutableList<DuplicateIssue>, read: (InputStream) -> T): T? = try {
        source.open(candidate).use(read)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        issues += DuplicateIssue(candidate, "read-failed", e.message ?: e.javaClass.simpleName)
        null
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
        val issues = candidates
            .filter { it.sizeBytes != expectedSizeBytes }
            .mapTo(mutableListOf()) { DuplicateIssue(it, "changed", "媒体大小已变化") }
        if (issues.isNotEmpty()) return issues
        val keeper = candidates.first()
        for (candidate in candidates.drop(1)) {
            try {
                val comparison = compareAndDigest(keeper, candidate)
                val keeperChanged = comparison.firstDigest != expectedDigest
                val candidateChanged = comparison.secondDigest != expectedDigest
                if (keeperChanged && issues.none { it.candidate == keeper && it.code == "changed" }) {
                    issues += DuplicateIssue(keeper, "changed", "媒体内容已变化")
                }
                if (candidateChanged) issues += DuplicateIssue(candidate, "changed", "媒体内容已变化")
                if (!keeperChanged && !candidateChanged && !comparison.equal) {
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

    private data class ContentComparison(
        val equal: Boolean,
        val firstDigest: String,
        val secondDigest: String,
    )

    private suspend fun compareAndDigest(first: DuplicateCandidate, second: DuplicateCandidate): ContentComparison {
        source.open(first).use { left ->
            source.open(second).use { right ->
                val firstDigest = MessageDigest.getInstance("SHA-256")
                val secondDigest = MessageDigest.getInstance("SHA-256")
                val leftBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
                val rightBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var equal = true
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val leftCount = readChunk(left, leftBuffer)
                    val rightCount = readChunk(right, rightBuffer)
                    if (leftCount > 0) firstDigest.update(leftBuffer, 0, leftCount)
                    if (rightCount > 0) secondDigest.update(rightBuffer, 0, rightCount)
                    if (leftCount != rightCount) equal = false
                    val sharedCount = minOf(leftCount.coerceAtLeast(0), rightCount.coerceAtLeast(0))
                    for (i in 0 until sharedCount) {
                        if (leftBuffer[i] != rightBuffer[i]) equal = false
                    }
                    if (leftCount < 0 && rightCount < 0) break
                }
                return ContentComparison(
                    equal = equal,
                    firstDigest = firstDigest.digest().toHex(),
                    secondDigest = secondDigest.digest().toHex(),
                )
            }
        }
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

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
