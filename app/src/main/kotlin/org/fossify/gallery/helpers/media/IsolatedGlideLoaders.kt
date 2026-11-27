package org.fossify.gallery.helpers.media

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Option
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.bumptech.glide.signature.ObjectKey
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.EncryptedGlideOptions
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

private const val THUMB_PREVIEW_LIMIT = 600

object IsolatedGlideOptions {
    val SCALE_CENTER_CROP: Option<Boolean> =
        Option.memory("org.fossify.gallery.scale_center_crop", false)
    val INCLUDE_GAINMAP: Option<Boolean> =
        Option.memory("org.fossify.gallery.include_gainmap", false)
}

class IsolatedBitmapPacket(
    val result: IsolatedDecodeResult? = null,
    private var directBitmap: Bitmap? = null,
    val isEncrypted: Boolean = false,
) : Closeable {
    fun takeDirectBitmap(): Bitmap? {
        val bmp = directBitmap
        directBitmap = null
        return bmp
    }

    override fun close() {
        runCatching { result?.close() }
        val leftover = directBitmap
        directBitmap = null
        if (leftover != null && !leftover.isRecycled) {
            if (isEncrypted) {
                VaultBitmapPool.release(leftover)
            } else {
                runCatching { leftover.recycle() }
            }
        }
    }
}

class IsolatedBitmapResourceDecoder(
    private val bitmapPool: BitmapPool,
) : ResourceDecoder<IsolatedBitmapPacket, Bitmap> {
    override fun handles(source: IsolatedBitmapPacket, options: Options): Boolean = true

    override fun decode(
        source: IsolatedBitmapPacket,
        width: Int,
        height: Int,
        options: Options,
    ): Resource<Bitmap>? {
        return source.use { pkt ->
            val bmp = pkt.takeDirectBitmap() ?: pkt.result?.let { res ->
                val supplier: (Int, Int) -> Bitmap? = if (pkt.isEncrypted) {
                    { w, h -> VaultBitmapPool.acquire(w, h) }
                } else {
                    { w, h -> bitmapPool.getDirty(w, h, Bitmap.Config.ARGB_8888) }
                }
                IsolatedPixelTransport.unpackBitmap(res, supplier)
            } ?: return@use null

            if (pkt.isEncrypted) {
                VaultBitmapResource(bmp)
            } else {
                BitmapResource.obtain(bmp, bitmapPool)
            }
        }
    }
}

private data class IsolatedFetchSpec(
    val file: File?,
    val uri: Uri?,
    val maxWidth: Int,
    val maxHeight: Int,
    val flags: Int,
    val preferEmbeddedThumbnail: Boolean,
    val preferFullDecryptedContent: Boolean,
)

class IsolatedStringModelLoader(
    private val context: Context,
) : ModelLoader<String, IsolatedBitmapPacket> {
    override fun handles(model: String): Boolean {
        val clean = model.removePrefix("file://")
        if (CryptoManager.isEncryptedFile(clean)) return true
        val uri = Uri.parse(model)
        if (uri.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE) return false
        return !isVideoUriOrPath(context, model, uri)
    }

    override fun buildLoadData(
        model: String,
        width: Int,
        height: Int,
        options: Options,
    ): ModelLoader.LoadData<IsolatedBitmapPacket> {
        val uri = Uri.parse(model)
        val clean = if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path.orEmpty()
        } else {
            model.removePrefix("file://")
        }
        val isUriModel = uri.scheme == ContentResolver.SCHEME_CONTENT ||
            (uri.scheme == ContentResolver.SCHEME_FILE && !CryptoManager.isEncryptedFile(clean))
        val targetW = normalizeDim(width)
        val targetH = normalizeDim(height)
        val flags = buildFlags(context, targetW, targetH, options)
        val preferThumb = options.get(EncryptedGlideOptions.PREFER_EMBEDDED_THUMBNAIL) == true
        val preferFull = options.get(EncryptedGlideOptions.PREFER_FULL_DECRYPTED_CONTENT) == true
        val file = File(clean)
        val sig = if (isUriModel) {
            ObjectKey("iso:$model:${targetW}x${targetH}:$flags")
        } else {
            ObjectKey("iso:${file.absolutePath}:${targetW}x${targetH}:$flags:$preferThumb:$preferFull")
        }
        val spec = IsolatedFetchSpec(
            file = if (isUriModel) null else file,
            uri = if (isUriModel) uri else null,
            maxWidth = targetW,
            maxHeight = targetH,
            flags = flags,
            preferEmbeddedThumbnail = preferThumb,
            preferFullDecryptedContent = preferFull,
        )
        return ModelLoader.LoadData(sig, IsolatedPacketFetcher(context, spec))
    }

    class Factory(private val context: Context) : ModelLoaderFactory<String, IsolatedBitmapPacket> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<String, IsolatedBitmapPacket> {
            return IsolatedStringModelLoader(context.applicationContext)
        }

        override fun teardown() = Unit
    }
}

class IsolatedFileModelLoader(
    private val context: Context,
) : ModelLoader<File, IsolatedBitmapPacket> {
    override fun handles(model: File): Boolean {
        return CryptoManager.isEncryptedFile(model.name) || !IsolatedFormatSniffer.isVideoFileHint(model.name)
    }

    override fun buildLoadData(
        model: File,
        width: Int,
        height: Int,
        options: Options,
    ): ModelLoader.LoadData<IsolatedBitmapPacket> {
        val targetW = normalizeDim(width)
        val targetH = normalizeDim(height)
        val flags = buildFlags(context, targetW, targetH, options)
        val preferThumb = options.get(EncryptedGlideOptions.PREFER_EMBEDDED_THUMBNAIL) == true
        val preferFull = options.get(EncryptedGlideOptions.PREFER_FULL_DECRYPTED_CONTENT) == true
        val sig = ObjectKey("iso:${model.absolutePath}:${targetW}x${targetH}:$flags:$preferThumb:$preferFull")
        val spec = IsolatedFetchSpec(
            file = model,
            uri = null,
            maxWidth = targetW,
            maxHeight = targetH,
            flags = flags,
            preferEmbeddedThumbnail = preferThumb,
            preferFullDecryptedContent = preferFull,
        )
        return ModelLoader.LoadData(sig, IsolatedPacketFetcher(context, spec))
    }

    class Factory(private val context: Context) : ModelLoaderFactory<File, IsolatedBitmapPacket> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<File, IsolatedBitmapPacket> {
            return IsolatedFileModelLoader(context.applicationContext)
        }

        override fun teardown() = Unit
    }
}

class IsolatedUriModelLoader(
    private val context: Context,
) : ModelLoader<Uri, IsolatedBitmapPacket> {
    override fun handles(model: Uri): Boolean {
        if (model.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE) return false
        val path = model.path.orEmpty()
        if (CryptoManager.isEncryptedFile(path)) return true
        return !isVideoUriOrPath(context, model.toString(), model)
    }

    override fun buildLoadData(
        model: Uri,
        width: Int,
        height: Int,
        options: Options,
    ): ModelLoader.LoadData<IsolatedBitmapPacket> {
        val clean = model.path.orEmpty()
        val isEncryptedFile = model.scheme == ContentResolver.SCHEME_FILE && CryptoManager.isEncryptedFile(clean)
        val targetW = normalizeDim(width)
        val targetH = normalizeDim(height)
        val flags = buildFlags(context, targetW, targetH, options)
        val preferThumb = options.get(EncryptedGlideOptions.PREFER_EMBEDDED_THUMBNAIL) == true
        val preferFull = options.get(EncryptedGlideOptions.PREFER_FULL_DECRYPTED_CONTENT) == true
        val spec = IsolatedFetchSpec(
            file = if (isEncryptedFile) File(clean) else null,
            uri = if (isEncryptedFile) null else model,
            maxWidth = targetW,
            maxHeight = targetH,
            flags = flags,
            preferEmbeddedThumbnail = preferThumb,
            preferFullDecryptedContent = preferFull,
        )
        return ModelLoader.LoadData(
            ObjectKey("iso:$model:${targetW}x${targetH}:$flags:$preferThumb:$preferFull"),
            IsolatedPacketFetcher(context, spec),
        )
    }

    class Factory(private val context: Context) : ModelLoaderFactory<Uri, IsolatedBitmapPacket> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Uri, IsolatedBitmapPacket> {
            return IsolatedUriModelLoader(context.applicationContext)
        }

        override fun teardown() = Unit
    }
}

private class IsolatedPacketFetcher(
    private val context: Context,
    private val spec: IsolatedFetchSpec,
) : DataFetcher<IsolatedBitmapPacket> {
    private val cancelled = AtomicBoolean(false)
    private var activePacket: IsolatedBitmapPacket? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in IsolatedBitmapPacket>) {
        if (cancelled.get()) {
            callback.onLoadFailed(CancellationException("Cancelled"))
            return
        }
        val isoPriority = mapPriority(priority)
        val packet = fetchPacket(isoPriority)
        when {
            cancelled.get() -> {
                packet?.close()
                callback.onLoadFailed(CancellationException("Cancelled"))
            }
            packet != null -> {
                activePacket = packet
                callback.onDataReady(packet)
            }
            else -> callback.onLoadFailed(FileNotFoundException("Isolated decoder returned null"))
        }
    }

    private fun mapPriority(priority: Priority): Int {
        return when (priority) {
            Priority.IMMEDIATE, Priority.HIGH -> IsolatedServiceBridge.PRIORITY_IMMEDIATE
            Priority.NORMAL -> IsolatedServiceBridge.PRIORITY_NORMAL
            Priority.LOW -> IsolatedServiceBridge.PRIORITY_LOW
        }
    }

    private fun fetchPacket(isoPriority: Int): IsolatedBitmapPacket? {
        val file = spec.file
        val uri = spec.uri
        return when {
            file != null && CryptoManager.isEncryptedFile(file.name) -> fetchEncryptedPacket(file, isoPriority)
            file != null -> IsolatedDecodeRequestClient.decodeFileResult(
                context = context,
                file = file,
                maxWidth = spec.maxWidth,
                maxHeight = spec.maxHeight,
                flags = spec.flags,
                priority = isoPriority,
                cancelFlag = cancelled,
            )?.let { IsolatedBitmapPacket(result = it, isEncrypted = false) }
            uri != null -> IsolatedDecodeRequestClient.decodeUriResult(
                context = context,
                uri = uri,
                maxWidth = spec.maxWidth,
                maxHeight = spec.maxHeight,
                flags = spec.flags,
                priority = isoPriority,
                cancelFlag = cancelled,
            )?.let { IsolatedBitmapPacket(result = it, isEncrypted = false) }
            else -> null
        }
    }

    private fun fetchEncryptedPacket(file: File, isoPriority: Int): IsolatedBitmapPacket? {
        return when (
            val outcome = IsolatedDecodeRequestClient.decodeEncryptedPacket(
                context = context,
                file = file,
                maxWidth = spec.maxWidth,
                maxHeight = spec.maxHeight,
                preferEmbeddedThumbnail = spec.preferEmbeddedThumbnail,
                preferFullContent = spec.preferFullDecryptedContent,
                flags = spec.flags,
                priority = isoPriority,
                cancelFlag = cancelled,
            )
        ) {
            is EncryptedPacketOutcome.Result -> IsolatedBitmapPacket(
                result = outcome.result,
                isEncrypted = true,
            )
            is EncryptedPacketOutcome.DirectBitmap -> IsolatedBitmapPacket(
                directBitmap = outcome.bitmap,
                isEncrypted = true,
            )
            null -> null
        }
    }

    override fun cleanup() {
        activePacket?.close()
        activePacket = null
    }

    override fun cancel() {
        cancelled.set(true)
    }

    override fun getDataClass(): Class<IsolatedBitmapPacket> = IsolatedBitmapPacket::class.java
    override fun getDataSource(): DataSource = DataSource.LOCAL
}

class BlockedMainProcessStreamLoader<Model : Any> : ModelLoader<Model, InputStream> {
    override fun handles(model: Model): Boolean = !isAndroidResourceModel(model)

    override fun buildLoadData(
        model: Model,
        width: Int,
        height: Int,
        options: Options,
    ): ModelLoader.LoadData<InputStream> {
        return ModelLoader.LoadData(
            ObjectKey(model.toString()),
            FailingDataFetcher(InputStream::class.java),
        )
    }

    class Factory<Model : Any> : ModelLoaderFactory<Model, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Model, InputStream> {
            return BlockedMainProcessStreamLoader()
        }

        override fun teardown() = Unit
    }
}

class BlockedMainProcessByteBufferFileLoader : ModelLoader<File, ByteBuffer> {
    override fun handles(model: File): Boolean = true

    override fun buildLoadData(
        model: File,
        width: Int,
        height: Int,
        options: Options,
    ): ModelLoader.LoadData<ByteBuffer> {
        return ModelLoader.LoadData(
            ObjectKey(model.absolutePath),
            FailingDataFetcher(ByteBuffer::class.java),
        )
    }

    class Factory : ModelLoaderFactory<File, ByteBuffer> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<File, ByteBuffer> {
            return BlockedMainProcessByteBufferFileLoader()
        }

        override fun teardown() = Unit
    }
}

class VideoOnlyPfdModelLoader<Model : Any>(
    private val context: Context,
) : ModelLoader<Model, ParcelFileDescriptor> {
    override fun handles(model: Model): Boolean = !isAndroidResourceModel(model)

    override fun buildLoadData(
        model: Model,
        width: Int,
        height: Int,
        options: Options,
    ): ModelLoader.LoadData<ParcelFileDescriptor> {
        return ModelLoader.LoadData(
            ObjectKey(model.toString()),
            VideoOnlyPfdFetcher(context, model),
        )
    }

    class Factory<Model : Any>(private val context: Context) : ModelLoaderFactory<Model, ParcelFileDescriptor> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Model, ParcelFileDescriptor> {
            return VideoOnlyPfdModelLoader(context.applicationContext)
        }

        override fun teardown() = Unit
    }
}

class VideoOnlyAfdModelLoader<Model : Any>(
    private val context: Context,
) : ModelLoader<Model, AssetFileDescriptor> {
    override fun handles(model: Model): Boolean = !isAndroidResourceModel(model)

    override fun buildLoadData(
        model: Model,
        width: Int,
        height: Int,
        options: Options,
    ): ModelLoader.LoadData<AssetFileDescriptor> {
        return ModelLoader.LoadData(
            ObjectKey(model.toString()),
            VideoOnlyAfdFetcher(context, model),
        )
    }

    class Factory<Model : Any>(private val context: Context) : ModelLoaderFactory<Model, AssetFileDescriptor> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Model, AssetFileDescriptor> {
            return VideoOnlyAfdModelLoader(context.applicationContext)
        }

        override fun teardown() = Unit
    }
}

private class VideoOnlyPfdFetcher<Model : Any>(
    private val context: Context,
    private val model: Model,
) : DataFetcher<ParcelFileDescriptor> {
    private var pfd: ParcelFileDescriptor? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in ParcelFileDescriptor>) {
        val opened = openIfVerifiedVideo(context, model)
        if (opened != null) {
            pfd = opened
            callback.onDataReady(opened)
        } else {
            callback.onLoadFailed(SecurityException("Direct main-process image decoding is disabled"))
        }
    }

    override fun cleanup() {
        runCatching { pfd?.close() }
        pfd = null
    }

    override fun cancel() = Unit
    override fun getDataClass(): Class<ParcelFileDescriptor> = ParcelFileDescriptor::class.java
    override fun getDataSource(): DataSource = DataSource.LOCAL
}

private class VideoOnlyAfdFetcher<Model : Any>(
    private val context: Context,
    private val model: Model,
) : DataFetcher<AssetFileDescriptor> {
    private var afd: AssetFileDescriptor? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in AssetFileDescriptor>) {
        val opened = openIfVerifiedVideo(context, model)?.let { pfd ->
            AssetFileDescriptor(pfd, 0L, AssetFileDescriptor.UNKNOWN_LENGTH)
        }
        if (opened != null) {
            afd = opened
            callback.onDataReady(opened)
        } else {
            callback.onLoadFailed(SecurityException("Direct main-process image decoding is disabled"))
        }
    }

    override fun cleanup() {
        runCatching { afd?.close() }
        afd = null
    }

    override fun cancel() = Unit
    override fun getDataClass(): Class<AssetFileDescriptor> = AssetFileDescriptor::class.java
    override fun getDataSource(): DataSource = DataSource.LOCAL
}

private class FailingDataFetcher<Data>(private val dataClass: Class<Data>) : DataFetcher<Data> {
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Data>) {
        callback.onLoadFailed(SecurityException("Direct main-process image decoding is disabled"))
    }

    override fun cleanup() = Unit
    override fun cancel() = Unit
    override fun getDataClass(): Class<Data> = dataClass
    override fun getDataSource(): DataSource = DataSource.LOCAL
}

private fun normalizeDim(dim: Int): Int {
    return if (dim <= 0) IsolatedFormatSniffer.MAX_OUTPUT_DIMENSION else dim
}

private fun buildFlags(context: Context, targetW: Int, targetH: Int, options: Options): Int {
    var flags = IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION
    if (targetW in 1..THUMB_PREVIEW_LIMIT && targetH in 1..THUMB_PREVIEW_LIMIT) {
        flags = flags or IsolatedDecodeRequest.FLAG_PREFER_EMBEDDED_PREVIEW
    }
    if (options.get(IsolatedGlideOptions.SCALE_CENTER_CROP) == true) {
        flags = flags or IsolatedDecodeRequest.FLAG_CENTER_CROP
    }
    if (context.config.showSvgFiles) {
        flags = flags or IsolatedDecodeRequest.FLAG_ALLOW_SVG
    }
    if (options.get(IsolatedGlideOptions.INCLUDE_GAINMAP) == true) {
        flags = flags or IsolatedDecodeRequest.FLAG_INCLUDE_GAINMAP
    }
    return flags
}

private fun isAndroidResourceModel(model: Any): Boolean {
    return when (model) {
        is Uri -> model.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE
        is String -> Uri.parse(model).scheme == ContentResolver.SCHEME_ANDROID_RESOURCE
        else -> false
    }
}

private fun isVideoUriOrPath(context: Context, raw: String, uri: Uri): Boolean {
    if (IsolatedFormatSniffer.isVideoFileHint(raw)) return true
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (mime?.startsWith("video/") == true) return true
    }
    return false
}

private fun openIfVerifiedVideo(context: Context, model: Any): ParcelFileDescriptor? {
    return when (model) {
        is File -> {
            if (!CryptoManager.isEncryptedFile(model.name) && IsolatedFormatSniffer.isVideoFileHint(model.name)) {
                runCatching { ParcelFileDescriptor.open(model, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
            } else {
                null
            }
        }
        is Uri -> {
            if (isVideoUriOrPath(context, model.toString(), model) &&
                !CryptoManager.isEncryptedFile(model.path.orEmpty())
            ) {
                runCatching { context.contentResolver.openFileDescriptor(model, "r") }.getOrNull()
            } else {
                null
            }
        }
        is String -> {
            val uri = Uri.parse(model)
            val clean = if (uri.scheme == ContentResolver.SCHEME_FILE) {
                uri.path.orEmpty()
            } else {
                model.removePrefix("file://")
            }
            if (!CryptoManager.isEncryptedFile(clean) && isVideoUriOrPath(context, clean, uri)) {
                if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                    runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
                } else {
                    runCatching {
                        ParcelFileDescriptor.open(File(clean), ParcelFileDescriptor.MODE_READ_ONLY)
                    }.getOrNull()
                }
            } else {
                null
            }
        }
        else -> null
    }
}
