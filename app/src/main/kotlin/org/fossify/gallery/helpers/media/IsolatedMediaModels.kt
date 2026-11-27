package org.fossify.gallery.helpers.media

import android.os.Build
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import java.io.Closeable

data class IsolatedDecodeRequest(
    val inputPfd: ParcelFileDescriptor? = null,
    val sharedInputHandle: Parcelable? = null,
    val inputLength: Long = 0L,
    val maxWidth: Int = 0,
    val maxHeight: Int = 0,
    val nameHint: String = "",
    val flags: Int = FLAG_NONE,
) : Parcelable, Closeable {

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(inputPfd, flags)
        dest.writeParcelable(sharedInputHandle, flags)
        dest.writeLong(inputLength)
        dest.writeInt(maxWidth)
        dest.writeInt(maxHeight)
        dest.writeString(nameHint)
        dest.writeInt(this.flags)
    }

    override fun describeContents(): Int = Parcelable.CONTENTS_FILE_DESCRIPTOR

    override fun close() {
        runCatching { inputPfd?.close() }
        IsolatedPixelTransport.closeHandle(sharedInputHandle)
    }

    companion object {
        const val FLAG_NONE = 0
        const val FLAG_PREFER_EMBEDDED_PREVIEW = 1
        const val FLAG_APPLY_EXIF_ORIENTATION = 2
        const val FLAG_ALLOW_LARGE_OUTPUT = 4
        const val FLAG_CENTER_CROP = 8
        const val FLAG_ALLOW_SVG = 16
        const val FLAG_INCLUDE_GAINMAP = 32

        @JvmField
        val CREATOR: Parcelable.Creator<IsolatedDecodeRequest> =
            object : Parcelable.Creator<IsolatedDecodeRequest> {
                override fun createFromParcel(source: Parcel): IsolatedDecodeRequest {
                    return IsolatedDecodeRequest(
                        inputPfd = source.readCompatParcelable(ParcelFileDescriptor::class.java),
                        sharedInputHandle = source.readCompatParcelable(Parcelable::class.java),
                        inputLength = source.readLong(),
                        maxWidth = source.readInt(),
                        maxHeight = source.readInt(),
                        nameHint = source.readString().orEmpty(),
                        flags = source.readInt(),
                    )
                }

                override fun newArray(size: Int): Array<IsolatedDecodeRequest?> = arrayOfNulls(size)
            }
    }
}

data class IsolatedHdrPayload(
    val gainmapWidth: Int,
    val gainmapHeight: Int,
    val gainmapByteCount: Int,
    val gainmapHandle: Parcelable? = null,
    val gainmapPipePfd: ParcelFileDescriptor? = null,
    val gainmapMeta: FloatArray = FloatArray(0),
) : Parcelable, Closeable {

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(gainmapWidth)
        dest.writeInt(gainmapHeight)
        dest.writeInt(gainmapByteCount)
        dest.writeParcelable(gainmapHandle, flags)
        dest.writeParcelable(gainmapPipePfd, flags)
        dest.writeFloatArray(gainmapMeta)
    }

    override fun describeContents(): Int = Parcelable.CONTENTS_FILE_DESCRIPTOR

    override fun close() {
        IsolatedPixelTransport.closeHandle(gainmapHandle)
        runCatching { gainmapPipePfd?.close() }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IsolatedHdrPayload) return false
        return gainmapWidth == other.gainmapWidth &&
            gainmapHeight == other.gainmapHeight &&
            gainmapByteCount == other.gainmapByteCount &&
            gainmapHandle == other.gainmapHandle &&
            gainmapPipePfd == other.gainmapPipePfd &&
            gainmapMeta.contentEquals(other.gainmapMeta)
    }

    override fun hashCode(): Int {
        var result = gainmapWidth
        result = 31 * result + gainmapHeight
        result = 31 * result + gainmapByteCount
        result = 31 * result + (gainmapHandle?.hashCode() ?: 0)
        result = 31 * result + (gainmapPipePfd?.hashCode() ?: 0)
        result = 31 * result + gainmapMeta.contentHashCode()
        return result
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<IsolatedHdrPayload> =
            object : Parcelable.Creator<IsolatedHdrPayload> {
                override fun createFromParcel(source: Parcel): IsolatedHdrPayload {
                    return IsolatedHdrPayload(
                        gainmapWidth = source.readInt(),
                        gainmapHeight = source.readInt(),
                        gainmapByteCount = source.readInt(),
                        gainmapHandle = source.readCompatParcelable(Parcelable::class.java),
                        gainmapPipePfd = source.readCompatParcelable(ParcelFileDescriptor::class.java),
                        gainmapMeta = source.createFloatArray() ?: FloatArray(0),
                    )
                }

                override fun newArray(size: Int): Array<IsolatedHdrPayload?> = arrayOfNulls(size)
            }
    }
}

data class IsolatedDecodeResult(
    val status: Int,
    val width: Int = 0,
    val height: Int = 0,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val rotationDegrees: Int = 0,
    val byteCount: Int = 0,
    val sharedOutputHandle: Parcelable? = null,
    val pixelPipePfd: ParcelFileDescriptor? = null,
    val colorSpaceId: Int = IsolatedHdrTransport.COLOR_SPACE_SRGB,
    val hdrPayload: IsolatedHdrPayload? = null,
) : Parcelable, Closeable {

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(status)
        dest.writeInt(width)
        dest.writeInt(height)
        dest.writeInt(sourceWidth)
        dest.writeInt(sourceHeight)
        dest.writeInt(rotationDegrees)
        dest.writeInt(byteCount)
        dest.writeParcelable(sharedOutputHandle, flags)
        dest.writeParcelable(pixelPipePfd, flags)
        dest.writeInt(colorSpaceId)
        dest.writeParcelable(hdrPayload, flags)
    }

    override fun describeContents(): Int = Parcelable.CONTENTS_FILE_DESCRIPTOR

    override fun close() {
        IsolatedPixelTransport.closeHandle(sharedOutputHandle)
        runCatching { pixelPipePfd?.close() }
        runCatching { hdrPayload?.close() }
    }

    companion object {
        const val STATUS_OK = 0
        const val STATUS_DECODE_FAILED = 1
        const val STATUS_TOO_LARGE = 2
        const val STATUS_UNSUPPORTED = 3
        const val STATUS_INVALID_INPUT = 4

        @JvmField
        val CREATOR: Parcelable.Creator<IsolatedDecodeResult> =
            object : Parcelable.Creator<IsolatedDecodeResult> {
                override fun createFromParcel(source: Parcel): IsolatedDecodeResult {
                    return IsolatedDecodeResult(
                        status = source.readInt(),
                        width = source.readInt(),
                        height = source.readInt(),
                        sourceWidth = source.readInt(),
                        sourceHeight = source.readInt(),
                        rotationDegrees = source.readInt(),
                        byteCount = source.readInt(),
                        sharedOutputHandle = source.readCompatParcelable(Parcelable::class.java),
                        pixelPipePfd = source.readCompatParcelable(ParcelFileDescriptor::class.java),
                        colorSpaceId = source.readInt(),
                        hdrPayload = source.readCompatParcelable(IsolatedHdrPayload::class.java),
                    )
                }

                override fun newArray(size: Int): Array<IsolatedDecodeResult?> = arrayOfNulls(size)
            }
    }
}

data class IsolatedBoundsResult(
    val status: Int,
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
) : Parcelable {

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(status)
        dest.writeInt(width)
        dest.writeInt(height)
        dest.writeInt(rotationDegrees)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<IsolatedBoundsResult> =
            object : Parcelable.Creator<IsolatedBoundsResult> {
                override fun createFromParcel(source: Parcel): IsolatedBoundsResult {
                    return IsolatedBoundsResult(
                        status = source.readInt(),
                        width = source.readInt(),
                        height = source.readInt(),
                        rotationDegrees = source.readInt(),
                    )
                }

                override fun newArray(size: Int): Array<IsolatedBoundsResult?> = arrayOfNulls(size)
            }
    }
}

data class IsolatedRegionOpenResult(
    val status: Int,
    val sessionId: Long = -1L,
    val width: Int = 0,
    val height: Int = 0,
) : Parcelable {

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(status)
        dest.writeLong(sessionId)
        dest.writeInt(width)
        dest.writeInt(height)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<IsolatedRegionOpenResult> =
            object : Parcelable.Creator<IsolatedRegionOpenResult> {
                override fun createFromParcel(source: Parcel): IsolatedRegionOpenResult {
                    return IsolatedRegionOpenResult(
                        status = source.readInt(),
                        sessionId = source.readLong(),
                        width = source.readInt(),
                        height = source.readInt(),
                    )
                }

                override fun newArray(size: Int): Array<IsolatedRegionOpenResult?> = arrayOfNulls(size)
            }
    }
}

private fun <T : Parcelable> Parcel.readCompatParcelable(clazz: Class<T>): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        readParcelable(clazz.classLoader, clazz)
    } else {
        @Suppress("DEPRECATION")
        readParcelable(clazz.classLoader)
    }
}
