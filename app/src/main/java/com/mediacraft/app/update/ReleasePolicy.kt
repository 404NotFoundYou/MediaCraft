package com.mediacraft.app.update

import java.net.URI
import java.util.Locale

object ReleasePolicy {
    const val OWNER = "404NotFoundYou"
    const val REPO = "MediaCraft"
    const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    const val PACKAGE_NAME = "com.mediacraft.app"
    const val MAX_APK_BYTES = 256L * 1024L * 1024L

    private const val MANIFEST_NAME = "update.json"
    private const val MAX_MANIFEST_BYTES = 2L * 1024L * 1024L
    private val stableVersion = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$")
    private val sha256 = Regex("^[0-9a-fA-F]{64}$")
    private val allowedHosts = setOf(
        "api.github.com",
        "github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com",
        "github-releases.githubusercontent.com",
    )

    fun manifestAsset(release: GitHubRelease): ReleaseAsset {
        validateStableRelease(release)
        val matches = release.assets.filter { it.name == MANIFEST_NAME }
        require(matches.size == 1) { "Release 必须且只能包含一个 $MANIFEST_NAME" }
        val asset = matches.single()
        require(asset.size in 1..MAX_MANIFEST_BYTES) { "$MANIFEST_NAME 大小无效" }
        require(asset.url == expectedAssetUrl(release.tag, MANIFEST_NAME)) { "$MANIFEST_NAME 下载地址未绑定指定仓库和 tag" }
        require(isAllowedNetworkUrl(asset.url)) { "$MANIFEST_NAME 下载地址不受信任" }
        asset.digest?.let { digest ->
            require(digest.startsWith("sha256:", ignoreCase = true) && sha256.matches(digest.substringAfter(':'))) {
                "$MANIFEST_NAME 附件摘要格式无效"
            }
        }
        return asset
    }

    fun resolve(
        release: GitHubRelease,
        manifest: UpdateManifest,
        current: InstalledVersion,
        sdk: Int,
    ): UpdateCheckResult {
        validateStableRelease(release)
        validateManifest(manifest)
        require(current.code >= 0 && current.name.isNotBlank()) { "当前安装版本信息无效" }
        require(sdk > 0) { "Android SDK 版本无效" }
        require(release.tag == "v${manifest.versionName}") { "Release tag 与 update.json 版本不一致" }

        val apkAssets = release.assets.filter { it.name.lowercase(Locale.ROOT).endsWith(".apk") }
        require(apkAssets.size == 1) { "Release 必须且只能包含一个 APK 附件" }
        val asset = apkAssets.single()
        require(asset.name == manifest.apkName) { "APK 附件名称与 update.json 不一致" }
        require(asset.size in 1..MAX_APK_BYTES) { "APK 附件大小无效" }
        require(asset.url == expectedAssetUrl(release.tag, manifest.apkName)) { "APK 下载地址未绑定指定仓库、tag 和文件名" }
        require(isAllowedNetworkUrl(asset.url)) { "APK 下载地址不受信任" }
        asset.digest?.let { digest ->
            require(digest.equals("sha256:${manifest.sha256}", ignoreCase = true)) { "GitHub 附件摘要与 update.json 不一致" }
        }

        val latest = InstalledVersion(manifest.versionName, manifest.versionCode)
        if (manifest.versionCode <= current.code) return UpdateCheckResult.Current(latest)
        if (sdk < manifest.minSdk) {
            return UpdateCheckResult.Unsupported("新版本要求 Android SDK ${manifest.minSdk} 或更高")
        }
        return UpdateCheckResult.Available(
            AppUpdate(
                versionName = manifest.versionName,
                versionCode = manifest.versionCode,
                notes = release.notes,
                pageUrl = release.pageUrl,
                asset = asset,
                sha256 = manifest.sha256.lowercase(Locale.ROOT),
                minSdk = manifest.minSdk,
            ),
        )
    }

    fun isAllowedNetworkUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null) return false
        if (uri.port !in setOf(-1, 443) || uri.rawFragment != null) return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        if (host !in allowedHosts) return false
        if (host == "api.github.com") {
            return uri.rawPath == "/repos/$OWNER/$REPO/releases/latest" && uri.rawQuery == null
        }
        if (host == "github.com") {
            val prefix = "/$OWNER/$REPO/releases/download/"
            return uri.rawQuery == null && uri.rawPath.startsWith(prefix)
        }
        return true
    }

    private fun validateStableRelease(release: GitHubRelease) {
        require(!release.draft) { "草稿 Release 不能用于应用更新" }
        require(!release.prerelease) { "预发布 Release 不能用于稳定版更新" }
        require(release.tag.startsWith("v") && stableVersion.matches(release.tag.drop(1))) { "Release tag 必须是稳定版 vX.Y.Z" }
        require(release.pageUrl == "https://github.com/$OWNER/$REPO/releases/tag/${release.tag}") { "Release 页面未绑定指定仓库和 tag" }
        require(isAllowedPageUrl(release.pageUrl)) { "Release 页面地址不受信任" }
    }

    private fun validateManifest(manifest: UpdateManifest) {
        require(manifest.schemaVersion == 1) { "不支持的 update.json schemaVersion" }
        require(stableVersion.matches(manifest.versionName)) { "versionName 必须是稳定版 X.Y.Z" }
        require(manifest.versionCode > 0) { "versionCode 必须大于 0" }
        require(manifest.packageName == PACKAGE_NAME) { "packageName 不匹配" }
        require(manifest.minSdk in 1..10_000) { "minSdk 无效" }
        require(manifest.apkName == "MediaCraft-${manifest.versionName}.apk") { "apkName 不符合发布命名规则" }
        require(sha256.matches(manifest.sha256)) { "sha256 必须是 64 位十六进制" }
    }

    private fun isAllowedPageUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host == "github.com" && uri.rawUserInfo == null &&
            uri.port in setOf(-1, 443) && uri.rawQuery == null && uri.rawFragment == null
    }

    private fun expectedAssetUrl(tag: String, name: String): String =
        "https://github.com/$OWNER/$REPO/releases/download/$tag/$name"
}
