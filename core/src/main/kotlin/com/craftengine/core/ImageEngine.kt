package com.craftengine.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Platform-neutral image used by every Craft app. Pixels are row-major ARGB ints. */
data class CraftImage(val width: Int, val height: Int, val pixels: IntArray) {
    init { require(width > 0 && height > 0); require(pixels.size == width * height) }
    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]
}

data class ImageConversionOptions(
    val targetWidth: Int,
    val targetHeight: Int,
    val palette: List<CraftColor>
)

object ImageEngine {
    /** Center-crop + area sampling, then map every sample to the supplied product palette. */
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
            val sampled = averageRegion(image, sx0, sy0, sx1, sy1)
            cells += CraftCell(PaletteEngine.nearestColor(sampled, options.palette))
        }
        return CraftGrid(options.targetWidth, options.targetHeight, options.palette, cells)
    }

    private fun averageRegion(img: CraftImage, x0: Double, y0: Double, x1: Double, y1: Double): Int {
        val ix0 = max(0, x0.toInt()); val iy0 = max(0, y0.toInt())
        val ix1 = min(img.width - 1, max(ix0, (x1 - 0.0001).toInt()))
        val iy1 = min(img.height - 1, max(iy0, (y1 - 0.0001).toInt()))
        var a=0L; var r=0L; var g=0L; var b=0L; var n=0L
        for (y in iy0..iy1) for (x in ix0..ix1) {
            val c=img[x,y]; a += c ushr 24 and 255; r += c ushr 16 and 255; g += c ushr 8 and 255; b += c and 255; n++
        }
        if (n == 0L) return img[min(img.width-1,x0.roundToInt()), min(img.height-1,y0.roundToInt())]
        return ((a/n).toInt() shl 24) or ((r/n).toInt() shl 16) or ((g/n).toInt() shl 8) or (b/n).toInt()
    }
}
