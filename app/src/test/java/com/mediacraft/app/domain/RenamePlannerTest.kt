package com.mediacraft.app.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenamePlannerTest {
    private val planner = RenamePlanner(zoneId = ZoneId.of("Asia/Shanghai"))

    @Test
    fun customPlan_usesStableMixedMediaOrderAndPreservesExtensions() {
        val latePhoto = media("2", "IMG.JPG", MediaKind.PHOTO, taken = 2_000)
        val earlyVideo = media("1", "clip.MP4", MediaKind.VIDEO, taken = 1_000)

        val plan = planner.create(
            selected = listOf(latePhoto, earlyVideo),
            allMedia = listOf(latePhoto, earlyVideo),
            options = RenameOptions(template = "旅行_{seq:3}", start = 7),
        )

        assertEquals(listOf(earlyVideo.id, latePhoto.id), plan.items.map { it.media.id })
        assertEquals(listOf("旅行_007.MP4", "旅行_008.JPG"), plan.items.map { it.newName })
        assertTrue(plan.canExecute)
    }

    @Test
    fun missingTakenDate_fallsBackToAddedDateWithWarning() {
        val item = media("1", "old.jpg", MediaKind.PHOTO, taken = 0, added = 1_794_240_000_000)

        val plan = planner.create(listOf(item), listOf(item), RenameOptions(template = "图_{date}"))

        assertEquals("图_20261110.jpg", plan.items.single().newName)
        assertTrue(plan.items.single().warnings.single().contains("使用添加日期"))
        assertTrue(plan.canExecute)
    }

    @Test
    fun unknownVariableAndIllegalPathAreRejected() {
        val item = media("1", "old.jpg", MediaKind.PHOTO)

        val unknown = planner.create(listOf(item), listOf(item), RenameOptions(template = "{album}_{seq:3}"))
        val illegal = planner.create(listOf(item), listOf(item), RenameOptions(template = "bad/name"))

        assertTrue(unknown.error?.contains("未知变量") == true)
        assertTrue(illegal.items.single().error?.contains("非法字符") == true)
        assertFalse(illegal.canExecute)
    }

    @Test
    fun directoryCollisionAndBatchDuplicateAreRejectedWithoutOverwrite() {
        val first = media("1", "a.jpg", MediaKind.PHOTO)
        val second = media("2", "b.jpg", MediaKind.PHOTO)
        val existing = media("3", "taken.jpg", MediaKind.PHOTO)

        val occupied = planner.create(listOf(first), listOf(first, existing), RenameOptions(template = "taken"))
        val duplicate = planner.create(listOf(first, second), listOf(first, second), RenameOptions(template = "same"))

        assertTrue(occupied.items.single().error?.contains("占用") == true)
        assertNull(duplicate.items.first().error)
        assertTrue(duplicate.items.last().error?.contains("重复") == true)
        assertFalse(duplicate.canExecute)
    }

    @Test
    fun photoAndVideoInTheSameVolumeDirectoryShareOneCollisionDomain() {
        val selectedVideo = media("1", "clip.mp4", MediaKind.VIDEO)
        val existingPhoto = media("2", "taken.mp4", MediaKind.PHOTO)

        val plan = planner.create(
            selected = listOf(selectedVideo),
            allMedia = listOf(selectedVideo, existingPhoto),
            options = RenameOptions(template = "taken"),
        )

        assertTrue("Media collections do not create separate filesystem directories", plan.items.single().error?.contains("占用") == true)
        assertFalse(plan.canExecute)
    }

    @Test
    fun collisionChecksUseRootCaseFoldingAndUnicodeNfc() {
        val selected = media("1", "source.jpg", MediaKind.PHOTO)
        val uppercase = media("2", "TAKEN.JPG", MediaKind.PHOTO)
        val composed = media("3", "Caf\u00e9.jpg", MediaKind.PHOTO)

        val caseCollision = planner.create(
            listOf(selected),
            listOf(selected, uppercase),
            RenameOptions(template = "taken"),
        )
        val nfcCollision = planner.create(
            listOf(selected),
            listOf(selected, composed),
            RenameOptions(template = "Cafe\u0301"),
        )

        assertTrue(caseCollision.items.single().error?.contains("占用") == true)
        assertTrue(nfcCollision.items.single().error?.contains("占用") == true)
    }

    @Test
    fun exchangeOrCycleNamesAreRejected() {
        val first = media("1", "2.jpg", MediaKind.PHOTO, taken = 1)
        val second = media("2", "1.jpg", MediaKind.PHOTO, taken = 2)

        val plan = planner.create(
            listOf(first, second),
            listOf(first, second),
            RenameOptions(template = "{seq:1}", start = 1),
        )

        assertTrue(plan.items.all { it.error?.contains("交换或循环") == true })
        assertFalse(plan.canExecute)
    }

    @Test
    fun unchangedItemDoesNotCountAsExecutableWork() {
        val item = media("1", "keep.jpg", MediaKind.PHOTO)

        val plan = planner.create(listOf(item), listOf(item), RenameOptions(template = "{original}"))

        assertTrue(plan.items.single().unchanged)
        assertFalse(plan.canExecute)
    }

    @Test
    fun randomPreviewCreatesLockedUniqueNamesWithOriginalExtensions() {
        val first = media("1", "a.jpg", MediaKind.PHOTO)
        val second = media("2", "b.mp4", MediaKind.VIDEO)

        val plan = planner.create(listOf(first, second), listOf(first, second), RenameOptions(mode = RenameMode.RANDOM))
        val names = plan.items.map { it.newName }

        assertTrue(names[0].matches(Regex("MC_[A-HJ-NP-Z2-9]{10}\\.jpg")))
        assertTrue(names[1].matches(Regex("MC_[A-HJ-NP-Z2-9]{10}\\.mp4")))
        assertNotEquals(names[0].substringBeforeLast('.'), names[1].substringBeforeLast('.'))
        assertEquals(names, plan.items.map { it.newName })
        assertTrue(plan.canExecute)
    }

    private fun media(
        id: String,
        name: String,
        kind: MediaKind,
        taken: Long = 1_000,
        added: Long = 2_000,
    ) = MediaItem(
        id = "content://media/external/${if (kind == MediaKind.PHOTO) "images" else "video"}/media/$id",
        displayName = name,
        kind = kind,
        mimeType = if (kind == MediaKind.PHOTO) "image/jpeg" else "video/mp4",
        relativePath = "Pictures/",
        size = 10,
        width = 100,
        height = 100,
        dateTakenMillis = taken,
        dateAddedMillis = added,
        dateModifiedSeconds = 1,
    )
}
