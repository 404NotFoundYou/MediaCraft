package com.mediacraft.app

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.mediacraft.app.data.MediaRepository
import com.mediacraft.app.data.OperationRepository
import com.mediacraft.app.domain.OperationSpec
import com.mediacraft.app.domain.OperationStatus
import com.mediacraft.app.domain.OperationType
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val permissionRule = GrantPermissionRule.grant(*readMediaPermissions())

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(permissionRule).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val created = mutableListOf<Uri>()
    private val prefix = "MC_UI_${UUID.randomUUID().toString().take(8)}"
    private val album = "Pictures/MediaCraftUi/$prefix/"
    private lateinit var highName: String
    private lateinit var lowName: String

    @Before
    fun createOwnedPhotoVersions() {
        highName = "${prefix}_high.jpg"
        lowName = "${prefix}_low.jpg"
        insertPhoto(highName, photoBytes(400, 300), 400, 300)
        insertPhoto(lowName, photoBytes(200, 150), 200, 150)

        // MainActivity may have completed its first MediaStore query before @Before inserted fixtures.
        composeRule.activityRule.scenario.recreate()
        selectFixtureAlbum()
    }

    @After
    fun removeOnlyOwnedFixtures() {
        created.forEach { uri -> resolver.delete(uri, null, null) }
    }

    @Test
    fun photoVersionRequiresComparisonAndExplicitReviewBeforeConfirmation() {
        scrollLibraryToVisible(hasText("开始扫描"))
        composeRule.onNodeWithText("开始扫描").performClick()

        waitForText("重新扫描")
        composeRule.onNodeWithTag("dedup-list").performScrollToNode(hasText("疑似同内容 · 照片版本"))
        composeRule.onNodeWithTag("dedup-list").performScrollToNode(hasText(highName))
        composeRule.onNodeWithText(highName).assertExists()
        composeRule.onNodeWithText(lowName).assertExists()
        composeRule.onNodeWithText("像素更高：400 × 300").assertExists()

        val fixtureComparison = hasContentDescription("对比", substring = true) and hasContentDescription(highName, substring = true)
        composeRule.onNodeWithTag("dedup-list").performScrollToNode(fixtureComparison)
        composeRule.onNode(fixtureComparison).performClick()
        composeRule.onNodeWithText("照片放大对比").assertExists()
        assertDialogContainsFileName(highName)
        assertDialogContainsFileName(lowName)
        composeRule.onNodeWithText("完成对比").performClick()

        composeRule.onNodeWithTag("dedup-list").performScrollToNode(hasText("复核并处理"))
        composeRule.onNodeWithText("复核并处理").assertIsNotEnabled()
        val confirmation = hasContentDescription("内容一致", substring = true) and hasContentDescription(highName, substring = true)
        composeRule.onNodeWithTag("dedup-list").performScrollToNode(confirmation)
        composeRule.onNode(confirmation).assertIsOff().performClick()
        composeRule.onNodeWithTag("dedup-list").performScrollToNode(hasText("复核并处理"))
        composeRule.onNodeWithText("复核并处理").assertIsEnabled().performClick()

        composeRule.onNodeWithText("确认移入回收站").assertExists()
        composeRule.onNodeWithText("人工确认版本 1 组", substring = true).assertExists()
        composeRule.onNodeWithText("保留：$highName").assertExists()
        composeRule.onNodeWithText("移入回收站：$lowName").assertExists()
        composeRule.onNodeWithText("返回检查").performClick()
    }

    @Test
    fun randomAndCustomRenamePreviewsRemainLockedAcrossNavigation() {
        composeRule.onNodeWithText("重命名").performClick()
        waitForText(highName)
        selectForRename(highName)
        selectForRename(lowName)

        composeRule.onNodeWithTag("rename-list").performScrollToNode(hasText("随机命名"))
        composeRule.onNodeWithText("随机命名").performClick()
        createRenamePreview()
        val randomNames = previewNames()
        assertEquals("Expected two random preview names", 2, randomNames.size)
        assertTrue(
            "Random preview must preserve JPG extensions: $randomNames",
            randomNames.all { it.matches(Regex("MC_[A-HJ-NP-Z2-9]{10}\\.jpg")) },
        )
        assertPreviewSurvivesNavigation(randomNames)

        composeRule.onNodeWithTag("rename-list").performScrollToNode(hasText("自定义命名"))
        composeRule.onNodeWithText("自定义命名").performClick()
        composeRule.onNodeWithTag("rename-list").performScrollToNode(hasText("旅行_{date}_{seq:3}"))
        composeRule.onNode(hasSetTextAction() and hasText("旅行_{date}_{seq:3}"))
            .performTextReplacement("UI_{seq:2}")
        createRenamePreview()
        val customNames = previewNames()
        assertEquals("Custom preview must use one stable batch order", listOf("UI_01.jpg", "UI_02.jpg"), customNames)
        assertPreviewSurvivesNavigation(customNames)
    }

    @Test
    fun historyCanExpandPastTheFirstFiveFailedItems() = runBlocking {
        val mediaRepository = MediaRepository(context)
        val operationRepository = OperationRepository(context, mediaRepository)
        val fixtures = (1..6).map { index ->
            val name = "${prefix}_history_$index.jpg"
            val uri = insertPhoto(name, photoBytes(120, 90), 120, 90)
            mediaRepository.find(uri.toString()) ?: error("Inserted history fixture $index is not queryable")
        }
        val prepared = operationRepository.prepare(
            OperationType.RENAME,
            fixtures.mapIndexed { index, media ->
                OperationSpec(
                    media = media,
                    targetName = "${prefix}_blocked_${index + 1}.jpg",
                    validationError = "fixture blocked ${index + 1}",
                )
            },
        )
        assertTrue("Validation failures must not request a system write", prepared.uris.isEmpty())
        val record = operationRepository.complete(prepared.id, approved = true)
        assertEquals(6, record.items.count { it.status == OperationStatus.FAILED })

        composeRule.onNodeWithText("记录").performClick()
        val expandLabel = "展开逐项结果（6）"
        waitUntilNodeExists(hasText(expandLabel), timeoutMillis = 20_000)
        composeRule.onNodeWithTag("history-list").performScrollToNode(hasText(expandLabel))
        val sixthName = fixtures.last().displayName
        assertTrue(
            "The collapsed summary must not pretend the sixth failure is already visible",
            composeRule.onAllNodes(hasText(sixthName, substring = true)).fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onAllNodes(hasText(expandLabel))[0].performClick()

        composeRule.onNodeWithTag("history-list").performScrollToNode(hasText(sixthName, substring = true))
        composeRule.onNodeWithText(sixthName, substring = true).assertExists()
        composeRule.onNodeWithText("fixture blocked 6", substring = true).assertExists()
        Unit
    }

    private fun selectFixtureAlbum() {
        waitUntilNodeExists(hasText(album), timeoutMillis = 20_000)
        scrollLibraryToVisible(hasText(album))
        composeRule.onNodeWithText(album).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(album).assertIsSelected()
    }

    private fun scrollLibraryToVisible(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeRule.onNodeWithTag("dedup-list").performScrollToNode(matcher)
        repeat(8) {
            if (composeRule.onNode(matcher).isDisplayed()) return
            composeRule.onNodeWithTag("dedup-list").performTouchInput { swipeUp() }
        }
        composeRule.onNode(matcher).assertIsDisplayed()
    }

    private fun selectForRename(name: String) {
        val selector = hasContentDescription("选择 $name")
        composeRule.onNodeWithTag("rename-list").performScrollToNode(selector)
        composeRule.onNodeWithContentDescription("选择 $name").performClick()
    }

    private fun createRenamePreview() {
        composeRule.onNodeWithTag("rename-list").performScrollToNode(hasText("生成名称预览"))
        composeRule.onNodeWithText("生成名称预览").performClick()
        composeRule.onNodeWithTag("rename-list").performScrollToNode(hasText("名称预览 · 已锁定"))
        composeRule.onNodeWithText("名称预览 · 已锁定").assertExists()
    }

    private fun previewNames(): List<String> {
        composeRule.onNodeWithTag("rename-list").performScrollToNode(hasText("确认并请求系统授权"))
        waitUntilNodeCount(hasText("↳", substring = true), 2)
        return composeRule.onAllNodes(hasText("↳", substring = true)).fetchSemanticsNodes()
            .map { node ->
                node.config[SemanticsProperties.Text].joinToString("") { it.text }
                    .removePrefix("↳ ")
            }
    }

    private fun assertPreviewSurvivesNavigation(expected: List<String>) {
        composeRule.onNodeWithText("去重").performClick()
        composeRule.onNodeWithText("重命名").performClick()
        composeRule.onNodeWithTag("rename-list").performScrollToNode(hasText("名称预览 · 已锁定"))
        assertEquals("Locked preview changed after navigation", expected, previewNames())
    }

    private fun waitForText(text: String) = waitUntilNodeExists(hasText(text), timeoutMillis = 30_000)

    private fun assertDialogContainsFileName(name: String) {
        val matcher = hasText(name) and hasAnyAncestor(isDialog())
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilNodeExists(matcher: androidx.compose.ui.test.SemanticsMatcher, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilNodeCount(matcher: androidx.compose.ui.test.SemanticsMatcher, count: Int) {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().size == count
        }
    }

    private fun insertPhoto(name: String, bytes: ByteArray, width: Int, height: Int): Uri {
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, album)
                put(MediaStore.MediaColumns.WIDTH, width)
                put(MediaStore.MediaColumns.HEIGHT, height)
                put(MediaStore.MediaColumns.DATE_TAKEN, 1_794_355_200_000L)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            },
        ) ?: error("Could not create UI test photo")
        created += uri
        resolver.openOutputStream(uri, "w")!!.use { it.write(bytes) }
        check(
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            ) == 1,
        )
        return uri
    }

    private fun photoBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.scale(width / 400f, height / 300f)
            canvas.drawColor(Color.rgb(50, 120, 185))
            paint.color = Color.rgb(225, 205, 120)
            canvas.drawRect(0f, 180f, 400f, 300f, paint)
            paint.color = Color.rgb(35, 80, 45)
            canvas.drawRect(30f, 70f, 150f, 240f, paint)
            paint.color = Color.rgb(250, 235, 155)
            canvas.drawCircle(310f, 65f, 35f, paint)
            return ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        fun readMediaPermissions(): Array<String> = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
