package org.fossify.gallery.helpers.media

import android.os.Build
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable

data class IsolatedGifOpenResult(
    val status: Int,
    val sessionId: Long = 0L,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val frameCount: Int = 0,
    val slot0Handle: Parcelable? = null,
    val slot1Handle: Parcelable? = null,
) : Parcelable {
    override fun describeContents(): Int = Parcelable.CONTENTS_FILE_DESCRIPTOR

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(status)
        dest.writeLong(sessionId)
        dest.writeInt(frameWidth)
        dest.writeInt(frameHeight)
        dest.writeInt(frameCount)
        dest.writeParcelable(slot0Handle, flags)
        dest.writeParcelable(slot1Handle, flags)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<IsolatedGifOpenResult> =
            object : Parcelable.Creator<IsolatedGifOpenResult> {
                override fun createFromParcel(source: Parcel): IsolatedGifOpenResult {
                    return IsolatedGifOpenResult(
                        status = source.readInt(),
                        sessionId = source.readLong(),
                        frameWidth = source.readInt(),
                        frameHeight = source.readInt(),
                        frameCount = source.readInt(),
                        slot0Handle = source.readGifCompatParcelable(Parcelable::class.java),
                        slot1Handle = source.readGifCompatParcelable(Parcelable::class.java),
                    )
                }

                override fun newArray(size: Int): Array<IsolatedGifOpenResult?> = arrayOfNulls(size)
            }
    }
}

data class IsolatedGifFrameResult(
    val status: Int,
    val slotIndex: Int = 0,
    val durationMs: Int = 0,
    val byteCount: Int = 0,
    val pixelPipePfd: ParcelFileDescriptor? = null,
) : Parcelable {
    override fun describeContents(): Int = Parcelable.CONTENTS_FILE_DESCRIPTOR

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(status)
        dest.writeInt(slotIndex)
        dest.writeInt(durationMs)
        dest.writeInt(byteCount)
        dest.writeParcelable(pixelPipePfd, flags)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<IsolatedGifFrameResult> =
            object : Parcelable.Creator<IsolatedGifFrameResult> {
                override fun createFromParcel(source: Parcel): IsolatedGifFrameResult {
                    return IsolatedGifFrameResult(
                        status = source.readInt(),
                        slotIndex = source.readInt(),
                        durationMs = source.readInt(),
                        byteCount = source.readInt(),
                        pixelPipePfd = source.readGifCompatParcelable(ParcelFileDescriptor::class.java),
                    )
                }

                override fun newArray(size: Int): Array<IsolatedGifFrameResult?> = arrayOfNulls(size)
            }
    }
}

private fun <T : Parcelable> Parcel.readGifCompatParcelable(clazz: Class<T>): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        readParcelable(clazz.classLoader, clazz)
    } else {
        @Suppress("DEPRECATION")
        readParcelable(clazz.classLoader)
    }
}
