package com.mediacraft.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ReleasePolicyTest {
    @Test
    fun validStableReleaseWithHigherCodeIsAvailable() {
        val result = ReleasePolicy.resolve(release(), manifest(), InstalledVersion("0.1.0", 1), sdk = 36)

        assertTrue(result is UpdateCheckResult.Available)
        val update = (result as UpdateCheckResult.Available).update
        assertEquals("0.2.0", update.versionName)
        assertEquals(apkAsset(), update.asset)
        assertEquals(SHA, update.sha256)
    }

    @Test
    fun currentOrOlderValidReleaseIsCurrentButInvalidReleaseIsNeverCurrent() {
        assertTrue(ReleasePolicy.resolve(release(), manifest(), InstalledVersion("0.2.0", 2), 36) is UpdateCheckResult.Current)
        assertTrue(ReleasePolicy.resolve(release(), manifest(), InstalledVersion("0.3.0", 3), 36) is UpdateCheckResult.Current)

        val wrongHash = manifest().copy(sha256 = "not-a-sha")
        assertThrows(IllegalArgumentException::class.java) {
            ReleasePolicy.resolve(release(), wrongHash, InstalledVersion("0.3.0", 3), 36)
        }
    }

    @Test
    fun draftAndPrereleaseCannotEnterStableUpdateFlow() {
        assertThrows(IllegalArgumentException::class.java) {
            ReleasePolicy.resolve(release().copy(draft = true), manifest(), InstalledVersion("0.1.0", 1), 36)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReleasePolicy.resolve(release().copy(prerelease = true), manifest(), InstalledVersion("0.1.0", 1), 36)
        }
    }

    @Test
    fun manifestAssetMustBeUniqueAndBoundToThisRepositoryTag() {
        assertEquals("update.json", ReleasePolicy.manifestAsset(release()).name)
        assertThrows(IllegalArgumentException::class.java) {
            ReleasePolicy.manifestAsset(release().copy(assets = listOf(apkAsset())))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReleasePolicy.manifestAsset(
                release().copy(assets = listOf(manifestAsset(), manifestAsset().copy(url = manifestAsset().url + "?copy=1"), apkAsset())),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReleasePolicy.manifestAsset(
                release().copy(assets = listOf(manifestAsset().copy(url = "https://github.com/other/repo/releases/download/v0.2.0/update.json"), apkAsset())),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReleasePolicy.manifestAsset(
                release().copy(assets = listOf(manifestAsset().copy(digest = "invalid"), apkAsset())),
            )
        }
    }

    @Test
    fun tagPackageApkAndDigestMustAgree() {
        val current = InstalledVersion("0.1.0", 1)
        val failures = listOf(
            release().copy(tag = "v0.2.1") to manifest(),
            release() to manifest().copy(packageName = "other.package"),
            release() to manifest().copy(apkName = "other.apk"),
            release().copy(assets = listOf(manifestAsset(), apkAsset(), apkAsset().copy(name = "second.apk"))) to manifest(),
            release().copy(assets = listOf(manifestAsset(), apkAsset().copy(digest = "sha256:${"b".repeat(64)}"))) to manifest(),
        )
        failures.forEach { (candidate, candidateManifest) ->
            assertThrows(IllegalArgumentException::class.java) {
                ReleasePolicy.resolve(candidate, candidateManifest, current, 36)
            }
        }
    }

    @Test
    fun missingOrOversizedApkIsRejectedBeforeVersionComparison() {
        val current = InstalledVersion("0.3.0", 3)
        assertThrows(IllegalArgumentException::class.java) {
            ReleasePolicy.resolve(release().copy(assets = listOf(manifestAsset())), manifest(), current, 36)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReleasePolicy.resolve(
                release().copy(assets = listOf(manifestAsset(), apkAsset().copy(size = ReleasePolicy.MAX_APK_BYTES + 1))),
                manifest(),
                current,
                36,
            )
        }
    }

    @Test
    fun apkUrlMustMatchRepositoryTagAndNameEvenOnAllowedHost() {
        val wrongRepository = apkAsset().copy(
            url = "https://github.com/other/repo/releases/download/v0.2.0/MediaCraft-0.2.0.apk",
        )
        val allowedCdnButNotBoundAsset = apkAsset().copy(
            url = "https://release-assets.githubusercontent.com/unrelated/signed.apk",
        )
        listOf(wrongRepository, allowedCdnButNotBoundAsset).forEach { asset ->
            assertThrows(IllegalArgumentException::class.java) {
                ReleasePolicy.resolve(
                    release().copy(assets = listOf(manifestAsset(), asset)),
                    manifest(),
                    InstalledVersion("0.1.0", 1),
                    36,
                )
            }
        }
    }

    @Test
    fun unsupportedSdkIsExplicitAndDoesNotMasqueradeAsCurrent() {
        val result = ReleasePolicy.resolve(release(), manifest().copy(minSdk = 35), InstalledVersion("0.1.0", 1), sdk = 34)

        assertTrue(result is UpdateCheckResult.Unsupported)
    }

    @Test
    fun networkAllowlistRequiresHttpsNoCredentialsAndStandardPort() {
        assertTrue(ReleasePolicy.isAllowedNetworkUrl(ReleasePolicy.API_URL))
        assertTrue(ReleasePolicy.isAllowedNetworkUrl("https://api.github.com:443/repos/${ReleasePolicy.OWNER}/${ReleasePolicy.REPO}/releases/latest"))
        assertTrue(ReleasePolicy.isAllowedNetworkUrl(apkAsset().url))
        assertTrue(ReleasePolicy.isAllowedNetworkUrl("https://release-assets.githubusercontent.com/path/file?token=opaque"))
        assertFalse(ReleasePolicy.isAllowedNetworkUrl("http://github.com/${ReleasePolicy.OWNER}/${ReleasePolicy.REPO}/releases/download/v0.2.0/file.apk"))
        assertFalse(ReleasePolicy.isAllowedNetworkUrl("https://user@github.com/${ReleasePolicy.OWNER}/${ReleasePolicy.REPO}/releases/download/v0.2.0/file.apk"))
        assertFalse(ReleasePolicy.isAllowedNetworkUrl("https://github.com:444/${ReleasePolicy.OWNER}/${ReleasePolicy.REPO}/releases/download/v0.2.0/file.apk"))
        assertFalse(ReleasePolicy.isAllowedNetworkUrl("https://github.com.evil.example/${ReleasePolicy.OWNER}/${ReleasePolicy.REPO}/releases/download/v0.2.0/file.apk"))
        assertFalse(ReleasePolicy.isAllowedNetworkUrl("https://github.com/other/repo/releases/download/v0.2.0/file.apk"))
    }

    private fun release() = GitHubRelease(
        tag = "v0.2.0",
        notes = "Stable release",
        pageUrl = "https://github.com/${ReleasePolicy.OWNER}/${ReleasePolicy.REPO}/releases/tag/v0.2.0",
        draft = false,
        prerelease = false,
        assets = listOf(manifestAsset(), apkAsset()),
    )

    private fun manifest() = UpdateManifest(
        schemaVersion = 1,
        versionName = "0.2.0",
        versionCode = 2,
        packageName = ReleasePolicy.PACKAGE_NAME,
        minSdk = 30,
        apkName = "MediaCraft-0.2.0.apk",
        sha256 = SHA,
    )

    private fun manifestAsset() = ReleaseAsset(
        name = "update.json",
        url = "https://github.com/${ReleasePolicy.OWNER}/${ReleasePolicy.REPO}/releases/download/v0.2.0/update.json",
        size = 512,
    )

    private fun apkAsset() = ReleaseAsset(
        name = "MediaCraft-0.2.0.apk",
        url = "https://github.com/${ReleasePolicy.OWNER}/${ReleasePolicy.REPO}/releases/download/v0.2.0/MediaCraft-0.2.0.apk",
        size = 10_000_000,
        digest = "sha256:$SHA",
    )

    private companion object {
        val SHA = "a".repeat(64)
    }
}
