package com.craftengine.core

object ProgressEngine {
    fun toggle(grid: CraftGrid, x: Int, y: Int): CraftGrid {
        if (x !in 0 until grid.width || y !in 0 until grid.height) return grid
        val index = y * grid.width + x
        val cells = grid.cells.toMutableList()
        cells[index] = cells[index].copy(completed = !cells[index].completed)
        return grid.copy(cells = cells)
    }
    fun clear(grid: CraftGrid) = grid.copy(cells = grid.cells.map { it.copy(completed = false) })
}
