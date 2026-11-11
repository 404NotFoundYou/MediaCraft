package com.mediacraft.app.update

import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitHubUpdateClientTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun latest404IsNoReleaseRatherThanCurrent() = runTest {
        val transport = QueueTransport(response(404))
        val result = client(transport).check(InstalledVersion("0.1.0", 1), 36)
        assertTrue(result is UpdateCheckResult.NoRelease)
        assertEquals(ReleasePolicy.API_URL, transport.requests.single().url)
        assertEquals("2026-03-10", transport.requests.single().headers["X-GitHub-Api-Version"])
        assertEquals("application/vnd.github+json", transport.requests.single().headers["Accept"])
        assertEquals("identity", transport.requests.single().headers["Accept-Encoding"])
        assertTrue(transport.requests.single().headers.keys.none { it.equals("Authorization", true) })
    }

    @Test
    fun anonymousRateLimitIsReportedDistinctly() = runTest {
        val transport = QueueTransport(response(403, headers = mapOf("X-RateLimit-Remaining" to listOf("0"))))
        try {
            client(transport).check(InstalledVersion("0.1.0", 1), 36)
            fail("Rate limit must not be reported as current")
        } catch (expected: UpdateNetworkException) {
            assertTrue(expected.message.orEmpty().contains("次数已用完"))
        }
    }

    @Test
    fun redirectToUntrustedHostIsRejectedBeforeSecondRequest() = runTest {
        val transport = QueueTransport(
            response(302, headers = mapOf("Location" to listOf("https://example.com/update.json"))),
        )
        try {
            client(transport).check(InstalledVersion("0.1.0", 1), 36)
            fail("Untrusted redirect must fail")
        } catch (expected: UpdateNetworkException) {
            assertTrue(expected.message.orEmpty().contains("不受信任"))
            assertEquals(1, transport.requests.size)
        }
    }

    @Test
    fun downloadStreamsExactBytesChecksDigestAndReportsProgress() = runTest {
        val bytes = ByteArray(128 * 1024 + 7) { index -> (index * 17).toByte() }
        val update = update(bytes)
        val transport = QueueTransport(
            response(200, bytes, mapOf("Content-Length" to listOf(bytes.size.toString()))),
        )
        val progress = mutableListOf<UpdateDownloadProgress>()
        val verified = client(transport).download(update, progress::add)
        assertTrue(verified.file.isFile)
        assertTrue(bytes.contentEquals(verified.file.readBytes()))
        assertEquals(UpdateDownloadProgress(0, bytes.size.toLong()), progress.first())
        assertEquals(UpdateDownloadProgress(bytes.size.toLong(), bytes.size.toLong()), progress.last())
        assertTrue(transport.requests.single().headers["Accept"].orEmpty().contains("application/octet-stream"))
        assertEquals("identity", transport.requests.single().headers["Accept-Encoding"])
    }

    @Test
    fun oversizedJsonIsRejectedBeforeParsing() = runTest {
        val declared = 2L * 1024 * 1024 + 1
        val transport = QueueTransport(
            response(200, headers = mapOf("Content-Length" to listOf(declared.toString()))),
        )
        try {
            client(transport).check(InstalledVersion("0.1.0", 1), 36)
            fail("Oversized JSON must fail")
        } catch (expected: UpdateNetworkException) {
            assertTrue(expected.message.orEmpty().contains("2048 KiB"))
        }
    }

    @Test
    fun cancellationRemovesOnlyPartialDownload() = runTest {
        val bytes = ByteArray(256 * 1024) { it.toByte() }
        val update = update(bytes)
        val transport = QueueTransport(response(200, bytes))
        lateinit var downloadJob: Job
        downloadJob = launch {
            try {
                client(transport).download(update) { progress ->
                    if (progress.downloaded > 0) downloadJob.cancel()
                }
                fail("Cancelled download must not complete")
            } catch (_: CancellationException) {
                // Expected: the client checks cancellation between streamed chunks.
            }
        }
        downloadJob.join()
        val updates = File(temporaryFolder.root, "updates")
        assertTrue(updates.listFiles().isNullOrEmpty())
    }

    @Test
    fun wrongManifestGitHubDigestIsRejected() {
        val body = "{\"schemaVersion\":1}".encodeToByteArray()
        val wrong = "sha256:" + "0".repeat(64)
        try {
            verifyAssetDigest("更新清单", body, wrong)
            fail("Manifest body must match GitHub's attachment digest")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("摘要不一致"))
        }
    }

    private fun client(transport: HttpTransport): GitHubUpdateClient {
        val directory = File(temporaryFolder.root, "updates")
        val validation: suspend (File, AppUpdate) -> VerifiedApk = { file, update -> VerifiedApk(file, update) }
        return GitHubUpdateClient(directory, transport, validation)
    }

    private fun update(bytes: ByteArray): AppUpdate {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return AppUpdate(
            versionName = "0.2.0",
            versionCode = 2,
            notes = "test",
            pageUrl = "https://github.com/404NotFoundYou/MediaCraft/releases/tag/v0.2.0",
            asset = ReleaseAsset(
                name = "MediaCraft-0.2.0.apk",
                url = "https://github.com/404NotFoundYou/MediaCraft/releases/download/v0.2.0/MediaCraft-0.2.0.apk",
                size = bytes.size.toLong(),
                digest = "sha256:$digest",
            ),
            sha256 = digest,
            minSdk = 30,
        )
    }

    private fun response(
        status: Int,
        body: ByteArray = ByteArray(0),
        headers: Map<String, List<String>> = emptyMap(),
    ) = HttpResponse(status, headers, ByteArrayInputStream(body)) {}

    private class QueueTransport(vararg responses: HttpResponse) : HttpTransport {
        private val responses = ArrayDeque(responses.toList())
        val requests = mutableListOf<HttpRequest>()

        override fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return responses.removeFirst()
        }
    }
}
