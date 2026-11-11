package com.mediacraft.app.update

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class GitHubUpdateClient internal constructor(
    private val updatesDirectory: File,
    private val transport: HttpTransport,
    private val apkValidation: suspend (File, AppUpdate) -> VerifiedApk,
) : UpdateClient {
    constructor(context: Context) : this(
        File(context.applicationContext.cacheDir, "updates"),
        UrlConnectionTransport(),
        ApkVerifier(context)::verify,
    )

    override suspend fun check(current: InstalledVersion, sdk: Int): UpdateCheckResult = withContext(Dispatchers.IO) {
        val releaseResponse = request(ReleasePolicy.API_URL, ACCEPT_GITHUB_JSON)
        if (releaseResponse.status == 404) {
            releaseResponse.close()
            return@withContext UpdateCheckResult.NoRelease()
        }
        val releaseText = releaseResponse.requireSuccess("检查 GitHub Release").use { response ->
            readText(response, MAX_JSON_BYTES, "GitHub Release 响应")
        }
        val release = UpdateJson.parseRelease(releaseText)
        val manifestAsset = ReleasePolicy.manifestAsset(release)
        val manifestBytes = request(manifestAsset.url, ACCEPT_JSON).requireSuccess("下载更新清单").use { response ->
            readBytes(response, MAX_JSON_BYTES, "更新清单")
        }
        verifyAssetDigest("更新清单", manifestBytes, manifestAsset.digest)
        val manifestText = manifestBytes.toString(Charsets.UTF_8)
        val manifest = UpdateJson.parseManifest(manifestText)
        ReleasePolicy.resolve(release, manifest, current, sdk)
    }

    override suspend fun download(
        update: AppUpdate,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ): VerifiedApk = withContext(Dispatchers.IO) {
        require(update.asset.size in 1..ReleasePolicy.MAX_APK_BYTES) { "更新包大小超出允许范围" }
        require(ReleasePolicy.isAllowedNetworkUrl(update.asset.url)) { "更新包下载地址不受信任" }
        check(updatesDirectory.exists() || updatesDirectory.mkdirs()) { "无法创建更新缓存目录" }
        val part = File(updatesDirectory, "MediaCraft-update-${update.versionCode}.apk.part")
        val target = File(updatesDirectory, "MediaCraft-update-${update.versionCode}.apk")
        requireInsideUpdates(part)
        requireInsideUpdates(target)
        part.delete()
        var committed = false
        try {
            request(update.asset.url, ACCEPT_APK).requireSuccess("下载 APK").use { response ->
                val headerLength = response.header("Content-Length")?.toLongOrNull()
                if (headerLength != null) {
                    require(headerLength == update.asset.size) {
                        "服务器返回的 APK 长度不符：期望 ${update.asset.size}，响应 $headerLength"
                    }
                    require(headerLength <= ReleasePolicy.MAX_APK_BYTES) { "服务器返回的 APK 过大" }
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                onProgress(UpdateDownloadProgress(0, update.asset.size))
                part.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = response.body.read(buffer)
                        if (read < 0) break
                        downloaded += read
                        require(downloaded <= update.asset.size && downloaded <= ReleasePolicy.MAX_APK_BYTES) {
                            "APK 下载内容超过声明大小"
                        }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        onProgress(UpdateDownloadProgress(downloaded, update.asset.size))
                    }
                }
                require(downloaded == update.asset.size) {
                    "APK 下载不完整：期望 ${update.asset.size}，实际 $downloaded"
                }
                val actual = digest.digest().toHex()
                require(digestEquals(actual, update.sha256)) { "APK 下载摘要与更新清单不一致" }
            }
            if (target.exists()) require(target.delete()) { "无法替换旧的更新缓存" }
            require(part.renameTo(target)) { "无法提交已下载的更新包" }
            committed = true
            apkValidation(target, update)
        } catch (error: IOException) {
            if (committed) target.delete()
            throw UpdateNetworkException("APK 下载过程中网络中断：${error.message ?: error.javaClass.simpleName}", error)
        } catch (error: Throwable) {
            if (committed) target.delete()
            throw error
        } finally {
            part.delete()
        }
    }

    override suspend fun verify(file: File, update: AppUpdate): VerifiedApk = withContext(Dispatchers.IO) {
        requireInsideUpdates(file)
        apkValidation(file, update)
    }

    private fun request(initialUrl: String, accept: String): HttpResponse {
        require(ReleasePolicy.isAllowedNetworkUrl(initialUrl)) { "网络地址不受信任" }
        var current = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val response = try {
                transport.execute(
                    HttpRequest(
                        current,
                        mapOf(
                            "Accept" to accept,
                            "Accept-Encoding" to "identity",
                            "X-GitHub-Api-Version" to "2026-03-10",
                            "User-Agent" to "MediaCraft-Android-Update",
                        ),
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                throw UpdateNetworkException("网络连接失败：${error.message ?: error.javaClass.simpleName}", error)
            }
            if (response.status !in REDIRECT_STATUSES) return response
            val location = response.header("Location")
            response.close()
            if (redirectCount == MAX_REDIRECTS) throw UpdateNetworkException("更新下载重定向次数超过 $MAX_REDIRECTS 次")
            if (location.isNullOrBlank()) throw UpdateNetworkException("更新服务器返回了无目标的重定向")
            val next = runCatching { URI(current).resolve(location).toString() }
                .getOrElse { throw UpdateNetworkException("更新服务器返回了无效重定向地址", it) }
            if (!ReleasePolicy.isAllowedNetworkUrl(next)) throw UpdateNetworkException("重定向目标不受信任")
            current = next
        }
        throw UpdateNetworkException("无法完成更新请求")
    }

    private fun HttpResponse.requireSuccess(action: String): HttpResponse {
        if (status in 200..299) return this
        val remaining = header("X-RateLimit-Remaining")
        val reset = header("X-RateLimit-Reset")
        close()
        when {
            status == 403 && remaining == "0" -> throw UpdateNetworkException(
                "$action 失败：GitHub 匿名请求次数已用完" + (reset?.let { "，重置时间戳 $it" } ?: ""),
            )
            status == 429 -> throw UpdateNetworkException("$action 失败：服务器请求过于频繁，请稍后重试")
            status == 404 -> throw UpdateNetworkException("$action 失败：资源不存在")
            else -> throw UpdateNetworkException("$action 失败：HTTP $status")
        }
    }

    private suspend fun readText(response: HttpResponse, limit: Long, label: String): String {
        return readBytes(response, limit, label).toString(Charsets.UTF_8)
    }

    private suspend fun readBytes(response: HttpResponse, limit: Long, label: String): ByteArray {
        val declared = response.header("Content-Length")?.toLongOrNull()
        if (declared != null && declared > limit) throw UpdateNetworkException("$label 超过 ${limit / 1024} KiB 限制")
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        try {
            while (true) {
                coroutineContext.ensureActive()
                val read = response.body.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw UpdateNetworkException("$label 超过 ${limit / 1024} KiB 限制")
                output.write(buffer, 0, read)
            }
        } catch (error: IOException) {
            throw UpdateNetworkException("读取${label}时网络中断：${error.message ?: error.javaClass.simpleName}", error)
        }
        return output.toByteArray()
    }

    private fun requireInsideUpdates(file: File) {
        val root = updatesDirectory.canonicalFile
        val candidate = file.canonicalFile
        require(candidate.parentFile == root) { "安装包不在受控更新缓存目录中" }
    }

    private fun digestEquals(actual: String, expected: String): Boolean = MessageDigest.isEqual(
        actual.lowercase().encodeToByteArray(),
        expected.lowercase().encodeToByteArray(),
    )

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_JSON_BYTES = 2L * 1024 * 1024
        private const val MAX_REDIRECTS = 5
        private const val ACCEPT_GITHUB_JSON = "application/vnd.github+json"
        private const val ACCEPT_JSON = "application/json"
        private const val ACCEPT_APK = "application/vnd.android.package-archive, application/octet-stream"
        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
    }
}

internal fun verifyAssetDigest(label: String, bytes: ByteArray, expected: String?) {
    if (expected == null) return
    require(expected.startsWith("sha256:", ignoreCase = true)) { "$label 的 GitHub 摘要格式不受支持" }
    val expectedHex = expected.substringAfter(':')
    require(expectedHex.matches(Regex("^[0-9a-fA-F]{64}$"))) { "$label 的 GitHub SHA-256 无效" }
    val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    require(
        MessageDigest.isEqual(actual.encodeToByteArray(), expectedHex.lowercase().encodeToByteArray()),
    ) { "$label 的下载内容与 GitHub 附件摘要不一致" }
}
