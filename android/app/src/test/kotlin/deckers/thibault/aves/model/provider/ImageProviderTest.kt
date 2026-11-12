package deckers.thibault.aves.model.provider

import deckers.thibault.aves.utils.TimeZones
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.TimeZone

class ImageProviderTest {
    @Test
    fun captureOffsetsPreservePositiveNegativeAndHalfHourZones() {
        val dateMillis = Instant.parse("1990-02-11T12:00:00Z").toEpochMilli()
        assertEquals("+01:00", TimeZones.format(TimeZone.getTimeZone("Europe/Paris"), dateMillis))
        assertEquals("+00:00", TimeZones.format(TimeZone.getTimeZone("UTC"), dateMillis))
        assertEquals("+05:30", TimeZones.format(TimeZone.getTimeZone("Asia/Kolkata"), dateMillis))
        assertEquals("-06:00", TimeZones.format(TimeZone.getTimeZone("America/Chicago"), dateMillis))
    }

    @Test
    fun captureOffsetsUseThePhotoDateForDaylightSavingTime() {
        val dateMillis = Instant.parse("1990-07-11T12:00:00Z").toEpochMilli()
        assertEquals("+02:00", TimeZones.format(TimeZone.getTimeZone("Europe/Paris"), dateMillis))
        assertEquals("-05:00", TimeZones.format(TimeZone.getTimeZone("America/Chicago"), dateMillis))
    }
}
