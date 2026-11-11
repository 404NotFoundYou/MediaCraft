package com.mediacraft.app.domain

import kotlin.math.abs

internal data class QualityRecommendation(
    val mediaId: String?,
    val reason: String,
    val warnings: List<String> = emptyList(),
)

internal class QualityRecommender {
    fun recommend(
        kind: DuplicateKind,
        items: List<MediaItem>,
        fingerprints: Map<String, ContentFingerprint> = emptyMap(),
    ): QualityRecommendation = when (kind) {
        DuplicateKind.EXACT -> recommendExact(items)
        DuplicateKind.PHOTO_VERSION -> recommendPhoto(items, fingerprints)
        DuplicateKind.VIDEO_VERSION -> recommendVideo(items, fingerprints)
    }

    private fun recommendExact(items: List<MediaItem>): QualityRecommendation {
        val recommended = items.minWithOrNull(
            compareByDescending<MediaItem> { it.favorite }
                .thenByDescending { isCameraPath(it.relativePath) }
                .thenBy { it.dateAddedMillis.takeIf { date -> date > 0 } ?: Long.MAX_VALUE }
                .thenBy { it.id },
        )
        return QualityRecommendation(
            mediaId = recommended?.id,
            reason = "文件内容完全相同；按收藏、相机目录、较早添加时间建议保留",
        )
    }

    private fun recommendPhoto(
        items: List<MediaItem>,
        fingerprints: Map<String, ContentFingerprint>,
    ): QualityRecommendation {
        if (items.any { it.width <= 0 || it.height <= 0 }) {
            return noRecommendation("缺少照片尺寸，无法可靠推荐")
        }
        val sorted = items.sortedWith(compareByDescending<MediaItem> { it.pixels }.thenBy { it.id })
        val best = sorted.firstOrNull() ?: return noRecommendation("没有可比较的照片")
        val runnerUp = sorted.getOrNull(1) ?: return noRecommendation("没有可比较的照片")
        if (best.pixels <= runnerUp.pixels * 1.1) {
            return noRecommendation("像素接近，请人工选择")
        }

        val bestSharpness = sharpness(fingerprints[best.id])
        val otherSharpness = sorted.drop(1).mapNotNull { sharpness(fingerprints[it.id]) }.maxOrNull()
        if (bestSharpness != null && otherSharpness != null && bestSharpness < otherSharpness * 0.9) {
            return noRecommendation("高像素版本清晰度较低，疑似插值或失焦，请人工选择")
        }

        return QualityRecommendation(
            mediaId = best.id,
            reason = "像素更高：${best.width} × ${best.height}",
        )
    }

    private fun recommendVideo(
        items: List<MediaItem>,
        fingerprints: Map<String, ContentFingerprint>,
    ): QualityRecommendation {
        if (items.size < 2) return noRecommendation("没有可比较的视频")
        if (items.any { it.width <= 0 || it.height <= 0 || it.durationMillis <= 0 || it.video == null }) {
            return noRecommendation("视频元数据不完整，无法可靠推荐")
        }
        val metadata = items.map { requireNotNull(it.video) }
        if (!sameNonNull(metadata.map { it.codec }) ||
            !sameNonNull(metadata.map { it.dynamicRange }) ||
            !sameFloat(metadata.map { it.frameRate }) ||
            !sameNonNull(metadata.map { it.audioSignature }) ||
            !sameNonNull(metadata.map { it.bitDepth })
        ) {
            return noRecommendation("编码、HDR、帧率、色深或音轨各有差异，默认全部保留")
        }

        val sorted = items.sortedWith(compareByDescending<MediaItem> { it.pixels }.thenBy { it.id })
        val best = sorted.first()
        val runnerUp = sorted[1]
        if (best.pixels <= runnerUp.pixels * 1.1) {
            return noRecommendation("分辨率接近，不能仅凭码率判断画质")
        }
        val bestBitrate = best.video?.bitrate
        val otherItems = sorted.drop(1)
        if (bestBitrate == null || otherItems.any { it.video?.bitrate == null }) {
            return noRecommendation("缺少码率信息，无法排除拉伸转码")
        }
        if (otherItems.any { bestBitrate < requireNotNull(it.video?.bitrate) }) {
            return noRecommendation("高分辨率版本总码率更低，疑似拉伸或转码，请人工选择")
        }

        val evidence = sorted.map { fingerprints[it.id] }
        if (evidence.filterNotNull().map { it.frames.size }.distinct().size > 1) {
            return noRecommendation("规范化采样点不一致，细节证据不足，无法可靠推荐")
        }
        val details = sorted.associateWith { videoDetail(fingerprints[it.id]) }
        if (details.values.any { it == null }) {
            return noRecommendation("缺少可比较的规范化采样细节，无法可靠推荐")
        }
        val bestDetail = requireNotNull(details[best])
        val strongestOtherDetail = otherItems.maxOf { requireNotNull(details[it]) }
        if (bestDetail < strongestOtherDetail * MIN_DETAIL_RATIO) {
            return noRecommendation("高分辨率版本的规范化采样细节较低，疑似拉伸或低细节转码")
        }

        val bestBitsPerPixel = bestBitrate.toDouble() / best.pixels
        val bitrateConflict = otherItems.any { other ->
            val otherBitrate = requireNotNull(other.video?.bitrate)
            val otherBitsPerPixel = otherBitrate.toDouble() / other.pixels
            bestBitsPerPixel < otherBitsPerPixel * MIN_BITS_PER_PIXEL_RATIO
        }
        if (bitrateConflict) {
            return noRecommendation("高分辨率版本的单位像素码率明显较低，不能仅凭分辨率或总码率推荐")
        }
        return QualityRecommendation(
            mediaId = best.id,
            reason = "同编码、HDR、帧率和音轨下，分辨率更高且规范化采样细节与码率证据一致：${best.width} × ${best.height}",
        )
    }

    private fun sharpness(fingerprint: ContentFingerprint?): Double? =
        fingerprint?.frames?.map { it.sharpness }?.filter { it > 0 }?.average()?.takeIf { !it.isNaN() }

    private fun videoDetail(fingerprint: ContentFingerprint?): Double? {
        val frames = fingerprint?.frames ?: return null
        if (frames.size < 3 || frames.any { it.sharpness <= 0 }) return null
        return frames.map { it.sharpness }.average().takeIf { !it.isNaN() }
    }

    private fun isCameraPath(path: String): Boolean =
        path.replace('\\', '/').trim('/').let { normalized ->
            normalized.equals("DCIM/Camera", ignoreCase = true) ||
                normalized.startsWith("DCIM/Camera/", ignoreCase = true)
        }

    private fun <T> sameNonNull(values: List<T?>): Boolean =
        values.isNotEmpty() && values.none { it == null } && values.distinct().size == 1

    private fun sameFloat(values: List<Float?>): Boolean {
        if (values.any { it == null }) return false
        val numbers = values.filterNotNull()
        return numbers.all { abs(it - numbers.first()) <= 0.01f }
    }

    private fun noRecommendation(reason: String) = QualityRecommendation(
        mediaId = null,
        reason = reason,
        warnings = listOf("候选需要人工核对，未自动选择待处理项"),
    )

    private companion object {
        const val MIN_DETAIL_RATIO = 0.9
        const val MIN_BITS_PER_PIXEL_RATIO = 0.6
    }
}
