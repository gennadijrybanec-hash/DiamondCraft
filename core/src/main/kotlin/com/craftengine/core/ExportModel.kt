package com.craftengine.core

data class ExportCell(val x: Int, val y: Int, val colorId: String, val completed: Boolean)
data class ExportDocument(val projectName: String, val mode: CraftMode, val width: Int, val height: Int, val palette: List<CraftColor>, val cells: List<ExportCell>)
object ExportModelFactory {
    fun from(project: CraftProject): ExportDocument {
        val g = project.grid
        return ExportDocument(project.name, project.mode, g.width, g.height, g.palette,
            g.cells.mapIndexedNotNull { i, cell -> if (cell.hidden) null else ExportCell(i % g.width, i / g.width, g.palette[cell.colorIndex].id, cell.completed) })
    }
}
