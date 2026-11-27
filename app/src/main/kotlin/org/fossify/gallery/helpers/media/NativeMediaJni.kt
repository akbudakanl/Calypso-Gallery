package org.fossify.gallery.helpers.media

import java.nio.ByteBuffer

/**
 * Raw JNI bindings backed by pure-Rust `libcalypso_media.so` (`rust/calypso-media`).
 */
internal object NativeMediaJni {
    @JvmStatic
    external fun probeBoundsNative(formatCode: Int, data: ByteArray): IntArray?

    @JvmStatic
    external fun decodeStaticRgbaNative(
        formatCode: Int,
        data: ByteArray,
        maxWidth: Int,
        maxHeight: Int,
        outDims: IntArray,
    ): ByteArray?

    @JvmStatic
    external fun decodeStaticRgbaExNative(
        formatCode: Int,
        data: ByteArray,
        maxWidth: Int,
        maxHeight: Int,
        exifOrientation: Int,
        scaleMode: Int,
        outMeta: IntArray,
    ): ByteArray?

    @Suppress("LongParameterList")
    @JvmStatic
    external fun decodeStaticRgbaIntoBufferNative(
        formatCode: Int,
        data: ByteArray,
        maxWidth: Int,
        maxHeight: Int,
        exifOrientation: Int,
        scaleMode: Int,
        directBuffer: ByteBuffer,
        outMeta: IntArray,
    ): Boolean

    @JvmStatic
    external fun openGifSessionNative(
        data: ByteArray,
        maxWidth: Int,
        maxHeight: Int,
        outInfo: IntArray,
    ): Long

    @JvmStatic
    external fun renderGifFrameNative(handle: Long, frameIndex: Int, outMeta: IntArray): ByteArray?

    @JvmStatic
    external fun renderGifFrameIntoBufferNative(
        handle: Long,
        frameIndex: Int,
        directBuffer: ByteBuffer,
        outMeta: IntArray,
    ): Boolean

    @JvmStatic
    external fun closeGifSessionNative(handle: Long)

    @JvmStatic
    external fun closeAllGifSessionsNative()
}
