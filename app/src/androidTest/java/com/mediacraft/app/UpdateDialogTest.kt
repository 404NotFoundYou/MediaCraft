package com.mediacraft.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mediacraft.app.ui.UpdateDialog
import com.mediacraft.app.ui.UpdatePhase
import com.mediacraft.app.ui.UpdateViewModel
import com.mediacraft.app.update.AppUpdate
import com.mediacraft.app.update.InstalledVersion
import com.mediacraft.app.update.ReleaseAsset
import com.mediacraft.app.update.UpdateCheckResult
import com.mediacraft.app.update.UpdateClient
import com.mediacraft.app.update.UpdateDownloadProgress
import com.mediacraft.app.update.UpdateJson
import com.mediacraft.app.update.VerifiedApk
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val created = mutableListOf<File>()

    @After
    fun cleanup() {
        created.forEach(File::delete)
    }

    @Test
    fun updateCheckAndDownloadOnlyStartAfterExplicitClicks() {
        val client = FakeClient(application)
        val viewModel = UpdateViewModel(application, SavedStateHandle(), client)
        composeRule.setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            UpdateDialog(state, viewModel)
        }

        composeRule.runOnIdle { viewModel.openDialog() }
        composeRule.onNodeWithText("MediaCraft 更新").assertExists()
        composeRule.runOnIdle { assertEquals(0, client.checks.get()) }

        composeRule.onNodeWithText("检查更新").performClick()
        waitForText("发现版本 ${client.update.versionName} (${client.update.versionCode})")
        composeRule.runOnIdle { assertEquals(1, client.checks.get()); assertEquals(0, client.downloads.get()) }

        composeRule.onNodeWithText("下载 APK").performClick()
        waitForText("安装更新")
        composeRule.onNodeWithText("下载和校验完成。安装前会再次校验。").assertExists()
        composeRule.runOnIdle { assertEquals(1, client.downloads.get()); created += client.downloadedFile }
    }

    @Test
    fun olderGithubReleaseIsDescribedAsOlderThanInstalledVersion() {
        val client = FakeClient(
            application,
            checkResult = UpdateCheckResult.Current(InstalledVersion("0.1.0", 1)),
        )
        val viewModel = UpdateViewModel(application, SavedStateHandle(), client)
        composeRule.setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            UpdateDialog(state, viewModel)
        }
        composeRule.runOnIdle { viewModel.openDialog() }
        composeRule.onNodeWithText("检查更新").performClick()
        waitForText("高于 GitHub 正式发布", substring = true)
        composeRule.onNodeWithText("当前已是最新版本 0.1.0").assertDoesNotExist()
    }

    @Test
    fun restoredVerifiedMetadataDoesNotVerifyOrInstallAutomatically() {
        val client = FakeClient(application)
        client.downloadedFile.parentFile?.mkdirs()
        client.downloadedFile.writeBytes(byteArrayOf(1, 2, 3))
        created += client.downloadedFile
        val handle = SavedStateHandle(
            mapOf(
                UpdateViewModel.UPDATE_JSON_KEY to UpdateJson.encodeUpdate(client.update),
                UpdateViewModel.VERIFIED_PATH_KEY to client.downloadedFile.absolutePath,
                UpdateViewModel.DOWNLOADING_KEY to false,
            ),
        )
        val viewModel = UpdateViewModel(application, handle, client)
        composeRule.setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            UpdateDialog(state, viewModel)
        }

        composeRule.runOnIdle {
            assertEquals(UpdatePhase.READY, viewModel.state.value.phase)
            assertEquals(0, client.verifications.get())
            viewModel.openDialog()
        }
        composeRule.onNodeWithText("安装更新").assertExists()
        composeRule.onNodeWithText("正在重新校验安装包").assertDoesNotExist()
        composeRule.runOnIdle { assertFalse(viewModel.state.value.phase == UpdatePhase.VERIFYING) }
    }

    @Test
    fun restoredUpdateAtCurrentVersionIsClearedInsteadOfOfferedForInstall() {
        val client = FakeClient(application)
        val stale = client.update.copy(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            pageUrl = "https://github.com/404NotFoundYou/MediaCraft/releases/tag/v${BuildConfig.VERSION_NAME}",
            asset = client.update.asset.copy(
                name = "MediaCraft-${BuildConfig.VERSION_NAME}.apk",
                url = "https://github.com/404NotFoundYou/MediaCraft/releases/download/v${BuildConfig.VERSION_NAME}/MediaCraft-${BuildConfig.VERSION_NAME}.apk",
            ),
        )
        client.downloadedFile.parentFile?.mkdirs()
        client.downloadedFile.writeBytes(byteArrayOf(1, 2, 3))
        created += client.downloadedFile
        val handle = SavedStateHandle(
            mapOf(
                UpdateViewModel.UPDATE_JSON_KEY to UpdateJson.encodeUpdate(stale),
                UpdateViewModel.VERIFIED_PATH_KEY to client.downloadedFile.absolutePath,
                UpdateViewModel.DOWNLOADING_KEY to false,
            ),
        )
        val viewModel = UpdateViewModel(application, handle, client)
        composeRule.setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            UpdateDialog(state, viewModel)
        }
        composeRule.runOnIdle { viewModel.openDialog() }
        composeRule.onNodeWithText("安装更新").assertDoesNotExist()
        composeRule.onNodeWithText("无需安装。", substring = true).assertExists()
        composeRule.runOnIdle {
            assertEquals(UpdatePhase.CURRENT, viewModel.state.value.phase)
            assertEquals(null, viewModel.state.value.update)
            assertEquals(null, handle.get<String>(UpdateViewModel.UPDATE_JSON_KEY))
            assertEquals(null, handle.get<String>(UpdateViewModel.VERIFIED_PATH_KEY))
        }
    }

    @Test
    fun activeDownloadCanBeCancelledWithVisibleResult() {
        val client = FakeClient(application, slowDownload = true)
        val viewModel = UpdateViewModel(application, SavedStateHandle(), client)
        composeRule.setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            UpdateDialog(state, viewModel)
        }
        composeRule.runOnIdle { viewModel.openDialog() }
        composeRule.onNodeWithText("检查更新").performClick()
        waitForText("下载 APK")
        composeRule.onNodeWithText("下载 APK").performClick()
        waitForText("取消下载")
        composeRule.onNodeWithText("取消下载").performClick()
        waitForText("下载已取消。")
        composeRule.runOnIdle { assertEquals(UpdatePhase.AVAILABLE, viewModel.state.value.phase) }
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private class FakeClient(
        application: Application,
        private val slowDownload: Boolean = false,
        private val checkResult: UpdateCheckResult? = null,
    ) : UpdateClient {
        val checks = AtomicInteger()
        val downloads = AtomicInteger()
        val verifications = AtomicInteger()
        val downloadedFile = File(application.cacheDir, "updates/MediaCraft-test.apk")
        private val nextVersionCode = BuildConfig.VERSION_CODE.toLong() + 1
        private val nextVersionName = "0.$nextVersionCode.0"
        val update = AppUpdate(
            versionName = nextVersionName,
            versionCode = nextVersionCode,
            notes = "修复重复整理并改进更新流程",
            pageUrl = "https://github.com/404NotFoundYou/MediaCraft/releases/tag/v$nextVersionName",
            asset = ReleaseAsset(
                name = "MediaCraft-$nextVersionName.apk",
                url = "https://github.com/404NotFoundYou/MediaCraft/releases/download/v$nextVersionName/MediaCraft-$nextVersionName.apk",
                size = 3,
            ),
            sha256 = "0".repeat(64),
            minSdk = 30,
        )

        override suspend fun check(current: InstalledVersion, sdk: Int): UpdateCheckResult {
            checks.incrementAndGet()
            return checkResult ?: UpdateCheckResult.Available(update)
        }

        override suspend fun download(update: AppUpdate, onProgress: (UpdateDownloadProgress) -> Unit): VerifiedApk {
            downloads.incrementAndGet()
            onProgress(UpdateDownloadProgress(1, 3))
            delay(if (slowDownload) 60_000 else 20)
            downloadedFile.parentFile?.mkdirs()
            downloadedFile.writeBytes(byteArrayOf(1, 2, 3))
            onProgress(UpdateDownloadProgress(3, 3))
            return VerifiedApk(downloadedFile, update)
        }

        override suspend fun verify(file: File, update: AppUpdate): VerifiedApk {
            verifications.incrementAndGet()
            return VerifiedApk(file, update)
        }
    }
}
