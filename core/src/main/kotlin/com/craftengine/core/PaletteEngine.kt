package com.craftengine.core

import kotlin.math.cbrt
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Adaptive palette generation tuned for diamond-painting photographs. */
object PaletteEngine {
    class Matcher internal constructor(private val palette: List<CraftColor>) {
        private val labs = palette.map { rgbToLab(it.argb) }
        fun nearest(argb: Int): Int {
            val p = rgbToLab(argb)
            var best = 0
            var bestD = Double.MAX_VALUE
            for (i in labs.indices) {
                val d = deltaEWeightedSq(p, labs[i])
                if (d < bestD) { bestD = d; best = i }
            }
            return best
        }
    }

    fun matcher(palette: List<CraftColor>): Matcher {
        require(palette.isNotEmpty())
        return Matcher(palette)
    }

    fun nearestColor(argb: Int, palette: List<CraftColor>): Int = matcher(palette).nearest(argb)

    /** Compatibility path; production photo conversion should prefer adaptivePaletteFromPixels. */
    fun adaptivePalette(image: CraftImage, requestedColors: Int): List<CraftColor> =
        adaptivePaletteFromPixels(image.pixels, requestedColors)

    /**
     * Builds the palette from the exact target-cell colors. Colors are first grouped into
     * a compact 5-bit RGB histogram so common shades carry more weight than one-off noise.
     * K-means then runs in Lab space with frequency weights.
     */
    fun adaptivePaletteFromPixels(pixels: IntArray, requestedColors: Int): List<CraftColor> {
        val wanted = requestedColors.coerceIn(12, 120)
        if (pixels.isEmpty()) return listOf(CraftColor("#808080", "RGB #808080", 0xFF808080.toInt()))

        data class Bin(var count: Int = 0, var sr: Long = 0, var sg: Long = 0, var sb: Long = 0)
        val bins = HashMap<Int, Bin>()
        for (raw in pixels) {
            val c = enhanceForPalette(raw)
            val r = c shr 16 and 255; val g = c shr 8 and 255; val b = c and 255
            val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
            val bin = bins.getOrPut(key) { Bin() }
            bin.count++
            bin.sr += r; bin.sg += g; bin.sb += b
        }

        data class Point(val lab: DoubleArray, val weight: Int)
        val points = bins.values.map { bin ->
            val r = (bin.sr / bin.count).toInt(); val g = (bin.sg / bin.count).toInt(); val b = (bin.sb / bin.count).toInt()
            Point(rgbToLab((0xFF shl 24) or (r shl 16) or (g shl 8) or b), bin.count)
        }
        if (points.size <= wanted) {
            return points.sortedBy { it.lab[0] }.mapIndexed { index, p ->
                run {
                    val argb = labToArgb(p.lab)
                    val hex = "#%06X".format(argb and 0xFFFFFF)
                    CraftColor(hex, "RGB $hex", argb)
                }
            }
        }

        val k = min(wanted, points.size)
        val centers = seedWeighted(points.map { it.lab }, points.map { it.weight }, k)
        val assignment = IntArray(points.size)

        repeat(12) {
            val sumL = DoubleArray(k); val sumA = DoubleArray(k); val sumB = DoubleArray(k); val weights = LongArray(k)
            for (i in points.indices) {
                val p = points[i]
                var best = 0; var bestD = Double.MAX_VALUE
                for (j in 0 until k) {
                    val d = deltaEWeightedSq(p.lab, centers[j])
                    if (d < bestD) { bestD = d; best = j }
                }
                assignment[i] = best
                val w = p.weight.toLong()
                sumL[best] += p.lab[0] * w
                sumA[best] += p.lab[1] * w
                sumB[best] += p.lab[2] * w
                weights[best] += w
            }
            for (j in 0 until k) if (weights[j] > 0) {
                centers[j][0] = sumL[j] / weights[j]
                centers[j][1] = sumA[j] / weights[j]
                centers[j][2] = sumB[j] / weights[j]
            }
        }

        val clusterWeight = LongArray(k)
        for (i in points.indices) clusterWeight[assignment[i]] += points[i].weight.toLong()
        val ordered = (0 until k).sortedByDescending { clusterWeight[it] }
        val unique = mutableListOf<DoubleArray>()
        for (idx in ordered) {
            if (clusterWeight[idx] == 0L) continue
            val c = centers[idx]
            // Avoid allocating separate palette entries to visually indistinguishable shades.
            if (unique.none { sqrt(deltaEWeightedSq(it, c)) < 2.7 }) unique += c.copyOf()
            if (unique.size >= wanted) break
        }

        return unique.sortedBy { it[0] }.mapIndexed { index, lab ->
            run {
                val argb = labToArgb(lab)
                val hex = "#%06X".format(argb and 0xFFFFFF)
                CraftColor(id = hex, name = "RGB $hex", argb = argb)
            }
        }
    }

    private fun seedWeighted(points: List<DoubleArray>, weights: List<Int>, k: Int): Array<DoubleArray> {
        val centers = Array(k) { DoubleArray(3) }
        var totalW = 0L
        val mean = DoubleArray(3)
        for (i in points.indices) {
            val w = weights[i].toLong(); totalW += w
            mean[0] += points[i][0] * w; mean[1] += points[i][1] * w; mean[2] += points[i][2] * w
        }
        mean[0] /= totalW.toDouble(); mean[1] /= totalW.toDouble(); mean[2] /= totalW.toDouble()
        var first = 0; var best = Double.MAX_VALUE
        for (i in points.indices) {
            val d = deltaEWeightedSq(points[i], mean)
            if (d < best) { best = d; first = i }
        }
        centers[0] = points[first].copyOf()

        val nearest = DoubleArray(points.size) { deltaEWeightedSq(points[it], centers[0]) }
        for (c in 1 until k) {
            var pick = 0; var score = -1.0
            for (i in points.indices) {
                // Frequency matters, but sqrt-like damping prevents a huge background from taking every seed.
                val weighted = nearest[i] * kotlin.math.sqrt(weights[i].toDouble())
                if (weighted > score) { score = weighted; pick = i }
            }
            centers[c] = points[pick].copyOf()
            for (i in points.indices) nearest[i] = min(nearest[i], deltaEWeightedSq(points[i], centers[c]))
        }
        return centers
    }

    private fun enhanceForPalette(argb: Int): Int {
        var r = argb shr 16 and 255; var g = argb shr 8 and 255; var b = argb and 255
        val contrast = 1.05
        r = ((r - 128.0) * contrast + 128.0).toInt().coerceIn(0, 255)
        g = ((g - 128.0) * contrast + 128.0).toInt().coerceIn(0, 255)
        b = ((b - 128.0) * contrast + 128.0).toInt().coerceIn(0, 255)
        val gray = 0.299 * r + 0.587 * g + 0.114 * b
        val sat = 1.06
        r = (gray + (r - gray) * sat).toInt().coerceIn(0, 255)
        g = (gray + (g - gray) * sat).toInt().coerceIn(0, 255)
        b = (gray + (b - gray) * sat).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun rgbToLab(argb: Int): DoubleArray {
        fun lin(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = lin(argb shr 16 and 255); val g = lin(argb shr 8 and 255); val b = lin(argb and 255)
        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
        fun f(t: Double): Double = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116.0
        val fx = f(x); val fy = f(y); val fz = f(z)
        return doubleArrayOf(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    private fun labToArgb(lab: DoubleArray): Int {
        val fy = (lab[0] + 16.0) / 116.0; val fx = lab[1] / 500.0 + fy; val fz = fy - lab[2] / 200.0
        fun inv(t: Double): Double { val t3 = t * t * t; return if (t3 > 0.008856) t3 else (t - 16.0 / 116.0) / 7.787 }
        val x = 0.95047 * inv(fx); val y = inv(fy); val z = 1.08883 * inv(fz)
        val r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val b = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
        fun srgb(v: Double): Int {
            val c = if (v <= 0.0031308) 12.92 * v else 1.055 * v.coerceAtLeast(0.0).pow(1.0 / 2.4) - 0.055
            return (c * 255.0).toInt().coerceIn(0, 255)
        }
        val ri = srgb(r); val gi = srgb(g); val bi = srgb(b)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    /** Slightly deemphasize L so similarly bright but differently colored drills stay distinct. */
    private fun deltaEWeightedSq(a: DoubleArray, b: DoubleArray): Double {
        val dl = (a[0] - b[0]) * 0.82
        val da = a[1] - b[1]
        val db = a[2] - b[2]
        return dl * dl + da * da + db * db
    }
}
