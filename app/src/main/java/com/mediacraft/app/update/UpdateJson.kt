package com.mediacraft.app.update

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object UpdateJson {
    fun parseRelease(text: String): GitHubRelease = parse("GitHub Release") {
        val json = JSONObject(text)
        val assetsJson = json.requiredArray("assets")
        val assets = buildList {
            for (index in 0 until assetsJson.length()) {
                val asset = assetsJson.requiredObject(index)
                add(
                    ReleaseAsset(
                        name = asset.requiredString("name"),
                        url = asset.requiredString("browser_download_url"),
                        size = asset.requiredLong("size", minimum = 0),
                        digest = asset.optionalString("digest"),
                    ),
                )
            }
        }
        GitHubRelease(
            tag = json.requiredString("tag_name"),
            notes = json.requiredNullableString("body") ?: "",
            pageUrl = json.requiredString("html_url"),
            draft = json.requiredBoolean("draft"),
            prerelease = json.requiredBoolean("prerelease"),
            assets = assets,
        )
    }

    fun parseManifest(text: String): UpdateManifest = parse("update.json") {
        val json = JSONObject(text)
        UpdateManifest(
            schemaVersion = json.requiredInt("schemaVersion", minimum = 1),
            versionName = json.requiredString("versionName"),
            versionCode = json.requiredLong("versionCode", minimum = 1),
            packageName = json.requiredString("packageName"),
            minSdk = json.requiredInt("minSdk", minimum = 1),
            apkName = json.requiredString("apkName"),
            sha256 = json.requiredString("sha256"),
        )
    }

    fun encodeUpdate(update: AppUpdate): String {
        val validated = validateCachedUpdate(update)
        return JSONObject()
        .put("schemaVersion", 1)
        .put("versionName", validated.versionName)
        .put("versionCode", validated.versionCode)
        .put("notes", validated.notes)
        .put("pageUrl", validated.pageUrl)
        .put("sha256", validated.sha256)
        .put("minSdk", validated.minSdk)
        .put(
            "asset",
            JSONObject()
                .put("name", validated.asset.name)
                .put("url", validated.asset.url)
                .put("size", validated.asset.size)
                .put("digest", validated.asset.digest ?: JSONObject.NULL),
        )
        .toString()
    }

    fun decodeUpdate(text: String): AppUpdate = parse("缓存更新信息") {
        val json = JSONObject(text)
        require(json.requiredInt("schemaVersion", minimum = 1) == 1) { "缓存更新信息 schemaVersion 不受支持" }
        val assetJson = json.requiredObject("asset")
        validateCachedUpdate(AppUpdate(
            versionName = json.requiredString("versionName"),
            versionCode = json.requiredLong("versionCode", minimum = 1),
            notes = json.requiredString("notes", allowEmpty = true),
            pageUrl = json.requiredString("pageUrl"),
            asset = ReleaseAsset(
                name = assetJson.requiredString("name"),
                url = assetJson.requiredString("url"),
                size = assetJson.requiredLong("size", minimum = 1),
                digest = assetJson.optionalString("digest"),
            ),
            sha256 = json.requiredString("sha256"),
            minSdk = json.requiredInt("minSdk", minimum = 1),
        ))
    }

    private fun validateCachedUpdate(update: AppUpdate): AppUpdate {
        val release = GitHubRelease(
            tag = "v${update.versionName}",
            notes = update.notes,
            pageUrl = update.pageUrl,
            draft = false,
            prerelease = false,
            assets = listOf(update.asset),
        )
        val manifest = UpdateManifest(
            schemaVersion = 1,
            versionName = update.versionName,
            versionCode = update.versionCode,
            packageName = ReleasePolicy.PACKAGE_NAME,
            minSdk = update.minSdk,
            apkName = update.asset.name,
            sha256 = update.sha256,
        )
        val result = ReleasePolicy.resolve(
            release = release,
            manifest = manifest,
            current = InstalledVersion("0.0.0", 0),
            sdk = update.minSdk,
        )
        require(result is UpdateCheckResult.Available) { "缓存更新信息不是可安装的新版本" }
        return result.update
    }

    private inline fun <T> parse(label: String, block: () -> T): T = try {
        block()
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: JSONException) {
        throw IllegalArgumentException("$label JSON 无效：${error.message}", error)
    }

    private fun JSONObject.requiredString(name: String, allowEmpty: Boolean = false): String {
        val value = requiredValue(name)
        require(value is String) { "$name 必须是字符串" }
        require(allowEmpty || value.isNotBlank()) { "$name 不能为空" }
        return value
    }

    private fun JSONObject.optionalString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        val value = get(name)
        require(value is String && value.isNotBlank()) { "$name 必须是非空字符串或 null" }
        return value
    }

    private fun JSONObject.requiredNullableString(name: String): String? {
        require(has(name)) { "缺少字段 $name" }
        if (isNull(name)) return null
        val value = get(name)
        require(value is String) { "$name 必须是字符串或 null" }
        return value
    }

    private fun JSONObject.requiredBoolean(name: String): Boolean {
        val value = requiredValue(name)
        require(value is Boolean) { "$name 必须是布尔值" }
        return value
    }

    private fun JSONObject.requiredLong(name: String, minimum: Long): Long {
        val value = requiredValue(name)
        val number = when (value) {
            is Byte -> value.toLong()
            is Short -> value.toLong()
            is Int -> value.toLong()
            is Long -> value
            else -> throw IllegalArgumentException("$name 必须是整数")
        }
        require(number >= minimum) { "$name 超出允许范围" }
        return number
    }

    private fun JSONObject.requiredInt(name: String, minimum: Int): Int {
        val value = requiredLong(name, minimum.toLong())
        require(value <= Int.MAX_VALUE) { "$name 超出允许范围" }
        return value.toInt()
    }

    private fun JSONObject.requiredArray(name: String): JSONArray {
        val value = requiredValue(name)
        require(value is JSONArray) { "$name 必须是数组" }
        return value
    }

    private fun JSONObject.requiredObject(name: String): JSONObject {
        val value = requiredValue(name)
        require(value is JSONObject) { "$name 必须是对象" }
        return value
    }

    private fun JSONArray.requiredObject(index: Int): JSONObject {
        val value = get(index)
        require(value is JSONObject) { "assets[$index] 必须是对象" }
        return value
    }

    private fun JSONObject.requiredValue(name: String): Any {
        require(has(name) && !isNull(name)) { "缺少字段 $name" }
        return get(name)
    }
}
