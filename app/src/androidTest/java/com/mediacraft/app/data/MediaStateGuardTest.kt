package com.mediacraft.app.data

import android.content.ContentValues
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStateGuardTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private val media = MediaRepository(context)
    private val operations = OperationRepository(context, media)
    private val created = mutableListOf<Uri>()
    private val prefix = "MC_STATE_${UUID.randomUUID().toString().take(8)}"

    @After
    fun removeOnlyCreatedFixtures() {
        created.forEach { resolver.delete(it, null, null) }
    }

    @Test
    fun exactTrashPlanRejectsKeeperChangedToDifferentBytesOfSameSize() = runBlocking {
        val originalBytes = ByteArray(4096) { index -> (index * 31).toByte() }
        val changedBytes = ByteArray(originalBytes.size) { index -> (index * 17 + 9).toByte() }
        val keeperAtScan = insert("keeper.jpg", originalBytes, MediaKind.PHOTO, "image/jpeg")
        val duplicateAtScan = insert("copy.jpg", originalBytes, MediaKind.PHOTO, "image/jpeg")

        resolver.openOutputStream(Uri.parse(keeperAtScan.id), "w")!!.use { it.write(changedBytes) }
        assertNotNull("Changed keeper should remain queryable", media.find(keeperAtScan.id))
        val changedKeeper: MediaItem = media.find(keeperAtScan.id)!!
        assertEquals("The guard must not depend on a size change", keeperAtScan.size, changedKeeper.size)

        val prepared = operations.prepare(
            OperationType.TRASH,
            listOf(
                OperationSpec(
                    media = duplicateAtScan,
                    keeper = keeperAtScan,
                    duplicateKind = DuplicateKind.EXACT,
                ),
            ),
        )
        assertTrue("A stale exact group must not enter the system authorization request", prepared.uris.isEmpty())
        val record = operations.complete(prepared.id, approved = true)
        val result = record.items.single()
        assertEquals(OperationStatus.FAILED, result.status)
        assertTrue(
            "The persisted failure should identify stale identity or failed exact-content verification: ${result.message}",
            result.message.contains("变化") || result.message.contains("复核失败"),
        )
    }

    @Test
    fun h264FixtureExposesComparableVideoAndAudioMetadata() = runBlocking {
        val source = insertVideo("metadata.mp4", "clip_source.mp4")
        val metadata = assertNotNull("H.264 fixture metadata must be present", source.video).let { source.video!! }
        assertTrue("Expected H.264/AVC codec, got ${metadata.codec}", metadata.codec.orEmpty().contains("avc", true))
        assertTrue("Expected approximately 24 fps, got ${metadata.frameRate}", metadata.frameRate != null && metadata.frameRate!! in 23f..25f)
        assertEquals(
            "BT.709 fixture should be classified as SDR. ${videoColorDiagnostics(source)}",
            "SDR",
            metadata.dynamicRange,
        )
        assertEquals("Known non-High10 H.264 profiles should report 8-bit", 8, metadata.bitDepth)
        assertTrue(
            "AAC stereo audio track should be visible; actual signature=${metadata.audioSignature}",
            metadata.audioSignature.orEmpty().contains("audio/mp4a-latm") &&
                metadata.audioSignature.orEmpty().contains("/2/"),
        )
    }

    @Test
    fun fullSourceAndScaledVideoProduceExplainableRecommendation() = runBlocking {
        val source = insertVideo("source.mp4", "clip_source.mp4")
        val scaled = insertVideo("scaled.mp4", "clip_scaled.mp4")
        val result = DuplicateScanner(only(source, scaled)).scan(ScanScope())
        val found = result.groups.singleOrNull { it.kind == DuplicateKind.VIDEO_VERSION }
        assertNotNull("Expected one full-length video-version group; issues=${result.issues}", found)
        val group = found!!
        if (group.recommendedId == null) {
            fail(
                "Comparable fixtures lacked an explainable recommendation. " +
                    "source=${source.video}, scaled=${scaled.video}, warnings=${group.warnings}, reason=${group.reason}",
            )
        }
        assertEquals("The higher-resolution source should be recommended", source.id, group.recommendedId)
        assertTrue("Recommendation reason should expose comparable quality evidence", group.reason.isNotBlank())
    }

    private fun only(vararg items: MediaItem): MediaSource = object : MediaSource {
        override suspend fun list(scope: ScanScope) = MediaListing(items.toList())
        override fun open(item: MediaItem) = media.open(item)
        override suspend fun fingerprint(item: MediaItem) = media.fingerprint(item)
    }

    private suspend fun insertVideo(name: String, asset: String): MediaItem {
        val bytes = instrumentation.context.assets.open(asset).use { it.readBytes() }
        return insert(name, bytes, MediaKind.VIDEO, "video/mp4")
    }

    private suspend fun insert(name: String, bytes: ByteArray, kind: MediaKind, mimeType: String): MediaItem {
        val collection = if (kind == MediaKind.PHOTO) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${prefix}_$name")
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (kind == MediaKind.PHOTO) "Pictures/MediaCraftStateTests/" else "Movies/MediaCraftStateTests/",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: error("Could not create owned test media")
        created += uri
        resolver.openOutputStream(uri, "w")!!.use { it.write(bytes) }
        check(resolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null) == 1)
        return media.find(uri.toString()) ?: error("Inserted fixture is not queryable")
    }

    private fun videoColorDiagnostics(item: MediaItem): String {
        val uri = Uri.parse(item.id)
        var videoFormat: MediaFormat? = null
        resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                for (index in 0 until extractor.trackCount) {
                    val candidate = extractor.getTrackFormat(index)
                    if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                        videoFormat = candidate
                        break
                    }
                }
            } finally {
                extractor.release()
            }
        }
        val format = videoFormat
        val parsed = format?.let(CodecColorParser::read)
        val csdHex = format?.getByteBuffer("csd-0")?.duplicate()?.let { buffer ->
            val bytes = ByteArray(minOf(buffer.remaining(), 256))
            buffer.get(bytes)
            bytes.joinToString("") { "%02x".format(it) } +
                if (buffer.hasRemaining()) "...(${buffer.remaining()} more bytes)" else ""
        }
        val retrieverTransfer = resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)
            } finally {
                retriever.release()
            }
        }
        return "trackFormat=$format; codecColor=$parsed; csd0=$csdHex; retrieverColorTransfer=$retrieverTransfer"
    }
}
