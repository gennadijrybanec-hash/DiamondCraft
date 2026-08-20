package com.craftengine.core

data class ProductProfile(val mode: CraftMode, val defaultColors: Int, val maxColors: Int, val supportsPhysicalPalette: Boolean, val exportFormats: Set<String>)
object ProductProfiles {
    val stitch = ProductProfile(CraftMode.CROSS_STITCH, 24, 64, true, setOf("PDF","PNG","CSV"))
    val diamond = ProductProfile(CraftMode.DIAMOND_PAINTING, 24, 64, true, setOf("PDF","PNG","CSV"))
    val color = ProductProfile(CraftMode.COLOR_BY_NUMBER, 12, 40, false, setOf("PDF","PNG"))
    val pixel = ProductProfile(CraftMode.PIXEL_ART, 16, 64, false, setOf("PNG","PDF"))
    val laser = ProductProfile(CraftMode.LASER, 2, 8, false, setOf("PNG","SVG"))
    val pyro = ProductProfile(CraftMode.PYROGRAPHY, 4, 12, false, setOf("PNG","PDF","SVG"))
}
