package deckers.thibault.aves.channel

import android.media.MediaMetadataRetriever.OPTION_CLOSEST
import android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SimilarEngineTest {
    private val engine = SimilarEngine(
        imageLoader = SimilarImageLoader { error("image loader should not be used") },
        videoLoader = SimilarVideoLoader { error("video loader should not be used") },
    )

    @Test
    fun exactDuplicateMembersCanBeExcludedBeforeSimilarityGrouping() = runBlocking {
        val first = imageCandidate(1)
        val second = imageCandidate(2)
        val third = imageCandidate(3)
        val result = engine.scan(
            candidates = listOf(first, second, third),
            cached = mapOf(
                1L to imageFingerprint(first),
                2L to imageFingerprint(second),
                3L to imageFingerprint(third),
            ),
            excludedEntryIds = setOf(1, 2),
        ) { _, _, _ -> }

        assertTrue(result.groups.isEmpty())
        assertEquals(listOf(3L), result.fingerprints.map { it.candidate.entryId })
    }

    @Test
    fun cachedHashesKeepCurrentMediaMetadataForReview() = runBlocking {
        val first = imageCandidate(1, path = "/current/first.jpg", rotationDegrees = 90)
        val second = imageCandidate(2, path = "/current/second.jpg")
        val staleFirst = first.copy(path = null)
        val staleSecond = second.copy(path = null)
        val result = engine.scan(
            candidates = listOf(first, second),
            cached = mapOf(
                1L to imageFingerprint(staleFirst),
                2L to imageFingerprint(staleSecond),
            ),
        ) { _, _, _ -> }

        val members = result.groups.single().members.associateBy { it.entryId }
        assertEquals("/current/first.jpg", members.getValue(1).path)
        assertEquals(90, members.getValue(1).rotationDegrees)
        assertEquals("/current/second.jpg", members.getValue(2).path)
    }

    @Test
    fun similarImagesAcrossAspectRatioBucketsRemainGrouped() = runBlocking {
        val first = imageCandidate(1, width = 1024, height = 1000)
        val second = imageCandidate(2, width = 1076, height = 1000)
        val result = engine.scan(
            candidates = listOf(first, second),
            cached = mapOf(
                1L to imageFingerprint(first),
                2L to imageFingerprint(second),
            ),
        ) { _, _, _ -> }

        assertEquals(1, result.groups.size)
        assertEquals(setOf(1L, 2L), result.groups.single().members.map { it.entryId }.toSet())
    }

    @Test
    fun videoSimilarityRequiresEnoughMatchingFramesAndSimilarDuration() = runBlocking {
        val first = videoCandidate(1, durationMillis = 10_000)
        val second = videoCandidate(2, durationMillis = 10_000)
        val fourFrameMatch = listOf("0000000000000000", "0000000000000000", "0000000000000000", "0000000000000000", "ffffffffffffffff", "ffffffffffffffff", "ffffffffffffffff")
        val result = engine.scan(
            candidates = listOf(first, second),
            cached = mapOf(
                1L to videoFingerprint(first, List(7) { "0000000000000000" }),
                2L to videoFingerprint(second, fourFrameMatch),
            ),
        ) { _, _, _ -> }

        assertEquals(1, result.groups.size)
        assertEquals(4, result.groups.single().matchedFrames)
        assertEquals(7, result.groups.single().totalFrames)
        assertTrue(!result.groups.single().highConfidence)

        val durationMismatch = engine.scan(
            candidates = listOf(first, second.copy(durationMillis = 14_000)),
            cached = mapOf(
                1L to videoFingerprint(first, List(7) { "0000000000000000" }),
                2L to videoFingerprint(second.copy(durationMillis = 14_000), fourFrameMatch),
            ),
        ) { _, _, _ -> }

        assertTrue(durationMismatch.groups.isEmpty())
    }

    @Test
    fun aWorkingSyncFrameDoesNotDecodeExtraFrames() = runBlocking {
        val frame = readVideoSample(1, 500_000, scaled = { time, option ->
            assertEquals(500_000L, time)
            assertEquals(OPTION_CLOSEST_SYNC, option)
            "sync"
        }, original = { _, _ -> error("unnecessary full-size decode") })
        assertEquals("sync", frame)
    }

    @Test
    fun aMissingKeyframeCanUseThePictureAtTheSameTime() = runBlocking {
        val options = mutableListOf<Int>()
        val frame = readVideoSample(4, 5_000_000, scaled = { time, option ->
            assertEquals(5_000_000L, time)
            options += option
            if (option == OPTION_CLOSEST) "middle" else null
        }, original = { _, _ -> error("scaled frame was available") })
        assertEquals(listOf(OPTION_CLOSEST_SYNC, OPTION_CLOSEST), options)
        assertEquals("middle", frame)
    }

    @Test
    fun aBrokenScaledDecoderFallsBackToTheSameTimeAtOriginalSize() = runBlocking {
        val frame = readVideoSample(4, 5_000_000, scaled = { _, _ -> throw IllegalStateException("decoder failed") }, original = { time, option ->
            assertEquals(5_000_000L, time)
            assertEquals(OPTION_CLOSEST_SYNC, option)
            "original"
        })
        assertEquals("original", frame)
    }

    @Test
    fun missingFramesReportThePositionInsteadOfSubstitutingACover() {
        val positions = mutableListOf<Long>()
        val failure = assertThrows<IllegalStateException> {
            runBlocking {
                readVideoSample<Any>(7, 9_500_000, scaled = null) { time, _ -> positions.add(time); null }
            }
        }
        assertEquals(listOf(9_500_000L, 9_500_000L), positions)
        assertEquals("无法读取视频采样帧（第 7/7 帧，9500 毫秒）", failure.message)
    }

    @Test
    fun cancellationAndLostPermissionStopDecoderRetries() {
        for (failure in listOf(CancellationException("scan cancelled"), SecurityException("access revoked"))) {
            var attempts = 0
            val thrown = assertThrows<RuntimeException> {
                runBlocking {
                    readVideoSample<Any>(1, 500_000, scaled = { _, _ -> attempts++; throw failure }, original = { _, _ -> error("should not retry") })
                }
            }
            assertEquals(failure, thrown)
            assertEquals(1, attempts)
        }
    }

    @Test
    fun cachedSamplesKeepTheRecoveredDurationWhenTheCatalogReportsZero() = runBlocking {
        val known = videoCandidate(1, durationMillis = 10_000)
        val result = engine.scan(
            candidates = listOf(known.copy(durationMillis = 0)),
            cached = mapOf(1L to videoFingerprint(known, List(7) { "0000000000000000" })),
        ) { _, _, _ -> }
        assertEquals(10_000L, result.fingerprints.single().candidate.durationMillis)
    }

    @Test
    fun editedMetadataAndCorruptSamplesCannotReuseOldFingerprints() = runBlocking {
        val previous = videoCandidate(1, 10000)
        val cached = videoFingerprint(previous, List(7) { "0123456789abcdef" })
        for (changed in listOf(previous.copy(pageId = 2), previous.copy(width = 12), previous.copy(height = 15), previous.copy(durationMillis = 9000),
            previous.copy(isFlipped = true), previous.copy(rotationDegrees = 90), previous.copy(mimeType = "video/webm"))) {
            val result = engine.scan(listOf(changed), mapOf(1L to cached)) { _, _, _ -> }
            assertEquals("similar-read-failed", result.issues.single().code)
            assertTrue(result.fingerprints.isEmpty())
        }
        for (bad in listOf(cached.copy(videoDHashes = listOf("broken")), cached.copy(videoPHashes = emptyList()))) {
            assertTrue(engine.scan(listOf(previous), mapOf(1L to bad)) { _, _, _ -> }.fingerprints.isEmpty())
        }
    }

    @Test
    fun metricIndexFindsExactlyTheBruteForceHammingNeighbors() {
        val random = java.util.Random(42)
        val hashes = List(512) { random.nextLong() }
        val index = HashIndex()
        hashes.forEachIndexed { i, value -> index.add(value, i) }
        for (hash in hashes.take(50)) {
            val query = hash xor 0x7711L
            val found = mutableSetOf<Int>()
            index.find(query, 12, found)
            assertEquals(hashes.indices.filter { java.lang.Long.bitCount(hashes[it] xor query) <= 12 }.toSet(), found)
        }
    }

    @Test
    fun trimmedVideoShowsAlignedTimestampsButStaticFramesDoNotProveContainment() = runBlocking {
        val long = videoCandidate(1, 24000)
        val short = videoCandidate(2, 10000)
        val random = java.util.Random(7)
        val timeline = List(12) { "%016x".format(random.nextLong()) }
        suspend fun scan(hashes: List<String>): List<SimilarGroup> {
            fun fp(c: DuplicateCandidate, clip: List<String>) = videoFingerprint(c, List(7) { "0000000000000000" }).copy(clipPHashes = clip, clipDHashes = clip)
            return engine.scan(listOf(long, short), mapOf(1L to fp(long, hashes), 2L to fp(short, hashes.subList(3, 8)))) { _, _, _ -> }.groups
        }
        val group = scan(timeline).single()
        assertTrue(group.fragment && !group.highConfidence)
        val frame = group.evidence.values.single().first()
        assertEquals(setOf(6000L, 0L), setOf(frame.referenceMillis, frame.memberMillis))
        assertTrue(scan(List(12) { "0000000000000000" }).isEmpty())
    }

    private fun imageCandidate(
        id: Long,
        path: String? = "/media/$id.jpg",
        rotationDegrees: Int = 0,
        width: Int = 100,
        height: Int = 100,
    ) = DuplicateCandidate(
        entryId = id,
        uri = "content://media/$id",
        sizeBytes = 100 + id,
        dateModifiedMillis = 200 + id,
        mimeType = "image/jpeg",
        path = path,
        width = width,
        height = height,
        rotationDegrees = rotationDegrees,
    )

    private fun imageFingerprint(candidate: DuplicateCandidate) = SimilarFingerprint(
        candidate = candidate,
        algorithmVersion = ALGORITHM_VERSION,
        kind = "image",
        imagePHash = "0000000000000000",
        imageDHash = "0000000000000000",
        videoPHashes = emptyList(),
        videoDHashes = emptyList(),
    )

    private fun videoCandidate(id: Long, durationMillis: Long) = DuplicateCandidate(
        entryId = id,
        uri = "content://media/$id",
        sizeBytes = 100 + id,
        dateModifiedMillis = 200 + id,
        mimeType = "video/mp4",
        path = "/media/$id.mp4",
        width = 1920,
        height = 1080,
        durationMillis = durationMillis,
        isVideo = true,
    )

    private fun videoFingerprint(candidate: DuplicateCandidate, hashes: List<String>) = SimilarFingerprint(
        candidate = candidate,
        algorithmVersion = ALGORITHM_VERSION,
        kind = "video",
        imagePHash = null,
        imageDHash = null,
        videoPHashes = hashes,
        videoDHashes = hashes,
    )
}
