package com.mediacraft.app

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryPrototypeTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val permissions = GrantPermissionRule.grant(*when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    })
    @get:Rule val rules: TestRule = RuleChain.outerRule(permissions).around(composeRule)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val created = mutableListOf<Uri>()
    private val prefix = "MC_PROTOTYPE_${UUID.randomUUID().toString().take(8)}"
    private val newest = "${prefix}_01.jpg"
    private val videoName = "${prefix}_video.mp4"
    private var expectedTotal = 0
    private var expectedVideos = 0

    @Before
    fun createOnlyOwnedMedia() {
        expectedVideos = countMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI) + 1
        expectedTotal = countMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI) + expectedVideos + 6
        repeat(6) { index ->
            val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val palettes = listOf(0xFF5F9D8E.toInt(), 0xFFD68863.toInt(), 0xFF8096D1.toInt(), 0xFF729B57.toInt(), 0xFFB76F83.toInt(), 0xFFE7A94B.toInt())
            paint.shader = LinearGradient(0f, 0f, 600f, 600f, Color.rgb(225, 238, 225), palettes[index], Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 600f, 600f, paint)
            paint.shader = null
            paint.color = Color.argb(70, 255, 255, 255)
            canvas.drawCircle(460f, 130f, 100f, paint)
            paint.color = Color.argb(100, 30, 65, 48)
            canvas.drawOval(-160f, 330f, 800f, 900f, paint)
            val bytes = ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output); output.toByteArray() }
            bitmap.recycle()
            insert("${prefix}_0${index + 1}.jpg", "image/jpeg", bytes, 1_794_400_000_000L - index * 1000)
        }
        insert(videoName, "video/mp4", instrumentation.context.assets.open("clip_source.mp4").use { it.readBytes() }, 1_794_399_990_000L)
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(20_000) { composeRule.onAllNodes(hasText("按拍摄时间排列 · 共 $expectedTotal 项")).fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun removeOnlyOwnedMedia() { created.forEach { context.contentResolver.delete(it, null, null) } }

    @Test
    fun galleryKeepsPrototypeHierarchyAndLargeSquareTilesThenOpensRealDetails() {
        // The gallery must lead with browsing, so scanning controls cannot replace the prototype hero.
        composeRule.onNodeWithTag("gallery-hero").assertIsDisplayed()
        composeRule.onNodeWithText("从浏览开始，再决定整理什么。").assertIsDisplayed()
        composeRule.onNodeWithTag("gallery-overview").assertIsDisplayed()
        composeRule.onNodeWithTag("dedup-list").assertDoesNotExist()
        screenshot("prototype-gallery-top.png")
        composeRule.onNodeWithTag("gallery-grid").performScrollToNode(hasContentDescription(newest))
        val tile = composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("gallery-tile"))[0].fetchSemanticsNode().boundsInRoot
        assertTrue("Media should be a large square tile rather than the former 72dp thumbnail: $tile", tile.width >= 140 * context.resources.displayMetrics.density && kotlin.math.abs(tile.width - tile.height) < 2)
        screenshot("prototype-gallery-grid.png")
        composeRule.onNodeWithContentDescription(newest).performClick()
        composeRule.onNodeWithText("媒体详情").assertIsDisplayed()
        composeRule.onNodeWithText("尺寸").assertExists()
        composeRule.onNodeWithText("文件大小").assertExists()
        screenshot("prototype-media-detail.png")
        composeRule.onNodeWithText("关闭详情").performClick()
        composeRule.onNodeWithContentDescription(newest).assertExists()
        composeRule.onNodeWithTag("gallery-grid").performScrollToIndex(0)
        composeRule.onNodeWithText("扫描重复项").performClick()
        composeRule.onNodeWithTag("dedup-list").assertIsDisplayed()
    }

    @Test
    fun filtersChangeRealMediaAndHistoryRemainsReachableFromMoreMenu() {
        composeRule.onNodeWithTag("gallery-grid").performScrollToNode(hasText("视频"))
        // Filter chips change the actual permission-scoped listing, not just their active color.
        composeRule.onNode(hasText("视频") and androidx.compose.ui.test.hasClickAction()).performClick()
        composeRule.onNode(hasText("视频") and androidx.compose.ui.test.hasClickAction()).assertIsDisplayed()
        composeRule.onNodeWithText("全部").performClick()
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("活动记录").performClick()
        composeRule.onNodeWithTag("history-list").assertIsDisplayed()
        screenshot("prototype-history.png")
        composeRule.onNodeWithTag("nav-rename").performClick()
        screenshot("prototype-rename.png")
        composeRule.onNodeWithTag("nav-settings").performClick()
        screenshot("prototype-settings.png")
    }

    private fun insert(name: String, mime: String, bytes: ByteArray, dateTaken: Long) {
        val video = mime.startsWith("video")
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = context.contentResolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/MediaCraftPrototype/$prefix/" else "Pictures/MediaCraftPrototype/$prefix/")
            put(MediaStore.MediaColumns.DATE_TAKEN, dateTaken)
            put(MediaStore.MediaColumns.WIDTH, 600)
            put(MediaStore.MediaColumns.HEIGHT, 600)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: error("Could not create prototype fixture")
        created += uri
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }

    private fun screenshot(name: String) {
        composeRule.waitForIdle()
        val file = File(context.cacheDir, name)
        assertTrue("Could not save UI evidence $name", UiDevice.getInstance(instrumentation).takeScreenshot(file))
    }

    private fun countMedia(uri: Uri): Int = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.count } ?: 0
}
