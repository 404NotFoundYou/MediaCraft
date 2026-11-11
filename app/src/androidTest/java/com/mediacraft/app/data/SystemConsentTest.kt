package com.mediacraft.app.data

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Environment
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.rule.GrantPermissionRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.mediacraft.app.MainActivity
import com.mediacraft.app.domain.DuplicateKind
import com.mediacraft.app.domain.MediaItem
import com.mediacraft.app.domain.OperationStatus
import com.mediacraft.app.domain.OperationType
import com.mediacraft.app.domain.RenameMode
import com.mediacraft.app.domain.ScanScope
import com.mediacraft.app.ui.MediaCraftViewModel
import java.io.File
import java.util.UUID
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SdkSuppress(minSdkVersion = 33)
@RunWith(AndroidJUnit4::class)
class SystemConsentTest {
    @get:Rule
    val mediaPermission: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_MEDIA_IMAGES,
    )

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val media = MediaRepository(context)
    private val created = mutableListOf<ExternalFixture>()

    @After
    fun removeOnlyExactCreatedFixtures() {
        created.forEach { fixture ->
            fixture.uri?.let { device.executeShellCommand("content delete --uri $it") }
            device.executeShellCommand("rm -f ${fixture.path}")
            fixture.source.delete()
        }
    }

    @Test
    fun activityConfirmsTrashThenRestoresThroughRealSystemConsent() = runBlocking {
        val runId = UUID.randomUUID().toString().replace("-", "").take(12)
        val album = "Pictures/MediaCraftConsentTests/$runId/"
        val bytes = jpegBytes()
        createExternalFixture(album, "MC_AUTH_${runId}_a.jpg", bytes)
        createExternalFixture(album, "MC_AUTH_${runId}_b.jpg", bytes)
        val inserted = waitForAlbum(album, expected = 2)
        created.zip(inserted.sortedBy { it.displayName }).forEach { (fixture, item) -> fixture.uri = item.id }

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val viewModel = scenario.viewModel()
            scenario.onActivity { viewModel.updateScope(photos = true, videos = false, album = album) }
            waitFor("fixtures visible in ViewModel") {
                viewModel.state.value.media.count { it.relativePath == album && it.displayName.startsWith("MC_AUTH_$runId") } == 2
            }
            scenario.onActivity { viewModel.startScan() }
            waitFor("exact duplicate scan result") {
                !viewModel.state.value.scanning && viewModel.state.value.groups.any { group ->
                    group.kind == DuplicateKind.EXACT && group.items.count { it.displayName.startsWith("MC_AUTH_$runId") } == 2
                }
            }
            val group = viewModel.state.value.groups.first { candidate ->
                candidate.kind == DuplicateKind.EXACT && candidate.items.count { it.displayName.startsWith("MC_AUTH_$runId") } == 2
            }
            val keeperId = group.recommendedId ?: group.items.first().id
            val trashedId = group.items.first { it.id != keeperId }.id
            scenario.onActivity {
                viewModel.chooseKeeper(group.id, keeperId)
                viewModel.confirmContent(group.id, true)
                viewModel.prepareTrash()
            }
            clickSystemDialog(approve = true)
            waitFor("trash operation record") {
                viewModel.state.value.records.any { record ->
                    record.type == OperationType.TRASH && record.items.any { it.spec.media.id == trashedId && it.status == OperationStatus.SUCCESS }
                }
            }
            assertEquals(true, media.isTrashed(trashedId))
            assertEquals(false, media.isTrashed(keeperId))

            val trashRecord = viewModel.state.value.records.first { record ->
                record.type == OperationType.TRASH && record.items.any { it.spec.media.id == trashedId }
            }
            scenario.onActivity { viewModel.prepareUndo(trashRecord) }
            clickSystemDialog(approve = true)
            waitFor("restore operation record") {
                viewModel.state.value.records.any { record ->
                    record.type == OperationType.RESTORE && record.sourceId == trashRecord.id &&
                        record.items.any { it.spec.media.id == trashedId && it.status == OperationStatus.SUCCESS }
                }
            }
            assertEquals(false, media.isTrashed(trashedId))
            assertNotNull(media.find(trashedId))
            assertNotNull(media.find(keeperId))
        } finally {
            scenario.close()
        }
    }

    @Test
    fun activityCancelsWriteConsentWithoutRenamingExternalMedia() = runBlocking {
        val runId = UUID.randomUUID().toString().replace("-", "").take(12)
        val album = "Pictures/MediaCraftConsentTests/$runId/"
        val original = "MC_AUTH_${runId}_rename.jpg"
        createExternalFixture(album, original, jpegBytes())
        val inserted = waitForAlbum(album, expected = 1).single()
        created.single().uri = inserted.id

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val viewModel = scenario.viewModel()
            scenario.onActivity {
                viewModel.updateScope(photos = true, videos = false, album = album)
            }
            waitFor("rename fixture visible in ViewModel") {
                viewModel.state.value.media.any { it.id == inserted.id }
            }
            scenario.onActivity {
                viewModel.toggleRenameSelection(inserted.id)
                viewModel.updateRenameOptions(
                    mode = RenameMode.CUSTOM,
                    template = "MC_AUTH_${runId}_changed_{seq:1}",
                    start = 1,
                )
                viewModel.createRenamePreview()
                viewModel.prepareRename()
            }
            clickSystemDialog(approve = false)
            waitFor("cancelled rename record") {
                viewModel.state.value.records.any { record ->
                    record.type == OperationType.RENAME && record.items.any {
                        it.spec.media.id == inserted.id && it.status == OperationStatus.CANCELLED
                    }
                }
            }
            assertEquals(original, media.find(inserted.id)?.displayName)
            assertFalse(viewModel.state.value.awaitingSystemAuthorization)
        } finally {
            scenario.close()
        }
    }

    private fun ActivityScenario<MainActivity>.viewModel(): MediaCraftViewModel {
        lateinit var result: MediaCraftViewModel
        onActivity { activity -> result = ViewModelProvider(activity)[MediaCraftViewModel::class.java] }
        return result
    }

    private suspend fun waitForAlbum(album: String, expected: Int): List<MediaItem> {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            val result = media.list(ScanScope(photos = true, videos = false, album = album)).items
                .filter { it.relativePath == album && it.displayName.startsWith("MC_AUTH_") }
            if (result.size == expected) return result
            Thread.sleep(100)
        }
        fail("Timed out waiting for MediaStore scan for $album")
        return emptyList()
    }

    private fun createExternalFixture(album: String, name: String, bytes: ByteArray) {
        val externalCache = context.externalCacheDir
        assertNotNull("External cache is required for shell-readable test fixtures", externalCache)
        val source = File(externalCache!!, name).apply { writeBytes(bytes) }
        val directory = "${Environment.getExternalStorageDirectory().absolutePath}/$album".trimEnd('/')
        val path = "$directory/$name"
        created += ExternalFixture(path = path, source = source)

        device.executeShellCommand("mkdir -p $directory")
        device.executeShellCommand("cp ${source.absolutePath} $path")
        val actualSize = device.executeShellCommand("stat -c %s $path").trim().toLongOrNull()
        assertEquals("Shell copy did not create the complete external fixture at $path", bytes.size.toLong(), actualSize)
        val broadcast = device.executeShellCommand(
            "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://$path",
        )
        assertTrue("Media scan broadcast failed for $path: $broadcast", broadcast.contains("Broadcast completed"))
    }

    private fun jpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(96, 72, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.rgb(37, 121, 169))
            java.io.ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun clickSystemDialog(approve: Boolean) {
        val androidButton = By.res("android", if (approve) "button1" else "button2")
        var button = device.wait(Until.findObject(androidButton), 10_000)
        if (button == null) {
            val text = if (approve) {
                Pattern.compile("(?i).*(allow|move|restore|trash|允许|移动|恢复|回收).*")
            } else {
                Pattern.compile("(?i).*(cancel|don't allow|取消|不允许).*")
            }
            button = device.wait(Until.findObject(By.text(text)), 5_000)
        }
        if (button == null) {
            val dump = File(context.cacheDir, "system-consent-${System.currentTimeMillis()}.xml")
            device.dumpWindowHierarchy(dump)
            fail("System consent dialog button not found; package=${device.currentPackageName}; dump=${dump.absolutePath}")
            return
        }
        button.click()
        device.waitForIdle()
    }

    private fun waitFor(label: String, timeoutMillis: Long = 30_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        fail("Timed out waiting for $label")
    }

    private data class ExternalFixture(
        val path: String,
        val source: File,
        var uri: String? = null,
    )
}
