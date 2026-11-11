package com.mediacraft.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaLocationRepositoryTest {
    @Test
    fun iso6709Location_supportsAltitudeAndTrailingSlash() {
        val coordinates = MediaLocationRepository.parseIso6709("+1.4055722+103.7945222+15.5/")

        assertEquals(1.4055722, coordinates?.latitude ?: error("missing latitude"), 0.0000001)
        assertEquals(103.7945222, coordinates.longitude, 0.0000001)
    }

    @Test
    fun iso6709Location_preservesSouthernAndWesternSigns() {
        val coordinates = MediaLocationRepository.parseIso6709("-33.8688-151.2093/")

        assertEquals(-33.8688, coordinates?.latitude ?: error("missing latitude"), 0.0000001)
        assertEquals(-151.2093, coordinates.longitude, 0.0000001)
    }

    @Test
    fun malformedOrOutOfRangeIso6709Location_isRejected() {
        assertNull(MediaLocationRepository.parseIso6709("1.4055722,103.7945222"))
        assertNull(MediaLocationRepository.parseIso6709("+91.0+103.0/"))
        assertNull(MediaLocationRepository.parseIso6709(""))
    }
}
