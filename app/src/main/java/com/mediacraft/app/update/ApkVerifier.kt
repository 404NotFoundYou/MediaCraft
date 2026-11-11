package com.mediacraft.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ApkVerifier(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    suspend fun verify(file: File, update: AppUpdate): VerifiedApk = withContext(Dispatchers.IO) {
        require(file.isFile) { "下载的安装包不存在" }
        require(file.length() == update.asset.size) {
            "安装包长度不符：期望 ${update.asset.size}，实际 ${file.length()}"
        }
        require(file.length() in 1..ReleasePolicy.MAX_APK_BYTES) { "安装包大小超出允许范围" }
        val actualDigest = sha256(file)
        require(digestEquals(actualDigest, update.sha256)) { "安装包 SHA-256 校验失败" }

        val archive = archiveInfo(file) ?: throw IllegalArgumentException("Android 无法解析该 APK")
        require(archive.packageName == ReleasePolicy.PACKAGE_NAME) {
            "安装包包名不符：${archive.packageName}"
        }
        require(archive.longVersionCode == update.versionCode) {
            "安装包版本号不符：${archive.longVersionCode}"
        }
        require(archive.versionName == update.versionName) {
            "安装包版本名称不符：${archive.versionName ?: "缺失"}"
        }
        val archiveMinSdk = archive.applicationInfo?.minSdkVersion
            ?: throw IllegalArgumentException("无法读取安装包最低系统版本")
        require(archiveMinSdk == update.minSdk) { "安装包最低系统版本与更新清单不符" }
        require(Build.VERSION.SDK_INT >= archiveMinSdk) { "当前 Android 版本不支持此安装包" }

        val installed = installedInfo()
        require(archive.longVersionCode > installed.longVersionCode) {
            "拒绝安装相同或更低版本（当前 ${installed.longVersionCode}，安装包 ${archive.longVersionCode}）"
        }
        val installedSigners = signerDigests(installed)
        val archiveSigners = signerDigests(archive)
        require(installedSigners.isNotEmpty() && archiveSigners.isNotEmpty()) { "无法读取应用签名证书" }
        require(installedSigners == archiveSigners) {
            "安装包签名与当前应用不一致；旧调试版迁移到正式签名版需要先卸载旧版"
        }
        VerifiedApk(file, update)
    }

    private fun archiveInfo(file: File): PackageInfo? {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        info?.applicationInfo?.apply {
            sourceDir = file.absolutePath
            publicSourceDir = file.absolutePath
        }
        return info
    }

    private fun installedInfo(): PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getPackageInfo(
            ReleasePolicy.PACKAGE_NAME,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(ReleasePolicy.PACKAGE_NAME, PackageManager.GET_SIGNING_CERTIFICATES)
    }

    private fun signerDigests(info: PackageInfo): Set<String> {
        val signingInfo = info.signingInfo ?: return emptySet()
        return signingInfo.apkContentsSigners.mapTo(linkedSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex()
        }
    }

    private suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun digestEquals(actual: String, expected: String): Boolean = MessageDigest.isEqual(
        actual.lowercase().encodeToByteArray(),
        expected.lowercase().encodeToByteArray(),
    )

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
