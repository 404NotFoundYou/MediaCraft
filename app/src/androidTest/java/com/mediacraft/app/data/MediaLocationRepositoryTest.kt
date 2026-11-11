package com.mediacraft.app.data

import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mediacraft.app.domain.LocationReadResult
import com.mediacraft.app.domain.MediaItem
import com.mediacraft.app.domain.MediaKind
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaLocationRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixture = File(context.cacheDir, "location_${System.nanoTime()}.jpg")

    @After
    fun removeFixture() {
        fixture.delete()
    }

    @Test
    fun generatedJpegExif_isReadLazilyFromItsUri() = runBlocking {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            fixture.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output) }
        } finally {
            bitmap.recycle()
        }
        ExifInterface(fixture).apply {
            setLatLong(1.4055722, 103.7945222)
            saveAttributes()
        }

        val result = MediaLocationRepository(context).read(media(fixture))

        assertTrue("Expected EXIF coordinates, got $result", result is LocationReadResult.Found)
        val coordinates = (result as LocationReadResult.Found).coordinates
        assertEquals(1.4055722, coordinates.latitude, 0.000001)
        assertEquals(103.7945222, coordinates.longitude, 0.000001)
    }

    private fun media(file: File) = MediaItem(
        id = Uri.fromFile(file).toString(),
        displayName = file.name,
        kind = MediaKind.PHOTO,
        mimeType = "image/jpeg",
        relativePath = "cache/",
        size = file.length(),
        width = 2,
        height = 2,
        dateTakenMillis = 1,
        dateAddedMillis = 1,
        dateModifiedSeconds = 1,
    )
}
