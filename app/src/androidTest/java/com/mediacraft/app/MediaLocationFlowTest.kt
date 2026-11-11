package com.mediacraft.app

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaLocationFlowTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val permissionRule = GrantPermissionRule.grant(*requiredPermissions())

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(permissionRule).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val resolver = context.contentResolver
    private val createdUris = mutableListOf<Uri>()
    private val cacheFiles = mutableListOf<File>()
    private val prefix = "MC_LOCATION_${UUID.randomUUID().toString().take(8)}"
    private val album = "Pictures/MediaCraftLocation/$prefix/"
    private lateinit var locatedName: String
    private lateinit var missingName: String

    @Before
    fun createOwnedLocationFixtures() {
        locatedName = "${prefix}_located.jpg"
        missingName = "${prefix}_missing.jpg"
        insertPhoto(locatedName, jpegWithLocation(1.4055722, 103.7945222))
        insertPhoto(missingName, jpegBytes())

        // MainActivity may have completed its first MediaStore query before the fixtures existed.
        composeRule.activityRule.scenario.recreate()
        waitForThumbnail(locatedName)
        waitForThumbnail(missingName)
    }

    @After
    fun removeOnlyOwnedFixtures() {
        createdUris.forEach { uri -> resolver.delete(uri, null, null) }
        cacheFiles.forEach { file -> file.delete() }
    }

    @Test
    fun locatedPhotoShowsCoordinatesMapEntryAndExplicitGeocoderOutcome() {
        openDetails(locatedName)

        composeRule.onNodeWithTag("media-location").assertExists()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("1° 24′ 20.06″ N, 103° 47′ 40.28″ E")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("media-coordinates").assertExists()
        composeRule.onNodeWithText("加载拍摄位置地图").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("© OpenStreetMap contributors")).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(hasText("地图加载失败", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(20_000) { mapLoadingFinished() }
        val mapFailed = composeRule.onAllNodes(hasText("地图加载失败", substring = true)).fetchSemanticsNodes().isNotEmpty()
        if (mapFailed) {
            println("MediaLocationFlowTest map unavailable; explicit failure fallback was visible")
        } else {
            composeRule.onNodeWithText("© OpenStreetMap contributors").assertExists()
            composeRule.onNodeWithText("© OpenStreetMap contributors").performScrollTo()
            // MapLibre paints its canvas after the document load event and asynchronous tile requests.
            android.os.SystemClock.sleep(8_000)
            val webView = composeRule.runOnIdle {
                WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::findWebView)
            }
            assertNotNull("Map loading finished without a WebView or an explicit failure", webView)
            println("MediaLocationFlowTest map WebView: ${inspectMapDocument(webView!!)}")
        }

        composeRule.waitUntil(12_000) { geocoderFinished() }
        val placeNodes = composeRule.onAllNodes(hasTestTag("media-place-name")).fetchSemanticsNodes()
        if (placeNodes.isNotEmpty()) {
            val place = placeNodes.single().config[SemanticsProperties.Text].joinToString("") { it.text }
            println("MediaLocationFlowTest geocoder success: $place")
        } else {
            println("MediaLocationFlowTest geocoder fallback: ${visibleGeocoderFailure()}")
        }
        takeReportScreenshot("location-test-gps.png")

        closeDetails()
        composeRule.onNodeWithContentDescription(locatedName).assertExists()
    }

    @Test
    fun photoWithoutGpsShowsMissingStateAndNoMapEntry() {
        openDetails(missingName)

        composeRule.onNodeWithTag("media-location").assertExists()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("未记录位置")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("加载拍摄位置地图").assertDoesNotExist()
        composeRule.onNodeWithTag("media-coordinates").assertDoesNotExist()
        composeRule.onNodeWithText("未记录位置").performScrollTo()
        takeReportScreenshot("location-test-missing.png")

        closeDetails()
        composeRule.onNodeWithContentDescription(missingName).assertExists()
    }

    private fun openDetails(name: String) {
        composeRule.onNodeWithContentDescription(name).performClick()
        composeRule.onNodeWithText("媒体详情").assertExists()
    }

    private fun closeDetails() {
        composeRule.onNodeWithText("关闭详情").performClick()
        composeRule.onNodeWithText("媒体详情").assertDoesNotExist()
    }

    private fun waitForThumbnail(name: String) {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodes(hasContentDescription(name)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun geocoderFinished(): Boolean {
        if (composeRule.onAllNodes(hasTestTag("media-place-name")).fetchSemanticsNodes().isNotEmpty()) return true
        val retryVisible = composeRule.onAllNodes(hasText("重新查询地名")).fetchSemanticsNodes().isNotEmpty()
        val explicitFailure = listOf(
            "暂时无法识别地点",
            "地名查询超时",
            "地名查询失败",
        ).any { prefix -> composeRule.onAllNodes(hasText(prefix, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        return retryVisible && explicitFailure
    }

    private fun visibleGeocoderFailure(): String = listOf(
        "暂时无法识别地点",
        "地名查询超时",
        "地名查询失败",
    ).firstNotNullOfOrNull { prefix ->
        composeRule.onAllNodes(hasText(prefix, substring = true)).fetchSemanticsNodes().firstOrNull()
            ?.config?.get(SemanticsProperties.Text)?.joinToString("") { it.text }
    } ?: "unknown failure; coordinate fallback remained visible"

    private fun mapLoadingFinished(): Boolean =
        composeRule.onAllNodes(hasText("地图加载失败", substring = true)).fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodes(hasText("地图加载中…")).fetchSemanticsNodes().isEmpty()

    private fun inspectMapDocument(webView: WebView): String {
        val result = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)
        composeRule.activity.runOnUiThread {
            webView.evaluateJavascript(
                "(function(){" +
                    "var tileResources=performance.getEntriesByType('resource').filter(function(entry){return entry.name.indexOf('tile.openstreetmap.org')>=0;});" +
                    "return JSON.stringify({" +
                    "url:document.URL," +
                    "title:document.title," +
                    "body:document.body?document.body.innerText.substring(0,300):''," +
                    "coords:(document.URL.match(/[?&]marker=([^&]+)/)||[])[1]||null," +
                    "readyState:document.readyState," +
                    "canvases:Array.from(document.querySelectorAll('canvas')).map(function(canvas){var rect=canvas.getBoundingClientRect();return{className:String(canvas.className),width:canvas.width,height:canvas.height,rect:{x:rect.x,y:rect.y,width:rect.width,height:rect.height}};})," +
                    "images:document.images.length," +
                    "completeImages:Array.from(document.images).filter(function(image){return image.complete&&image.naturalWidth>0;}).length," +
                    "osmTileResourceCount:tileResources.length," +
                    "recentOsmTileResources:tileResources.slice(-5).map(function(entry){return{name:entry.name,initiatorType:entry.initiatorType,duration:entry.duration,transferSize:entry.transferSize,decodedBodySize:entry.decodedBodySize,nextHopProtocol:entry.nextHopProtocol,responseStatus:entry.responseStatus||null};})" +
                    "});" +
                    "})()",
            ) { value -> result.set(value); latch.countDown() }
        }
        check(latch.await(5, TimeUnit.SECONDS)) { "Timed out while inspecting the map document" }
        return result.get() ?: "null"
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun takeReportScreenshot(name: String) {
        val output = File(context.cacheDir, name)
        assertTrue("Could not write $name", UiDevice.getInstance(instrumentation).takeScreenshot(output))
        assertTrue("Screenshot $name was empty", output.isFile && output.length() > 0)
        println("MediaLocationFlowTest screenshot: ${output.absolutePath}")
    }

    private fun insertPhoto(name: String, bytes: ByteArray) {
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, album)
                put(MediaStore.MediaColumns.WIDTH, 64)
                put(MediaStore.MediaColumns.HEIGHT, 48)
                put(MediaStore.MediaColumns.DATE_TAKEN, 1_794_355_200_000L)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            },
        ) ?: error("Could not create location test photo")
        createdUris += uri
        resolver.openOutputStream(uri, "w")!!.use { it.write(bytes) }
        check(
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            ) == 1,
        )
    }

    private fun jpegWithLocation(latitude: Double, longitude: Double): ByteArray {
        val file = File(context.cacheDir, "${prefix}_${System.nanoTime()}.jpg")
        cacheFiles += file
        file.writeBytes(jpegBytes())
        ExifInterface(file).apply {
            setLatLong(latitude, longitude)
            saveAttributes()
        }
        return file.readBytes()
    }

    private fun jpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.rgb(55, 135, 105))
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        fun requiredPermissions(): Array<String> = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            )
        }
    }
}
