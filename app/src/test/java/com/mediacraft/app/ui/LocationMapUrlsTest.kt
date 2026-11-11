package com.mediacraft.app.ui

import com.mediacraft.app.domain.GeoCoordinates
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationMapUrlsTest {
    @Test
    fun mapAndMarkerUseMediaCoordinatesInsteadOfThePrototypeLocation() {
        val coordinates = GeoCoordinates(-33.8688, 151.2093)
        assertTrue(locationMapUrl(coordinates).contains("mlat=-33.8688&mlon=151.2093"))
        assertTrue(locationMapEmbedUrl(coordinates).contains("marker=-33.8688,151.2093"))
        val bounds = locationMapEmbedUrl(coordinates).substringAfter("bbox=").substringBefore('&').split(',').map(String::toDouble)
        assertTrue(bounds[0] < coordinates.longitude && bounds[2] > coordinates.longitude)
        assertTrue(bounds[1] < coordinates.latitude && bounds[3] > coordinates.latitude)
    }

    @Test
    fun internationalDateLineAndPolesKeepTheMapRequestWithinValidBounds() {
        for (coordinates in listOf(GeoCoordinates(90.0, 180.0), GeoCoordinates(-90.0, -180.0))) {
            val bounds = locationMapEmbedUrl(coordinates).substringAfter("bbox=").substringBefore('&').split(',').map(String::toDouble)
            assertTrue(bounds[0] >= -180 && bounds[2] <= 180)
            assertTrue(bounds[1] >= -90 && bounds[3] <= 90)
            assertTrue(bounds[0] < bounds[2] && bounds[1] < bounds[3])
        }
    }

    @Test
    fun deviceLocaleDoesNotTurnCoordinateDecimalPointsIntoSeparators() {
        val previous = Locale.getDefault()
        try {
            val coordinates = GeoCoordinates(1.4055722222, 103.7945222222)
            val expected = locationMapEmbedUrl(coordinates)
            Locale.setDefault(Locale.GERMANY)
            assertEquals(expected, locationMapEmbedUrl(coordinates))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
