package com.craftengine.diamondcraft

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.craftengine.core.CraftProject
import com.craftengine.core.DiamondMaterialEstimate
import java.io.OutputStream
import kotlin.math.min

/** Print-sized paginated, numbered COLOR pattern; unlike the separate materials-only PDF. */
internal fun writePatternPdf(output: OutputStream, project: CraftProject, estimate: DiamondMaterialEstimate) {
    val grid = project.grid
    require(grid.width > 0 && grid.height > 0 && grid.palette.isNotEmpty())
    val pdf = PdfDocument()
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 10f }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 6.2f; textAlign = Paint.Align.CENTER }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 0.35f }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val cell = 12f
    val colsPerPage = 40
    val rowsPerPage = 54
    val pagesX = (grid.width + colsPerPage - 1) / colsPerPage
    val pagesY = (grid.height + rowsPerPage - 1) / rowsPerPage
    val totalPatternPages = pagesX * pagesY
    var pageNo = 0
    try {
        for (tileY in 0 until pagesY) for (tileX in 0 until pagesX) {
            pageNo++
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
            val canvas = page.canvas
            val startX = tileX * colsPerPage
            val startY = tileY * rowsPerPage
            val cols = min(colsPerPage, grid.width - startX)
            val rows = min(rowsPerPage, grid.height - startY)
            text.textSize = 15f
            canvas.drawText("DiamondCraft - ${project.name.take(43)}", 35f, 38f, text)
            text.textSize = 10f
            canvas.drawText("Pattern ${grid.width} x ${grid.height}  |  page $pageNo / $totalPatternPages  |  columns ${startX + 1}-${startX + cols}, rows ${startY + 1}-${startY + rows}", 35f, 57f, text)
            val x0 = 48f
            val y0 = 83f
            for (ry in 0 until rows) for (rx in 0 until cols) {
                val x = startX + rx
                val y = startY + ry
                val drill = grid.cells[y * grid.width + x]
                val left = x0 + rx * cell
                val top = y0 + ry * cell
                fill.color = if (drill.hidden) android.graphics.Color.WHITE else grid.palette[drill.colorIndex].argb or -0x1000000
                canvas.drawRect(left, top, left + cell, top + cell, fill)
                stroke.strokeWidth = if (x % 10 == 0 || y % 10 == 0) 0.9f else 0.25f
                canvas.drawRect(left, top, left + cell, top + cell, stroke)
                if (!drill.hidden) {
                    val color = fill.color
                    val luminance = (android.graphics.Color.red(color) * 299 + android.graphics.Color.green(color) * 587 + android.graphics.Color.blue(color) * 114) / 1000
                    small.color = if (luminance < 140) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                    // 1-based palette key remains visible even on a black-and-white print.
                    canvas.drawText((drill.colorIndex + 1).toString(), left + cell / 2f, top + 8.5f, small)
                }
            }
            text.textSize = 9f
            canvas.drawText("Number in each cell = palette number; full color key and material quantities follow the pattern.", 35f, 754f, text)
            pdf.finishPage(page)
        }
        val counts = IntArray(grid.palette.size)
        grid.cells.forEach { if (!it.hidden) counts[it.colorIndex]++ }
        val perPage = 48
        val legendPages = (grid.palette.size + perPage - 1) / perPage
        for (part in 0 until legendPages) {
            pageNo++
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
            val canvas = page.canvas
            text.textSize = 17f
            canvas.drawText("DiamondCraft - color key / materials", 35f, 40f, text)
            text.textSize = 10f
            canvas.drawText("${grid.width} x ${grid.height}  |  ${estimate.totalRequiredDrills} drills incl. ${estimate.reservePercent}% reserve  |  ~${estimate.totalBags} bags", 35f, 60f, text)
            canvas.drawText("No.  Color ID                       In pattern           Buy with reserve", 35f, 88f, text)
            for (i in part * perPage until min((part + 1) * perPage, grid.palette.size)) {
                val y = 109f + (i % perPage) * 14.3f
                fill.color = grid.palette[i].argb or -0x1000000
                canvas.drawRect(38f, y - 8f, 49f, y + 2f, fill)
                stroke.strokeWidth = 0.4f
                canvas.drawRect(38f, y - 8f, 49f, y + 2f, stroke)
                val material = estimate.colors.firstOrNull { it.color.id == grid.palette[i].id }
                text.textSize = 10f
                canvas.drawText("${i + 1}.   ${grid.palette[i].id.take(24)}", 56f, y, text)
                canvas.drawText("${counts[i]}", 295f, y, text)
                canvas.drawText("${material?.requiredCount ?: counts[i]} drills, ${material?.bags ?: 0} bags", 391f, y, text)
            }
            pdf.finishPage(page)
        }
        pdf.writeTo(output)
    } finally {
        pdf.close()
    }
}
