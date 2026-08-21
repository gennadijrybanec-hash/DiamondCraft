package com.craftengine.core

data class CraftColor(val id: String, val name: String, val argb: Int)
data class CraftCell(val colorIndex: Int, val completed: Boolean = false, val hidden: Boolean = false)
data class CraftGrid(val width: Int, val height: Int, val palette: List<CraftColor>, val cells: List<CraftCell>) {
    init { require(width > 0 && height > 0); require(cells.size == width * height) }
    fun cell(x: Int, y: Int) = cells[y * width + x]
    fun completedCount() = cells.count { !it.hidden && it.completed }
    fun activeCount() = cells.count { !it.hidden }
    fun progressPercent(): Int = progressPercentExact().toInt()
    fun progressPercentExact(): Double = if (activeCount() == 0) 0.0 else completedCount() * 100.0 / activeCount()
}

enum class CraftMode { CROSS_STITCH, DIAMOND_PAINTING, COLOR_BY_NUMBER, PIXEL_ART, LASER, PYROGRAPHY }
data class CraftProject(val id: String, val name: String, val mode: CraftMode, val grid: CraftGrid, val updatedAt: Long)
