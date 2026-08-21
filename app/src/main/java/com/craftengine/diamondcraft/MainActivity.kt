package com.craftengine.diamondcraft

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
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
import java.util.Locale
import java.util.UUID
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { DiamondApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiamondApp() {
    val context = LocalContext.current
    var project by remember { mutableStateOf<CraftProject?>(null) }
    var width by remember { mutableFloatStateOf(80f) }
    var colorCount by remember { mutableFloatStateOf(60f) }
    var reserve by remember { mutableFloatStateOf(10f) }
    var drillShape by remember { mutableStateOf(DrillShape.SQUARE) }
    var status by remember { mutableStateOf("Выберите фотографию") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri).use { input -> BitmapFactory.decodeStream(input) }!!
        }.onSuccess { bmp ->
            val maxSide = width.toInt().coerceIn(20, 160)
            val h = (maxSide * bmp.height.toFloat() / bmp.width).toInt().coerceIn(20, 220)
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val source = CraftImage(bmp.width, bmp.height, px)
            status = "Анализ цветов и деталей…"
            val adaptivePalette = PaletteEngine.adaptivePalette(source, colorCount.toInt())
            val grid = ImageEngine.toGrid(
                source,
                ImageConversionOptions(maxSide, h, adaptivePalette)
            )
            project = CraftProject(
                UUID.randomUUID().toString(),
                "Моя алмазная картина",
                CraftMode.DIAMOND_PAINTING,
                grid,
                System.currentTimeMillis()
            )
            status = "Схема создана: ${grid.width} × ${grid.height}"
        }.onFailure { status = "Не удалось открыть изображение" }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("💎 DiamondCraft 0.6") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(12.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Фото → схема алмазной мозаики", style = MaterialTheme.typography.titleMedium)
            Text("Ширина схемы: ${width.toInt()} страз")
            Slider(width, { width = it }, valueRange = 20f..160f, steps = 13)
            Text("Детализация цвета: ${colorCount.toInt()} цветов")
            Slider(colorCount, { colorCount = it }, valueRange = 24f..96f, steps = 5)
            Text("Для портретов и животных рекомендуем 80–120 страз по ширине и 48–72 цвета.")
            Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text("Выбрать фотографию")
            }
            Text(status)

            project?.let { p ->
                val stats = DiamondEngine.stats(p.grid)
                val estimate = DiamondMaterialEngine.estimate(
                    p.grid,
                    DiamondMaterialOptions(
                        drillShape = drillShape,
                        reservePercent = reserve.toInt(),
                        drillsPerBag = 200,
                        canvasMarginCm = 3.0
                    )
                )

                Text("Установлено: ${stats.completedDrills} / ${stats.totalDrills} • ${p.grid.progressPercent()}%")
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

                Text(
                    "Размер изображения: ${cm(estimate.pictureWidthCm)} × ${cm(estimate.pictureHeightCm)} см"
                )
                Text(
                    "Клеевая основа с полями: ${cm(estimate.canvasWidthCm)} × ${cm(estimate.canvasHeightCm)} см"
                )
                Text(
                    "Стразы: ${estimate.totalExactDrills} шт. + ${estimate.reservePercent}% = ${estimate.totalRequiredDrills} шт."
                )
                Text("Ориентировочно пакетиков по 200 шт.: ${estimate.totalBags}")

                Text("Палитра и закупка", style = MaterialTheme.typography.titleMedium)
                estimate.colors.forEachIndexed { i, item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Box(Modifier.size(22.dp).background(Color(item.color.argb)))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${i + 1}. ${item.color.name} (${item.color.id}) — " +
                                "${item.exactCount} / купить ${item.requiredCount} шт. (${item.bags} пак.)"
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        // v0.4 intentionally keeps shopping provider-neutral.
                        // DiamondShoppingModel.from(estimate) is ready for store/API adapters.
                        status = "Список покупок готов: ${DiamondShoppingModel.from(estimate).size} позиций"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Подготовить список покупок") }
            }
        }
    }
}

private fun cm(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

@Composable
private fun DiamondGrid(grid: CraftGrid, onCell: (Int, Int) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        Modifier.fillMaxWidth().height(430.dp).background(Color(0xFFF5F5F5))
            .pointerInput(grid, scale, pan) {
                detectTransformGestures { _, panChange, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.8f, 6f)
                    pan += panChange
                }
            }
            .pointerInput(grid, scale, pan) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull() ?: continue
                        if (!ch.pressed && ch.previousPressed) {
                            val base = min(size.width / grid.width.toFloat(), size.height / grid.height.toFloat())
                            val cell = base * scale
                            val x = ((ch.position.x - pan.x) / cell).toInt()
                            val y = ((ch.position.y - pan.y) / cell).toInt()
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
            drawCircle(Color(grid.palette[c.colorIndex].argb), cell * 0.38f, Offset(left + cell / 2, top + cell / 2))
            drawCircle(
                if (c.completed) Color.Black else Color.Gray,
                cell * 0.38f,
                Offset(left + cell / 2, top + cell / 2),
                style = Stroke(if (c.completed) cell * 0.16f else 1f)
            )
        }
    }
}
