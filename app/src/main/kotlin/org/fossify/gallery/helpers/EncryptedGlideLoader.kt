package org.fossify.gallery.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.ImageHeaderParser
import com.bumptech.glide.load.ImageHeaderParserUtils
import com.bumptech.glide.load.Option
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.bumptech.glide.signature.ObjectKey
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

object EncryptedGlideOptions {
    val PREFER_EMBEDDED_THUMBNAIL: Option<Boolean> =
        Option.memory("org.fossify.gallery.prefer_embedded_thumbnail", false)
}

class EncryptedStringModelLoader : ModelLoader<String, InputStream> {
    override fun handles(model: String): Boolean {
        val cleanPath = model.removePrefix("file://")
        return CryptoManager.isEncryptedFile(cleanPath)
    }

    override fun buildLoadData(
        model: String,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? {
        val cleanPath = model.removePrefix("file://")
        val file = File(cleanPath)
        val preferEmbeddedThumbnail = options.get(EncryptedGlideOptions.PREFER_EMBEDDED_THUMBNAIL) == true
        return ModelLoader.LoadData(
            ObjectKey("${file.absolutePath}_${file.lastModified()}_${width}x${height}_${preferEmbeddedThumbnail}"),
            EncryptedStreamFetcher(file, width, height, preferEmbeddedThumbnail)
        )
    }

    class Factory : ModelLoaderFactory<String, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<String, InputStream> {
            return EncryptedStringModelLoader()
        }

        override fun teardown() {}
    }
}

class EncryptedFileModelLoader : ModelLoader<File, InputStream> {
    override fun handles(model: File): Boolean {
        return CryptoManager.isEncryptedFile(model.name)
    }

    override fun buildLoadData(
        model: File,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? {
        val preferEmbeddedThumbnail = options.get(EncryptedGlideOptions.PREFER_EMBEDDED_THUMBNAIL) == true
        return ModelLoader.LoadData(
            ObjectKey("${model.absolutePath}_${model.lastModified()}_${width}x${height}_${preferEmbeddedThumbnail}"),
            EncryptedStreamFetcher(model, width, height, preferEmbeddedThumbnail)
        )
    }

    class Factory : ModelLoaderFactory<File, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<File, InputStream> {
            return EncryptedFileModelLoader()
        }

        override fun teardown() {}
    }
}

class EncryptedStreamFetcher(
    private val file: File,
    private val width: Int,
    private val height: Int,
    private val preferEmbeddedThumbnail: Boolean = false
) : DataFetcher<InputStream> {
    private var stream: InputStream? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        if (!CryptoManager.isVaultUnlocked) {
            callback.onLoadFailed(FileNotFoundException("Vault is locked"))
            return
        }

        try {
            val meta = CryptoManager.readMetadata(file)
            val isThumbnailSize = width in 1..600 && height in 1..600
            val isVideo = meta?.mediaType == TYPE_VIDEOS

            if (isVideo || preferEmbeddedThumbnail || (isThumbnailSize && meta?.mediaType != TYPE_GIFS)) {
                val thumbBytes = CryptoManager.readEmbeddedThumbnailBytes(file)
                if (thumbBytes != null && thumbBytes.isNotEmpty()) {
                    stream = ByteArrayInputStream(thumbBytes)
                    callback.onDataReady(stream)
                    return
                }
            }

            stream = CryptoManager.openDecryptedContentStream(file)
            if (stream != null) {
                callback.onDataReady(stream)
            } else {
                callback.onLoadFailed(FileNotFoundException("Unable to decrypt stream for ${file.name}"))
            }
        } catch (e: Exception) {
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {
        try {
            stream?.close()
        } catch (_: Exception) {
        }
        stream = null
    }

    override fun cancel() {}

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL
}

/**
 * Decodes static first-frame Bitmaps from GIF InputStreams without relying on
 * Glide's InputStreamRewinder 5 MB mark/reset buffer limit.
 */
class GifBitmapStreamDecoder(
    private val parsers: List<ImageHeaderParser>,
    private val bitmapPool: BitmapPool,
    private val arrayPool: ArrayPool
) : ResourceDecoder<InputStream, Bitmap> {
    override fun handles(source: InputStream, options: Options): Boolean {
        return ImageHeaderParserUtils.getType(parsers, source, arrayPool) == ImageHeaderParser.ImageType.GIF
    }

    override fun decode(
        source: InputStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val bytes = source.readBytes()
        if (bytes.isEmpty()) return null

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)

        var sampleSize = 1
        if (width > 0 && height > 0 && boundsOpts.outWidth > 0 && boundsOpts.outHeight > 0) {
            while (boundsOpts.outWidth / (sampleSize * 2) >= width &&
                boundsOpts.outHeight / (sampleSize * 2) >= height
            ) {
                sampleSize *= 2
            }
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
        return BitmapResource.obtain(bitmap, bitmapPool)
    }
}
