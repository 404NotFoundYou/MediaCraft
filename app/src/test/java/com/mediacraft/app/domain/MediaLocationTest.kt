package com.mediacraft.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaLocationTest {
    @Test
    fun singaporeCoordinate_hasStableDecimalAndDmsText() {
        val coordinates = GeoCoordinates(1.4055722, 103.7945222)

        assertEquals("1.405572° N, 103.794522° E", coordinates.decimalText())
        assertEquals("1° 24′ 20.06″ N, 103° 47′ 40.28″ E", coordinates.dmsText())
    }

    @Test
    fun southernAndWesternCoordinates_useCorrectHemisphere() {
        val coordinates = GeoCoordinates(-33.8688, -151.2093)

        assertEquals("33.868800° S, 151.209300° W", coordinates.decimalText())
        assertEquals("33° 52′ 07.68″ S, 151° 12′ 33.48″ W", coordinates.dmsText())
    }

    @Test
    fun dmsRounding_carriesSixtySecondsIntoMinutesAndDegrees() {
        val coordinates = GeoCoordinates(12.9999999, 179.9999999)

        assertEquals("13° 00′ 00.00″ N, 180° 00′ 00.00″ E", coordinates.dmsText())
    }

    @Test
    fun invalidAndNonFiniteCoordinates_areRejected() {
        assertThrows(IllegalArgumentException::class.java) { GeoCoordinates(90.0001, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { GeoCoordinates(0.0, -180.0001) }
        assertThrows(IllegalArgumentException::class.java) { GeoCoordinates(Double.NaN, 0.0) }
    }
}
