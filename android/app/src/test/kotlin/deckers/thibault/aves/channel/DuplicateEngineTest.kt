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
    fun verificationRejectsChangedKeeper() = runBlocking {
        val keeper = candidate(1, 3)
        val delete = candidate(2, 3)
        val original = byteArrayOf(1, 2, 3)
        val originalDigest = Sha256Digester().digest(ByteArrayInputStream(original)) {}
        val engine = DuplicateEngine(source(mapOf(keeper.uri to byteArrayOf(1, 2, 4), delete.uri to original)))

        val issues = engine.verify(listOf(keeper, delete), 3, originalDigest)

        assertEquals("changed", issues.single().code)
        assertEquals(keeper.entryId, issues.single().candidate.entryId)
    }

    @Test
    fun verificationRejectsKeeperAndDeleteChangingToSameOtherContent() = runBlocking {
        val keeper = candidate(1, 3)
        val delete = candidate(2, 3)
        val originalDigest = Sha256Digester().digest(ByteArrayInputStream(byteArrayOf(1, 2, 3))) {}
        val changedBytes = byteArrayOf(4, 5, 6)
        val engine = DuplicateEngine(source(mapOf(keeper.uri to changedBytes, delete.uri to changedBytes)))

        val issues = engine.verify(listOf(keeper, delete), 3, originalDigest)

        assertEquals(setOf(keeper.entryId, delete.entryId), issues.map { it.candidate.entryId }.toSet())
        assertTrue(issues.all { it.code == "changed" })
    }

    @Test
    fun verificationCombinesDigestAndByteChecksInOneReadPerPair() = runBlocking {
        val keeper = candidate(1, 3)
        val firstDelete = candidate(2, 3)
        val secondDelete = candidate(3, 3)
        val bytes = byteArrayOf(1, 2, 3)
        val openCounts = mutableMapOf<String, Int>()
        var bytesRead = 0
        val engine = DuplicateEngine(DuplicateInputSource { candidate ->
            openCounts[candidate.uri] = openCounts.getOrDefault(candidate.uri, 0) + 1
            object : FilterInputStream(ByteArrayInputStream(bytes)) {
                override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    super.read(buffer, offset, length).also { if (it > 0) bytesRead += it }
            }
        })
        val digest = Sha256Digester().digest(ByteArrayInputStream(bytes)) {}

        val issues = engine.verify(listOf(keeper, firstDelete, secondDelete), 3, digest)

        assertTrue(issues.isEmpty())
        assertEquals(2, openCounts[keeper.uri])
        assertEquals(1, openCounts[firstDelete.uri])
        assertEquals(1, openCounts[secondDelete.uri])
        assertEquals(4, openCounts.values.sum())
        assertEquals(12, bytesRead)
    }

    @Test
    fun collisionVerificationUsesIndeterminateProgressInsteadOfWrongTotal() = runBlocking {
        val candidates = (1L..4L).map { candidate(it, 3) }
        val bytesByUri = candidates.associate { it.uri to byteArrayOf(1, 2, 3) }
        val progress = mutableListOf<Triple<Int, Int, String>>()

        DuplicateEngine(source(bytesByUri)).scan(candidates) { processed, total, stage ->
            progress += Triple(processed, total, stage)
        }

        assertTrue(progress.zipWithNext().all { (previous, next) -> next.first >= previous.first })
        val verifying = progress.filter { it.third == "verifying" }
        assertEquals(listOf(4, 5, 6), verifying.map { it.first })
        assertTrue(verifying.all { it.second == 0 })
        assertEquals(Triple(7, 7, "completed"), progress.last())
    }

    @Test
    fun largeDuplicateGroupUsesAtMostThreeScansWorthOfInput() = runBlocking {
        val bytes = ByteArray(8 * 1024 * 1024) { it.toByte() }
        val candidates = (1L..8L).map { candidate(it, bytes.size.toLong()) }
        val bytesReadByUri = mutableMapOf<String, Long>()
        val engine = DuplicateEngine(DuplicateInputSource { candidate ->
            object : FilterInputStream(ByteArrayInputStream(bytes)) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    super.read(buffer, offset, length).also { count ->
                        if (count > 0) bytesReadByUri[candidate.uri] = bytesReadByUri.getOrDefault(candidate.uri, 0) + count
                    }
            }
        })

        val result = engine.scan(candidates) { _, _, _ -> }
        val totalRead = bytesReadByUri.values.sum()
        println("Duplicate scan input: $totalRead bytes; representative: ${bytesReadByUri[candidates.first().uri]} bytes")

        assertTrue(result.issues.isEmpty())
        assertEquals(candidates, result.groups.single().members)
        assertTrue(totalRead <= (3L * candidates.size - 2) * bytes.size, "Scanning identical files should not perform all-pairs comparisons")
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
