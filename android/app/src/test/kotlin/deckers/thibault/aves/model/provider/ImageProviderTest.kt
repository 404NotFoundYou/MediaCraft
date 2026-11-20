package deckers.thibault.aves.model.provider

import deckers.thibault.aves.utils.TimeZones
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Instant
import java.util.TimeZone

class ImageProviderTest {
    @TempDir
    lateinit var folder: File

    @Test
    fun preparingAnOverwriteReadsTheChosenSourceIncludingWhenItIsTheTarget() {
        val source = File(folder, "source/photo.jpg").apply {
            parentFile!!.mkdirs()
            writeText("selected photo")
        }
        val target = File(folder, "photo.jpg").apply { writeText("different photo") }
        for (input in listOf(source, target)) {
            val chosenBytes = input.readText()
            val previousTarget = target.readText()
            val staged = File.createTempFile("export-", null, folder)
            withStagedOutput(staged, prepare = { it.writeText(input.readText() + " with metadata") }) {
                assertEquals(previousTarget, target.readText(), "Do not destroy the target while decoding or writing metadata")
                Files.move(it.toPath(), target.toPath(), REPLACE_EXISTING)
            }
            assertEquals(chosenBytes + " with metadata", target.readText())
            assertFalse(staged.exists())
        }
    }

    @Test
    fun preparationFailureLeavesTheOriginalUntouched() {
        val target = File(folder, "photo.jpg").apply { writeText("original target") }
        val staged = File.createTempFile("export-", null, folder)
        assertThrows(IOException::class.java) {
            withStagedOutput(staged, prepare = {
                it.writeText("incomplete")
                throw IOException("metadata write failed")
            }) { Files.move(it.toPath(), target.toPath(), REPLACE_EXISTING) }
        }
        assertEquals("original target", target.readText())
        assertFalse(staged.exists())
    }

    @Test
    fun providerWriteFailureRestoresOldBytesOrKeepsTheRecoveryCopy() {
        val staged = File(folder, "prepared.jpg").apply { writeText("finished output") }
        val target = File(folder, "photo.jpg").apply { writeText("original target") }
        val backup = File(folder, "recovery.jpg").apply { writeText(target.readText()) }
        for (failRestore in listOf(false, true)) {
            var attempt = 0
            val failure = assertThrows(IOException::class.java) {
                writeReplacement(staged, target, backup) {
                    if (++attempt == 1 || failRestore) {
                        // Simulate a provider that truncates then fails while writing.
                        target.writeText("partial")
                        object : OutputStream() {
                            override fun write(value: Int) { throw IOException("write failed") }
                        }
                    } else target.outputStream()
                }
            }
            assertTrue(failure.message!!.contains(backup.path))
            assertEquals(2, attempt)
            assertEquals("original target", backup.readText())
            if (!failRestore) assertEquals("original target", target.readText())
        }
    }

    @Test
    fun renameTargetsStayInTheSourceDirectory() {
        assertEquals("photo 001", validateRenameName("photo 001"))
        assertThrows(IllegalArgumentException::class.java) { validateRenameName("") }
        assertThrows(IllegalArgumentException::class.java) { validateRenameName("..") }
        assertThrows(IllegalArgumentException::class.java) { validateRenameName("../other/photo") }
        assertThrows(IllegalArgumentException::class.java) { validateRenameName("sub\\photo") }
        assertThrows(IllegalArgumentException::class.java) { validateRenameName(".hidden") }
        assertThrows(IllegalArgumentException::class.java) { validateRenameName("line\nbreak") }
        assertThrows(IllegalArgumentException::class.java) { buildRenameTarget("source", "../escape.jpg") }
    }

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
