package com.craftengine.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Platform-neutral image used by every Craft app. Pixels are row-major ARGB ints. */
data class CraftImage(val width: Int, val height: Int, val pixels: IntArray) {
    init { require(width > 0 && height > 0); require(pixels.size == width * height) }
    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]
}



enum class ImageProfile(
    val displayName: String,
    val detail: Double,
    val saturation: Double,
    val contrast: Double,
    val cleanupPasses: Int,
    val islandVotes: Int
) {
    AUTO("Авто", 0.36, 1.10, 1.08, 1, 6),
    PORTRAIT("Портрет", 0.28, 1.04, 1.05, 1, 7),
    OBJECT("Предмет", 0.46, 1.14, 1.12, 1, 5),
    LANDSCAPE("Пейзаж", 0.40, 1.16, 1.10, 1, 5)
}

data class ImageConversionOptions(
    val targetWidth: Int,
    val targetHeight: Int,
    val palette: List<CraftColor>,
    val detailBoost: Double = 0.42,
    val saturationBoost: Double = 1.16,
    val contrastBoost: Double = 1.13
)

object ImageEngine {
    /**
     * Production path for photographs: first creates the exact target-cell image,
     * performs edge-preserving cleanup, then builds the palette from those cells.
     * This avoids wasting palette entries on source pixels that disappear after down-sampling.
     */
    fun toAdaptiveGrid(
        image: CraftImage,
        targetWidth: Int,
        targetHeight: Int,
        requestedColors: Int,
        profile: ImageProfile = ImageProfile.AUTO
    ): CraftGrid {
        require(targetWidth > 0 && targetHeight > 0)
        val sampled = sampleTarget(
            image,
            targetWidth,
            targetHeight,
            profile.detail,
            profile.saturation,
            profile.contrast
        )
        val cleaned = edgePreservingCleanup(sampled, targetWidth, targetHeight, profile.cleanupPasses)
        val palette = PaletteEngine.adaptivePaletteFromPixels(cleaned, requestedColors)
        val matcher = PaletteEngine.matcher(palette)
        val mapped = IntArray(cleaned.size) { matcher.nearest(cleaned[it]) }
        val stable = cleanupMappedIslands(mapped, targetWidth, targetHeight, profile.islandVotes)
        val cells = stable.map { CraftCell(it) }
        return CraftGrid(targetWidth, targetHeight, palette, cells)
    }

    /** Center crop + area sampling with edge/detail preservation for a supplied physical palette. */
    fun toGrid(image: CraftImage, options: ImageConversionOptions): CraftGrid {
        require(options.targetWidth > 0 && options.targetHeight > 0)
        require(options.palette.isNotEmpty())
        val sampled = sampleTarget(
            image,
            options.targetWidth,
            options.targetHeight,
            options.detailBoost,
            options.saturationBoost,
            options.contrastBoost
        )
        val cleaned = edgePreservingCleanup(sampled, options.targetWidth, options.targetHeight)
        val matcher = PaletteEngine.matcher(options.palette)
        return CraftGrid(
            options.targetWidth,
            options.targetHeight,
            options.palette,
            cleaned.map { CraftCell(matcher.nearest(it)) }
        )
    }

    private fun sampleTarget(
        image: CraftImage,
        targetWidth: Int,
        targetHeight: Int,
        detailBoost: Double,
        saturationBoost: Double,
        contrastBoost: Double
    ): IntArray {
        val sourceAspect = image.width.toDouble() / image.height
        val targetAspect = targetWidth.toDouble() / targetHeight
        val cropW: Double
        val cropH: Double
        if (sourceAspect > targetAspect) {
            cropH = image.height.toDouble(); cropW = cropH * targetAspect
        } else {
            cropW = image.width.toDouble(); cropH = cropW / targetAspect
        }
        val left = (image.width - cropW) / 2.0
        val top = (image.height - cropH) / 2.0
        val result = IntArray(targetWidth * targetHeight)

        for (ty in 0 until targetHeight) for (tx in 0 until targetWidth) {
            val sx0 = left + tx * cropW / targetWidth
            val sy0 = top + ty * cropH / targetHeight
            val sx1 = left + (tx + 1) * cropW / targetWidth
            val sy1 = top + (ty + 1) * cropH / targetHeight

            val avg = averageRegionLinear(image, sx0, sy0, sx1, sy1)
            val cx = (sx0 + sx1) * 0.5
            val cy = (sy0 + sy1) * 0.5
            val center = bilinear(image, cx, cy)
            val detail = strongestDetailSample(image, avg, sx0, sy0, sx1, sy1)
            result[ty * targetWidth + tx] = mixAndEnhance(
                avg, center, detail, detailBoost, saturationBoost, contrastBoost
            )
        }
        return result
    }

    /**
     * Removes isolated one-cell noise while keeping real edges. A neighbour contributes only
     * when it is perceptually close to the center cell, so facial outlines and object borders stay sharp.
     */
    private fun edgePreservingCleanup(src: IntArray, width: Int, height: Int, passes: Int = 1): IntArray {
        if (width < 3 || height < 3) return src
        var current = src
        repeat(passes.coerceIn(0, 2)) {
            val out = current.copyOf()
            for (y in 1 until height - 1) for (x in 1 until width - 1) {
                val center = current[y * width + x]
                var sr = (center shr 16 and 255) * 3.0
                var sg = (center shr 8 and 255) * 3.0
                var sb = (center and 255) * 3.0
                var weight = 3.0
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val c = current[(y + dy) * width + (x + dx)]
                    val d = perceptualRgbDistance(center, c)
                    if (d < 2200) {
                        val w = if (dx == 0 || dy == 0) 1.0 else 0.65
                        sr += (c shr 16 and 255) * w
                        sg += (c shr 8 and 255) * w
                        sb += (c and 255) * w
                        weight += w
                    }
                }
                out[y * width + x] = argb(
                    (sr / weight).roundToInt().coerceIn(0, 255),
                    (sg / weight).roundToInt().coerceIn(0, 255),
                    (sb / weight).roundToInt().coerceIn(0, 255)
                )
            }
            current = out
        }
        return current
    }

    /** Removes only obvious one-cell palette islands after quantization. */
    private fun cleanupMappedIslands(src: IntArray, width: Int, height: Int, requiredVotes: Int): IntArray {
        if (width < 3 || height < 3) return src
        val out = src.copyOf()
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val index = y * width + x
            val center = src[index]
            val counts = HashMap<Int, Int>(8)
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val value = src[(y + dy) * width + (x + dx)]
                counts[value] = (counts[value] ?: 0) + 1
            }
            val winner = counts.maxByOrNull { it.value }
            if (winner != null && winner.key != center && winner.value >= requiredVotes) {
                out[index] = winner.key
            }
        }
        return out
    }

    private fun strongestDetailSample(img: CraftImage, avg: Int, x0: Double, y0: Double, x1: Double, y1: Double): Int {
        val points = arrayOf(
            doubleArrayOf((x0 * 3 + x1) / 4.0, (y0 * 3 + y1) / 4.0),
            doubleArrayOf((x0 + x1 * 3) / 4.0, (y0 * 3 + y1) / 4.0),
            doubleArrayOf((x0 * 3 + x1) / 4.0, (y0 + y1 * 3) / 4.0),
            doubleArrayOf((x0 + x1 * 3) / 4.0, (y0 + y1 * 3) / 4.0)
        )
        var best = bilinear(img, points[0][0], points[0][1])
        var bestD = perceptualRgbDistance(avg, best)
        for (i in 1 until points.size) {
            val c = bilinear(img, points[i][0], points[i][1])
            val d = perceptualRgbDistance(avg, c)
            if (d > bestD) { bestD = d; best = c }
        }
        return best
    }

    private fun averageRegionLinear(img: CraftImage, x0: Double, y0: Double, x1: Double, y1: Double): Int {
        val ix0 = max(0, x0.toInt()); val iy0 = max(0, y0.toInt())
        val ix1 = min(img.width - 1, max(ix0, (x1 - 0.0001).toInt()))
        val iy1 = min(img.height - 1, max(iy0, (y1 - 0.0001).toInt()))
        var lr = 0.0; var lg = 0.0; var lb = 0.0; var n = 0
        for (y in iy0..iy1) for (x in ix0..ix1) {
            val c = img[x, y]
            lr += srgbToLinear(c shr 16 and 255)
            lg += srgbToLinear(c shr 8 and 255)
            lb += srgbToLinear(c and 255)
            n++
        }
        if (n == 0) return img[min(img.width - 1, x0.roundToInt()), min(img.height - 1, y0.roundToInt())]
        return argb(linearToSrgb(lr / n), linearToSrgb(lg / n), linearToSrgb(lb / n))
    }

    private fun bilinear(img: CraftImage, x: Double, y: Double): Int {
        val fx = x.coerceIn(0.0, (img.width - 1).toDouble())
        val fy = y.coerceIn(0.0, (img.height - 1).toDouble())
        val x0 = fx.toInt(); val y0 = fy.toInt()
        val x1 = min(img.width - 1, x0 + 1); val y1 = min(img.height - 1, y0 + 1)
        val dx = fx - x0; val dy = fy - y0
        fun channel(shift: Int): Int {
            fun ch(px: Int) = (px shr shift) and 255
            val a = ch(img[x0, y0]) * (1 - dx) + ch(img[x1, y0]) * dx
            val b = ch(img[x0, y1]) * (1 - dx) + ch(img[x1, y1]) * dx
            return (a * (1 - dy) + b * dy).roundToInt().coerceIn(0, 255)
        }
        return argb(channel(16), channel(8), channel(0))
    }

    private fun mixAndEnhance(
        avg: Int,
        center: Int,
        detail: Int,
        detailBoost: Double,
        saturationBoost: Double,
        contrastBoost: Double
    ): Int {
        val centerWeight = detailBoost.coerceIn(0.0, 0.50)
        val detailWeight = 0.08
        var r = weighted(avg shr 16 and 255, center shr 16 and 255, detail shr 16 and 255, centerWeight, detailWeight)
        var g = weighted(avg shr 8 and 255, center shr 8 and 255, detail shr 8 and 255, centerWeight, detailWeight)
        var b = weighted(avg and 255, center and 255, detail and 255, centerWeight, detailWeight)

        r = ((r - 128.0) * contrastBoost + 128.0).roundToInt().coerceIn(0, 255)
        g = ((g - 128.0) * contrastBoost + 128.0).roundToInt().coerceIn(0, 255)
        b = ((b - 128.0) * contrastBoost + 128.0).roundToInt().coerceIn(0, 255)

        val gray = 0.299 * r + 0.587 * g + 0.114 * b
        r = (gray + (r - gray) * saturationBoost).roundToInt().coerceIn(0, 255)
        g = (gray + (g - gray) * saturationBoost).roundToInt().coerceIn(0, 255)
        b = (gray + (b - gray) * saturationBoost).roundToInt().coerceIn(0, 255)
        return argb(r, g, b)
    }

    private fun weighted(a: Int, center: Int, detail: Int, cw: Double, dw: Double): Int {
        val aw = (1.0 - cw - dw).coerceAtLeast(0.35)
        val norm = aw + cw + dw
        return ((a * aw + center * cw + detail * dw) / norm).roundToInt().coerceIn(0, 255)
    }

    private fun perceptualRgbDistance(a: Int, b: Int): Int {
        val r1 = a shr 16 and 255; val g1 = a shr 8 and 255; val b1 = a and 255
        val r2 = b shr 16 and 255; val g2 = b shr 8 and 255; val b2 = b and 255
        val rMean = (r1 + r2) / 2
        val dr = r1 - r2; val dg = g1 - g2; val db = b1 - b2
        return (((512 + rMean) * dr * dr) shr 8) + 4 * dg * dg + (((767 - rMean) * db * db) shr 8)
    }

    private fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun srgbToLinear(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }
    private fun linearToSrgb(v: Double): Int {
        val s = if (v <= 0.0031308) 12.92 * v else 1.055 * Math.pow(v, 1.0 / 2.4) - 0.055
        return (s * 255.0).roundToInt().coerceIn(0, 255)
    }
}
