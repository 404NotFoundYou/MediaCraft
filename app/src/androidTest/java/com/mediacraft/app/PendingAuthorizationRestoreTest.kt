package com.mediacraft.app

import android.Manifest
import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.mediacraft.app.data.MediaRepository
import com.mediacraft.app.data.OperationRepository
import com.mediacraft.app.domain.DuplicateScanner
import com.mediacraft.app.domain.OperationSpec
import com.mediacraft.app.domain.OperationStatus
import com.mediacraft.app.domain.OperationType
import com.mediacraft.app.domain.RenamePlanner
import com.mediacraft.app.ui.MediaCraftViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingAuthorizationRestoreTest {
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(*readMediaPermissions())

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val resolver = application.contentResolver
    private var created: Uri? = null

    @After
    fun cleanup() {
        created?.let { resolver.delete(it, null, null) }
    }

    @Test
    fun restoredPendingAuthorizationIsExcludedFromStartupReconcileAndCanBeCancelled() = runBlocking {
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "pending_restore_${System.nanoTime()}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/MediaCraftPending/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            },
        ) ?: error("无法创建测试媒体")
        created = uri
        resolver.openOutputStream(uri, "w")!!.use { it.write(byteArrayOf(1, 2, 3, 4)) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)

        val media = MediaRepository(application)
        val operations = OperationRepository(application, media)
        val item = media.find(uri.toString()) ?: error("测试媒体不可读")
        val prepared = operations.prepare(
            OperationType.RENAME,
            listOf(OperationSpec(item, targetName = "restored_pending_${System.nanoTime()}.jpg")),
        )
        val handle = SavedStateHandle(
            mapOf(
                MediaCraftViewModel.PENDING_ID_KEY to prepared.id,
                MediaCraftViewModel.PENDING_TYPE_KEY to prepared.type.name,
                MediaCraftViewModel.PENDING_URIS_KEY to ArrayList(prepared.uris),
            ),
        )
        val viewModel = MediaCraftViewModel(
            application,
            handle,
            DuplicateScanner(media),
            RenamePlanner(),
            media,
            operations,
        )

        val waiting = withTimeout(5_000) { viewModel.state.first { it.awaitingSystemAuthorization } }
        assertTrue(waiting.operationPending)
        assertEquals(item.displayName, media.find(item.id)?.displayName)

        viewModel.cancelPendingAuthorization()
        val finished = withTimeout(5_000) { viewModel.state.first { !it.operationPending && !it.awaitingSystemAuthorization } }
        assertFalse(finished.awaitingSystemAuthorization)
        val record = withTimeout(5_000) { operations.records.first { records -> records.any { it.id == prepared.id } } }
            .first { it.id == prepared.id }
        assertEquals(OperationStatus.CANCELLED, record.items.single().status)
        assertEquals(item.displayName, media.find(item.id)?.displayName)
    }

    private companion object {
        fun readMediaPermissions(): Array<String> = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
