package com.craftengine.core

import kotlin.math.ceil

/** Common drill shapes used by diamond-painting suppliers. */
enum class DrillShape(val displayName: String, val pitchMm: Double) {
    SQUARE("Квадратные", 2.5),
    ROUND("Круглые", 2.8)
}

data class DiamondMaterialOptions(
    val drillShape: DrillShape = DrillShape.SQUARE,
    val reservePercent: Int = 10,
    val drillsPerBag: Int = 200,
    val canvasMarginCm: Double = 3.0
) {
    init {
        require(reservePercent in 0..100)
        require(drillsPerBag > 0)
        require(canvasMarginCm >= 0)
    }
}

data class DiamondColorRequirement(
    val color: CraftColor,
    val exactCount: Int,
    val requiredCount: Int,
    val bags: Int
)

data class DiamondMaterialEstimate(
    val drillShape: DrillShape,
    val reservePercent: Int,
    val totalExactDrills: Int,
    val totalRequiredDrills: Int,
    val pictureWidthCm: Double,
    val pictureHeightCm: Double,
    val canvasWidthCm: Double,
    val canvasHeightCm: Double,
    val colors: List<DiamondColorRequirement>
) {
    val totalBags: Int get() = colors.sumOf { it.bags }
}

object DiamondMaterialEngine {
    fun estimate(
        grid: CraftGrid,
        options: DiamondMaterialOptions = DiamondMaterialOptions()
    ): DiamondMaterialEstimate {
        val stats = DiamondEngine.stats(grid)
        val multiplier = 1.0 + options.reservePercent / 100.0

        val requirements = grid.palette.mapNotNull { color ->
            val exact = stats.byColor[color.id] ?: 0
            if (exact <= 0) return@mapNotNull null
            val required = ceil(exact * multiplier).toInt()
            DiamondColorRequirement(
                color = color,
                exactCount = exact,
                requiredCount = required,
                bags = ceil(required.toDouble() / options.drillsPerBag).toInt()
            )
        }

        val pictureWidthCm = grid.width * options.drillShape.pitchMm / 10.0
        val pictureHeightCm = grid.height * options.drillShape.pitchMm / 10.0

        return DiamondMaterialEstimate(
            drillShape = options.drillShape,
            reservePercent = options.reservePercent,
            totalExactDrills = stats.totalDrills,
            totalRequiredDrills = requirements.sumOf { it.requiredCount },
            pictureWidthCm = pictureWidthCm,
            pictureHeightCm = pictureHeightCm,
            canvasWidthCm = pictureWidthCm + options.canvasMarginCm * 2,
            canvasHeightCm = pictureHeightCm + options.canvasMarginCm * 2,
            colors = requirements
        )
    }
}
