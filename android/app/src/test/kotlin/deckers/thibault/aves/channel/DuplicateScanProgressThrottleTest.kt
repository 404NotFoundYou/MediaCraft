package deckers.thibault.aves.channel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DuplicateScanProgressThrottleTest {
    @Test
    fun burstProgressDoesNotFloodChannelOrHideStageChanges() {
        var now = 0L
        val throttle = DuplicateScanProgressThrottle { now }
        val delivered = mutableListOf<Pair<String, Long>>()
        fun submit(stage: String) {
            if (throttle.shouldEmit(stage)) delivered += stage to now
        }

        for (millis in 0L..200L) {
            now = millis
            repeat(50) { submit("hashing") }
        }
        now++
        repeat(50) { submit("verifying") }
        submit("completed")

        assertEquals(
            listOf("hashing" to 0L, "hashing" to 100L, "hashing" to 200L, "verifying" to 201L, "completed" to 201L),
            delivered,
        )
    }
}
