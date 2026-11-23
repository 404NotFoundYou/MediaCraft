package deckers.thibault.aves.channel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.os.Build
import deckers.thibault.aves.utils.MemoryUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI

private const val HASH_SIZE = 32
private const val HASH_BLOCK_SIZE = 8
// shortcut: 固定七帧采样无法覆盖剪辑片段，需支持片段匹配时升级。
private const val FRAME_COUNT = 7
private const val MAX_FALLBACK_FRAME_PIXELS = 4096L * 2160
private val FRAME_OPTIONS = intArrayOf(MediaMetadataRetriever.OPTION_CLOSEST_SYNC, MediaMetadataRetriever.OPTION_CLOSEST)
private const val ALGORITHM_VERSION = 2
private const val IMAGE_HIGH_PHASH_DISTANCE = 8
private const val IMAGE_HIGH_DHASH_DISTANCE = 10
private const val IMAGE_REVIEW_PHASH_DISTANCE = 12
private const val IMAGE_REVIEW_DHASH_DISTANCE = 14
private const val VIDEO_HIGH_PHASH_DISTANCE = 10
private const val VIDEO_HIGH_DHASH_DISTANCE = 14
private const val VIDEO_REVIEW_PHASH_DISTANCE = 14
private const val VIDEO_REVIEW_DHASH_DISTANCE = 18
private const val ASPECT_RATIO_BUCKET_WIDTH = .025
private const val ASPECT_RATIO_BUCKET_RADIUS = 3

internal fun interface SimilarImageLoader {
    fun load(candidate: DuplicateCandidate): Bitmap
}

internal fun interface SimilarVideoLoader {
    fun open(candidate: DuplicateCandidate): MediaMetadataRetriever
}

internal suspend fun <T : Any> readVideoSample(
    number: Int,
    timeMicros: Long,
    scaled: ((Long, Int) -> T?)?,
    original: (Long, Int) -> T?,
): T {
    var cause: RuntimeException? = null
    for (read in listOfNotNull(scaled, original)) {
        for (option in FRAME_OPTIONS) {
            currentCoroutineContext().ensureActive()
            try {
                read(timeMicros, option)?.let { return it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                throw e
            } catch (e: RuntimeException) {
                cause = e
            }
        }
    }
    throw IllegalStateException("无法读取视频采样帧（第 $number/$FRAME_COUNT 帧，${timeMicros / 1000} 毫秒）", cause)
}

internal data class SimilarFingerprint(
    val candidate: DuplicateCandidate,
    val algorithmVersion: Int,
    val kind: String,
    val imagePHash: String?,
    val imageDHash: String?,
    val videoPHashes: List<String>,
    val videoDHashes: List<String>,
)

internal data class SimilarGroup(
    val kind: String,
    val highConfidence: Boolean,
    val score: Double,
    val matchedFrames: Int?,
    val totalFrames: Int?,
    val durationDeltaMillis: Long?,
    val members: List<DuplicateCandidate>,
)

internal data class SimilarScanOutcome(
    val groups: List<SimilarGroup>,
    val issues: List<DuplicateIssue>,
    val fingerprints: List<SimilarFingerprint>,
)

internal class SimilarEngine(
    private val imageLoader: SimilarImageLoader,
    private val videoLoader: SimilarVideoLoader,
) {
    suspend fun scan(
        candidates: List<DuplicateCandidate>,
        cached: Map<Long, SimilarFingerprint>,
        excludedEntryIds: Set<Long> = emptySet(),
        onProgress: (processed: Int, total: Int, stage: String) -> Unit,
    ): SimilarScanOutcome {
        val issues = mutableListOf<DuplicateIssue>()
        val seenIds = mutableSetOf<Long>()
        val seenUris = mutableSetOf<String>()
        val uniqueCandidates = candidates.filter { candidate ->
            when {
                candidate.entryId in excludedEntryIds -> false
                candidate.sizeBytes < 0 -> {
                    issues += DuplicateIssue(candidate, "invalid-size", "媒体大小未知，无法进行相似检测")
                    false
                }
                !seenIds.add(candidate.entryId) || !seenUris.add(candidate.uri) -> {
                    issues += DuplicateIssue(candidate, "duplicate-input", "同一媒体被重复提交")
                    false
                }
                else -> true
            }
        }

        val fingerprints = mutableListOf<SimilarFingerprint>()
        var processed = 0
        for (candidate in uniqueCandidates) {
            currentCoroutineContext().ensureActive()
            val fingerprint = cached[candidate.entryId]?.takeIf { isReusable(candidate, it) }
                ?.withCandidate(candidate)
                ?: readFingerprint(candidate, issues)
            if (fingerprint != null) fingerprints += fingerprint
            onProgress(++processed, uniqueCandidates.size, "similar-fingerprinting")
        }

        currentCoroutineContext().ensureActive()
        val groups = group(fingerprints)
        onProgress(processed, uniqueCandidates.size, "similar-completed")
        return SimilarScanOutcome(groups, issues, fingerprints)
    }

    suspend fun verify(
        candidates: List<DuplicateCandidate>,
        expectedKind: String,
        expectedHighConfidence: Boolean,
        expectedScore: Double,
        expectedMatchedFrames: Int?,
        expectedTotalFrames: Int?,
    ): List<DuplicateIssue> {
        if (candidates.size < 2) {
            val candidate = candidates.firstOrNull() ?: invalidCandidate()
            return listOf(DuplicateIssue(candidate, "invalid-selection", "至少需要一个保留项和一个待清理项"))
        }
        if (candidates.map { it.entryId }.toSet().size != candidates.size || candidates.map { it.uri }.toSet().size != candidates.size) {
            return listOf(DuplicateIssue(candidates.first(), "invalid-selection", "保留项和待清理项不能重复"))
        }

        val issues = mutableListOf<DuplicateIssue>()
        val fingerprints = mutableListOf<SimilarFingerprint>()
        for (candidate in candidates) {
            currentCoroutineContext().ensureActive()
            readFingerprint(candidate, issues)?.let(fingerprints::add)
        }
        if (issues.isNotEmpty()) return issues
        if (fingerprints.size != candidates.size || fingerprints.any { it.kind != expectedKind }) {
            return listOf(DuplicateIssue(candidates.first(), "kind-changed", "媒体类型已变化，请重新扫描"))
        }

        val representative = fingerprints.first()
        for (candidate in fingerprints.drop(1)) {
            val match = compare(representative, candidate) ?: return listOf(
                DuplicateIssue(candidate.candidate, "content-mismatch", "媒体不再满足本次相似分组条件"),
            )
            if (expectedHighConfidence && !match.highConfidence) {
                return listOf(DuplicateIssue(candidate.candidate, "similarity-decreased", "媒体相似度已降低，请重新扫描"))
            }
            if (match.score + 0.05 < expectedScore) {
                return listOf(DuplicateIssue(candidate.candidate, "similarity-decreased", "媒体相似度已降低，请重新扫描"))
            }
            if (expectedMatchedFrames != null && (match.matchedFrames ?: 0) < expectedMatchedFrames) {
                return listOf(DuplicateIssue(candidate.candidate, "frame-match-changed", "视频匹配帧数已变化，请重新扫描"))
            }
            if (expectedTotalFrames != null && match.totalFrames != expectedTotalFrames) {
                return listOf(DuplicateIssue(candidate.candidate, "frame-count-changed", "视频采样结果已变化，请重新扫描"))
            }
        }
        return emptyList()
    }

    private suspend fun readFingerprint(candidate: DuplicateCandidate, issues: MutableList<DuplicateIssue>): SimilarFingerprint? = try {
        if (candidate.isVideo) readVideoFingerprint(candidate) else readImageFingerprint(candidate)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        issues += DuplicateIssue(candidate, "similar-read-failed", e.message ?: e.javaClass.simpleName)
        null
    }

    private fun readImageFingerprint(candidate: DuplicateCandidate): SimilarFingerprint {
        val source = imageLoader.load(candidate)
        return try {
            val (pHash, dHash) = hash(source)
            SimilarFingerprint(
                candidate = candidate,
                algorithmVersion = ALGORITHM_VERSION,
                kind = "image",
                imagePHash = pHash.toHexHash(),
                imageDHash = dHash.toHexHash(),
                videoPHashes = emptyList(),
                videoDHashes = emptyList(),
            )
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    private suspend fun readVideoFingerprint(candidate: DuplicateCandidate): SimilarFingerprint {
        val retriever = videoLoader.open(candidate)
        return try {
            val durationMillis = candidate.durationMillis?.takeIf { it > 0 }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (durationMillis == null || durationMillis <= 0) throw IllegalStateException("无法读取视频时长")
            val scaled: ((Long, Int) -> Bitmap?)? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                { time, option -> retriever.getScaledFrameAtTime(time, option, 64, 64) }
            } else null
            val frameHashes = mutableListOf<Pair<Long, Long>>()
            for ((index, point) in SAMPLE_POINTS.withIndex()) {
                val timeMicros = (durationMillis * point * 1000.0).toLong()
                val frame = readVideoSample(index + 1, timeMicros, scaled) { time, option ->
                    if (scaled == null || canReadOriginalFrame(retriever)) retriever.getFrameAtTime(time, option) else null
                }
                try {
                    frameHashes += hash(frame)
                } finally {
                    if (!frame.isRecycled) frame.recycle()
                }
            }
            SimilarFingerprint(
                candidate = candidate.copy(durationMillis = durationMillis),
                algorithmVersion = ALGORITHM_VERSION,
                kind = "video",
                imagePHash = null,
                imageDHash = null,
                videoPHashes = frameHashes.map { it.first.toHexHash() },
                videoDHashes = frameHashes.map { it.second.toHexHash() },
            )
        } finally {
            retriever.release()
        }
    }

    private fun canReadOriginalFrame(retriever: MediaMetadataRetriever): Boolean {
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return false
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return false
        val pixels = width.toLong() * height
        // A full-resolution HDR bitmap may need eight bytes per pixel.
        return width > 0 && height > 0 && pixels <= MAX_FALLBACK_FRAME_PIXELS && MemoryUtils.canAllocate(pixels * 8)
    }

    private fun group(fingerprints: List<SimilarFingerprint>): List<SimilarGroup> {
        val groups = mutableListOf<SimilarGroup>()
        for (kind in listOf("image", "video")) {
            val remaining = arrayListOf<GroupCandidate?>().apply {
                addAll(
                    fingerprints.filter { it.kind == kind }
                        .sortedWith(compareByDescending<SimilarFingerprint> { quality(it.candidate) }.thenBy { it.candidate.entryId })
                        .map { GroupCandidate(it, aspectRatioBucket(it.candidate)) },
                )
            }
            var activeCount = remaining.size
            var representativeIndex = 0
            while (activeCount > 1) {
                while (remaining[representativeIndex] == null) representativeIndex++
                val representative = remaining[representativeIndex]!!
                remaining[representativeIndex] = null
                activeCount--
                val matches = mutableListOf<Pair<SimilarFingerprint, SimilarMatch>>()
                val iterator = remaining.listIterator(representativeIndex + 1)
                while (iterator.hasNext()) {
                    val candidate = iterator.next() ?: continue
                    if (!mayShareAspectRatio(representative.aspectRatioBucket, candidate.aspectRatioBucket)) continue
                    val match = compare(representative.fingerprint, candidate.fingerprint)
                    if (match != null) {
                        matches += candidate.fingerprint to match
                        iterator.set(null)
                        activeCount--
                    }
                }
                if (matches.isNotEmpty()) {
                    val allMatches = listOf(representative.fingerprint to null) + matches
                    val bestMatch = matches.minBy { it.second.score }
                    groups += SimilarGroup(
                        kind = kind,
                        highConfidence = matches.all { it.second.highConfidence },
                        score = bestMatch.second.score,
                        matchedFrames = bestMatch.second.matchedFrames,
                        totalFrames = bestMatch.second.totalFrames,
                        durationDeltaMillis = bestMatch.second.durationDeltaMillis,
                        members = allMatches.map { it.first.candidate },
                    )
                }
                representativeIndex++
            }
        }
        return groups
    }

    private fun compare(first: SimilarFingerprint, second: SimilarFingerprint): SimilarMatch? {
        if (first.kind != second.kind) return null
        if (aspectRatioDifference(first.candidate, second.candidate) > .05) return null
        return when (first.kind) {
            "image" -> compareImages(first, second)
            "video" -> compareVideos(first, second)
            else -> null
        }
    }

    private fun compareImages(first: SimilarFingerprint, second: SimilarFingerprint): SimilarMatch? {
        val firstPHash = first.imagePHash?.parseHash() ?: return null
        val secondPHash = second.imagePHash?.parseHash() ?: return null
        val firstDHash = first.imageDHash?.parseHash() ?: return null
        val secondDHash = second.imageDHash?.parseHash() ?: return null
        val pDistance = hamming(firstPHash, secondPHash)
        val dDistance = hamming(firstDHash, secondDHash)
        val high = pDistance <= IMAGE_HIGH_PHASH_DISTANCE && dDistance <= IMAGE_HIGH_DHASH_DISTANCE
        val review = pDistance <= IMAGE_REVIEW_PHASH_DISTANCE && dDistance <= IMAGE_REVIEW_DHASH_DISTANCE
        if (!review) return null
        val score = 1 - (pDistance * .6 + dDistance * .4) / 64
        return SimilarMatch(high, score, null, null, null)
    }

    private fun compareVideos(first: SimilarFingerprint, second: SimilarFingerprint): SimilarMatch? {
        val firstDuration = first.candidate.durationMillis
        val secondDuration = second.candidate.durationMillis
        val durationDelta = if (firstDuration != null && secondDuration != null) abs(firstDuration - secondDuration) else null
        if (durationDelta != null && durationDelta > maxOf(2_000L, max(firstDuration ?: 0L, secondDuration ?: 0L) * 2 / 100)) return null

        val frameCount = min(first.videoPHashes.size, second.videoPHashes.size)
        if (frameCount == 0 || frameCount != FRAME_COUNT) return null
        val distances = (0 until frameCount).map { index ->
            val pDistance = hamming(first.videoPHashes[index].parseHash(), second.videoPHashes[index].parseHash())
            val dDistance = hamming(first.videoDHashes[index].parseHash(), second.videoDHashes[index].parseHash())
            pDistance to dDistance
        }
        val matched = distances.count { (pDistance, dDistance) ->
            pDistance <= VIDEO_HIGH_PHASH_DISTANCE && dDistance <= VIDEO_HIGH_DHASH_DISTANCE
        }
        val reviewMatched = distances.count { (pDistance, dDistance) ->
            pDistance <= VIDEO_REVIEW_PHASH_DISTANCE && dDistance <= VIDEO_REVIEW_DHASH_DISTANCE
        }
        val medianPHash = distances.map { it.first }.sorted()[distances.size / 2]
        val high = matched >= 5 && medianPHash <= 8
        if (reviewMatched < 4 || medianPHash > 12) return null
        val score = .7 * reviewMatched / frameCount + .3 * (1 - medianPHash / 64.0)
        return SimilarMatch(high, score, matched, frameCount, durationDelta)
    }

    private fun isReusable(candidate: DuplicateCandidate, fingerprint: SimilarFingerprint): Boolean =
        fingerprint.algorithmVersion == ALGORITHM_VERSION &&
            fingerprint.entryId() == candidate.entryId &&
            fingerprint.kind == (if (candidate.isVideo) "video" else "image") &&
            fingerprint.candidate.uri == candidate.uri &&
            fingerprint.candidate.sizeBytes == candidate.sizeBytes &&
            fingerprint.candidate.dateModifiedMillis == candidate.dateModifiedMillis

    private fun SimilarFingerprint.withCandidate(candidate: DuplicateCandidate) = copy(
        candidate = candidate.copy(
            durationMillis = candidate.durationMillis?.takeIf { it > 0 } ?: this.candidate.durationMillis,
        ),
    )

    private fun SimilarFingerprint.entryId() = candidate.entryId

    private fun quality(candidate: DuplicateCandidate): Long {
        val pixels = candidate.width.toLong() * candidate.height.toLong()
        return max(pixels, candidate.sizeBytes)
    }

    private fun aspectRatioDifference(first: DuplicateCandidate, second: DuplicateCandidate): Double {
        if (first.width <= 0 || first.height <= 0 || second.width <= 0 || second.height <= 0) return 0.0
        val firstRatio = orientedAspectRatio(first)
        val secondRatio = orientedAspectRatio(second)
        return abs(firstRatio - secondRatio) / max(firstRatio, secondRatio)
    }

    private fun orientedAspectRatio(candidate: DuplicateCandidate): Double {
        val rotated = candidate.rotationDegrees % 180 != 0
        val width = if (rotated) candidate.height else candidate.width
        val height = if (rotated) candidate.width else candidate.height
        return width.toDouble() / height.toDouble()
    }

    private fun aspectRatioBucket(candidate: DuplicateCandidate): Int? {
        if (candidate.width <= 0 || candidate.height <= 0) return null
        return floor(ln(orientedAspectRatio(candidate)) / ASPECT_RATIO_BUCKET_WIDTH).toInt()
    }

    private fun mayShareAspectRatio(first: Int?, second: Int?): Boolean =
        first == null || second == null || abs(first - second) <= ASPECT_RATIO_BUCKET_RADIUS

    private fun hash(source: Bitmap): Pair<Long, Long> {
        val normalized = normalize(source)
        return try {
            val grayscale = DoubleArray(HASH_SIZE * HASH_SIZE)
            val pixels = IntArray(HASH_SIZE * HASH_SIZE)
            normalized.getPixels(pixels, 0, HASH_SIZE, 0, 0, HASH_SIZE, HASH_SIZE)
            for (index in pixels.indices) {
                val color = pixels[index]
                grayscale[index] = Color.red(color) * .299 + Color.green(color) * .587 + Color.blue(color) * .114
            }
            Pair(perceptualHash(grayscale), differenceHash(grayscale))
        } finally {
            if (!normalized.isRecycled) normalized.recycle()
        }
    }

    private fun normalize(source: Bitmap): Bitmap {
        val normalized = Bitmap.createBitmap(HASH_SIZE, HASH_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(normalized)
        canvas.drawColor(Color.WHITE)
        val scale = min(HASH_SIZE.toFloat() / source.width, HASH_SIZE.toFloat() / source.height)
        val width = source.width * scale
        val height = source.height * scale
        val left = (HASH_SIZE - width) / 2
        val top = (HASH_SIZE - height) / 2
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, android.graphics.RectF(left, top, left + width, top + height), paint)
        return normalized
    }

    private fun perceptualHash(grayscale: DoubleArray): Long {
        val dct = DoubleArray(HASH_BLOCK_SIZE * HASH_BLOCK_SIZE)
        for (u in 0 until HASH_BLOCK_SIZE) {
            for (v in 0 until HASH_BLOCK_SIZE) {
                var sum = 0.0
                for (x in 0 until HASH_SIZE) {
                    for (y in 0 until HASH_SIZE) {
                        sum += grayscale[y * HASH_SIZE + x] *
                            DCT_COSINES[u][x] *
                            DCT_COSINES[v][y]
                    }
                }
                dct[u * HASH_BLOCK_SIZE + v] = sum
            }
        }
        val lowFrequencies = dct.drop(1)
        val median = lowFrequencies.sorted()[lowFrequencies.size / 2]
        return lowFrequencies.fold(0L) { hash, value ->
            (hash shl 1) or if (value > median) 1L else 0L
        }
    }

    private fun differenceHash(grayscale: DoubleArray): Long {
        var hash = 0L
        for (y in 0 until HASH_BLOCK_SIZE) {
            val sourceY = y * (HASH_SIZE - 1) / (HASH_BLOCK_SIZE - 1)
            for (x in 0 until HASH_BLOCK_SIZE) {
                val leftX = x * (HASH_SIZE - 1) / HASH_BLOCK_SIZE
                val rightX = (x + 1) * (HASH_SIZE - 1) / HASH_BLOCK_SIZE
                val left = grayscale[sourceY * HASH_SIZE + leftX]
                val right = grayscale[sourceY * HASH_SIZE + rightX]
                hash = (hash shl 1) or if (left > right) 1L else 0L
            }
        }
        return hash
    }

    private fun hamming(first: Long, second: Long): Int = java.lang.Long.bitCount(first xor second)

    private fun Long.toHexHash() = java.lang.Long.toUnsignedString(this, 16).padStart(16, '0')

    private fun String.parseHash() = java.lang.Long.parseUnsignedLong(this, 16)

    private fun invalidCandidate() = DuplicateCandidate(-1, "", -1, null)

    private data class SimilarMatch(
        val highConfidence: Boolean,
        val score: Double,
        val matchedFrames: Int?,
        val totalFrames: Int?,
        val durationDeltaMillis: Long?,
    )

    private data class GroupCandidate(
        val fingerprint: SimilarFingerprint,
        val aspectRatioBucket: Int?,
    )

    companion object {
        private val SAMPLE_POINTS = listOf(.05, .20, .35, .50, .65, .80, .95)
        private val DCT_COSINES = Array(HASH_BLOCK_SIZE) { frequency ->
            DoubleArray(HASH_SIZE) { sample ->
                cos((2 * sample + 1) * frequency * PI / (2 * HASH_SIZE))
            }
        }
    }
}
