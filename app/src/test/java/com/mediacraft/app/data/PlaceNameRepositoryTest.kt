package com.mediacraft.app.data

import com.mediacraft.app.domain.GeoCoordinates
import com.mediacraft.app.domain.PlaceName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class PlaceNameRepositoryTest {
    @Test
    fun successfulLookupIsCachedForTheExactCoordinateAndLocale() = runTest {
        var calls = 0
        val repository = PlaceNameRepository("zh-CN") { _, _ ->
            calls++
            PlaceName("新加坡 · 万礼（Mandai）", "Mandai Lake Road 周边")
        }
        val coordinates = GeoCoordinates(1.4055722, 103.7945222)

        assertEquals(repository.resolve(coordinates), repository.resolve(coordinates))
        assertEquals(1, calls)
    }

    @Test
    fun missingResultIsNotCachedSoTheUserCanRetry() = runTest {
        var calls = 0
        val repository = PlaceNameRepository("zh-CN") { _, _ ->
            calls++
            if (calls == 1) null else PlaceName("万礼", null)
        }
        val coordinates = GeoCoordinates(1.4055722, 103.7945222)

        assertNull(repository.resolve(coordinates))
        assertEquals(PlaceName("万礼", null), repository.resolve(coordinates))
        assertEquals(2, calls)
    }

    @Test
    fun failureIsNotCachedAndCancellationOrErrorsRemainVisible() = runTest {
        var calls = 0
        val repository = PlaceNameRepository("zh-CN") { _, _ ->
            calls++
            if (calls == 1) error("服务暂不可用") else PlaceName("万礼", null)
        }
        val coordinates = GeoCoordinates(1.4055722, 103.7945222)

        try {
            repository.resolve(coordinates)
            fail("地名解析错误必须显式传递给界面")
        } catch (expected: IllegalStateException) {
            assertEquals("服务暂不可用", expected.message)
        }
        assertEquals(PlaceName("万礼", null), repository.resolve(coordinates))
        assertEquals(2, calls)
    }

    @Test
    fun differentCoordinatesNeverShareAPlaceName() = runTest {
        var calls = 0
        val repository = PlaceNameRepository("zh-CN") { latitude, _ ->
            calls++
            PlaceName("纬度 $latitude", null)
        }

        assertEquals("纬度 1.4055722", repository.resolve(GeoCoordinates(1.4055722, 103.7945222))?.title)
        assertEquals("纬度 1.406", repository.resolve(GeoCoordinates(1.406, 103.7945222))?.title)
        assertEquals(2, calls)
    }

    @Test
    fun cancellationIsNotCachedSoAVisibleDetailCanRetry() = runTest {
        var calls = 0
        val repository = PlaceNameRepository("zh-CN") { _, _ ->
            calls++
            if (calls == 1) throw CancellationException("详情已关闭") else PlaceName("新加坡 · 万礼", null)
        }
        val coordinates = GeoCoordinates(1.4055722, 103.7945222)

        try {
            repository.resolve(coordinates)
            fail("取消必须向上传递")
        } catch (_: CancellationException) {
            // 详情关闭后不应留下一个失败或半成品缓存。
        }
        assertEquals(PlaceName("新加坡 · 万礼", null), repository.resolve(coordinates))
        assertEquals(2, calls)
    }

    @Test
    fun leastRecentlyUsedEntryIsEvictedAfterCapacityIsExceeded() = runTest {
        var calls = 0
        val repository = PlaceNameRepository("zh-CN") { latitude, _ ->
            calls++
            PlaceName("地点 $latitude", null)
        }
        val first = GeoCoordinates(0.0, 100.0)

        repeat(65) { index -> repository.resolve(GeoCoordinates(index.toDouble(), 100.0)) }
        assertEquals(65, calls)
        repository.resolve(first)
        assertEquals(66, calls)
    }
}
