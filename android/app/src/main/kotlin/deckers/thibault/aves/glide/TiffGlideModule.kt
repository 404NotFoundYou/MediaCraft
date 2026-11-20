package deckers.thibault.aves.glide

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.core.graphics.scale
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.data.DataFetcher.DataCallback
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.module.LibraryGlideModule
import com.bumptech.glide.signature.ObjectKey
import deckers.thibault.aves.model.ContentAddress
import org.beyka.tiffbitmapfactory.TiffBitmapFactory
import org.beyka.tiffbitmapfactory.exceptions.CantOpenFileException

@GlideModule
class TiffGlideModule : LibraryGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.append(TiffImage::class.java, Bitmap::class.java, TiffLoader.Factory())
    }
}

class TiffImage(val context: Context, val contentAddress: ContentAddress) {
    override fun toString(): String = "TiffImage#${hashCode()}{$contentAddress}"
}

internal class TiffLoader : ModelLoader<TiffImage, Bitmap> {
    override fun buildLoadData(model: TiffImage, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap> {
        val uri = model.contentAddress.uri
        return ModelLoader.LoadData(ObjectKey(uri), TiffFetcher(model, width, height))
    }

    override fun handles(model: TiffImage): Boolean = true

    internal class Factory : ModelLoaderFactory<TiffImage, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<TiffImage, Bitmap> = TiffLoader()

        override fun teardown() {}
    }
}

internal class TiffFetcher(val model: TiffImage, val width: Int, val height: Int) : DataFetcher<Bitmap> {
    override fun loadData(priority: Priority, callback: DataCallback<in Bitmap>) {
        val context = model.context
        val uri = model.contentAddress.uri
        val page = model.contentAddress.pageId ?: 0

        try {
            var sampleSize = 1
            val customSize = width > 0 && height > 0
            if (customSize) {
                // determine sample size
                val options = buildOptions().apply {
                    inJustDecodeBounds = true
                    inDirectoryNumber = page
                }
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: return callback.onLoadFailed(Exception("null file descriptor"))
                decode(pfd, options)
                val imageWidth = options.outWidth
                val imageHeight = options.outHeight
                if (imageWidth > width || imageHeight > height) {
                    while (imageHeight / (sampleSize * 2) >= height && imageWidth / (sampleSize * 2) >= width) {
                        sampleSize *= 2
                    }
                }
            }

            val options = buildOptions().apply {
                inDirectoryNumber = page
                inSampleSize = sampleSize
            }
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return callback.onLoadFailed(Exception("null file descriptor"))
            val bitmap = decode(pfd, options)
            if (bitmap == null) {
                callback.onLoadFailed(Exception("Decoding full TIFF yielded null bitmap"))
            } else if (customSize) {
                val dstWidth: Int
                val dstHeight: Int
                val aspectRatio = bitmap.width.toFloat() / bitmap.height
                if (aspectRatio > 1) {
                    dstWidth = (height * aspectRatio).toInt()
                    dstHeight = height
                } else {
                    dstWidth = width
                    dstHeight = (width / aspectRatio).toInt()
                }
                callback.onDataReady(bitmap.scale(dstWidth, dstHeight))
            } else {
                callback.onDataReady(bitmap)
            }
        } catch (e: Exception) {
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {}

    // cannot cancel
    override fun cancel() {}

    override fun getDataClass(): Class<Bitmap> = Bitmap::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL

    companion object {
        fun decode(pfd: ParcelFileDescriptor, options: TiffBitmapFactory.Options): Bitmap? = pfd.use {
            options.inThrowException = true
            val fd = it.detachFd()
            try {
                TiffBitmapFactory.decodeFileDescriptor(fd, options)
            } catch (e: CantOpenFileException) {
                // TIFFFdOpen did not take ownership; every later outcome is closed by TIFFClose.
                TiffBitmapFactory.closeFd(fd)
                throw e
            }
        }

        fun buildOptions(): TiffBitmapFactory.Options {
            return TiffBitmapFactory.Options().apply {
                inUseOrientationTag = false
            }
        }
    }
}