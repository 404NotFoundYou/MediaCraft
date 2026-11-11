package com.mediacraft.app.domain

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateScannerTest {
    @Test
    fun exactDuplicate_requiresEqualBytesAndIgnoresNames() = runTest {
        val first = media("1", "one.jpg", bytes = "same")
        val second = media("2", "renamed.jpg", bytes = "same", favorite = true)
        val sameNameDifferentContent = media("3", "one.jpg", bytes = "diff")
        val source = FakeMediaSource(listOf(first, second, sameNameDifferentContent))

        val result = DuplicateScanner(source).scan(ScanScope())

        val group = result.groups.single()
        assertEquals(DuplicateKind.EXACT, group.kind)
        assertEquals(setOf(first.id, second.id), group.items.map { it.id }.toSet())
        assertEquals(second.id, group.recommendedId)
        assertFalse(group.reason.contains("像素"))
    }

    @Test
    fun readFailure_isReportedAndDoesNotCreateGroup() = runTest {
        val first = media("1", "one.jpg", bytes = "same")
        val second = media("2", "two.jpg", bytes = "same")
        val source = FakeMediaSource(listOf(first, second), failingOpenIds = setOf(second.id))

        val result = DuplicateScanner(source).scan(ScanScope())

        assertTrue(result.groups.isEmpty())
        assertTrue(result.issues.any { it.mediaId == second.id && it.message.contains("读取失败") })
    }

    @Test
    fun photoVersion_isOnlyCandidateAndPrefersClearlyHigherPixels() = runTest {
        val high = media("1", "high.jpg", "high", width = 4032, height = 3024)
        val low = media("2", "low.jpg", "low!", width = 1920, height = 1440)
        val fingerprint = fingerprint(0b1010, sharpness = 10.0)
        val source = FakeMediaSource(
            listOf(high, low),
            fingerprints = mapOf(high.id to fingerprint, low.id to fingerprint),
        )

        val group = DuplicateScanner(source).scan(ScanScope()).groups.single()

        assertEquals(DuplicateKind.PHOTO_VERSION, group.kind)
        assertEquals(high.id, group.recommendedId)
        assertTrue(group.reason.contains("像素更高"))
        assertTrue(group.warnings.any { it.contains("人工核对") })
    }

    @Test
    fun exactKeeperAlsoRepresentsItsCopiesInPhotoVersionCandidates() = runTest {
        val originalCopy = media("1", "original-copy.jpg", "same", width = 4032, height = 3024)
        val preferredKeeper = media(
            "2",
            "original.jpg",
            "same",
            width = 4032,
            height = 3024,
            favorite = true,
        )
        val scaled = media("3", "scaled.jpg", "small", width = 1920, height = 1440)
        val matching = fingerprint(0b1010, sharpness = 10.0)
        val source = FakeMediaSource(
            listOf(originalCopy, preferredKeeper, scaled),
            fingerprints = mapOf(preferredKeeper.id to matching, scaled.id to matching),
        )

        val result = DuplicateScanner(source).scan(ScanScope())
        val exact = result.groups.single { it.kind == DuplicateKind.EXACT }
        val version = result.groups.single { it.kind == DuplicateKind.PHOTO_VERSION }

        assertEquals(preferredKeeper.id, exact.recommendedId)
        assertEquals(setOf(preferredKeeper.id, scaled.id), version.items.map { it.id }.toSet())
        assertEquals(exact.recommendedId, version.recommendedId)
        assertFalse(version.items.any { it.id == originalCopy.id })
    }

    @Test
    fun photoVersion_doesNotRecommendUpscaledOrBlurredHigherPixelCandidate() = runTest {
        val high = media("1", "high.jpg", "high", width = 4032, height = 3024)
        val low = media("2", "low.jpg", "low!", width = 1920, height = 1440)
        val source = FakeMediaSource(
            listOf(high, low),
            fingerprints = mapOf(
                high.id to fingerprint(0, sharpness = 4.0),
                low.id to fingerprint(0, sharpness = 10.0),
            ),
        )

        val group = DuplicateScanner(source).scan(ScanScope()).groups.single()

        assertNull(group.recommendedId)
        assertTrue(group.reason.contains("插值") || group.reason.contains("清晰度"))
    }

    @Test
    fun versionGrouping_requiresEveryPairAndDoesNotChainCandidates() = runTest {
        val a = media("a", "a.jpg", "a", width = 100, height = 100)
        val b = media("b", "b.jpg", "bb", width = 100, height = 100)
        val c = media("c", "c.jpg", "ccc", width = 100, height = 100)
        val source = FakeMediaSource(
            listOf(a, b, c),
            fingerprints = mapOf(
                a.id to fingerprint(0x0000),
                b.id to fingerprint(0x00ff),
                c.id to fingerprint(0xffff),
            ),
        )

        val groups = DuplicateScanner(source).scan(ScanScope()).groups

        assertEquals(1, groups.size)
        assertEquals(setOf(a.id, b.id), groups.single().items.map { it.id }.toSet())
    }

    @Test
    fun staticOrSingleFrameVideo_isNotAContentVersionCandidate() = runTest {
        val first = video("1", "one.mp4", "one")
        val second = video("2", "two.mp4", "two")
        val static = ContentFingerprint(listOf(frame(1), frame(1), frame(1)), hasMotion = false)
        val source = FakeMediaSource(listOf(first, second), mapOf(first.id to static, second.id to static))

        val result = DuplicateScanner(source).scan(ScanScope())

        assertTrue(result.groups.isEmpty())
    }

    @Test
    fun animatedPhoto_isNotTreatedAsAStillPhotoVersion() = runTest {
        val first = media("1", "one.jpg", "one")
        val second = media("2", "two.jpg", "two")
        val animated = fingerprint(1).copy(hasMotion = true)
        val source = FakeMediaSource(listOf(first, second), mapOf(first.id to animated, second.id to animated))

        val result = DuplicateScanner(source).scan(ScanScope())

        assertTrue(result.groups.isEmpty())
    }

    @Test
    fun missingFingerprint_isReportedInsteadOfSilentlySkipped() = runTest {
        val item = media("1", "one.jpg", "one")

        val result = DuplicateScanner(FakeMediaSource(listOf(item))).scan(ScanScope())

        assertTrue(result.issues.any { it.mediaId == item.id && it.message.contains("内容特征") })
    }

    @Test
    fun videoVersion_checksTheWholeSampleAndKeepsCodecConflicts() = runTest {
        val avc = video("1", "avc.mp4", "one", codec = "avc")
        val hevc = video("2", "hevc.mp4", "two", codec = "hevc")
        val matching = ContentFingerprint(listOf(frame(1), frame(2), frame(3)), hasMotion = true)
        val source = FakeMediaSource(listOf(avc, hevc), mapOf(avc.id to matching, hevc.id to matching))

        val group = DuplicateScanner(source).scan(ScanScope()).groups.single()

        assertEquals(DuplicateKind.VIDEO_VERSION, group.kind)
        assertNull(group.recommendedId)
        assertTrue(group.reason.contains("编码"))

        val changedEnding = matching.copy(frames = listOf(frame(1), frame(2), frame(-1L)))
        val rejected = FakeMediaSource(listOf(avc, hevc), mapOf(avc.id to matching, hevc.id to changedEnding))
        assertTrue(DuplicateScanner(rejected).scan(ScanScope()).groups.isEmpty())
    }

    @Test
    fun shortVideoWithTrimmedDuration_isNotAContentVersionCandidate() = runTest {
        val complete = video("1", "complete.mp4", "one", durationMillis = 4_000)
        val trimmed = video("2", "trimmed.mp4", "two", durationMillis = 3_000)
        val matchingFrames = ContentFingerprint(listOf(frame(1), frame(2), frame(3), frame(4), frame(5)), hasMotion = true)
        val source = FakeMediaSource(
            listOf(complete, trimmed),
            mapOf(complete.id to matchingFrames, trimmed.id to matchingFrames),
        )

        val result = DuplicateScanner(source).scan(ScanScope())

        assertTrue(result.groups.isEmpty())
    }

    @Test
    fun cancellationStopsStreamingScan() = runTest {
        val first = media("1", "one.jpg", ByteArray(DEFAULT_BUFFER_SIZE * 3) { 1 })
        val second = media("2", "two.jpg", ByteArray(DEFAULT_BUFFER_SIZE * 3) { 1 })
        lateinit var scanJob: Job
        val source = FakeMediaSource(listOf(first, second), cancelOnRead = { scanJob.cancel() })

        scanJob = launch { DuplicateScanner(source).scan(ScanScope()) }
        scanJob.join()

        assertTrue(scanJob.isCancelled)
        assertEquals("Cancellation must stop before reading the next media file", listOf(first.id), source.openedIds)
    }

    private class FakeMediaSource(
        private val items: List<MediaItem>,
        private val fingerprints: Map<String, ContentFingerprint> = emptyMap(),
        private val failingOpenIds: Set<String> = emptySet(),
        private val cancelOnRead: (() -> Unit)? = null,
    ) : MediaSource {
        private val bytes = items.associate { it.id to CONTENT.getValue(it.id) }
        val openedIds = mutableListOf<String>()

        override suspend fun list(scope: ScanScope) = MediaListing(items)

        override fun open(item: MediaItem): InputStream {
            openedIds += item.id
            if (item.id in failingOpenIds) throw IOException("denied")
            val delegate = ByteArrayInputStream(bytes.getValue(item.id))
            return object : InputStream() {
                private var cancelled = false
                override fun read(): Int = delegate.read()
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (!cancelled && cancelOnRead != null) {
                        cancelled = true
                        cancelOnRead.invoke()
                    }
                    return delegate.read(buffer, offset, length)
                }
            }
        }

        override suspend fun fingerprint(item: MediaItem) = fingerprints[item.id]
    }

    companion object {
        private val CONTENT = mutableMapOf<String, ByteArray>()

        private fun media(
            id: String,
            name: String,
            bytes: String,
            width: Int = 100,
            height: Int = 100,
            favorite: Boolean = false,
        ) = media(id, name, bytes.toByteArray(), width, height, favorite)

        private fun media(
            id: String,
            name: String,
            bytes: ByteArray,
            width: Int = 100,
            height: Int = 100,
            favorite: Boolean = false,
        ): MediaItem {
            CONTENT[id] = bytes
            return MediaItem(
                id = "content://media/$id",
                displayName = name,
                kind = MediaKind.PHOTO,
                mimeType = "image/jpeg",
                relativePath = "Pictures/",
                size = bytes.size.toLong(),
                width = width,
                height = height,
                dateTakenMillis = 1,
                dateAddedMillis = id.hashCode().toLong(),
                dateModifiedSeconds = 1,
                favorite = favorite,
            ).also { CONTENT[it.id] = bytes }
        }

        private fun video(
            id: String,
            name: String,
            bytes: String,
            codec: String = "avc",
            durationMillis: Long = 10_000,
        ): MediaItem {
            val content = bytes.toByteArray()
            return MediaItem(
                id = "content://media/$id",
                displayName = name,
                kind = MediaKind.VIDEO,
                mimeType = "video/mp4",
                relativePath = "Movies/",
                size = content.size.toLong(),
                width = 1920,
                height = 1080,
                dateTakenMillis = 1,
                dateAddedMillis = id.hashCode().toLong(),
                dateModifiedSeconds = 1,
                durationMillis = durationMillis,
                video = VideoMetadata(codec, 5_000_000, 30f, "SDR", 8, "stereo"),
            ).also { CONTENT[it.id] = content }
        }

        private fun fingerprint(hash: Long, sharpness: Double = 8.0) =
            ContentFingerprint(listOf(frame(hash, sharpness)))

        private fun frame(hash: Long, sharpness: Double = 8.0) =
            FrameFingerprint(hash, listOf(10, 20, 30, 40), sharpness)
    }
}
