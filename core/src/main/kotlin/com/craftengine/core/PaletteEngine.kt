package com.craftengine.core

import kotlin.math.pow

object PaletteEngine {
    fun nearestColor(argb: Int, palette: List<CraftColor>): Int {
        require(palette.isNotEmpty())
        val r = argb shr 16 and 255; val g = argb shr 8 and 255; val b = argb and 255
        return palette.indices.minBy { i ->
            val c = palette[i].argb
            val dr = r - (c shr 16 and 255); val dg = g - (c shr 8 and 255); val db = b - (c and 255)
            dr.toDouble().pow(2) + dg.toDouble().pow(2) + db.toDouble().pow(2)
        }
    }
}
