package org.fossify.gallery.helpers.filters

import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Color tint overlay parameters blended linearly onto the image RGB channels.
 */
data class ColorOverlaySpec(
    val depth: Float,
    val red: Float,
    val green: Float,
    val blue: Float,
)

/**
 * Optional master RGB and per-channel `(x, y)` control points in `0..255`.
 */
data class ToneCurveSpec(
    val rgb: List<Pair<Int, Int>>? = null,
    val red: List<Pair<Int, Int>>? = null,
    val green: List<Pair<Int, Int>>? = null,
    val blue: List<Pair<Int, Int>>? = null,
)

/**
 * Pure-Kotlin pixel math for `ColorMatrix` composition and monotone-cubic tone curves.
 */
@Suppress("MagicNumber")
object PhotoFilterMath {
    const val LUT_SIZE = 256
    const val LUT_MAX_INDEX = 255
    const val COLOR_MATRIX_SIZE = 20

    private const val LUM_R = 0.213f
    private const val LUM_G = 0.715f
    private const val LUM_B = 0.072f
    private const val CONTRAST_MIDPOINT = 128f
    private const val CHANNEL_MAX_F = 255f
    private const val ALPHA_MASK = -0x1000000
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BYTE_MASK = 0xFF
    private const val MONOTONE_MAX_NORM = 3.0

    fun buildColorMatrixValues(
        brightness: Float,
        contrast: Float,
        saturation: Float,
        overlay: ColorOverlaySpec?,
    ): FloatArray {
        val invSat = 1f - saturation
        val rWeight = LUM_R * invSat
        val gWeight = LUM_G * invSat
        val bWeight = LUM_B * invSat

        val values = floatArrayOf(
            rWeight + saturation, gWeight, bWeight, 0f, 0f,
            rWeight, gWeight + saturation, bWeight, 0f, 0f,
            rWeight, gWeight, bWeight + saturation, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )

        val offset = CONTRAST_MIDPOINT * (1f - contrast) + brightness
        for (row in 0..2) {
            val base = row * 5
            values[base] *= contrast
            values[base + 1] *= contrast
            values[base + 2] *= contrast
            values[base + 4] = offset
        }

        if (overlay != null) {
            val depth = overlay.depth.coerceIn(0f, 1f)
            val keep = 1f - depth
            val overlayAdds = floatArrayOf(
                overlay.red.coerceIn(0f, 1f) * CHANNEL_MAX_F * depth,
                overlay.green.coerceIn(0f, 1f) * CHANNEL_MAX_F * depth,
                overlay.blue.coerceIn(0f, 1f) * CHANNEL_MAX_F * depth,
            )
            for (row in 0..2) {
                val base = row * 5
                values[base] *= keep
                values[base + 1] *= keep
                values[base + 2] *= keep
                values[base + 4] = values[base + 4] * keep + overlayAdds[row]
            }
        }
        return values
    }

    fun buildToneLut(points: List<Pair<Int, Int>>): IntArray {
        if (points.size < 2) {
            return IntArray(LUT_SIZE) { it }
        }
        val normalized = normalizeControlPoints(points)
        if (normalized.size < 2) {
            return IntArray(LUT_SIZE) { it }
        }
        if (normalized.size == 2) {
            return buildLinearLut(normalized[0], normalized[1])
        }
        return buildMonotoneCubicLut(normalized)
    }

    fun buildChannelLuts(spec: ToneCurveSpec): Triple<IntArray, IntArray, IntArray> {
        val master = spec.rgb?.let(::buildToneLut)
        val rLut = composeLut(master, spec.red?.let(::buildToneLut))
        val gLut = composeLut(master, spec.green?.let(::buildToneLut))
        val bLut = composeLut(master, spec.blue?.let(::buildToneLut))
        return Triple(rLut, gLut, bLut)
    }

    fun applyLutsToPixels(
        pixels: IntArray,
        rLut: IntArray,
        gLut: IntArray,
        bLut: IntArray,
    ) {
        for (i in pixels.indices) {
            val px = pixels[i]
            val r = rLut[(px ushr RED_SHIFT) and BYTE_MASK]
            val g = gLut[(px ushr GREEN_SHIFT) and BYTE_MASK]
            val b = bLut[px and BYTE_MASK]
            pixels[i] = (px and ALPHA_MASK) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
        }
    }

    private fun composeLut(first: IntArray?, second: IntArray?): IntArray {
        return when {
            first == null && second == null -> IntArray(LUT_SIZE) { it }
            first == null -> second!!
            second == null -> first
            else -> IntArray(LUT_SIZE) { idx -> second[first[idx].coerceIn(0, LUT_MAX_INDEX)] }
        }
    }

    private fun normalizeControlPoints(points: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val map = sortedMapOf<Int, Int>()
        for ((x, y) in points) {
            map[x.coerceIn(0, LUT_MAX_INDEX)] = y.coerceIn(0, LUT_MAX_INDEX)
        }
        val list = map.entries.map { it.key to it.value }.toMutableList()
        if (list.isNotEmpty() && list.first().first > 0) {
            list.add(0, 0 to list.first().second)
        }
        if (list.isNotEmpty() && list.last().first < LUT_MAX_INDEX) {
            list.add(LUT_MAX_INDEX to list.last().second)
        }
        return list
    }

    private fun buildLinearLut(start: Pair<Int, Int>, end: Pair<Int, Int>): IntArray {
        val lut = IntArray(LUT_SIZE)
        val dx = (end.first - start.first).coerceAtLeast(1).toFloat()
        for (x in 0 until LUT_SIZE) {
            val t = ((x - start.first) / dx).coerceIn(0f, 1f)
            lut[x] = (start.second + (end.second - start.second) * t)
                .roundToInt()
                .coerceIn(0, LUT_MAX_INDEX)
        }
        return lut
    }

    private fun buildMonotoneCubicLut(points: List<Pair<Int, Int>>): IntArray {
        val n = points.size
        val xs = DoubleArray(n) { points[it].first.toDouble() }
        val ys = DoubleArray(n) { points[it].second.toDouble() }
        val deltas = DoubleArray(n - 1) { i ->
            (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]).coerceAtLeast(1.0)
        }
        val tangents = DoubleArray(n)
        tangents[0] = deltas[0]
        tangents[n - 1] = deltas[n - 2]
        for (i in 1 until n - 1) {
            if (deltas[i - 1] * deltas[i] <= 0.0) {
                tangents[i] = 0.0
            } else {
                tangents[i] = (deltas[i - 1] + deltas[i]) * 0.5
            }
        }
        for (i in 0 until n - 1) {
            if (deltas[i] == 0.0) {
                tangents[i] = 0.0
                tangents[i + 1] = 0.0
            } else {
                val a = tangents[i] / deltas[i]
                val b = tangents[i + 1] / deltas[i]
                val mag = hypot(a, b)
                if (mag > MONOTONE_MAX_NORM) {
                    val scale = MONOTONE_MAX_NORM / mag
                    tangents[i] = scale * a * deltas[i]
                    tangents[i + 1] = scale * b * deltas[i]
                }
            }
        }

        val lut = IntArray(LUT_SIZE)
        var seg = 0
        for (x in 0 until LUT_SIZE) {
            while (seg < n - 2 && x.toDouble() > xs[seg + 1]) {
                seg++
            }
            val h = (xs[seg + 1] - xs[seg]).coerceAtLeast(1.0)
            val t = ((x.toDouble() - xs[seg]) / h).coerceIn(0.0, 1.0)
            val t2 = t * t
            val t3 = t2 * t
            val h00 = 2.0 * t3 - 3.0 * t2 + 1.0
            val h10 = t3 - 2.0 * t2 + t
            val h01 = -2.0 * t3 + 3.0 * t2
            val h11 = t3 - t2
            val value = h00 * ys[seg] + h10 * h * tangents[seg] + h01 * ys[seg + 1] + h11 * h * tangents[seg + 1]
            lut[x] = value.roundToInt().coerceIn(0, LUT_MAX_INDEX)
        }
        return lut
    }
}
