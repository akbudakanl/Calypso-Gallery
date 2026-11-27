package org.fossify.gallery.helpers.media

import android.graphics.Bitmap
import android.os.Build
import com.bumptech.glide.load.engine.Resource
import org.fossify.gallery.helpers.CryptoManager

/**
 * Isolated ADR 0002 §4 bitmap pool dedicated exclusively to decrypted Vault bitmaps so they
 * never enter Glide's shared `BitmapPool` and are always zeroized (`eraseColor(0)`) on release
 * and vault lock.
 */
internal object VaultBitmapPool {
    private const val MAX_POOL_BYTES = 32 * 1024 * 1024
    private const val MAX_SINGLE_POOLED_BYTES = MAX_POOL_BYTES / 2
    private const val MAX_POOL_COUNT = 3
    private const val BYTES_PER_PIXEL = 4L

    private val lock = Any()
    private val pool = ArrayDeque<Bitmap>(MAX_POOL_COUNT + 1)
    private var pooledBytes = 0

    fun acquire(width: Int, height: Int): Bitmap? {
        if (!CryptoManager.isVaultUnlocked || width <= 0 || height <= 0) {
            return null
        }
        val neededBytes = width.toLong() * height.toLong() * BYTES_PER_PIXEL
        if (neededBytes <= 0L || neededBytes > MAX_SINGLE_POOLED_BYTES) {
            return null
        }

        val candidate = takeSmallestSuitableLocked(neededBytes.toInt()) ?: return null
        if (candidate.width == width && candidate.height == height) {
            return candidate
        }
        return runCatching {
            candidate.reconfigure(width, height, Bitmap.Config.ARGB_8888)
            candidate
        }.getOrElse {
            runCatching { candidate.recycle() }
            null
        }
    }

    fun release(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        runCatching { bitmap.eraseColor(0) }

        if (!isPoolable(bitmap)) {
            runCatching { bitmap.recycle() }
            return
        }

        val size = bitmap.allocationByteCount
        val evicted = mutableListOf<Bitmap>()
        synchronized(lock) {
            pool.addLast(bitmap)
            pooledBytes += size
            while (pool.size > MAX_POOL_COUNT || pooledBytes > MAX_POOL_BYTES) {
                val oldest = pool.removeFirstOrNull() ?: break
                pooledBytes = (pooledBytes - oldest.allocationByteCount).coerceAtLeast(0)
                evicted.add(oldest)
            }
        }
        evicted.forEach { old ->
            runCatching { old.eraseColor(0) }
            runCatching { old.recycle() }
        }
    }

    fun clearAndZeroize() {
        val toDispose: List<Bitmap>
        synchronized(lock) {
            toDispose = pool.toList()
            pool.clear()
            pooledBytes = 0
        }
        for (bmp in toDispose) {
            if (!bmp.isRecycled) {
                runCatching { bmp.eraseColor(0) }
                runCatching { bmp.recycle() }
            }
        }
    }

    private fun takeSmallestSuitableLocked(neededBytes: Int): Bitmap? {
        synchronized(lock) {
            var bestIdx = -1
            var bestBytes = Int.MAX_VALUE
            for (i in pool.indices) {
                val bmp = pool[i]
                if (bmp.isRecycled) continue
                val alloc = bmp.allocationByteCount
                if (alloc >= neededBytes && alloc < bestBytes) {
                    bestBytes = alloc
                    bestIdx = i
                }
            }
            if (bestIdx < 0) {
                return null
            }
            val chosen = pool.removeAt(bestIdx)
            pooledBytes = (pooledBytes - chosen.allocationByteCount).coerceAtLeast(0)
            return if (chosen.isRecycled) null else chosen
        }
    }

    private fun isPoolable(bitmap: Bitmap): Boolean {
        if (!CryptoManager.isVaultUnlocked || !bitmap.isMutable) return false
        if (bitmap.config != Bitmap.Config.ARGB_8888) return false
        if (bitmap.allocationByteCount > MAX_SINGLE_POOLED_BYTES) return false
        if (bitmap.colorSpace?.isWideGamut == true) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && bitmap.hasGainmap()) {
            return false
        }
        return true
    }
}

internal class VaultBitmapResource(
    private val bitmap: Bitmap,
) : Resource<Bitmap> {
    override fun getResourceClass(): Class<Bitmap> = Bitmap::class.java

    override fun get(): Bitmap = bitmap

    override fun getSize(): Int = if (bitmap.isRecycled) 0 else bitmap.allocationByteCount

    override fun recycle() {
        VaultBitmapPool.release(bitmap)
    }
}
