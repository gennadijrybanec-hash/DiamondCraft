package com.craftengine.diamondcraft

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.craftengine.core.*
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { DiamondApp() } }
    }
}

private data class SavedProjectInfo(val file: File, val project: CraftProject)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiamondApp() {
    val context = LocalContext.current
    var project by remember { mutableStateOf<CraftProject?>(null) }
    var width by remember { mutableFloatStateOf(100f) }
    var colorCount by remember { mutableFloatStateOf(72f) }
    var reserve by remember { mutableFloatStateOf(10f) }
    var drillShape by remember { mutableStateOf(DrillShape.SQUARE) }
    var imageProfile by remember { mutableStateOf(ImageProfile.AUTO) }
    var status by remember { mutableStateOf("Выберите фотографию") }
    var savedRefresh by remember { mutableIntStateOf(0) }
    var shoppingListText by remember { mutableStateOf<String?>(null) }

    val savedProjects = remember(savedRefresh) { listSavedProjects(context) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val p = project ?: return@rememberLauncherForActivityResult
        if (uri != null) runCatching {
            val estimate = materialEstimate(p, drillShape, reserve.toInt())
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(materialsCsv(p, estimate))
            }
        }.onSuccess { status = "CSV сохранён" }
            .onFailure { status = "Не удалось сохранить CSV" }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val p = project ?: return@rememberLauncherForActivityResult
        if (uri != null) runCatching {
            val estimate = materialEstimate(p, drillShape, reserve.toInt())
            context.contentResolver.openOutputStream(uri)?.use { output ->
                writeMaterialsPdf(output, p, estimate)
            }
        }.onSuccess { status = "PDF сохранён" }
            .onFailure { status = "Не удалось сохранить PDF" }
    }

    val pngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val p = project ?: return@rememberLauncherForActivityResult
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                writePatternPng(output, p)
            }
        }.onSuccess { status = "PNG сохранён" }
            .onFailure { status = "Не удалось сохранить PNG" }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri).use { input -> BitmapFactory.decodeStream(input) }!!
        }.onSuccess { bmp ->
            val targetW = width.toInt().coerceIn(30, 200)
            val targetH = (targetW * bmp.height.toFloat() / bmp.width).toInt().coerceIn(30, 280)
            val pixels = IntArray(bmp.width * bmp.height)
            bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val source = CraftImage(bmp.width, bmp.height, pixels)

            status = "Анализируем фотографию…"
            val grid = ImageEngine.toAdaptiveGrid(
                image = source,
                targetWidth = targetW,
                targetHeight = targetH,
                requestedColors = colorCount.toInt(),
                profile = imageProfile
            )
            project = CraftProject(
                id = UUID.randomUUID().toString(),
                name = "Моя алмазная картина",
                mode = CraftMode.DIAMOND_PAINTING,
                grid = grid,
                updatedAt = System.currentTimeMillis()
            )
            status = "Схема создана: ${grid.width} × ${grid.height} • ${grid.palette.size} цветов"
        }.onFailure { status = "Не удалось открыть изображение" }
    }

    shoppingListText?.let { text ->
        AlertDialog(
            onDismissRequest = { shoppingListText = null },
            title = { Text("Список покупок") },
            text = {
                Box(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text(text)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("DiamondCraft — список покупок", text))
                    status = "Список покупок скопирован"
                    shoppingListText = null
                }) { Text("Копировать") }
            },
            dismissButton = {
                TextButton(onClick = { shoppingListText = null }) { Text("Закрыть") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("💎 DiamondCraft ${BuildConfig.VERSION_NAME}") }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(12.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Фото → схема алмазной мозаики", style = MaterialTheme.typography.titleMedium)

            Text("Ширина схемы: ${width.toInt()} страз")
            Slider(width, { width = it }, valueRange = 30f..200f, steps = 16)

            Text("Детализация цвета: ${colorCount.toInt()} цветов")
            Slider(colorCount, { colorCount = it }, valueRange = 24f..120f, steps = 7)

            Text("Для портретов: 100–140 страз и 60–84 цвета. Для пейзажей: 120–180 и 72–108 цветов.")

            Text("Профиль обработки")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ImageProfile.entries.forEachIndexed { index, profile ->
                    SegmentedButton(
                        selected = imageProfile == profile,
                        onClick = { imageProfile = profile },
                        shape = SegmentedButtonDefaults.itemShape(index, ImageProfile.entries.size)
                    ) { Text(profile.displayName) }
                }
            }

            Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text("Выбрать фотографию")
            }
            Text(status)

            if (savedProjects.isNotEmpty()) {
                HorizontalDivider()
                Text("Сохранённые проекты", style = MaterialTheme.typography.titleMedium)
                savedProjects.take(5).forEach { saved ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                project = saved.project
                                status = "Проект восстановлен: ${saved.project.name}"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("${saved.project.name} • ${saved.project.grid.width}×${saved.project.grid.height}")
                        }
                        TextButton(onClick = {
                            saved.file.delete()
                            savedRefresh++
                        }) { Text("Удалить") }
                    }
                }
            }

            project?.let { p ->
                val stats = DiamondEngine.stats(p.grid)
                val estimate = materialEstimate(p, drillShape, reserve.toInt())

                HorizontalDivider()
                Text("Проект", style = MaterialTheme.typography.titleMedium)
                Text("${p.grid.width} × ${p.grid.height} • ${p.grid.palette.size} цветов")
                Text("Установлено: ${stats.completedDrills} / ${stats.totalDrills} • ${p.grid.progressPercent()}%")

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            saveProject(context, p.copy(updatedAt = System.currentTimeMillis()))
                            savedRefresh++
                            status = "Проект сохранён"
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Сохранить") }
                    OutlinedButton(
                        onClick = {
                            project = p.copy(
                                grid = ProgressEngine.clear(p.grid),
                                updatedAt = System.currentTimeMillis()
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Сбросить прогресс") }
                }

                DiamondGrid(p.grid) { x, y ->
                    project = p.copy(
                        grid = ProgressEngine.toggle(p.grid, x, y),
                        updatedAt = System.currentTimeMillis()
                    )
                }

                HorizontalDivider()
                Text("Расходники", style = MaterialTheme.typography.titleMedium)

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    DrillShape.entries.forEachIndexed { index, shape ->
                        SegmentedButton(
                            selected = drillShape == shape,
                            onClick = { drillShape = shape },
                            shape = SegmentedButtonDefaults.itemShape(index, DrillShape.entries.size)
                        ) { Text(shape.displayName) }
                    }
                }

                Text("Запас страз: ${reserve.toInt()}%")
                Slider(reserve, { reserve = it }, valueRange = 5f..20f, steps = 2)

                Text("Размер картины: ${cm(estimate.pictureWidthCm)} × ${cm(estimate.pictureHeightCm)} см")
                Text("Клеевая основа с полями: ${cm(estimate.canvasWidthCm)} × ${cm(estimate.canvasHeightCm)} см")
                Text("Стразы: ${estimate.totalExactDrills} шт. + ${estimate.reservePercent}% = ${estimate.totalRequiredDrills} шт.")
                Text("Пакетиков по 200 шт.: примерно ${estimate.totalBags}")

                Text("Палитра и закупка", style = MaterialTheme.typography.titleMedium)
                estimate.colors.forEachIndexed { i, item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Box(Modifier.size(22.dp).background(Color(item.color.argb)))
                        Spacer(Modifier.width(8.dp))
                        Text(
                             "${i + 1}. ${item.color.id} — ${item.exactCount} шт.; купить ${item.requiredCount} (${item.bags} пак.)"
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pngLauncher.launch("DiamondCraft_${p.grid.width}x${p.grid.height}_pattern.png") },
                        modifier = Modifier.weight(1f)
                    ) { Text("Схема PNG") }
                    OutlinedButton(
                        onClick = { pdfLauncher.launch("DiamondCraft_${p.grid.width}x${p.grid.height}_materials.pdf") },
                        modifier = Modifier.weight(1f)
                    ) { Text("Расходники PDF") }
                }
                OutlinedButton(
                    onClick = { csvLauncher.launch("DiamondCraft_${p.grid.width}x${p.grid.height}_materials.csv") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Расходники CSV") }

                Button(
                    onClick = { shoppingListText = buildShoppingList(p, estimate) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Подготовить список покупок") }

                Text(
                    "Free: генерация и сохранение проектов. Pro: расширенный экспорт, большие схемы и магазины будут подключены после настройки Google Play Billing.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun materialEstimate(project: CraftProject, shape: DrillShape, reserve: Int) =
    DiamondMaterialEngine.estimate(
        project.grid,
        DiamondMaterialOptions(
            drillShape = shape,
            reservePercent = reserve,
            drillsPerBag = 200,
            canvasMarginCm = 3.0
        )
    )

private fun saveProject(context: Context, project: CraftProject) {
    val dir = File(context.filesDir, "projects").apply { mkdirs() }
    File(dir, "${project.id}.dcproj").writeText(ProjectCodec.encode(project))
}

private fun listSavedProjects(context: Context): List<SavedProjectInfo> {
    val dir = File(context.filesDir, "projects")
    if (!dir.exists()) return emptyList()
    return dir.listFiles { f -> f.extension == "dcproj" }
        ?.mapNotNull { file -> runCatching { SavedProjectInfo(file, ProjectCodec.decode(file.readText())) }.getOrNull() }
        ?.sortedByDescending { it.project.updatedAt }
        .orEmpty()
}

private fun buildShoppingList(project: CraftProject, estimate: DiamondMaterialEstimate): String = buildString {
    appendLine("DiamondCraft — список покупок")
    appendLine("Проект: ${project.name}")
    appendLine("Картина: ${cm(estimate.pictureWidthCm)} × ${cm(estimate.pictureHeightCm)} см")
    appendLine("Клеевая основа: ${cm(estimate.canvasWidthCm)} × ${cm(estimate.canvasHeightCm)} см")
    appendLine("Стразы: ${estimate.drillShape.displayName.lowercase()}")
    appendLine("Запас: ${estimate.reservePercent}%")
    appendLine("Всего купить: ${estimate.totalRequiredDrills} шт. (~${estimate.totalBags} пак. по 200 шт.)")
    appendLine()
    appendLine("По цветам:")
    estimate.colors.forEachIndexed { index, item ->
        appendLine("${index + 1}. ${item.color.id}: ${item.requiredCount} шт. (${item.bags} пак.)")
    }
    appendLine()
    appendLine("Дополнительно: клеевая основа, лоток, стилус, воск/клей.")
}

private fun materialsCsv(project: CraftProject, estimate: DiamondMaterialEstimate): String = buildString {
    appendLine("DiamondCraft;${BuildConfig.VERSION_NAME}")
    appendLine("Project;${project.name}")
    appendLine("Grid;${project.grid.width}x${project.grid.height}")
    appendLine("Picture cm;${cm(estimate.pictureWidthCm)}x${cm(estimate.pictureHeightCm)}")
    appendLine("Canvas cm;${cm(estimate.canvasWidthCm)}x${cm(estimate.canvasHeightCm)}")
    appendLine("Reserve;${estimate.reservePercent}%")
    appendLine("Color ID;Exact;Required;Bags")
    estimate.colors.forEach { appendLine("${it.color.id};${it.exactCount};${it.requiredCount};${it.bags}") }
}

private fun writeMaterialsPdf(output: java.io.OutputStream, project: CraftProject, estimate: DiamondMaterialEstimate) {
    val pdf = PdfDocument()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; isFakeBoldText = true }
    var pageNo = 1
    var page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
    var canvas = page.canvas
    var y = 45f

    fun newPage() {
        pdf.finishPage(page)
        pageNo++
        page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        canvas = page.canvas
        y = 45f
    }
    fun line(text: String, bold: Boolean = false) {
        if (y > 805f) newPage()
        canvas.drawText(text, 40f, y, if (bold) titlePaint else paint)
        y += if (bold) 28f else 20f
    }

    line("DiamondCraft ${BuildConfig.VERSION_NAME}", true)
    line("Project: ${project.name}")
    line("Grid: ${project.grid.width} x ${project.grid.height}")
    line("Picture: ${cm(estimate.pictureWidthCm)} x ${cm(estimate.pictureHeightCm)} cm")
    line("Canvas: ${cm(estimate.canvasWidthCm)} x ${cm(estimate.canvasHeightCm)} cm")
    line("Drills: ${estimate.totalRequiredDrills} incl. ${estimate.reservePercent}% reserve")
    line("Bags ~200 pcs: ${estimate.totalBags}")
    y += 8f
    line("Materials", true)
    estimate.colors.forEachIndexed { index, item ->
        line("${index + 1}. ${item.color.id}: ${item.exactCount} -> ${item.requiredCount} pcs (${item.bags} bags)")
    }

    pdf.finishPage(page)
    pdf.writeTo(output)
    pdf.close()
}

private fun writePatternPng(output: java.io.OutputStream, project: CraftProject) {
    val grid = project.grid
    // Keep memory predictable even for 200×280 projects.
    val cellPx = min(14, maxOf(6, 2800 / maxOf(grid.width, grid.height)))
    val bitmap = android.graphics.Bitmap.createBitmap(
        grid.width * cellPx,
        grid.height * cellPx,
        android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = android.graphics.Color.argb(90, 60, 60, 60)
    }
    grid.cells.forEachIndexed { index, cell ->
        if (cell.hidden) return@forEachIndexed
        val x = index % grid.width
        val y = index / grid.width
        val left = x * cellPx.toFloat()
        val top = y * cellPx.toFloat()
        fill.color = grid.palette[cell.colorIndex].argb
        canvas.drawRect(left, top, left + cellPx, top + cellPx, fill)
        if (cellPx >= 8) canvas.drawRect(left, top, left + cellPx, top + cellPx, line)
    }
    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
    bitmap.recycle()
}

private fun cm(value: Double): String = String.format(Locale.US, "%.1f", value)

@Composable
private fun DiamondGrid(grid: CraftGrid, onCell: (Int, Int) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(460.dp)
            .background(Color(0xFFF5F5F5))
            .clipToBounds()
            .pointerInput(grid, scale) {
                detectTransformGestures { _, panChange, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.7f, 8f)
                    val base = min(
                        size.width.toFloat() / grid.width.toFloat(),
                        size.height.toFloat() / grid.height.toFloat()
                    )
                    val contentWidth = base * newScale * grid.width
                    val contentHeight = base * newScale * grid.height
                    val proposed = pan + panChange
                    val minX = min(0f, size.width.toFloat() - contentWidth)
                    val minY = min(0f, size.height.toFloat() - contentHeight)
                    pan = Offset(
                        x = if (contentWidth <= size.width) 0f else proposed.x.coerceIn(minX, 0f),
                        y = if (contentHeight <= size.height) 0f else proposed.y.coerceIn(minY, 0f)
                    )
                    scale = newScale
                }
            }
            .pointerInput(grid, scale, pan) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (!change.pressed && change.previousPressed) {
                            val base = min(size.width / grid.width.toFloat(), size.height / grid.height.toFloat())
                            val cell = base * scale
                            val x = ((change.position.x - pan.x) / cell).toInt()
                            val y = ((change.position.y - pan.y) / cell).toInt()
                            if (x in 0 until grid.width && y in 0 until grid.height) onCell(x, y)
                        }
                    }
                }
            }
    ) {
        val base = min(size.width / grid.width, size.height / grid.height)
        val cell = base * scale
        grid.cells.forEachIndexed { idx, c ->
            val x = idx % grid.width
            val y = idx / grid.width
            val left = pan.x + x * cell
            val top = pan.y + y * cell
            val center = Offset(left + cell / 2, top + cell / 2)
            drawCircle(Color(grid.palette[c.colorIndex].argb), cell * 0.38f, center)
            drawCircle(
                if (c.completed) Color.Black else Color.Gray,
                cell * 0.38f,
                center,
                style = Stroke(if (c.completed) cell * 0.16f else 1f)
            )
        }
    }
}
