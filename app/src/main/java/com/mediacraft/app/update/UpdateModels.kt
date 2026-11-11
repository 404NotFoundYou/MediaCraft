package com.mediacraft.app.update

import java.io.File

data class InstalledVersion(val name: String, val code: Long)
data class ReleaseAsset(val name: String, val url: String, val size: Long, val digest: String? = null)
data class GitHubRelease(
    val tag: String,
    val notes: String,
    val pageUrl: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>,
)
data class UpdateManifest(
    val schemaVersion: Int,
    val versionName: String,
    val versionCode: Long,
    val packageName: String,
    val minSdk: Int,
    val apkName: String,
    val sha256: String,
)
data class AppUpdate(
    val versionName: String,
    val versionCode: Long,
    val notes: String,
    val pageUrl: String,
    val asset: ReleaseAsset,
    val sha256: String,
    val minSdk: Int,
)
sealed interface UpdateCheckResult {
    data class Current(val latest: InstalledVersion) : UpdateCheckResult
    data class Available(val update: AppUpdate) : UpdateCheckResult
    data class NoRelease(val message: String = "暂未发布正式版本") : UpdateCheckResult
    data class Unsupported(val message: String) : UpdateCheckResult
}
data class UpdateDownloadProgress(val downloaded: Long, val total: Long)
data class VerifiedApk(val file: File, val update: AppUpdate)

interface UpdateClient {
    suspend fun check(current: InstalledVersion, sdk: Int): UpdateCheckResult
    suspend fun download(update: AppUpdate, onProgress: (UpdateDownloadProgress) -> Unit): VerifiedApk
    suspend fun verify(file: File, update: AppUpdate): VerifiedApk
}
