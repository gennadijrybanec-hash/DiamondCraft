package com.craftengine.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Platform-neutral image used by every Craft app. Pixels are row-major ARGB ints. */
data class CraftImage(val width: Int, val height: Int, val pixels: IntArray) {
    init { require(width > 0 && height > 0); require(pixels.size == width * height) }
    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]
}

data class ImageConversionOptions(
    val targetWidth: Int,
    val targetHeight: Int,
    val palette: List<CraftColor>,
    val detailBoost: Double = 0.32,
    val saturationBoost: Double = 1.08,
    val contrastBoost: Double = 1.06
)

object ImageEngine {
    /**
     * Center-crop + high quality area sampling. Each cell combines a linear-light area
     * average with a center sample so facial features, eyes, fur and edges survive the
     * downscale instead of becoming large muddy blocks.
     */
    fun toGrid(image: CraftImage, options: ImageConversionOptions): CraftGrid {
        require(options.targetWidth > 0 && options.targetHeight > 0)
        require(options.palette.isNotEmpty())
        val sourceAspect = image.width.toDouble() / image.height
        val targetAspect = options.targetWidth.toDouble() / options.targetHeight
        val cropW: Double
        val cropH: Double
        if (sourceAspect > targetAspect) {
            cropH = image.height.toDouble(); cropW = cropH * targetAspect
        } else {
            cropW = image.width.toDouble(); cropH = cropW / targetAspect
        }
        val left = (image.width - cropW) / 2.0
        val top = (image.height - cropH) / 2.0
        val cells = ArrayList<CraftCell>(options.targetWidth * options.targetHeight)

        for (ty in 0 until options.targetHeight) for (tx in 0 until options.targetWidth) {
            val sx0 = left + tx * cropW / options.targetWidth
            val sy0 = top + ty * cropH / options.targetHeight
            val sx1 = left + (tx + 1) * cropW / options.targetWidth
            val sy1 = top + (ty + 1) * cropH / options.targetHeight

            val avg = averageRegionLinear(image, sx0, sy0, sx1, sy1)
            val cx = (sx0 + sx1) * 0.5
            val cy = (sy0 + sy1) * 0.5
            val center = bilinear(image, cx, cy)
            val mixed = mixAndEnhance(avg, center, options)
            cells += CraftCell(PaletteEngine.nearestColor(mixed, options.palette))
        }
        return CraftGrid(options.targetWidth, options.targetHeight, options.palette, cells)
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

    private fun mixAndEnhance(avg: Int, center: Int, o: ImageConversionOptions): Int {
        val detail = o.detailBoost.coerceIn(0.0, 0.65)
        var r = mix(avg shr 16 and 255, center shr 16 and 255, detail)
        var g = mix(avg shr 8 and 255, center shr 8 and 255, detail)
        var b = mix(avg and 255, center and 255, detail)

        // Contrast around middle gray.
        r = ((r - 128.0) * o.contrastBoost + 128.0).roundToInt().coerceIn(0, 255)
        g = ((g - 128.0) * o.contrastBoost + 128.0).roundToInt().coerceIn(0, 255)
        b = ((b - 128.0) * o.contrastBoost + 128.0).roundToInt().coerceIn(0, 255)

        // Small saturation lift: enough to separate neighboring drill colors without neon artifacts.
        val gray = (0.299 * r + 0.587 * g + 0.114 * b)
        r = (gray + (r - gray) * o.saturationBoost).roundToInt().coerceIn(0, 255)
        g = (gray + (g - gray) * o.saturationBoost).roundToInt().coerceIn(0, 255)
        b = (gray + (b - gray) * o.saturationBoost).roundToInt().coerceIn(0, 255)
        return argb(r, g, b)
    }

    private fun mix(a: Int, b: Int, t: Double): Int = (a * (1.0 - t) + b * t).roundToInt().coerceIn(0, 255)
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
