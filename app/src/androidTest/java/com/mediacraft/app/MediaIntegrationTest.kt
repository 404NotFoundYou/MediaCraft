package com.mediacraft.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mediacraft.app.data.MediaRepository
import com.mediacraft.app.data.OperationRepository
import com.mediacraft.app.domain.DuplicateKind
import com.mediacraft.app.domain.DuplicateScanner
import com.mediacraft.app.domain.MediaItem
import com.mediacraft.app.domain.MediaKind
import com.mediacraft.app.domain.MediaListing
import com.mediacraft.app.domain.MediaSource
import com.mediacraft.app.domain.OperationSpec
import com.mediacraft.app.domain.OperationStatus
import com.mediacraft.app.domain.OperationType
import com.mediacraft.app.domain.ScanScope
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the media provider and actual decoder; no personal media enters a test scan. */
@RunWith(AndroidJUnit4::class)
class MediaIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private val media = MediaRepository(context)
    private val operations = OperationRepository(context, media)
    private val created = mutableListOf<Uri>()
    private val prefix = "MC_TEST_${UUID.randomUUID().toString().take(8)}"

    @After
    fun removeOnlyCreatedFixtures() {
        created.forEach { uri ->
            resolver.delete(uri, null, null)
        }
    }

    @Test
    fun exactGroupingRequiresActualBytesAndIgnoresNames() = runBlocking {
        val bytes = photoBytes(400, 300)
        val a = insertPhoto("a.jpg", bytes, 400, 300)
        val b = insertPhoto("renamed.jpg", bytes, 400, 300)
        val c = insertPhoto("different.jpg", photoBytes(400, 300, alternate = true), 400, 300)
        val result = DuplicateScanner(only(a, b, c)).scan(ScanScope())
        val exact = result.groups.filter { it.kind == DuplicateKind.EXACT }
        assertEquals("Identical content should form one group even under different names", 1, exact.size)
        assertEquals(setOf(a.id, b.id), exact.single().items.map { it.id }.toSet())
        assertFalse("A similarly shaped but different image must not become byte-identical", exact.any { group -> group.items.any { it.id == c.id } })
    }

    @Test
    fun resizedPhotoIsAnUncertainVersionAndRecommendsMorePixels() = runBlocking {
        val a = insertPhoto("original.jpg", photoBytes(400, 300), 400, 300)
        val b = insertPhoto("scaled.jpg", photoBytes(200, 150), 200, 150)
        val result = DuplicateScanner(only(a, b)).scan(ScanScope())
        val candidate = result.groups.single { it.kind == DuplicateKind.PHOTO_VERSION }
        assertEquals(setOf(a.id, b.id), candidate.items.map { it.id }.toSet())
        assertEquals("The larger version should be explained as more pixels, not an exact duplicate", a.id, candidate.recommendedId)
        assertTrue(candidate.reason.contains("像素"))
        assertFalse(result.groups.any { it.kind == DuplicateKind.EXACT })
    }

    @Test
    fun realVideoDecodingMatchesFullVersionsButRejectsTrimmedClip() = runBlocking {
        val source = insertVideo("source.mp4", "clip_source.mp4")
        val scaled = insertVideo("scaled.mp4", "clip_scaled.mp4")
        val trimmed = insertVideo("trimmed.mp4", "clip_trimmed.mp4")
        val fingerprint = media.fingerprint(source)
        assertNotNull("Actual decoder should produce comparable video samples", fingerprint)
        assertTrue("A first-frame-only signature cannot establish a full video match", fingerprint!!.frames.size >= 5)
        assertNotNull(source.video?.codec)
        val result = DuplicateScanner(only(source, scaled, trimmed)).scan(ScanScope())
        val candidate = result.groups.single { it.kind == DuplicateKind.VIDEO_VERSION }
        assertEquals(setOf(source.id, scaled.id), candidate.items.map { it.id }.toSet())
        assertFalse("A clip missing its ending is not a duplicate version", result.groups.any { group -> group.items.any { it.id == trimmed.id } })
    }

    @Test
    fun renameAndUndoPreserveBytesAndUsePersistedSuccessfulResults() = runBlocking {
        val original = insertPhoto("before.jpg", photoBytes(200, 150), 200, 150)
        val before = digest(original)
        val target = "${prefix}_after.jpg"
        val prepared = operations.prepare(OperationType.RENAME, listOf(OperationSpec(original, target)))
        val result = operations.complete(prepared.id, approved = true)
        assertEquals(OperationStatus.SUCCESS, result.items.single().status)
        val renamed = media.find(original.id)!!
        assertEquals(target, renamed.displayName)
        assertEquals("Renaming must change the name only, never the media bytes", before, digest(renamed))
        val undo = operations.prepare(OperationType.UNDO_RENAME, operations.undoSpecs(result), result.id)
        assertEquals(OperationStatus.SUCCESS, operations.complete(undo.id, true).items.single().status)
        assertEquals(original.displayName, media.find(original.id)!!.displayName)
        assertEquals(before, digest(media.find(original.id)!!))
    }

    @Test
    fun cancellingAuthorizationDoesNotRenameOrReportSuccess() = runBlocking {
        val original = insertPhoto("cancel.jpg", photoBytes(200, 150), 200, 150)
        val prepared = operations.prepare(OperationType.RENAME, listOf(OperationSpec(original, "${prefix}_never.jpg")))
        val result = operations.complete(prepared.id, approved = false)
        assertEquals(OperationStatus.CANCELLED, result.items.single().status)
        assertEquals(original.displayName, media.find(original.id)!!.displayName)
    }

    @Test
    fun systemApprovalAloneDoesNotProveThatTrashOperationSucceeded() = runBlocking {
        val bytes = photoBytes(200, 150)
        val keeper = insertPhoto("keeper.jpg", bytes, 200, 150)
        val duplicate = insertPhoto("copy.jpg", bytes, 200, 150)
        val prepared = operations.prepare(
            OperationType.TRASH,
            listOf(OperationSpec(duplicate, keeper = keeper, duplicateKind = DuplicateKind.EXACT)),
        )
        // No system action is performed. An approved callback cannot itself establish a changed state.
        val result = operations.complete(prepared.id, approved = true)
        assertEquals(OperationStatus.FAILED, result.items.single().status)
        assertNotNull(media.find(duplicate.id))
        assertNotNull(media.find(keeper.id))
    }

    @Test
    fun trashedItemCanBeFoundAndRestoredWithoutRemovingItsKeeper() = runBlocking {
        val bytes = photoBytes(200, 150)
        val keeper = insertPhoto("restore_keeper.jpg", bytes, 200, 150)
        val duplicate = insertPhoto("restore_copy.jpg", bytes, 200, 150)
        val prepared = operations.prepare(
            OperationType.TRASH,
            listOf(OperationSpec(duplicate, keeper = keeper, duplicateKind = DuplicateKind.EXACT)),
        )
        // The fixture is owned by this application; reproduce the provider state changed by consent.
        assertEquals(1, resolver.update(Uri.parse(duplicate.id), ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }, null, null))
        val trashed = operations.complete(prepared.id, true)
        assertEquals(OperationStatus.SUCCESS, trashed.items.single().status)
        assertNotNull("Recovery must explicitly query trashed rows", media.find(duplicate.id, includeTrashed = true))
        val restore = operations.prepare(OperationType.RESTORE, operations.undoSpecs(trashed), trashed.id)
        assertEquals(1, resolver.update(Uri.parse(duplicate.id), ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) }, null, null))
        assertEquals(OperationStatus.SUCCESS, operations.complete(restore.id, true).items.single().status)
        assertNotNull(media.find(duplicate.id))
        assertNotNull("The retained original must survive the complete workflow", media.find(keeper.id))
    }

    @Test
    fun aTrashBatchCannotContainItsOwnKeepers() = runBlocking {
        val bytes = photoBytes(200, 150)
        val a = insertPhoto("cycle_a.jpg", bytes, 200, 150)
        val b = insertPhoto("cycle_b.jpg", bytes, 200, 150)
        val prepared = operations.prepare(OperationType.TRASH, listOf(
            OperationSpec(a, keeper = b, duplicateKind = DuplicateKind.EXACT),
            OperationSpec(b, keeper = a, duplicateKind = DuplicateKind.EXACT),
        ))
        assertTrue("Cross-group choices must never remove the final retained copy", prepared.uris.isEmpty())
        val result = operations.complete(prepared.id, true)
        assertTrue(result.items.all { it.status == OperationStatus.FAILED })
        assertNotNull(media.find(a.id))
        assertNotNull(media.find(b.id))
    }

    @Test
    fun renameCollisionPreservesBothFilesAndPersistsTheFailure() = runBlocking {
        val a = insertPhoto("collision_a.jpg", photoBytes(200, 150), 200, 150)
        val b = insertPhoto("collision_b.jpg", photoBytes(200, 150, alternate = true), 200, 150)
        val digests = listOf(digest(a), digest(b))
        val prepared = operations.prepare(OperationType.RENAME, listOf(OperationSpec(a, targetName = b.displayName)))
        assertTrue(prepared.uris.isEmpty())
        val result = operations.complete(prepared.id, true)
        assertEquals(OperationStatus.FAILED, result.items.single().status)
        assertTrue(result.items.single().message.isNotBlank())
        assertEquals(a.displayName, media.find(a.id)!!.displayName)
        assertEquals(digests, listOf(digest(media.find(a.id)!!), digest(media.find(b.id)!!)))
    }

    @Test
    fun partialUndoKeepsAnExplicitFailureForAMissingFile() = runBlocking {
        val a = insertPhoto("undo_a.jpg", photoBytes(200, 150), 200, 150)
        val b = insertPhoto("undo_b.jpg", photoBytes(200, 150, alternate = true), 200, 150)
        val prepared = operations.prepare(OperationType.RENAME, listOf(
            OperationSpec(a, "${prefix}_undo_new_a.jpg"),
            OperationSpec(b, "${prefix}_undo_new_b.jpg"),
        ))
        val renamed = operations.complete(prepared.id, true)
        assertTrue(renamed.items.all { it.status == OperationStatus.SUCCESS })
        resolver.delete(Uri.parse(b.id), null, null)
        created.remove(Uri.parse(b.id))
        val undoSpecs = operations.undoSpecs(renamed)
        assertEquals("An unavailable item must remain visible in the undo result", 2, undoSpecs.size)
        assertEquals(1, undoSpecs.count { it.validationError != null })
        val undo = operations.prepare(OperationType.UNDO_RENAME, undoSpecs, renamed.id)
        assertEquals(1, undo.uris.size)
        val result = operations.complete(undo.id, true)
        assertEquals(1, result.items.count { it.status == OperationStatus.SUCCESS })
        assertEquals(1, result.items.count { it.status == OperationStatus.FAILED })
        assertEquals(a.displayName, media.find(a.id)!!.displayName)
    }

    @Test
    fun processRecoveryNeverReplaysAnUnapprovedRename() = runBlocking {
        val original = insertPhoto("pending.jpg", photoBytes(200, 150), 200, 150)
        val prepared = operations.prepare(OperationType.RENAME, listOf(OperationSpec(original, "${prefix}_not_approved.jpg")))
        operations.reconcilePending()
        val record = operations.records.first().single { it.id == prepared.id }
        assertEquals(OperationStatus.FAILED, record.items.single().status)
        assertEquals("Recovery reconciles the journal; it must not execute unapproved writes", original.displayName, media.find(original.id)!!.displayName)
    }

    private fun only(vararg items: MediaItem): MediaSource = object : MediaSource {
        override suspend fun list(scope: ScanScope) = MediaListing(items.toList())
        override fun open(item: MediaItem) = media.open(item)
        override suspend fun fingerprint(item: MediaItem) = media.fingerprint(item)
    }

    private suspend fun insertPhoto(name: String, bytes: ByteArray, width: Int, height: Int): MediaItem = insert(
        name, bytes, MediaKind.PHOTO, "image/jpeg", width, height,
    )

    private suspend fun insertVideo(name: String, asset: String): MediaItem {
        val bytes = instrumentation.context.assets.open(asset).use { it.readBytes() }
        val width = if (asset.contains("scaled")) 160 else 320
        val height = if (asset.contains("scaled")) 120 else 240
        return insert(name, bytes, MediaKind.VIDEO, "video/mp4", width, height)
    }

    private suspend fun insert(name: String, bytes: ByteArray, kind: MediaKind, mime: String, width: Int, height: Int): MediaItem {
        val uri = resolver.insert(
            if (kind == MediaKind.PHOTO) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${prefix}_$name")
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (kind == MediaKind.PHOTO) "Pictures/MediaCraftTests/" else "Movies/MediaCraftTests/")
                put(MediaStore.MediaColumns.WIDTH, width)
                put(MediaStore.MediaColumns.HEIGHT, height)
                put(MediaStore.MediaColumns.DATE_TAKEN, 1794355200000L)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            },
        ) ?: error("Could not create owned test media")
        created += uri
        resolver.openOutputStream(uri, "w")!!.use { it.write(bytes) }
        check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
        return media.find(uri.toString()) ?: error("Inserted fixture is not queryable")
    }

    private fun photoBytes(width: Int, height: Int, alternate: Boolean = false): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.scale(width / 400f, height / 300f)
            canvas.drawColor(if (alternate) Color.rgb(180, 70, 45) else Color.rgb(50, 120, 185))
            paint.color = Color.rgb(225, 205, 120)
            canvas.drawRect(0f, 180f, 400f, 300f, paint)
            paint.color = Color.rgb(35, 80, 45)
            canvas.drawRect(if (alternate) 230f else 30f, 70f, if (alternate) 350f else 150f, 240f, paint)
            paint.color = Color.rgb(250, 235, 155)
            canvas.drawCircle(if (alternate) 65f else 310f, 65f, 35f, paint)
            return ByteArrayOutputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out))
                out.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    private fun digest(item: MediaItem): String {
        val hash = MessageDigest.getInstance("SHA-256")
        media.open(item).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                hash.update(buffer, 0, read)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
}
