package org.fossify.gallery.svg

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.module.AppGlideModule
import org.fossify.gallery.helpers.media.BlockedMainProcessByteBufferFileLoader
import org.fossify.gallery.helpers.media.BlockedMainProcessStreamLoader
import org.fossify.gallery.helpers.media.IsolatedBitmapPacket
import org.fossify.gallery.helpers.media.IsolatedBitmapResourceDecoder
import org.fossify.gallery.helpers.media.IsolatedFileModelLoader
import org.fossify.gallery.helpers.media.IsolatedStringModelLoader
import org.fossify.gallery.helpers.media.IsolatedUriModelLoader
import org.fossify.gallery.helpers.media.VideoOnlyAfdModelLoader
import org.fossify.gallery.helpers.media.VideoOnlyPfdModelLoader
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer

@GlideModule
class SvgModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val appContext = context.applicationContext
        registry.replace(GlideUrl::class.java, InputStream::class.java, DisabledGlideNetworkLoader.Factory())
        registry.replace(String::class.java, InputStream::class.java, BlockedMainProcessStreamLoader.Factory())
        registry.replace(File::class.java, InputStream::class.java, BlockedMainProcessStreamLoader.Factory())
        registry.replace(Uri::class.java, InputStream::class.java, BlockedMainProcessStreamLoader.Factory())
        registry.replace(File::class.java, ByteBuffer::class.java, BlockedMainProcessByteBufferFileLoader.Factory())
        registry.replace(
            String::class.java,
            ParcelFileDescriptor::class.java,
            VideoOnlyPfdModelLoader.Factory(appContext),
        )
        registry.replace(
            File::class.java,
            ParcelFileDescriptor::class.java,
            VideoOnlyPfdModelLoader.Factory(appContext),
        )
        registry.replace(
            Uri::class.java,
            ParcelFileDescriptor::class.java,
            VideoOnlyPfdModelLoader.Factory(appContext),
        )
        registry.replace(String::class.java, AssetFileDescriptor::class.java, VideoOnlyAfdModelLoader.Factory())
        registry.replace(File::class.java, AssetFileDescriptor::class.java, VideoOnlyAfdModelLoader.Factory())
        registry.replace(Uri::class.java, AssetFileDescriptor::class.java, VideoOnlyAfdModelLoader.Factory())

        registry.prepend(
            String::class.java,
            IsolatedBitmapPacket::class.java,
            IsolatedStringModelLoader.Factory(appContext),
        )
        registry.prepend(
            File::class.java,
            IsolatedBitmapPacket::class.java,
            IsolatedFileModelLoader.Factory(appContext),
        )
        registry.prepend(Uri::class.java, IsolatedBitmapPacket::class.java, IsolatedUriModelLoader.Factory(appContext))
        registry.prepend(
            IsolatedBitmapPacket::class.java,
            Bitmap::class.java,
            IsolatedBitmapResourceDecoder(glide.bitmapPool),
        )
    }

    override fun isManifestParsingEnabled() = false

    private class DisabledGlideNetworkLoader : ModelLoader<GlideUrl, InputStream> {
        override fun handles(model: GlideUrl): Boolean = true

        override fun buildLoadData(
            model: GlideUrl,
            width: Int,
            height: Int,
            options: Options,
        ): ModelLoader.LoadData<InputStream> {
            return ModelLoader.LoadData(
                model,
                object : DataFetcher<InputStream> {
                    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
                        callback.onLoadFailed(SecurityException("Network access is permanently disabled in Calypso Gallery"))
                    }

                    override fun cleanup() {}
                    override fun cancel() {}
                    override fun getDataClass(): Class<InputStream> = InputStream::class.java
                    override fun getDataSource(): DataSource = DataSource.LOCAL
                },
            )
        }

        class Factory : ModelLoaderFactory<GlideUrl, InputStream> {
            override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<GlideUrl, InputStream> =
                DisabledGlideNetworkLoader()

            override fun teardown() {}
        }
    }
}
