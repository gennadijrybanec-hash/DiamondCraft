package com.craftengine.core

data class DiamondStats(val totalDrills:Int, val completedDrills:Int, val byColor:Map<String,Int>)
object DiamondEngine {
    fun stats(grid:CraftGrid):DiamondStats {
        val counts=linkedMapOf<String,Int>()
        grid.cells.forEach { cell -> if(!cell.hidden){ val id=grid.palette[cell.colorIndex].id; counts[id]=(counts[id]?:0)+1 } }
        return DiamondStats(grid.activeCount(),grid.completedCount(),counts)
    }
    fun symbolFor(colorIndex:Int):String = (colorIndex + 1).toString()
}
