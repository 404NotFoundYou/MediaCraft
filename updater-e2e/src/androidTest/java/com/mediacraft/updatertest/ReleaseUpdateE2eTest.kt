package com.mediacraft.updatertest

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import java.util.regex.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseUpdateE2eTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageManager = context.packageManager
    private val device = UiDevice.getInstance(instrumentation)
    private val arguments = InstrumentationRegistry.getArguments()

    private val targetPackage = "com.mediacraft.app"
    private val oldVersionName = arguments.getString("oldVersionName") ?: "0.1.0"
    private val oldVersionCode = arguments.getString("oldVersionCode")?.toLongOrNull() ?: 1L
    private val expectedVersionName = arguments.getString("expectedVersionName") ?: "0.2.0"
    private val expectedVersionCode = arguments.getString("expectedVersionCode")?.toLongOrNull() ?: 2L

    @Test
    fun updatesSignedQaBuildThroughRealGitHubAndSystemInstaller() {
        val before = packageInfoOrFail("QA旧版必须预先安装")
        assertEquals("预装版本名称不符合测试前提", oldVersionName, before.versionName)
        assertEquals("预装版本号不符合测试前提", oldVersionCode, before.longVersionCode)
        Log.i(LOG_TAG, "进入旧版：${before.versionName} (${before.longVersionCode})")

        launchTargetApp()
        clickRequired(By.desc("检查更新"), CHECK_TIMEOUT, "应用更新入口")
        clickRequired(By.text("下载 APK"), CHECK_TIMEOUT, "GitHub latest检查与新版展示")
        clickRequired(By.text("安装更新"), DOWNLOAD_TIMEOUT, "真实APK下载和校验")
        Log.i(LOG_TAG, "下载完成并通过应用校验，已请求安装")

        handleUnknownSourcesIfShown()
        waitForInstallerAndConfirm()
        waitForVersion(expectedVersionName, expectedVersionCode, INSTALL_TIMEOUT)
        Log.i(LOG_TAG, "已安装版本：$expectedVersionName ($expectedVersionCode)")

        openInstalledAppFromInstaller()
        clickRequired(By.desc("检查更新"), CHECK_TIMEOUT, "升级后应用更新入口")
        val current = device.wait(Until.findObject(By.textContains("当前已是最新版本")), CHECK_TIMEOUT)
            ?: failWithDump("升级后真实联网检查未显示已是最新")
        assertTrue("已最新文案未包含目标版本：${current.text}", current.text.contains(expectedVersionName))
        Log.i(LOG_TAG, "已确认最新：${current.text}")
    }

    private fun handleUnknownSourcesIfShown() {
        val deadline = System.currentTimeMillis() + SETTINGS_DETECTION_TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            val currentPackage = device.currentPackageName.orEmpty()
            if (currentPackage == "com.android.settings") {
                val allowFromSource = Pattern.compile("(?i).*(allow from this source|允许来自此来源).*")
                val toggle = waitForAny(
                    listOf(
                        By.pkg("com.android.settings")
                            .checkable(true)
                            .hasDescendant(By.text(allowFromSource)),
                        By.res("com.android.settings", "switch_widget"),
                        By.res("android", "switch_widget"),
                        By.text(allowFromSource).clickable(true),
                    ),
                    10_000,
                ) ?: failWithDump("未知来源设置页没有可用开关")
                if (!toggle.isChecked) toggle.click()
                device.waitForIdle()
                device.pressBack()
                return
            }
            if (isInstallerPackage(currentPackage)) return
            Thread.sleep(POLL_MILLIS)
        }
        failWithDump("点击安装更新后既未打开未知来源设置，也未打开系统安装器")
    }

    private fun waitForInstallerAndConfirm() {
        waitUntil(INSTALLER_OPEN_TIMEOUT, "系统安装器") {
            isInstallerPackage(device.currentPackageName.orEmpty())
        }
        Log.i(LOG_TAG, "进入系统安装器：${device.currentPackageName}")
        val install = waitForAny(
            listOf(
                By.text(Pattern.compile("(?i)^(update|install|更新|安装)$")),
                By.res("android", "button1"),
            ),
            20_000,
        ) ?: failWithDump("系统安装器没有找到更新或安装按钮")
        install.click()
        device.waitForIdle()
    }

    private fun openInstalledAppFromInstaller() {
        val open = device.wait(
            Until.findObject(By.text(Pattern.compile("(?i)^(open|打开)$")).enabled(true)),
            INSTALLER_OPEN_TIMEOUT,
        ) ?: failWithDump("安装成功后没有找到可用的 Open/打开 按钮")
        open.click()
        waitUntil(20_000, "从安装完成页重新打开MediaCraft") {
            device.currentPackageName == targetPackage
        }
        device.waitForIdle()
        Log.i(LOG_TAG, "安装完成页已重新打开MediaCraft")
    }

    private fun launchTargetApp() {
        val intent = packageManager.getLaunchIntentForPackage(targetPackage)
        assertNotNull("找不到 $targetPackage 的启动入口", intent)
        context.startActivity(intent!!.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        waitUntil(20_000, "MediaCraft前台界面") { device.currentPackageName == targetPackage }
        device.waitForIdle()
    }

    private fun waitForVersion(name: String, code: Long, timeoutMillis: Long) {
        waitUntil(timeoutMillis, "PackageManager报告新版本 $name ($code)") {
            runCatching {
                val info = packageInfo()
                info.versionName == name && info.longVersionCode == code
            }.getOrDefault(false)
        }
    }

    private fun packageInfoOrFail(message: String): PackageInfo = try {
        packageInfo()
    } catch (error: PackageManager.NameNotFoundException) {
        failWithDump("$message：$targetPackage 未安装")
    }

    private fun packageInfo(): PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getPackageInfo(targetPackage, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(targetPackage, 0)
    }

    private fun clickRequired(selector: BySelector, timeoutMillis: Long, stage: String) {
        val target = device.wait(Until.findObject(selector), timeoutMillis)
            ?: failWithDump("等待失败：$stage")
        target.click()
        device.waitForIdle()
    }

    private fun waitForAny(selectors: List<BySelector>, timeoutMillis: Long): UiObject2? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            selectors.firstNotNullOfOrNull { device.findObject(it) }?.let { return it }
            Thread.sleep(POLL_MILLIS)
        }
        return null
    }

    private fun waitUntil(timeoutMillis: Long, stage: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(POLL_MILLIS)
        }
        failWithDump("等待超时：$stage；当前前台包=${device.currentPackageName}")
    }

    private fun isInstallerPackage(packageName: String): Boolean =
        packageName.contains("packageinstaller", ignoreCase = true) ||
            packageName == "com.google.android.permissioncontroller" ||
            packageName == "com.android.permissioncontroller"

    private fun failWithDump(message: String): Nothing {
        val dump = File(context.cacheDir, "updater-e2e-${System.currentTimeMillis()}.xml")
        runCatching { device.dumpWindowHierarchy(dump) }
        fail("$message；窗口层级=${dump.absolutePath}")
        error(message)
    }

    companion object {
        private const val POLL_MILLIS = 250L
        private const val CHECK_TIMEOUT = 60_000L
        private const val DOWNLOAD_TIMEOUT = 180_000L
        private const val SETTINGS_DETECTION_TIMEOUT = 20_000L
        private const val INSTALLER_OPEN_TIMEOUT = 30_000L
        private const val INSTALL_TIMEOUT = 90_000L
        private const val LOG_TAG = "MediaCraftUpdaterE2E"
    }
}
