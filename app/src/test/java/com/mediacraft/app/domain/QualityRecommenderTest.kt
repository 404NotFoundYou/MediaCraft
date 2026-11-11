package com.mediacraft.app.domain

import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityRecommenderTest {
    private val recommender = QualityRecommender()

    @Test
    fun photoWithMissingDimensions_isNotRanked() {
        val complete = photo("1", 4032, 3024)
        val missing = photo("2", 0, 0)

        val result = recommender.recommend(DuplicateKind.PHOTO_VERSION, listOf(complete, missing))

        assertNull(result.mediaId)
        assertTrue(result.reason.contains("缺少"))
    }

    @Test
    fun videoConflicts_areAllKeptInsteadOfComparedByBitrate() {
        val hdrHighBitrate = video(
            id = "1",
            width = 3840,
            height = 2160,
            metadata = VideoMetadata("hevc", 20_000_000, 60f, "HDR", 10, "surround"),
        )
        val sdrLowerBitrate = video(
            id = "2",
            width = 1920,
            height = 1080,
            metadata = VideoMetadata("avc", 8_000_000, 30f, "SDR", 8, "stereo"),
        )

        val result = recommender.recommend(DuplicateKind.VIDEO_VERSION, listOf(hdrHighBitrate, sdrLowerBitrate))

        assertNull(result.mediaId)
        assertTrue(result.reason.contains("默认全部保留"))
    }

    @Test
    fun videoMissingMetadata_isNotRanked() {
        val complete = video("1", 3840, 2160, VideoMetadata("avc", 20_000_000, 30f, "SDR", 8, "stereo"))
        val missing = video("2", 1920, 1080, VideoMetadata(codec = "avc"))

        val result = recommender.recommend(DuplicateKind.VIDEO_VERSION, listOf(complete, missing))

        assertNull(result.mediaId)
        assertTrue(result.reason.contains("元数据") || result.reason.contains("差异"))
    }

    @Test
    fun higherResolutionVideoNeedsMatchingDetailAndAdequatePixelRate() {
        val source = video("source", 3840, 2160, comparableVideo(bitrate = 40_000_000))
        val scaled = video("scaled", 1920, 1080, comparableVideo(bitrate = 8_000_000))

        val result = recommender.recommend(
            DuplicateKind.VIDEO_VERSION,
            listOf(source, scaled),
            fingerprints(source.id to detailFingerprint(12.0), scaled.id to detailFingerprint(12.0)),
        )

        assertEquals(source.id, result.mediaId)
        assertTrue(result.reason.contains("细节") && result.reason.contains("码率"))
    }

    @Test
    fun lowDetailHighResolutionVideo_isNotRecommendedAsTheBetterCopy() {
        val lowDetail4k = video("low-detail", 3840, 2160, comparableVideo(bitrate = 40_000_000))
        val detailed1080 = video("detailed", 1920, 1080, comparableVideo(bitrate = 8_000_000))

        val result = recommender.recommend(
            DuplicateKind.VIDEO_VERSION,
            listOf(lowDetail4k, detailed1080),
            fingerprints(lowDetail4k.id to detailFingerprint(5.0), detailed1080.id to detailFingerprint(12.0)),
        )

        assertNull("More pixels cannot substitute for visible detail", result.mediaId)
        assertTrue(result.reason.contains("细节") || result.reason.contains("拉伸"))
    }

    @Test
    fun fourKWithOnlySlightlyHigherTotalBitrate_hasConflictingPixelRateEvidence() {
        val fourK = video("4k", 3840, 2160, comparableVideo(bitrate = 9_000_000))
        val fullHd = video("1080", 1920, 1080, comparableVideo(bitrate = 8_000_000))

        val result = recommender.recommend(
            DuplicateKind.VIDEO_VERSION,
            listOf(fourK, fullHd),
            fingerprints(fourK.id to detailFingerprint(12.0), fullHd.id to detailFingerprint(12.0)),
        )

        assertNull("A small absolute bitrate increase cannot support four times the pixels", result.mediaId)
        assertTrue(result.reason.contains("单位像素码率"))
    }

    @Test
    fun missingNormalizedDetailEvidence_isNotAHighConfidenceRecommendation() {
        val fourK = video("4k", 3840, 2160, comparableVideo(bitrate = 40_000_000))
        val fullHd = video("1080", 1920, 1080, comparableVideo(bitrate = 8_000_000))

        val result = recommender.recommend(DuplicateKind.VIDEO_VERSION, listOf(fourK, fullHd))

        assertNull(result.mediaId)
        assertTrue(result.reason.contains("缺少") && result.reason.contains("细节"))
    }

    @Test
    fun hdrFrameRateAudioAndCodecDifferencesPreserveEveryVersion() {
        val baseline = comparableVideo(bitrate = 40_000_000)
        val conflicts = listOf(
            baseline.copy(dynamicRange = "HDR"),
            baseline.copy(frameRate = 60f),
            baseline.copy(audioSignature = "surround"),
            baseline.copy(codec = "hevc"),
        )
        val fingerprints = fingerprints("high" to detailFingerprint(12.0), "low" to detailFingerprint(12.0))

        conflicts.forEach { conflict ->
            val high = video("high", 3840, 2160, conflict)
            val low = video("low", 1920, 1080, baseline.copy(bitrate = 8_000_000))
            val result = recommender.recommend(DuplicateKind.VIDEO_VERSION, listOf(high, low), fingerprints)
            assertNull("Conflicting delivery properties represent different uses", result.mediaId)
            assertTrue(result.reason.contains("默认全部保留"))
        }
    }

    private fun photo(id: String, width: Int, height: Int) = MediaItem(
        id = id,
        displayName = "$id.jpg",
        kind = MediaKind.PHOTO,
        mimeType = "image/jpeg",
        relativePath = "Pictures/",
        size = 1,
        width = width,
        height = height,
        dateTakenMillis = 1,
        dateAddedMillis = 1,
        dateModifiedSeconds = 1,
    )

    private fun video(id: String, width: Int, height: Int, metadata: VideoMetadata) = MediaItem(
        id = id,
        displayName = "$id.mp4",
        kind = MediaKind.VIDEO,
        mimeType = "video/mp4",
        relativePath = "Movies/",
        size = 1,
        width = width,
        height = height,
        dateTakenMillis = 1,
        dateAddedMillis = 1,
        dateModifiedSeconds = 1,
        durationMillis = 10_000,
        video = metadata,
    )

    private fun comparableVideo(bitrate: Long) =
        VideoMetadata("avc", bitrate, 30f, "SDR", 8, "stereo")

    private fun detailFingerprint(sharpness: Double) = ContentFingerprint(
        frames = List(5) { index -> FrameFingerprint(index.toLong(), List(72) { 100 }, sharpness) },
        hasMotion = true,
    )

    private fun fingerprints(vararg entries: Pair<String, ContentFingerprint>) = entries.toMap()
}
