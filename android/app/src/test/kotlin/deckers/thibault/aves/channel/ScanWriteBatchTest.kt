package deckers.thibault.aves.channel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ScanWriteBatchTest {
    @Test
    fun restartReplaysOnlyUncommittedItemsAndSuccessFlushesTheTail() {
        val saved = mutableListOf<Int>()
        fun writer() = ScanWriteBatch<Int> { saved.addAll(it) }
        val interrupted = writer()
        (1..79).forEach(interrupted::add)
        assertEquals((1..64).toList(), saved)
        val resumed = writer()
        (65..79).forEach(resumed::add)
        resumed.flush()
        resumed.flush()
        assertEquals((1..79).toList(), saved)
        assertEquals(3, interrupted.commits + resumed.commits)
    }

    @Test
    fun failedCommitDoesNotCountAsProgress() {
        val writes = ScanWriteBatch<Int> { error("disk full") }
        writes.add(1)
        assertThrows<IllegalStateException> { writes.flush() }
        assertEquals(0, writes.commits)
    }
}
