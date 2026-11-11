package com.mediacraft.app.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

data class GeoCoordinates(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "纬度必须是 -90 到 90 之间的有限数值" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "经度必须是 -180 到 180 之间的有限数值" }
    }

    fun decimalText(): String = String.format(
        Locale.ROOT,
        "%.6f° %s, %.6f° %s",
        abs(latitude),
        if (latitude < 0) "S" else "N",
        abs(longitude),
        if (longitude < 0) "W" else "E",
    )

    fun dmsText(): String = "${dms(latitude, "N", "S")}, ${dms(longitude, "E", "W")}"

    private fun dms(value: Double, positive: String, negative: String): String {
        val totalHundredths = (abs(value) * 60 * 60 * 100).roundToLong()
        val degrees = totalHundredths / (60 * 60 * 100)
        val remainingAfterDegrees = totalHundredths % (60 * 60 * 100)
        val minutes = remainingAfterDegrees / (60 * 100)
        val secondsHundredths = remainingAfterDegrees % (60 * 100)
        return String.format(
            Locale.ROOT,
            "%d° %02d′ %02d.%02d″ %s",
            degrees,
            minutes,
            secondsHundredths / 100,
            secondsHundredths % 100,
            if (value < 0) negative else positive,
        )
    }
}

sealed interface LocationReadResult {
    data class Found(val coordinates: GeoCoordinates) : LocationReadResult
    data object Missing : LocationReadResult
    data object PermissionRequired : LocationReadResult
    data class Failure(val message: String) : LocationReadResult
}

data class PlaceName(
    val title: String,
    val address: String? = null,
)
