package org.fossify.gallery.helpers

import android.util.Log
import org.fossify.gallery.BuildConfig

/**
 * ADR §11.1 - oynaticinin gercek okuma desenini cihazda olcmek icin probe.
 *
 * Debug derlemelerde **varsayilan aciktir** (SELinux setprop kisitlarina bagli
 * degildir). Release derlemelerde yalnizca sistem ozelligi ile acilir:
 *   adb shell setprop cipher.measure_reads 1
 *
 * Kullanim: debug APK'yi kur, bir sifreli video oynat/scrub yap, sonra:
 *   adb logcat -s CipherReadProbe
 *
 * Bu sinif sadece olcum verisi toplar; kriptografik bir islem yapmaz.
 */
internal class MediaReadPatternProbe {
    private val enabled = BuildConfig.DEBUG || runCatching {
        System.getProperty("cipher.measure_reads") == "1"
    }.getOrDefault(false)

    private var opens = 0
    private var seeks = 0
    private var reads = 0
    private var bytes = 0L
    private val sizeBuckets = IntArray(BUCKET_COUNT)

    fun onOpen(position: Long) {
        if (!enabled) return
        opens++
        if (position > 0L) seeks++
    }

    fun onRead(requested: Int, actual: Int) {
        if (!enabled || actual <= 0) return
        reads++
        bytes += actual.toLong()
        sizeBuckets[bucketOf(requested)]++
    }

    fun onSeek(position: Long) {
        if (!enabled || position <= 0L) return
        seeks++
    }

    fun dumpIfEnabled() {
        if (!enabled) return
        val total = sizeBuckets.sum().coerceAtLeast(1)
        val sb = StringBuilder("CipherReadProbe: opens=$opens seeks=$seeks reads=$reads bytes=$bytes ")
        sizeBuckets.forEachIndexed { i, count ->
            if (count > 0) {
                val label = if (i == BUCKET_COUNT - 1) {
                    ">=64K"
                } else {
                    val low = if (i == 0) 1 else (128 shl (i - 1)) + 1
                    val high = 128 shl i
                    "${low}B..${high}B"
                }
                sb.append("$label=${count * 100 / total}% ")
            }
        }
        Log.i(TAG, sb.toString())
    }

    private fun bucketOf(size: Int): Int {
        var v = size.coerceAtLeast(1)
        var bucket = 0
        while (v > 128 && bucket < BUCKET_COUNT - 1) {
            v /= 2
            bucket++
        }
        return bucket
    }

    private companion object {
        const val TAG = "CipherReadProbe"
        const val BUCKET_COUNT = 10
    }
}
