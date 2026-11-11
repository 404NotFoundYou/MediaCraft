package com.mediacraft.app.domain

import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DuplicateScanner(
    private val source: MediaSource,
) {
    private val qualityRecommender = QualityRecommender()
    suspend fun scan(
        scope: ScanScope,
        onProgress: (ScanProgress) -> Unit = {},
    ): ScanResult = withContext(Dispatchers.IO) {
        val listing = source.list(scope)
        val issues = listing.issues.toMutableList()
        val items = listing.items
            .asSequence()
            .filter { (it.kind == MediaKind.PHOTO && scope.photos) || (it.kind == MediaKind.VIDEO && scope.videos) }
            .filter { scope.album == null || it.relativePath == scope.album }
            .distinctBy { it.id }
            .sortedBy { it.id }
            .toList()

        val exactGroups = findExactGroups(items, issues, onProgress)
        val exactIds = exactGroups.flatMapTo(mutableSetOf()) { group -> group.items.map { it.id } }
        val exactRepresentatives = exactGroups.mapNotNull { group ->
            val representativeId = group.recommendedId ?: group.items.minByOrNull { it.id }?.id
            group.items.firstOrNull { it.id == representativeId }
        }
        val versionCandidates = (items.filterNot { it.id in exactIds } + exactRepresentatives)
            .distinctBy { it.id }
            .sortedBy { it.id }
        val versionGroups = findVersionGroups(versionCandidates, issues, onProgress)
        onProgress(ScanProgress("完成", items.size, items.size, issues.size))
        ScanResult(exactGroups + versionGroups, items, issues)
    }

    private suspend fun findExactGroups(
        items: List<MediaItem>,
        issues: MutableList<MediaIssue>,
        onProgress: (ScanProgress) -> Unit,
    ): List<DuplicateGroup> {
        val candidates = items.groupBy { it.size }.values.filter { it.size > 1 }.flatten()
        val itemsById = items.associateBy { it.id }
        val digests = mutableMapOf<String, String>()
        candidates.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            onProgress(ScanProgress("计算内容摘要", index, candidates.size, issues.size))
            try {
                digests[item.id] = digest(source.open(item))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                issues += MediaIssue(item.id, "读取失败：${error.message ?: error::class.simpleName}")
            }
        }

        val result = mutableListOf<DuplicateGroup>()
        digests.entries.groupBy { (id, digest) -> itemsById.getValue(id).size to digest }
            .values
            .filter { it.size > 1 }
            .forEach { sameDigest ->
                val pending = sameDigest.map { entry -> itemsById.getValue(entry.key) }.toMutableList()
                while (pending.size > 1) {
                    val representative = pending.removeAt(0)
                    val equal = mutableListOf(representative)
                    val iterator = pending.iterator()
                    while (iterator.hasNext()) {
                        val candidate = iterator.next()
                        currentCoroutineContext().ensureActive()
                        val matches = try {
                            streamsEqual(representative, candidate)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            issues += MediaIssue(candidate.id, "字节复核失败：${error.message ?: error::class.simpleName}")
                            false
                        }
                        if (matches) {
                            equal += candidate
                            iterator.remove()
                        }
                    }
                    if (equal.size > 1) result += exactGroup(equal)
                }
            }
        return result.sortedBy { it.id }
    }

    private suspend fun findVersionGroups(
        items: List<MediaItem>,
        issues: MutableList<MediaIssue>,
        onProgress: (ScanProgress) -> Unit,
    ): List<DuplicateGroup> {
        val fingerprints = mutableMapOf<String, ContentFingerprint>()
        items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            onProgress(ScanProgress("生成版本候选", index, items.size, issues.size))
            try {
                val fingerprint = source.fingerprint(item)
                if (fingerprint == null) {
                    issues += MediaIssue(item.id, "无法提取内容特征或该媒体不受支持，已跳过版本候选")
                } else {
                    fingerprints[item.id] = fingerprint
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                issues += MediaIssue(item.id, "候选特征读取失败：${error.message ?: error::class.simpleName}")
            }
        }

        return MediaKind.entries.flatMap { kind ->
            val remaining = items.filter { it.kind == kind && it.id in fingerprints }.toMutableList()
            val groups = mutableListOf<DuplicateGroup>()
            while (remaining.size > 1) {
                val seed = remaining.removeAt(0)
                val group = mutableListOf(seed)
                val iterator = remaining.iterator()
                while (iterator.hasNext()) {
                    currentCoroutineContext().ensureActive()
                    val candidate = iterator.next()
                    if (group.all { member -> versionsMatch(member, candidate, fingerprints) }) {
                        group += candidate
                        iterator.remove()
                    }
                }
                if (group.size > 1) groups += versionGroup(kind, group, fingerprints)
            }
            groups
        }.sortedBy { it.id }
    }

    private fun versionsMatch(
        first: MediaItem,
        second: MediaItem,
        fingerprints: Map<String, ContentFingerprint>,
    ): Boolean {
        if (first.kind != second.kind || !similarAspect(first, second)) return false
        val left = fingerprints[first.id] ?: return false
        val right = fingerprints[second.id] ?: return false
        return when (first.kind) {
            MediaKind.PHOTO -> photoFingerprintsMatch(left, right)
            MediaKind.VIDEO -> videoFingerprintsMatch(first, second, left, right)
        }
    }

    private fun photoFingerprintsMatch(left: ContentFingerprint, right: ContentFingerprint): Boolean {
        if (left.hasMotion || right.hasMotion || left.frames.size != 1 || right.frames.size != 1) return false
        return framesMatch(left.frames.single(), right.frames.single(), maxHamming = 8, maxLuminanceDelta = 12.0)
    }

    private fun videoFingerprintsMatch(
        first: MediaItem,
        second: MediaItem,
        left: ContentFingerprint,
        right: ContentFingerprint,
    ): Boolean {
        if (!left.hasMotion || !right.hasMotion || left.frames.size < 3 || left.frames.size != right.frames.size) return false
        if (first.durationMillis <= 0 || second.durationMillis <= 0) return false
        val durationTolerance = min(500L, max(100L, max(first.durationMillis, second.durationMillis) / 200L))
        if (abs(first.durationMillis - second.durationMillis) > durationTolerance) return false
        val leftAudio = first.video?.audioSignature
        val rightAudio = second.video?.audioSignature
        if (leftAudio != null && rightAudio != null && leftAudio != rightAudio) return false
        return left.frames.zip(right.frames).all { (a, b) ->
            framesMatch(a, b, maxHamming = 10, maxLuminanceDelta = 14.0)
        }
    }

    private fun framesMatch(
        left: FrameFingerprint,
        right: FrameFingerprint,
        maxHamming: Int,
        maxLuminanceDelta: Double,
    ): Boolean {
        if (java.lang.Long.bitCount(left.hash xor right.hash) > maxHamming) return false
        if (left.luminance.isEmpty() || left.luminance.size != right.luminance.size) return false
        return left.luminance.zip(right.luminance).sumOf { (a, b) -> abs(a - b).toLong() }.toDouble() /
            left.luminance.size <= maxLuminanceDelta
    }

    private fun similarAspect(first: MediaItem, second: MediaItem): Boolean {
        if (first.width <= 0 || first.height <= 0 || second.width <= 0 || second.height <= 0) return false
        val firstRatio = first.width.toDouble() / first.height
        val secondRatio = second.width.toDouble() / second.height
        return abs(firstRatio - secondRatio) <= 0.01
    }

    private fun exactGroup(items: List<MediaItem>): DuplicateGroup {
        val recommendation = qualityRecommender.recommend(DuplicateKind.EXACT, items)
        return DuplicateGroup(
            id = groupId("exact", items),
            kind = DuplicateKind.EXACT,
            items = items.sortedBy { it.id },
            recommendedId = recommendation.mediaId,
            reason = recommendation.reason,
            warnings = recommendation.warnings,
        )
    }

    private fun versionGroup(
        mediaKind: MediaKind,
        items: List<MediaItem>,
        fingerprints: Map<String, ContentFingerprint>,
    ): DuplicateGroup {
        val kind = if (mediaKind == MediaKind.PHOTO) DuplicateKind.PHOTO_VERSION else DuplicateKind.VIDEO_VERSION
        val recommendation = qualityRecommender.recommend(kind, items, fingerprints)
        return DuplicateGroup(
            id = groupId(kind.name.lowercase(), items),
            kind = kind,
            items = items.sortedBy { it.id },
            recommendedId = recommendation.mediaId,
            reason = recommendation.reason,
            warnings = listOf("疑似同内容候选，必须人工核对内容范围后才能处理") + recommendation.warnings,
        )
    }

    private fun groupId(prefix: String, items: List<MediaItem>): String {
        val value = items.map { it.id }.sorted().joinToString("\u0000")
        val suffix = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).take(8)
            .joinToString("") { "%02x".format(it) }
        return "$prefix-$suffix"
    }

    private suspend fun digest(stream: InputStream): String = stream.use {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = it.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private suspend fun streamsEqual(left: MediaItem, right: MediaItem): Boolean = source.open(left).use { a ->
        source.open(right).use inner@{ b ->
            val leftBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val rightBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val leftCount = a.readFullyAvailable(leftBuffer)
                val rightCount = b.readFullyAvailable(rightBuffer)
                if (leftCount != rightCount) return@inner false
                if (leftCount < 0) return@inner true
                for (index in 0 until leftCount) if (leftBuffer[index] != rightBuffer[index]) return@inner false
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }
    }

    private fun InputStream.readFullyAvailable(buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val count = read(buffer, offset, buffer.size - offset)
            if (count < 0) return if (offset == 0) -1 else offset
            if (count == 0) break
            offset += count
        }
        return offset
    }
}
