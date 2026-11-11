package com.mediacraft.app.data

import android.media.MediaFormat
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.NalUnitUtil

/** Read the encoded SPS when a media provider omits its color-transfer metadata. */
@androidx.annotation.OptIn(UnstableApi::class)
internal object CodecColorParser {
    data class Color(val transfer: Int, val bitDepth: Int)

    fun read(format: MediaFormat): Color? {
        val mime = format.getString(MediaFormat.KEY_MIME)
        if (mime != "video/avc" && mime != "video/hevc") return null
        val configuration = format.getByteBuffer("csd-0")?.duplicate() ?: return null
        if (configuration.remaining() !in 1..65_536) return null
        val bytes = ByteArray(configuration.remaining()).also(configuration::get)
        val offset = spsOffset(bytes, hevc = mime == "video/hevc") ?: return null
        return try {
            if (mime == "video/avc") {
                val sps = NalUnitUtil.parseSpsNalUnit(bytes, offset, bytes.size)
                Color(sps.colorTransfer, sps.bitDepthLumaMinus8 + 8)
            } else {
                val sps = NalUnitUtil.parseH265SpsNalUnit(bytes, offset, bytes.size, null)
                Color(sps.colorTransfer, sps.bitDepthLumaMinus8 + 8)
            }.takeIf { it.bitDepth in 8..16 }
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun spsOffset(bytes: ByteArray, hevc: Boolean): Int? {
        fun isSps(index: Int): Boolean {
            val header = bytes[index].toInt() and 0xff
            return if (hevc) (header shr 1 and 0x3f) == 33 else (header and 0x1f) == 7
        }
        for (index in 0 until bytes.size - 3) {
            if (bytes[index] == 0.toByte() && bytes[index + 1] == 0.toByte() && bytes[index + 2] == 1.toByte() && isSps(index + 3)) {
                return index + 3
            }
        }
        return 0.takeIf { isSps(0) }
    }
}
