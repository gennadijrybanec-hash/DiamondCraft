package com.craftengine.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Color selection and adaptive image quantisation for Craft products. */
object PaletteEngine {
    /**
     * Perceptual-ish weighted RGB distance. It preserves visible luminance differences
     * much better than plain Euclidean RGB while remaining fast on Android.
     */
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
     * Creates an image-specific palette using deterministic k-means. This is used for
     * preview/quality generation; a later store adapter can map these colors to physical
     * drill catalogue codes.
     */
    fun adaptivePalette(image: CraftImage, requestedColors: Int): List<CraftColor> {
        val k = requestedColors.coerceIn(8, 144)
        val samples = samplePixels(image, 18000)
        if (samples.isEmpty()) return listOf(CraftColor("DC001", "Цвет 1", 0xFF808080.toInt()))

        val centers = seedCenters(samples, min(k, samples.size))
        val assignments = IntArray(samples.size)

        repeat(10) {
            val sumR = LongArray(centers.size)
            val sumG = LongArray(centers.size)
            val sumB = LongArray(centers.size)
            val count = IntArray(centers.size)

            for (i in samples.indices) {
                val p = samples[i]
                val r = p shr 16 and 255
                val g = p shr 8 and 255
                val b = p and 255
                var best = 0
                var bestD = Long.MAX_VALUE
                for (j in centers.indices) {
                    val c = centers[j]
                    val d = colorDistance(r, g, b, c[0], c[1], c[2])
                    if (d < bestD) { bestD = d; best = j }
                }
                assignments[i] = best
                sumR[best] = sumR[best] + r.toLong(); sumG[best] = sumG[best] + g.toLong(); sumB[best] = sumB[best] + b.toLong(); count[best] = count[best] + 1
            }

            for (j in centers.indices) if (count[j] > 0) {
                centers[j][0] = (sumR[j] / count[j]).toInt()
                centers[j][1] = (sumG[j] / count[j]).toInt()
                centers[j][2] = (sumB[j] / count[j]).toInt()
            }
        }

        val weighted = centers.mapIndexed { index, c ->
            val usage = assignments.count { it == index }
            Pair(c, usage)
        }.filter { it.second > 0 }
            .sortedBy { luminance(it.first[0], it.first[1], it.first[2]) }

        return weighted.mapIndexed { index, pair ->
            val c = pair.first
            val argb = (0xFF shl 24) or (c[0] shl 16) or (c[1] shl 8) or c[2]
            CraftColor(
                id = "DC${(index + 1).toString().padStart(3, '0')}",
                name = "Цвет ${index + 1}",
                argb = argb
            )
        }
    }

    private fun samplePixels(image: CraftImage, limit: Int): IntArray {
        val total = image.pixels.size
        if (total <= limit) return image.pixels.copyOf()
        val step = max(1, total / limit)
        val out = IntArray(min(limit, (total + step - 1) / step))
        var src = 0; var dst = 0
        while (src < total && dst < out.size) {
            out[dst++] = image.pixels[src]
            src += step
        }
        return if (dst == out.size) out else out.copyOf(dst)
    }

    private fun seedCenters(samples: IntArray, k: Int): MutableList<IntArray> {
        // Sort by perceptual luminance, then take evenly spaced representatives.
        val sorted = samples.sortedBy {
            luminance(it shr 16 and 255, it shr 8 and 255, it and 255)
        }
        return MutableList(k) { i ->
            val pos = if (k == 1) sorted.size / 2 else i * (sorted.size - 1) / (k - 1)
            val p = sorted[pos]
            intArrayOf(p shr 16 and 255, p shr 8 and 255, p and 255)
        }
    }

    private fun luminance(r: Int, g: Int, b: Int): Int = 299 * r + 587 * g + 114 * b

    private fun colorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Long {
        val rMean = (r1 + r2) / 2
        val dr = r1 - r2
        val dg = g1 - g2
        val db = b1 - b2
        // Compuphase weighted RGB metric, integer form.
        return (((512 + rMean) * dr * dr) shr 8).toLong() +
            (4L * dg * dg) +
            (((767 - rMean) * db * db) shr 8).toLong()
    }
}
