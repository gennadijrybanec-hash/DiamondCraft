package com.craftengine.core

import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Adaptive palette generation tuned for diamond-painting photographs. */
object PaletteEngine {
    /** Fast final matcher used for every generated drill cell. */
    fun nearestColor(argb: Int, palette: List<CraftColor>): Int {
        require(palette.isNotEmpty())
        val r1 = argb shr 16 and 255
        val g1 = argb shr 8 and 255
        val b1 = argb and 255
        return palette.indices.minBy { i ->
            val c = palette[i].argb
            val r2 = c shr 16 and 255
            val g2 = c shr 8 and 255
            val b2 = c and 255
            colorDistance(r1, g1, b1, r2, g2, b2)
        }
    }

    /**
     * Builds a photograph-specific palette. v0.6 uses spatially uniform samples and
     * farthest-point Lab seeding, so a high color count does not collapse into dozens
     * of nearly identical beige/gray colors.
     */
    fun adaptivePalette(image: CraftImage, requestedColors: Int): List<CraftColor> {
        val wanted = requestedColors.coerceIn(12, 120)
        val samples = samplePixels2D(image, 14000)
        if (samples.isEmpty()) return listOf(CraftColor("DC001", "Цвет 1", 0xFF808080.toInt()))

        val enhanced = IntArray(samples.size) { enhanceForPalette(samples[it]) }
        val labs = Array(enhanced.size) { rgbToLab(enhanced[it]) }
        val k = min(wanted, enhanced.size)
        val centers = seedFarthest(labs, k)
        val assignments = IntArray(enhanced.size)

        repeat(9) {
            val sumL = DoubleArray(k)
            val sumA = DoubleArray(k)
            val sumB = DoubleArray(k)
            val count = IntArray(k)

            for (i in labs.indices) {
                val p = labs[i]
                var best = 0
                var bestD = Double.MAX_VALUE
                for (j in 0 until k) {
                    val d = labDistanceSq(p, centers[j])
                    if (d < bestD) { bestD = d; best = j }
                }
                assignments[i] = best
                sumL[best] += p[0]
                sumA[best] += p[1]
                sumB[best] += p[2]
                count[best]++
            }

            for (j in 0 until k) {
                if (count[j] > 0) {
                    centers[j][0] = sumL[j] / count[j]
                    centers[j][1] = sumA[j] / count[j]
                    centers[j][2] = sumB[j] / count[j]
                } else {
                    // Re-seed an empty cluster at a sample farthest from all live centers.
                    var farIdx = 0
                    var farD = -1.0
                    for (i in labs.indices step 3) {
                        var nearest = Double.MAX_VALUE
                        for (c in centers) nearest = min(nearest, labDistanceSq(labs[i], c))
                        if (nearest > farD) { farD = nearest; farIdx = i }
                    }
                    centers[j] = labs[farIdx].copyOf()
                }
            }
        }

        // Remove near duplicates that can still arise after convergence.
        val unique = mutableListOf<DoubleArray>()
        val usage = IntArray(k)
        assignments.forEach { usage[it]++ }
        val order = (0 until k).sortedByDescending { usage[it] }
        for (idx in order) {
            if (usage[idx] == 0) continue
            val c = centers[idx]
            if (unique.none { sqrt(labDistanceSq(it, c)) < 3.2 }) unique += c.copyOf()
            if (unique.size >= wanted) break
        }

        // Keep colors in a stable dark-to-light order for the materials list.
        return unique.sortedBy { it[0] }.mapIndexed { index, lab ->
            val argb = labToArgb(lab)
            CraftColor(
                id = "DC${(index + 1).toString().padStart(3, '0')}",
                name = "Цвет ${index + 1}",
                argb = argb
            )
        }
    }

    private fun samplePixels2D(image: CraftImage, limit: Int): IntArray {
        val total = image.width * image.height
        if (total <= limit) return image.pixels.copyOf()
        val scale = sqrt(total.toDouble() / limit)
        val step = max(1, scale.toInt())
        val out = ArrayList<Int>(limit)
        var y = step / 2
        while (y < image.height && out.size < limit) {
            var x = step / 2
            while (x < image.width && out.size < limit) {
                out += image[x, y]
                x += step
            }
            y += step
        }
        return out.toIntArray()
    }

    private fun seedFarthest(points: Array<DoubleArray>, k: Int): Array<DoubleArray> {
        val centers = Array(k) { DoubleArray(3) }
        // First center: point closest to average Lab, not an extreme highlight/shadow.
        val mean = DoubleArray(3)
        for (p in points) { mean[0] += p[0]; mean[1] += p[1]; mean[2] += p[2] }
        mean[0] = mean[0] / points.size; mean[1] = mean[1] / points.size; mean[2] = mean[2] / points.size
        var first = 0
        var firstD = Double.MAX_VALUE
        for (i in points.indices) {
            val d = labDistanceSq(points[i], mean)
            if (d < firstD) { firstD = d; first = i }
        }
        centers[0] = points[first].copyOf()

        val nearest = DoubleArray(points.size) { labDistanceSq(points[it], centers[0]) }
        for (c in 1 until k) {
            var far = 0
            var farScore = -1.0
            for (i in points.indices) {
                // Cap extreme outliers slightly so one noisy pixel does not monopolize a seed.
                val score = min(nearest[i], 5200.0)
                if (score > farScore) { farScore = score; far = i }
            }
            centers[c] = points[far].copyOf()
            for (i in points.indices) nearest[i] = min(nearest[i], labDistanceSq(points[i], centers[c]))
        }
        return centers
    }

    private fun enhanceForPalette(argb: Int): Int {
        var r = argb shr 16 and 255
        var g = argb shr 8 and 255
        var b = argb and 255
        val contrast = 1.12
        r = ((r - 128.0) * contrast + 128.0).toInt().coerceIn(0, 255)
        g = ((g - 128.0) * contrast + 128.0).toInt().coerceIn(0, 255)
        b = ((b - 128.0) * contrast + 128.0).toInt().coerceIn(0, 255)
        val gray = 0.299 * r + 0.587 * g + 0.114 * b
        val sat = 1.18
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
        val r = lin(argb shr 16 and 255)
        val g = lin(argb shr 8 and 255)
        val b = lin(argb and 255)
        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
        val y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b)
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
        fun f(t: Double): Double = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116.0
        val fx = f(x); val fy = f(y); val fz = f(z)
        return doubleArrayOf(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    private fun labToArgb(lab: DoubleArray): Int {
        val fy = (lab[0] + 16.0) / 116.0
        val fx = lab[1] / 500.0 + fy
        val fz = fy - lab[2] / 200.0
        fun inv(t: Double): Double {
            val t3 = t * t * t
            return if (t3 > 0.008856) t3 else (t - 16.0 / 116.0) / 7.787
        }
        val x = 0.95047 * inv(fx)
        val y = inv(fy)
        val z = 1.08883 * inv(fz)
        var r =  3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        var g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        var b =  0.0556434 * x - 0.2040259 * y + 1.0572252 * z
        fun srgb(v: Double): Int {
            val c = if (v <= 0.0031308) 12.92 * v else 1.055 * v.coerceAtLeast(0.0).pow(1.0 / 2.4) - 0.055
            return (c * 255.0).toInt().coerceIn(0, 255)
        }
        val ri = srgb(r); val gi = srgb(g); val bi = srgb(b)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    private fun labDistanceSq(a: DoubleArray, b: DoubleArray): Double {
        val dl = a[0] - b[0]
        val da = a[1] - b[1]
        val db = a[2] - b[2]
        return dl * dl + da * da + db * db
    }

    private fun colorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Long {
        val rMean = (r1 + r2) / 2
        val dr = r1 - r2
        val dg = g1 - g2
        val db = b1 - b2
        return (((512 + rMean) * dr * dr) shr 8).toLong() +
            (4L * dg * dg) +
            (((767 - rMean) * db * db) shr 8).toLong()
    }
}
