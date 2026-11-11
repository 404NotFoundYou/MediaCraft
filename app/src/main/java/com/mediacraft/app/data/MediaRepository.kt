package com.mediacraft.app.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import android.provider.DocumentsContract
import androidx.exifinterface.media.ExifInterface
import com.mediacraft.app.domain.ContentFingerprint
import com.mediacraft.app.domain.FrameFingerprint
import com.mediacraft.app.domain.MediaIssue
import com.mediacraft.app.domain.MediaItem
import com.mediacraft.app.domain.MediaKind
import com.mediacraft.app.domain.MediaListing
import com.mediacraft.app.domain.MediaSource
import com.mediacraft.app.domain.ScanScope
import com.mediacraft.app.domain.VideoMetadata
import java.io.FileNotFoundException
import java.io.InputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class MediaRepository(context: Context) : MediaSource {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun list(scope: ScanScope): MediaListing = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        val issues = mutableListOf<MediaIssue>()
        if (scope.folderUri != null) {
            queryTree(scope, items, issues)
        } else {
            if (scope.photos) queryCollection(MediaKind.PHOTO, scope.album, items, issues)
            if (scope.videos) queryCollection(MediaKind.VIDEO, scope.album, items, issues)
        }
        MediaListing(items.distinctBy(MediaItem::id), issues)
    }

    suspend fun find(id: String, includeTrashed: Boolean = false): MediaItem? = withContext(Dispatchers.IO) {
        val uri = id.toMediaUri() ?: return@withContext null
        val kind = kindFor(uri) ?: return@withContext null
        try {
            queryOne(uri, kind, includeTrashed)
        } catch (_: SecurityException) {
            null
        } catch (_: FileNotFoundException) {
            null
        }
    }

    suspend fun existingNames(media: MediaItem): Set<String> = withContext(Dispatchers.IO) {
        val uri = media.id.toMediaUri() ?: return@withContext emptySet()
        val volume = uri.pathSegments.firstOrNull().orEmpty().ifBlank { MediaStore.VOLUME_EXTERNAL }
        val collection = MediaStore.Files.getContentUri(volume)
        val names = linkedSetOf<String>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.IS_TRASHED}=0",
            arrayOf(media.relativePath),
            null,
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) names += cursor.getString(nameIndex)
        }
        names
    }

    override fun open(item: MediaItem): InputStream =
        resolver.openInputStream(Uri.parse(item.id)) ?: throw FileNotFoundException(item.id)

    override suspend fun fingerprint(item: MediaItem): ContentFingerprint? = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        when (item.kind) {
            MediaKind.PHOTO -> photoFingerprint(item)
            MediaKind.VIDEO -> videoFingerprint(item)
        }
    }

    internal suspend fun isTrashed(id: String): Boolean? = withContext(Dispatchers.IO) {
        val uri = id.toMediaUri() ?: return@withContext null
        val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) }
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_TRASHED), args, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null
            else cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_TRASHED)) != 0
        }
    }

    internal suspend fun digest(item: MediaItem): String = withContext(Dispatchers.IO) {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        open(item).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    internal suspend fun byteEquals(first: MediaItem, second: MediaItem): Boolean = withContext(Dispatchers.IO) {
        if (first.size != second.size) return@withContext false
        var equal = true
        open(first).buffered().use { left ->
            open(second).buffered().use { right ->
                val a = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                val b = ByteArray(a.size)
                while (true) {
                    coroutineContext.ensureActive()
                    val ar = left.read(a)
                    val br = right.read(b)
                    if (ar != br) { equal = false; break }
                    if (ar < 0) break
                    if ((0 until ar).any { a[it] != b[it] }) { equal = false; break }
                }
            }
        }
        equal
    }

    private suspend fun queryCollection(
        kind: MediaKind,
        album: String?,
        items: MutableList<MediaItem>,
        issues: MutableList<MediaIssue>,
    ) {
        coroutineContext.ensureActive()
        val collection = when (kind) {
            MediaKind.PHOTO -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        val selection = buildString {
            append("${MediaStore.MediaColumns.IS_PENDING}=0 AND ${MediaStore.MediaColumns.IS_TRASHED}=0")
            if (album != null) append(" AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?")
        }
        val signal = CancellationSignal()
        coroutineContext[ kotlinx.coroutines.Job ]?.invokeOnCompletion { signal.cancel() }
        try {
            resolver.query(
                collection,
                PROJECTION,
                selection,
                album?.let { arrayOf(it) },
                "${MediaStore.MediaColumns.DATE_ADDED} ASC, ${MediaStore.MediaColumns._ID} ASC",
                signal,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    runCatching { cursor.toMediaItem(collection, kind) }
                        .onSuccess(items::add)
                        .onFailure { error -> issues += MediaIssue(null, "媒体条目读取失败：${error.userMessage()}") }
                }
            }
        } catch (error: Exception) {
            coroutineContext.ensureActive()
            issues += MediaIssue(null, "${if (kind == MediaKind.PHOTO) "照片" else "视频"}列表不可访问：${error.userMessage()}")
        }
    }

    private suspend fun queryTree(
        scope: ScanScope,
        items: MutableList<MediaItem>,
        issues: MutableList<MediaIssue>,
    ) {
        val treeUri = runCatching { Uri.parse(scope.folderUri) }.getOrNull()
            ?: run {
                issues += MediaIssue(scope.folderUri, "选择的文件夹地址无效")
                return
            }
        val rootDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: run {
                issues += MediaIssue(scope.folderUri, "无法读取选择的文件夹")
                return
            }
        val rootName = documentName(treeUri, rootDocumentId).orEmpty().ifBlank { "已选文件夹" }
        val pending = ArrayDeque<Pair<String, String>>()
        pending += rootDocumentId to "$rootName/"
        try {
            while (pending.isNotEmpty()) {
                coroutineContext.ensureActive()
                val (parentId, parentPath) = pending.removeFirst()
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
                resolver.query(
                    childrenUri,
                    TREE_PROJECTION,
                    null,
                    null,
                    "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} ASC",
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    while (cursor.moveToNext()) {
                        coroutineContext.ensureActive()
                        val documentId = cursor.getString(idIndex)
                        val name = cursor.getString(nameIndex).orEmpty()
                        val mime = cursor.getString(mimeIndex).orEmpty()
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            pending += documentId to (parentPath + name + "/")
                            continue
                        }
                        val kind = when {
                            scope.photos && mime.startsWith("image/", ignoreCase = true) -> MediaKind.PHOTO
                            scope.videos && mime.startsWith("video/", ignoreCase = true) -> MediaKind.VIDEO
                            else -> null
                        } ?: continue
                        val modified = cursor.getLong(modifiedIndex).coerceAtLeast(0L)
                        items += MediaItem(
                            id = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString(),
                            displayName = name,
                            kind = kind,
                            mimeType = mime,
                            relativePath = parentPath,
                            size = cursor.getLong(sizeIndex).coerceAtLeast(0L),
                            width = 0,
                            height = 0,
                            dateTakenMillis = modified,
                            dateAddedMillis = modified,
                            dateModifiedSeconds = modified / 1000L,
                        )
                    }
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (security: SecurityException) {
            issues += MediaIssue(scope.folderUri, "文件夹授权已失效，请重新选择：${security.userMessage()}")
        } catch (error: Exception) {
            issues += MediaIssue(scope.folderUri, "读取文件夹失败：${error.userMessage()}")
        }
    }

    private fun documentName(treeUri: Uri, documentId: String): String? = runCatching {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        resolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun queryOne(uri: Uri, kind: MediaKind, includeTrashed: Boolean): MediaItem? {
        val args = Bundle().apply {
            putInt(
                MediaStore.QUERY_ARG_MATCH_TRASHED,
                if (includeTrashed) MediaStore.MATCH_INCLUDE else MediaStore.MATCH_EXCLUDE,
            )
        }
        return resolver.query(uri, PROJECTION, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.toMediaItem(collectionFor(uri, kind), kind) else null
        }
    }

    private fun Cursor.toMediaItem(collection: Uri, kind: MediaKind): MediaItem {
        val mediaUri = ContentUris.withAppendedId(collection, long(MediaStore.MediaColumns._ID))
        val item = MediaItem(
            id = mediaUri.toString(),
            displayName = string(MediaStore.MediaColumns.DISPLAY_NAME),
            kind = kind,
            mimeType = string(MediaStore.MediaColumns.MIME_TYPE),
            relativePath = string(MediaStore.MediaColumns.RELATIVE_PATH),
            size = long(MediaStore.MediaColumns.SIZE),
            width = int(MediaStore.MediaColumns.WIDTH),
            height = int(MediaStore.MediaColumns.HEIGHT),
            dateTakenMillis = long(MediaStore.MediaColumns.DATE_TAKEN),
            dateAddedMillis = long(MediaStore.MediaColumns.DATE_ADDED) * 1000L,
            dateModifiedSeconds = long(MediaStore.MediaColumns.DATE_MODIFIED),
            generation = long(MediaStore.MediaColumns.GENERATION_MODIFIED),
            durationMillis = long(MediaStore.MediaColumns.DURATION),
            favorite = int(MediaStore.MediaColumns.IS_FAVORITE) != 0,
        )
        return if (kind == MediaKind.VIDEO) item.copy(video = readVideoMetadata(mediaUri, item)) else item
    }

    private fun readVideoMetadata(uri: Uri, item: MediaItem): VideoMetadata {
        var codec: String? = null
        var bitrate: Long? = null
        var frameRate: Float? = null
        var dynamicRange: String? = null
        var bitDepth: Int? = null
        var colorSignalsConflict = false
        val audioTracks = mutableListOf<String>()
        var extractionSucceeded = false
        runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                    for (index in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(index)
                        val mime = format.stringValue(MediaFormat.KEY_MIME) ?: continue
                        if (mime.startsWith("video/") && codec == null) {
                            codec = mime.substringAfter('/')
                            bitrate = format.longValue(MediaFormat.KEY_BIT_RATE)
                            frameRate = format.floatValue(MediaFormat.KEY_FRAME_RATE)
                            val transfer = format.intValue(MediaFormat.KEY_COLOR_TRANSFER)
                            val codedColor = CodecColorParser.read(format)
                            val colorSignals = listOfNotNull(classifyTransfer(transfer), classifyTransfer(codedColor?.transfer)).distinct()
                            colorSignalsConflict = colorSignals.size > 1
                            dynamicRange = colorSignals.singleOrNull()
                            bitDepth = codedColor?.bitDepth ?: inferBitDepth(format)
                        } else if (mime.startsWith("audio/")) {
                            val channels = format.intValue(MediaFormat.KEY_CHANNEL_COUNT)?.toString() ?: "?"
                            val rate = format.intValue(MediaFormat.KEY_SAMPLE_RATE)?.toString() ?: "?"
                            val language = format.stringValue(MediaFormat.KEY_LANGUAGE) ?: "und"
                            audioTracks += "$mime/$channels/$rate/$language"
                        }
                    }
                    extractionSucceeded = true
                } finally {
                    extractor.release()
                }
            }
        }
        if (bitrate == null || dynamicRange == null) {
            runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                        if (bitrate == null) bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                        if (dynamicRange == null && !colorSignalsConflict) dynamicRange = classifyTransfer(
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)?.toIntOrNull(),
                        )
                    } finally {
                        retriever.release()
                    }
                }
            }
        }
        return VideoMetadata(
            codec = codec,
            bitrate = bitrate,
            frameRate = frameRate,
            dynamicRange = dynamicRange,
            bitDepth = bitDepth,
            audioSignature = when {
                audioTracks.isNotEmpty() -> audioTracks.sorted().joinToString(";") +
                    if (audioTracks.size > 1) ";multi-track" else ""
                extractionSucceeded -> "无音轨"
                else -> null
            },
        )
    }

    private fun classifyTransfer(transfer: Int?): String? = when (transfer) {
        MediaFormat.COLOR_TRANSFER_HLG -> "HDR HLG"
        MediaFormat.COLOR_TRANSFER_ST2084 -> "HDR PQ"
        MediaFormat.COLOR_TRANSFER_LINEAR, MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> "SDR"
        else -> null
    }

    private fun inferBitDepth(format: MediaFormat): Int? {
        val profile = format.intValue(MediaFormat.KEY_PROFILE) ?: return null
        val mime = format.stringValue(MediaFormat.KEY_MIME)
        return when {
            mime == "video/avc" && profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 -> 10
            mime == "video/avc" && profile in setOf(
                MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline,
                MediaCodecInfo.CodecProfileLevel.AVCProfileMain,
                MediaCodecInfo.CodecProfileLevel.AVCProfileExtended,
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
            ) -> 8
            mime == "video/hevc" && profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 -> 10
            mime == "video/hevc" && profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain -> 8
            mime == "video/av01" && profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain8 -> 8
            mime == "video/av01" && profile in setOf(
                MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10,
                MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10,
                MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus,
            ) -> 10
            else -> null
        }
    }

    private fun photoFingerprint(item: MediaItem): ContentFingerprint? {
        if (isAnimatedOrMotionPhoto(item)) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(item).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
        val decoded = open(item).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val oriented = orientPhoto(item, decoded)
        return try {
            ContentFingerprint(listOf(frameFingerprint(oriented)))
        } finally {
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
        }
    }

    private fun orientPhoto(item: MediaItem, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            resolver.openFileDescriptor(Uri.parse(item.id), "r")?.use { descriptor ->
                ExifInterface(descriptor.fileDescriptor).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun isAnimatedOrMotionPhoto(item: MediaItem): Boolean {
        if (item.mimeType.equals("image/gif", ignoreCase = true)) return true
        if (item.mimeType.equals("image/webp", ignoreCase = true)) {
            val prefix = runCatching {
                open(item).use { input ->
                    val bytes = ByteArray(64 * 1024)
                    val count = input.read(bytes)
                    if (count <= 0) ByteArray(0) else bytes.copyOf(count)
                }
            }.getOrDefault(ByteArray(0))
            if (prefix.containsAscii("ANIM") || prefix.containsAscii("ANMF")) return true
        }
        val xmp = runCatching {
            resolver.openFileDescriptor(Uri.parse(item.id), "r")?.use { descriptor ->
                ExifInterface(descriptor.fileDescriptor).getAttributeBytes(ExifInterface.TAG_XMP)
            }
        }.getOrNull() ?: return false
        val value = xmp.decodeToString()
        return MOTION_XMP_MARKERS.any(value::contains)
    }

    private fun ByteArray.containsAscii(value: String): Boolean {
        val needle = value.encodeToByteArray()
        if (needle.isEmpty() || size < needle.size) return false
        for (start in 0..size - needle.size) {
            var matches = true
            for (offset in needle.indices) {
                if (this[start + offset] != needle[offset]) { matches = false; break }
            }
            if (matches) return true
        }
        return false
    }

    private suspend fun videoFingerprint(item: MediaItem): ContentFingerprint? {
        val retriever = MediaMetadataRetriever()
        return try {
            resolver.openAssetFileDescriptor(Uri.parse(item.id), "r")?.use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                val durationUs = item.durationMillis.coerceAtLeast(1L) * 1000L
                val frames = listOf(0L, 10L, 30L, 50L, 70L, 90L, 100L).mapNotNull { percent ->
                    coroutineContext.ensureActive()
                    val timeUs = if (percent == 100L) (durationUs - 1L).coerceAtLeast(0L)
                    else durationUs * percent / 100L
                    val frame = retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        256,
                        256,
                    ) ?: return@mapNotNull null
                    try {
                        frameFingerprint(frame)
                    } finally {
                        frame.recycle()
                    }
                }
                if (frames.isEmpty()) null else ContentFingerprint(
                    frames = frames,
                    hasMotion = frames.zipWithNext().any { (a, b) -> java.lang.Long.bitCount(a.hash xor b.hash) >= 8 },
                )
            }
        } finally {
            retriever.release()
        }
    }

    private fun frameFingerprint(source: Bitmap): FrameFingerprint {
        val bitmap = Bitmap.createScaledBitmap(source, 9, 8, true)
        return try {
            val luminance = ArrayList<Int>(72)
            for (y in 0 until 8) for (x in 0 until 9) luminance += bitmap.getPixel(x, y).luma()
            var hash = 0L
            var bit = 0
            for (y in 0 until 8) for (x in 0 until 8) {
                if (luminance[y * 9 + x] > luminance[y * 9 + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
            FrameFingerprint(hash, luminance, detailSharpness(source))
        } finally {
            if (bitmap !== source) bitmap.recycle()
        }
    }

    private fun detailSharpness(source: Bitmap): Double {
        val sample = Bitmap.createScaledBitmap(source, 128, 128, true)
        return try {
            val gray = IntArray(128 * 128)
            for (y in 0 until 128) for (x in 0 until 128) {
                gray[y * 128 + x] = sample.getPixel(x, y).luma()
            }
            var total = 0.0
            var count = 0
            for (y in 1 until 127) for (x in 1 until 127) {
                val center = gray[y * 128 + x]
                total += abs(
                    4 * center - gray[(y - 1) * 128 + x] - gray[(y + 1) * 128 + x] -
                        gray[y * 128 + x - 1] - gray[y * 128 + x + 1],
                )
                count++
            }
            if (count == 0) 0.0 else total / count
        } finally {
            if (sample !== source) sample.recycle()
        }
    }

    private fun Int.luma(): Int {
        val red = this shr 16 and 0xff
        val green = this shr 8 and 0xff
        val blue = this and 0xff
        return (red * 299 + green * 587 + blue * 114) / 1000
    }

    private fun collectionFor(uri: Uri, kind: MediaKind): Uri {
        val volume = uri.pathSegments.firstOrNull().orEmpty().ifBlank { MediaStore.VOLUME_EXTERNAL }
        return when (kind) {
            MediaKind.PHOTO -> MediaStore.Images.Media.getContentUri(volume)
            MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(volume)
        }
    }

    private fun kindFor(uri: Uri): MediaKind? = when {
        uri.pathSegments.any { it == "images" } -> MediaKind.PHOTO
        uri.pathSegments.any { it == "video" } -> MediaKind.VIDEO
        else -> null
    }

    private fun String.toMediaUri(): Uri? = runCatching { Uri.parse(this) }
        .getOrNull()?.takeIf { it.scheme == "content" && it.authority == MediaStore.AUTHORITY }

    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column)).orEmpty()
    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
    private fun MediaFormat.stringValue(key: String): String? = if (containsKey(key)) getString(key) else null
    private fun MediaFormat.intValue(key: String): Int? = if (containsKey(key)) getNumber(key)?.toInt() else null
    private fun MediaFormat.longValue(key: String): Long? = if (containsKey(key)) getNumber(key)?.toLong() else null
    private fun MediaFormat.floatValue(key: String): Float? = if (containsKey(key)) getNumber(key)?.toFloat() else null
    private fun Throwable.userMessage(): String = message?.takeIf(String::isNotBlank) ?: javaClass.simpleName

    companion object {
        private val MOTION_XMP_MARKERS = listOf(
            "GCamera:MotionPhoto=\"1\"",
            "GCamera:MicroVideo=\"1\"",
            "GPhoto:MotionPhoto=\"1\"",
            "Camera:MotionPhoto=\"1\"",
            "Camera:MicroVideo=\"1\"",
        )
        private val PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.GENERATION_MODIFIED,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.IS_FAVORITE,
        )
        private val TREE_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
