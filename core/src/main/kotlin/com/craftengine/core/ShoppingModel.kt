package com.craftengine.core

/** Product-neutral shopping model. Store/API adapters can be added without changing project logic. */
enum class SupplyType { DRILLS, ADHESIVE_CANVAS, TOOL_KIT }

data class SupplyRequest(
    val type: SupplyType,
    val query: String,
    val colorId: String? = null,
    val quantity: Int? = null,
    val widthCm: Double? = null,
    val heightCm: Double? = null
)

object DiamondShoppingModel {
    fun from(estimate: DiamondMaterialEstimate): List<SupplyRequest> {
        val drillRequests = estimate.colors.map { item ->
            SupplyRequest(
                type = SupplyType.DRILLS,
                query = "${estimate.drillShape.displayName} стразы ${item.color.id}",
                colorId = item.color.id,
                quantity = item.requiredCount
            )
        }
        return drillRequests + SupplyRequest(
            type = SupplyType.ADHESIVE_CANVAS,
            query = "Клеевая основа для алмазной мозаики",
            widthCm = estimate.canvasWidthCm,
            heightCm = estimate.canvasHeightCm
        )
    }
}
