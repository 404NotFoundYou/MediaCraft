package deckers.thibault.aves.model.provider

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MediaStoreReconciliationTest {
    @Test
    fun incrementalChecksSkipEmptyInputAndBindOnlyRequestedIds() {
        val queriedBatches = mutableListOf<List<String>>()
        assertEquals(emptyList<Long>(), reconcileMediaRows(emptyList(), null) { _, _, _ -> error("Empty input must not query MediaStore") }.first)

        val knownIds = (1L..501L).toList()
        val (removed, _) = reconcileMediaRows(knownIds, null) { selection, args, onRow ->
            val batch = checkNotNull(args)
            assertEquals("_id IN (${batch.joinToString(",") { "?" }})", selection)
            queriedBatches.add(batch.toList())
            batch.map(String::toLong).filterNot { it == 500L }.forEach { onRow(it, null) }
        }

        assertEquals(listOf(500, 1), queriedBatches.map { it.size })
        assertEquals(knownIds.map(Long::toString), queriedBatches.flatten())
        assertEquals(listOf(500L), removed)
    }

    @Test
    fun populatedStartupCacheChecksMissingIdsAndChangedPathsInOnePass() {
        val knownPaths = (1L..501L).associateWith { "/album/$it.jpg" }
        var queryCount = 0
        val (removed, moved) = reconcileMediaRows(knownPaths.keys, knownPaths) { selection, args, onRow ->
            queryCount++
            assertNull(selection)
            assertNull(args)
            knownPaths.forEach { (id, path) ->
                if (id != 1L) onRow(id, if (id == 2L) "/other/2.jpg" else path)
            }
            onRow(502L, "/new/502.jpg")
        }

        assertEquals(1, queryCount)
        assertEquals(listOf(1L), removed)
        assertEquals(listOf(2L), moved)
    }

    @Test
    fun aLaterQueryFailureCannotPublishAnIncompleteDeletionList() {
        assertThrows(IllegalStateException::class.java) {
            reconcileMediaRows((1L..501L).toList(), null) { _, args, onRow ->
                if (args!!.singleOrNull() == "501") error("Video query failed")
                onRow(1L, null)
            }
        }
    }
}
