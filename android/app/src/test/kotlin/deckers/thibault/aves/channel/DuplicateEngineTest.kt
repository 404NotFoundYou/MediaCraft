package deckers.thibault.aves.channel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.FilterInputStream

class DuplicateEngineTest {
    @Test
    fun digestCollisionDoesNotMisgroupDifferentContent() = runBlocking {
        val first = candidate(1, 3)
        val second = candidate(2, 3)
        val engine = DuplicateEngine(
            source = source(mapOf(first.uri to byteArrayOf(1, 2, 3), second.uri to byteArrayOf(3, 2, 1))),
            digester = DuplicateDigester { _, _ -> "forced-collision" },
        )

        val result = engine.scan(listOf(first, second)) { _, _, _ -> }

        assertTrue(result.groups.isEmpty())
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun emptyFilesCanFormAnExactDuplicateGroup() = runBlocking {
        val first = candidate(1, 0)
        val second = candidate(2, 0)
        val result = DuplicateEngine(source(mapOf(first.uri to byteArrayOf(), second.uri to byteArrayOf())))
            .scan(listOf(first, second)) { _, _, _ -> }

        assertEquals(1, result.groups.size)
        assertEquals(setOf(1L, 2L), result.groups.single().members.map { it.entryId }.toSet())
    }

    @Test
    fun unreadableCandidateIsReportedAndNeverGrouped() = runBlocking {
        val readable = candidate(1, 3)
        val unreadable = candidate(2, 3)
        val engine = DuplicateEngine(DuplicateInputSource { candidate ->
            if (candidate == unreadable) throw FileNotFoundException("missing")
            ByteArrayInputStream(byteArrayOf(1, 2, 3))
        })

        val result = engine.scan(listOf(readable, unreadable)) { _, _, _ -> }

        assertTrue(result.groups.isEmpty())
        assertEquals("read-failed", result.issues.single().code)
        assertEquals(unreadable.entryId, result.issues.single().candidate.entryId)
    }

    @Test
    fun cancellationIsPropagated() {
        val first = candidate(1, 3)
        val second = candidate(2, 3)
        val engine = DuplicateEngine(
            source = source(mapOf(first.uri to byteArrayOf(1, 2, 3), second.uri to byteArrayOf(1, 2, 3))),
            digester = DuplicateDigester { _, _ -> throw CancellationException("cancelled") },
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { engine.scan(listOf(first, second)) { _, _, _ -> } }
        }
    }

    @Test
    fun verificationRejectsChangedDeleteMember() = runBlocking {
        val keeper = candidate(1, 3)
        val changed = candidate(2, 3)
        val originalDigest = Sha256Digester().digest(ByteArrayInputStream(byteArrayOf(1, 2, 3))) {}
        val engine = DuplicateEngine(source(mapOf(keeper.uri to byteArrayOf(1, 2, 3), changed.uri to byteArrayOf(1, 2, 4))))

        val issues = engine.verify(listOf(keeper, changed), 3, originalDigest)

        assertEquals("changed", issues.single().code)
        assertEquals(changed.entryId, issues.single().candidate.entryId)
    }

    @Test
    fun shortReadsDoNotCauseEqualContentToBeMissed() = runBlocking {
        val first = candidate(1, 5)
        val second = candidate(2, 5)
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val engine = DuplicateEngine(DuplicateInputSource { candidate ->
            val input = ByteArrayInputStream(bytes)
            if (candidate == first) input else object : FilterInputStream(input) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, minOf(1, length))
            }
        })

        val result = engine.scan(listOf(first, second)) { _, _, _ -> }

        assertEquals(1, result.groups.size)
    }

    @Test
    fun emptyVerificationIsInvalid() = runBlocking {
        val issues = DuplicateEngine(source(emptyMap())).verify(emptyList(), 0, "digest")

        assertEquals("invalid-selection", issues.single().code)
    }

    @Test
    fun verificationRejectsRepeatedKeeperAsDeleteMember() = runBlocking {
        val keeper = candidate(1, 3)
        val issues = DuplicateEngine(source(mapOf(keeper.uri to byteArrayOf(1, 2, 3))))
            .verify(listOf(keeper, keeper), 3, "digest")

        assertEquals("invalid-selection", issues.single().code)
    }

    @Test
    fun repeatedInputCannotCreateSelfDuplicateGroup() = runBlocking {
        val candidate = candidate(1, 3)
        val result = DuplicateEngine(source(mapOf(candidate.uri to byteArrayOf(1, 2, 3))))
            .scan(listOf(candidate, candidate)) { _, _, _ -> }

        assertTrue(result.groups.isEmpty())
        assertEquals("duplicate-input", result.issues.single().code)
    }

    private fun candidate(id: Long, size: Long) = DuplicateCandidate(id, "content://test/$id", size, null)

    private fun source(bytesByUri: Map<String, ByteArray>) = DuplicateInputSource { candidate ->
        ByteArrayInputStream(bytesByUri.getValue(candidate.uri))
    }
}
