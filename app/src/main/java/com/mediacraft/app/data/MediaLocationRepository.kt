package com.mediacraft.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.mediacraft.app.domain.GeoCoordinates
import com.mediacraft.app.domain.LocationReadResult
import com.mediacraft.app.domain.MediaItem
import com.mediacraft.app.domain.MediaKind
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class MediaLocationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    suspend fun read(media: MediaItem): LocationReadResult = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val uri = Uri.parse(media.id)
        if (isMediaStoreUri(uri) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasOriginalLocationPermission()) {
            return@withContext LocationReadResult.PermissionRequired
        }
        try {
            val readableUri = if (isMediaStoreUri(uri) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.setRequireOriginal(uri)
            } else {
                uri
            }
            val coordinates = when (media.kind) {
                MediaKind.PHOTO -> readPhoto(readableUri)
                MediaKind.VIDEO -> readVideo(readableUri)
            }
            coordinates?.let(LocationReadResult::Found) ?: LocationReadResult.Missing
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            if (isMediaStoreUri(uri) && !hasOriginalLocationPermission()) LocationReadResult.PermissionRequired
            else LocationReadResult.Failure("媒体读取权限已失效或无法访问原始文件")
        } catch (error: Exception) {
            LocationReadResult.Failure(error.message?.takeIf(String::isNotBlank) ?: error.javaClass.simpleName)
        }
    }

    private fun readPhoto(uri: Uri): GeoCoordinates? = (resolver.openInputStream(uri)
        ?: throw FileNotFoundException("无法打开媒体内容流")).use { input ->
        ExifInterface(input).latLong?.let { values -> GeoCoordinates(values[0], values[1]) }
    }

    private fun readVideo(uri: Uri): GeoCoordinates? = (resolver.openAssetFileDescriptor(uri, "r")
        ?: throw FileNotFoundException("无法打开视频内容")).use { descriptor ->
        val retriever = MediaMetadataRetriever()
        try {
            if (descriptor.length >= 0) {
                retriever.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            } else {
                retriever.setDataSource(descriptor.fileDescriptor)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let { value ->
                parseIso6709(value) ?: throw IllegalArgumentException("视频位置元数据格式无效：$value")
            }
        } finally {
            retriever.release()
        }
    }

    private fun hasOriginalLocationPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isMediaStoreUri(uri: Uri): Boolean =
        uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY

    internal companion object {
        private val ISO_6709 = Regex("^([+-]\\d{1,2}(?:\\.\\d+)?)([+-]\\d{1,3}(?:\\.\\d+)?)(?:[+-]\\d+(?:\\.\\d+)?)?/?$")

        fun parseIso6709(value: String): GeoCoordinates? {
            val match = ISO_6709.matchEntire(value.trim()) ?: return null
            val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
            val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
            return runCatching { GeoCoordinates(latitude, longitude) }.getOrNull()
        }
    }
}
