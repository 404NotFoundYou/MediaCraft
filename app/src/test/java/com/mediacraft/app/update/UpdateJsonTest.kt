package com.mediacraft.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateJsonTest {
    @Test
    fun parsesGitHubReleaseWithoutCoercingFieldTypes() {
        val release = UpdateJson.parseRelease(
            """
            {
              "tag_name":"v0.2.0",
              "body":"notes",
              "html_url":"https://github.com/${ReleasePolicy.OWNER}/${ReleasePolicy.REPO}/releases/tag/v0.2.0",
              "draft":false,
              "prerelease":false,
              "assets":[
                {"name":"update.json","browser_download_url":"https://github.com/${ReleasePolicy.OWNER}/${ReleasePolicy.REPO}/releases/download/v0.2.0/update.json","size":512,"digest":null}
              ]
            }
            """.trimIndent(),
        )

        assertEquals("v0.2.0", release.tag)
        assertEquals(1, release.assets.size)
        assertNull(release.assets.single().digest)

        assertThrows(IllegalArgumentException::class.java) {
            UpdateJson.parseRelease(
                """{"tag_name":2,"body":"","html_url":"x","draft":false,"prerelease":false,"assets":[]}""",
            )
        }
    }

    @Test
    fun parsesStrictManifestIntegersAndRequiredFields() {
        val manifest = UpdateJson.parseManifest(
            """{"schemaVersion":1,"versionName":"0.2.0","versionCode":2,"packageName":"${ReleasePolicy.PACKAGE_NAME}","minSdk":30,"apkName":"MediaCraft-0.2.0.apk","sha256":"$SHA"}""",
        )

        assertEquals(2L, manifest.versionCode)
        assertThrows(IllegalArgumentException::class.java) {
            UpdateJson.parseManifest(
                """{"schemaVersion":1,"versionName":"0.2.0","versionCode":2.0,"packageName":"${ReleasePolicy.PACKAGE_NAME}","minSdk":30,"apkName":"MediaCraft-0.2.0.apk","sha256":"$SHA"}""",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateJson.parseManifest("{}")
        }
    }

    @Test
    fun damagedJsonFailsExplicitly() {
        assertThrows(IllegalArgumentException::class.java) { UpdateJson.parseRelease("{broken") }
        assertThrows(IllegalArgumentException::class.java) { UpdateJson.parseManifest("not-json") }
        assertThrows(IllegalArgumentException::class.java) { UpdateJson.decodeUpdate("[]") }
    }

    @Test
    fun nullableGitHubReleaseNotesBecomeEmptyNotes() {
        val release = UpdateJson.parseRelease(
            """{"tag_name":"v0.2.0","body":null,"html_url":"https://github.com/${ReleasePolicy.OWNER}/${ReleasePolicy.REPO}/releases/tag/v0.2.0","draft":false,"prerelease":false,"assets":[]}""",
        )

        assertEquals("", release.notes)
    }

    @Test
    fun cachedUpdateRoundTripPreservesTrustedMetadata() {
        val update = update()

        val decoded = UpdateJson.decodeUpdate(UpdateJson.encodeUpdate(update))

        assertEquals(update, decoded)
    }

    @Test
    fun cachedUpdateRejectsWrongRepositoryHashAndFieldTypes() {
        val valid = UpdateJson.encodeUpdate(update())
        val wrongRepository = valid.replace(
            "https://github.com/${ReleasePolicy.OWNER}/${ReleasePolicy.REPO}/releases/download/",
            "https://github.com/other/repo/releases/download/",
        )
        val wrongHash = valid.replace(SHA, "bad")
        val wrongType = valid.replace("\"versionCode\":2", "\"versionCode\":\"2\"")

        listOf(wrongRepository, wrongHash, wrongType).forEach { text ->
            assertThrows(IllegalArgumentException::class.java) { UpdateJson.decodeUpdate(text) }
        }
    }

    private fun update() = AppUpdate(
        versionName = "0.2.0",
        versionCode = 2,
        notes = "notes",
        pageUrl = "https://github.com/${ReleasePolicy.OWNER}/${ReleasePolicy.REPO}/releases/tag/v0.2.0",
        asset = ReleaseAsset(
            name = "MediaCraft-0.2.0.apk",
            url = "https://github.com/${ReleasePolicy.OWNER}/${ReleasePolicy.REPO}/releases/download/v0.2.0/MediaCraft-0.2.0.apk",
            size = 10_000_000,
            digest = "sha256:$SHA",
        ),
        sha256 = SHA,
        minSdk = 30,
    )

    private companion object {
        val SHA = "a".repeat(64)
    }
}
