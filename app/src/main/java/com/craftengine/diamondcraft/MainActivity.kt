package com.craftengine.diamondcraft

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftengine.core.*
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.min
import kotlin.math.max
import kotlin.math.floor
import kotlin.math.ceil

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent { DiamondCraftTheme { DiamondApp() } }
    }
}


private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun DiamondCraftTheme(content: @Composable () -> Unit) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val compact = screenWidthDp <= 400
    val scheme = darkColorScheme(
        primary = Color(0xFFB97AF2),
        onPrimary = Color(0xFF26004A),
        primaryContainer = Color(0xFF55326F),
        onPrimaryContainer = Color(0xFFF2DCFF),
        secondary = Color(0xFFE875BD),
        onSecondary = Color(0xFF4B0037),
        secondaryContainer = Color(0xFF663052),
        onSecondaryContainer = Color(0xFFFFD8EE),
        tertiary = Color(0xFF74CDE8),
        onTertiary = Color(0xFF003544),
        background = Color(0xFF24212B),
        onBackground = Color(0xFFF3EFF5),
        surface = Color(0xFF2D2934),
        onSurface = Color(0xFFF3EFF5),
        surfaceVariant = Color(0xFF3A3442),
        onSurfaceVariant = Color(0xFFD8CFDC),
        outline = Color(0xFFA89EAD)
    )
    val baseTypography = Typography()
    val typography = if (compact) {
        Typography(
            bodyLarge = baseTypography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
            bodyMedium = baseTypography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
            bodySmall = baseTypography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
            titleLarge = baseTypography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
            titleMedium = baseTypography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
            titleSmall = baseTypography.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
            labelLarge = baseTypography.labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp),
            labelMedium = baseTypography.labelMedium.copy(fontSize = 11.sp, lineHeight = 15.sp),
            labelSmall = baseTypography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp)
        )
    } else {
        baseTypography
    }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

private data class SavedProjectInfo(val file: File, val project: CraftProject)

private object CommercialLimits {
    const val FREE_MAX_WIDTH = 100
    const val FREE_MAX_COLORS = 60
    const val PRO_MAX_WIDTH = 200
    const val PRO_MAX_COLORS = 120
}


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
    var colorStyle by remember { mutableStateOf(ColorStyle.BRIGHT) }
    var status by remember { mutableStateOf("Выберите фотографию") }
    var savedRefresh by remember { mutableIntStateOf(0) }
    var shoppingListText by remember { mutableStateOf<String?>(null) }
    var sourceImage by remember { mutableStateOf<CraftImage?>(null) }
    var showNewProjectConfirm by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<SavedProjectInfo?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var showProDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var saveAsName by remember { mutableStateOf("") }
    var renameCandidate by remember { mutableStateOf<SavedProjectInfo?>(null) }
    var renameText by remember { mutableStateOf("") }
    val undoStack = remember { mutableStateListOf<CraftGrid>() }
    val redoStack = remember { mutableStateListOf<CraftGrid>() }
    // Debug APK stays fully unlocked for our device testing. Release builds use Google Play entitlement.
    val billing = remember { if (BuildConfig.DEBUG) null else PlayBillingController(context.applicationContext) }
    val isPro = BuildConfig.DEBUG || (billing?.isPro == true)

    val savedProjects = remember(savedRefresh) { listSavedProjects(context) }
    val maxWidth = if (isPro) CommercialLimits.PRO_MAX_WIDTH else CommercialLimits.FREE_MAX_WIDTH
    val maxColors = if (isPro) CommercialLimits.PRO_MAX_COLORS else CommercialLimits.FREE_MAX_COLORS

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

    val projectExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val p = project ?: return@rememberLauncherForActivityResult
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(ProjectCodec.encode(p.copy(updatedAt = System.currentTimeMillis())))
            } ?: error("Output stream unavailable")
        }.onSuccess { status = "Файл проекта экспортирован" }
            .onFailure { status = "Не удалось экспортировать проект" }
    }

    val projectImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Input stream unavailable")
            ProjectCodec.decode(text).also { imported ->
                require(imported.mode == CraftMode.DIAMOND_PAINTING) { "Это не проект DiamondCraft" }
            }
        }.onSuccess { imported ->
            val restored = imported.copy(updatedAt = System.currentTimeMillis())
            project = restored
            undoStack.clear(); redoStack.clear()
            sourceImage = null
            showOriginal = false
            width = restored.grid.width.toFloat().coerceIn(30f, 200f)
            colorCount = restored.grid.palette.size.toFloat().coerceIn(24f, 120f)
            saveProject(context, restored)
            savedRefresh++
            status = "Проект импортирован: ${restored.name}"
        }.onFailure { status = "Не удалось импортировать файл проекта" }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri).use { input -> BitmapFactory.decodeStream(input) }!!
        }.onSuccess { bmp ->
            val targetW = width.toInt().coerceIn(30, maxWidth)
            val targetH = (targetW * bmp.height.toFloat() / bmp.width).toInt().coerceIn(30, 280)
            val pixels = IntArray(bmp.width * bmp.height)
            bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val source = CraftImage(bmp.width, bmp.height, pixels)
            sourceImage = source
            showOriginal = false

            status = "Анализируем фотографию…"
            val grid = ImageEngine.toAdaptiveGrid(
                image = source,
                targetWidth = targetW,
                targetHeight = targetH,
                requestedColors = colorCount.toInt().coerceAtMost(maxColors),
                profile = imageProfile,
                colorStyle = colorStyle
            )
            undoStack.clear(); redoStack.clear()
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

    if (showNewProjectConfirm) {
        AlertDialog(
            onDismissRequest = { showNewProjectConfirm = false },
            title = { Text("Новый проект", maxLines = 1) },
            text = { Text("Очистить текущую схему и выбрать новую фотографию? Несохранённые отметки текущего проекта будут потеряны.") },
            confirmButton = {
                TextButton(onClick = {
                    project = null
                    undoStack.clear(); redoStack.clear()
                    sourceImage = null
                    showOriginal = false
                    shoppingListText = null
                    status = "Выберите фотографию"
                    showNewProjectConfirm = false
                }) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectConfirm = false }) { Text("Отмена") }
            }
        )
    }

    deleteCandidate?.let { saved ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Удалить сохранённый проект?") },
            text = { Text("${saved.project.name} • ${saved.project.grid.width}×${saved.project.grid.height}", maxLines = 2) },
            confirmButton = {
                TextButton(onClick = {
                    if (saved.file.delete()) {
                        savedRefresh++
                        status = "Сохранённый проект удалён"
                    } else {
                        status = "Не удалось удалить проект"
                    }
                    deleteCandidate = null
                }) { Text("Удалить", maxLines = 1) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Отмена") }
            }
        )
    }

    if (showProDialog) {
        AlertDialog(
            onDismissRequest = { showProDialog = false },
            title = { Text("DiamondCraft Pro") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Бесплатная версия:")
                    Text("• схемы до ${CommercialLimits.FREE_MAX_WIDTH} страз по ширине")
                    Text("• до ${CommercialLimits.FREE_MAX_COLORS} цветов")
                    Text("• сохранение проектов и отслеживание прогресса")
                    HorizontalDivider()
                    Text("DiamondCraft Pro:")
                    Text("• схемы до ${CommercialLimits.PRO_MAX_WIDTH} страз")
                    Text("• до ${CommercialLimits.PRO_MAX_COLORS} цветов")
                    Text("• PNG, PDF и CSV экспорт")
                    Text("• импорт/экспорт .diamondcraft")
                    Text("• расширенные профили обработки")
                    Text("• будущий подбор расходников по каталогам")
                    HorizontalDivider()
                    Text(
                        if (BuildConfig.DEBUG)
                            "Тестовая APK-сборка: Pro открыт для проверки всех функций."
                        else
                            (billing?.status ?: "Google Play Billing недоступен"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                if (isPro) {
                    TextButton(onClick = { showProDialog = false }) { Text("Понятно") }
                } else {
                    TextButton(onClick = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            billing?.launchPurchase(activity)
                        } else {
                            status = "Не удалось открыть окно Google Play: Activity не найдена"
                        }
                    }) { Text("Получить Pro") }
                }
            },
            dismissButton = {
                if (!BuildConfig.DEBUG && !isPro) {
                    TextButton(onClick = { billing?.refresh() }) { Text("Восстановить покупку") }
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("💎  DiamondCraft") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DiamondCraft ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Превращайте любимые фотографии в красивые схемы алмазной мозаики.")
                    HorizontalDivider()
                    Text("Возможности:")
                    Text("• фото → схема")
                    Text("• интеллектуальная обработка и цветопередача")
                    Text("• квадратные и круглые стразы")
                    Text("• масштабирование и отметка прогресса")
                    Text("• сохранение и перенос проектов")
                    Text("• PNG, PDF и CSV")
                    Text("• расчёт страз, запаса и основы")
                    Text("• список покупок")
                    Text("• Undo / Redo и удобное управление проектами")
                    HorizontalDivider()
                    Text(
                        "Фотографии и проекты обрабатываются локально на устройстве. " +
                            "Платные функции будут подключены через Google Play Billing.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Закрыть") }
            }
        )
    }

    if (showSaveAsDialog) {
        AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            title = { Text("Сохранить проект как") },
            text = {
                OutlinedTextField(
                    value = saveAsName,
                    onValueChange = { saveAsName = it },
                    singleLine = true,
                    label = { Text("Название проекта") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = project ?: return@TextButton
                    val name = saveAsName.trim().ifBlank { "Проект ${savedProjects.size + 1}" }
                    val named = p.copy(name = name, updatedAt = System.currentTimeMillis())
                    project = named
                    saveProject(context, named)
                    savedRefresh++
                    status = "Проект сохранён: $name"
                    showSaveAsDialog = false
                }) { Text("Сохранить", maxLines = 1) }
            },
            dismissButton = { TextButton(onClick = { showSaveAsDialog = false }) { Text("Отмена") } }
        )
    }

    renameCandidate?.let { saved ->
        AlertDialog(
            onDismissRequest = { renameCandidate = null },
            title = { Text("Переименовать проект") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Название проекта") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = renameText.trim()
                    if (name.isNotEmpty()) {
                        val renamed = saved.project.copy(name = name, updatedAt = System.currentTimeMillis())
                        saveProject(context, renamed)
                        if (project?.id == renamed.id) project = renamed
                        savedRefresh++
                        status = "Проект переименован: $name"
                    }
                    renameCandidate = null
                }) { Text("Переименовать") }
            },
            dismissButton = { TextButton(onClick = { renameCandidate = null }) { Text("Отмена") } }
        )
    }

    val compactUi = LocalConfiguration.current.screenWidthDp <= 400
    val screenPadding = if (compactUi) 8.dp else 12.dp
    val contentSpacing = if (compactUi) 8.dp else 10.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DiamondCraft",
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                },
                actions = {
                    TextButton(onClick = { showProDialog = true }) {
                        Text(if (isPro) "PRO ✓" else "PRO", maxLines = 1)
                    }
                    TextButton(onClick = { showAboutDialog = true }) {
                        Text("О приложении", maxLines = 1)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(screenPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(contentSpacing)
        ) {
            Text("💎  Фото → схема алмазной мозаики", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                if (isPro) "DiamondCraft Pro • RC14" else "Бесплатный режим • до ${CommercialLimits.FREE_MAX_WIDTH} страз / ${CommercialLimits.FREE_MAX_COLORS} цветов",
                style = MaterialTheme.typography.bodySmall
            )

            Text("Ширина схемы: ${width.toInt()} страз")
            Slider(width, { width = it }, valueRange = 30f..maxWidth.toFloat(), steps = 16)

            Text("Детализация цвета: ${colorCount.toInt()} цветов")
            Slider(colorCount, { colorCount = it }, valueRange = 24f..maxColors.toFloat(), steps = 7)

            Text("Для портретов: 100–140 страз и 60–84 цвета. Для пейзажей: 120–180 и 72–108 цветов.")

            Text("Профиль обработки")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ImageProfile.entries.forEachIndexed { index, profile ->
                    SegmentedButton(
                        selected = imageProfile == profile,
                        onClick = { imageProfile = profile },
                        shape = SegmentedButtonDefaults.itemShape(index, ImageProfile.entries.size)
                    ) { Text(profile.displayName, maxLines = 1) }
                }
            }

            Text("Цветопередача")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ColorStyle.entries.forEachIndexed { index, style ->
                    SegmentedButton(
                        selected = colorStyle == style,
                        onClick = { colorStyle = style },
                        shape = SegmentedButtonDefaults.itemShape(index, ColorStyle.entries.size)
                    ) { Text(style.displayName, maxLines = 1) }
                }
            }
            Text(
                "Яркий — рекомендуемый режим для алмазной мозаики. Насыщенный сильнее подчёркивает цветные стразы.",
                style = MaterialTheme.typography.bodySmall
            )

            Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text("Выбрать фотографию")
            }
            OutlinedButton(
                onClick = {
                    if (isPro) projectImportLauncher.launch(arrayOf("*/*"))
                    else showProDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Импорт проекта (.diamondcraft)") }
            if (sourceImage != null) {
                OutlinedButton(
                    onClick = {
                        val source = sourceImage ?: return@OutlinedButton
                        val targetW = width.toInt().coerceIn(30, maxWidth)
                        val targetH = (targetW * source.height.toFloat() / source.width).toInt().coerceIn(30, 280)
                        status = "Пересчитываем схему…"
                        runCatching {
                            ImageEngine.toAdaptiveGrid(
                                image = source,
                                targetWidth = targetW,
                                targetHeight = targetH,
                                requestedColors = colorCount.toInt().coerceAtMost(maxColors),
                                profile = imageProfile,
                                colorStyle = colorStyle
                            )
                        }.onSuccess { grid ->
                            project = CraftProject(
                                id = project?.id ?: UUID.randomUUID().toString(),
                                name = project?.name ?: "Моя алмазная картина",
                                mode = CraftMode.DIAMOND_PAINTING,
                                grid = grid,
                                updatedAt = System.currentTimeMillis()
                            )
                            status = "Схема пересчитана: ${grid.width} × ${grid.height} • ${grid.palette.size} цветов • ${imageProfile.displayName} • ${colorStyle.displayName}"
                        }.onFailure { status = "Не удалось пересчитать схему" }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Пересчитать с текущими настройками") }
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
                                undoStack.clear(); redoStack.clear()
                                status = "Проект восстановлен: ${saved.project.name}"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("${saved.project.name} • ${saved.project.grid.width}×${saved.project.grid.height}", maxLines = if (compactUi) 2 else 1)
                        }
                        TextButton(onClick = { renameCandidate = saved; renameText = saved.project.name }) { Text("Имя", maxLines = 1) }
                        TextButton(onClick = { deleteCandidate = saved }) { Text("Удалить", maxLines = 1) }
                    }
                }
            }

            project?.let { p ->
                val stats = DiamondEngine.stats(p.grid)
                val estimate = materialEstimate(p, drillShape, reserve.toInt())

                HorizontalDivider()
                Text("Проект", style = MaterialTheme.typography.titleMedium)
                Text("${p.grid.width} × ${p.grid.height} • ${p.grid.palette.size} цветов")
                Text("Установлено: ${stats.completedDrills} / ${stats.totalDrills} • ${percent(p.grid.progressPercentExact())}%")

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (p.name == "Моя алмазная картина") {
                                saveAsName = ""
                                showSaveAsDialog = true
                            } else {
                                val saved = p.copy(updatedAt = System.currentTimeMillis())
                                project = saved
                                saveProject(context, saved)
                                savedRefresh++
                                status = "Проект сохранён: ${saved.name}"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Сохранить", maxLines = 1) }
                    OutlinedButton(
                        onClick = {
                            undoStack.add(p.grid)
                            if (undoStack.size > 50) undoStack.removeAt(0)
                            redoStack.clear()
                            project = p.copy(
                                grid = ProgressEngine.clear(p.grid),
                                updatedAt = System.currentTimeMillis()
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Снять все отметки", maxLines = 1) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                redoStack.add(p.grid)
                                val previous = undoStack.removeAt(undoStack.lastIndex)
                                project = p.copy(grid = previous, updatedAt = System.currentTimeMillis())
                            }
                        },
                        enabled = undoStack.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("↶ Назад", maxLines = 1) }
                    OutlinedButton(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                undoStack.add(p.grid)
                                val next = redoStack.removeAt(redoStack.lastIndex)
                                project = p.copy(grid = next, updatedAt = System.currentTimeMillis())
                            }
                        },
                        enabled = redoStack.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("↷ Вперёд", maxLines = 1) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showNewProjectConfirm = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Новый проект", maxLines = 1) }
                    OutlinedButton(
                        onClick = {
                            if (isPro) projectExportLauncher.launch("DiamondCraft_${p.grid.width}x${p.grid.height}.diamondcraft")
                            else showProDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Экспорт проекта", maxLines = 1) }
                }

                if (sourceImage != null) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !showOriginal,
                            onClick = { showOriginal = false },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) { Text("Схема", maxLines = 1) }
                        SegmentedButton(
                            selected = showOriginal,
                            onClick = { showOriginal = true },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) { Text("Оригинал", maxLines = 1) }
                    }
                }

                if (showOriginal && sourceImage != null) {
                    OriginalImagePreview(sourceImage!!)
                } else {
                    DiamondGrid(p.grid) { x, y ->
                        undoStack.add(p.grid)
                        if (undoStack.size > 50) undoStack.removeAt(0)
                        redoStack.clear()
                        project = p.copy(
                            grid = ProgressEngine.toggle(p.grid, x, y),
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }

                HorizontalDivider()
                Text("Расходники", style = MaterialTheme.typography.titleMedium)

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    DrillShape.entries.forEachIndexed { index, shape ->
                        SegmentedButton(
                            selected = drillShape == shape,
                            onClick = { drillShape = shape },
                            shape = SegmentedButtonDefaults.itemShape(index, DrillShape.entries.size)
                        ) { Text(shape.displayName, maxLines = 1) }
                    }
                }

                Text("Запас страз: ${reserve.toInt()}%")
                Slider(reserve, { reserve = it }, valueRange = 5f..20f, steps = 2)

                Text("Размер картины: ${cm(estimate.pictureWidthCm)} × ${cm(estimate.pictureHeightCm)} см")
                Text("Клеевая основа с полями: ${cm(estimate.canvasWidthCm)} × ${cm(estimate.canvasHeightCm)} см")
                Text("Стразы: ${estimate.totalExactDrills} шт. + ${estimate.reservePercent}% = ${estimate.totalRequiredDrills} шт.")
                Text("Количество страз задаётся сеткой схемы; тип страз влияет на физический размер картины.", style = MaterialTheme.typography.bodySmall)
                Text("Пакетиков по 200 шт.: примерно ${estimate.totalBags}")

                Text("Палитра и закупка", style = MaterialTheme.typography.titleMedium)
                Text("Цвета HEX рассчитаны по фотографии. Реальные артикулы магазина будут подбираться только из подключённого каталога.", style = MaterialTheme.typography.bodySmall)
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
                        onClick = {
                            if (isPro) pngLauncher.launch("DiamondCraft_${p.grid.width}x${p.grid.height}_pattern.png")
                            else showProDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (isPro) "Схема PNG" else "PNG • PRO", maxLines = 1) }
                    OutlinedButton(
                        onClick = {
                            if (isPro) pdfLauncher.launch("DiamondCraft_${p.grid.width}x${p.grid.height}_materials.pdf")
                            else showProDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (isPro) "Расходники PDF" else "PDF • PRO", maxLines = 1) }
                }
                OutlinedButton(
                    onClick = {
                        if (isPro) csvLauncher.launch("DiamondCraft_${p.grid.width}x${p.grid.height}_materials.csv")
                        else showProDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isPro) "Расходники CSV" else "CSV • PRO", maxLines = 1) }

                Button(
                    onClick = { shoppingListText = buildShoppingList(p, estimate) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Подготовить список покупок", maxLines = 1) }

                Text(
                    "DiamondCraft ${BuildConfig.VERSION_NAME} • Цвета изображения обозначаются HEX. Артикулы поставщиков будут показываться только после подключения проверенного каталога.",
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

private fun percent(value: Double): String = String.format(Locale.US, "%.1f", value)
private fun cm(value: Double): String = String.format(Locale.US, "%.1f", value)

@Composable
private fun OriginalImagePreview(image: CraftImage) {
    val bitmap = remember(image) {
        android.graphics.Bitmap.createBitmap(
            image.pixels,
            image.width,
            image.height,
            android.graphics.Bitmap.Config.ARGB_8888
        )
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Оригинальная фотография",
        modifier = Modifier
            .fillMaxWidth()
            .height(460.dp)
            .background(Color(0xFFF5F5F5)),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun DiamondGrid(grid: CraftGrid, onCell: (Int, Int) -> Unit) {
    var scale by remember(grid.width, grid.height) { mutableFloatStateOf(1f) }
    var pan by remember(grid.width, grid.height) { mutableStateOf(Offset.Zero) }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(460.dp)
            .background(Color(0xFFF5F5F5))
            .clipToBounds()
            // One finger is intentionally left to the parent vertical scroll.
            // Two fingers exclusively control zoom/pan of the pattern.
            .pointerInput(grid) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            val zoom = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(1f, 8f)
                            val base = min(
                                size.width.toFloat() / grid.width.toFloat(),
                                size.height.toFloat() / grid.height.toFloat()
                            )
                            val contentWidth = base * newScale * grid.width
                            val contentHeight = base * newScale * grid.height
                            val maxPanX = max(0f, (contentWidth - size.width) / 2f)
                            val maxPanY = max(0f, (contentHeight - size.height) / 2f)
                            pan = Offset(
                                (pan.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                (pan.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                            )
                            scale = newScale
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .pointerInput(grid, scale, pan) {
                detectTapGestures { position ->
                    val base = min(size.width / grid.width.toFloat(), size.height / grid.height.toFloat())
                    val cell = base * scale
                    val contentWidth = cell * grid.width
                    val contentHeight = cell * grid.height
                    val originX = (size.width - contentWidth) / 2f + pan.x
                    val originY = (size.height - contentHeight) / 2f + pan.y
                    val x = ((position.x - originX) / cell).toInt()
                    val y = ((position.y - originY) / cell).toInt()
                    if (x in 0 until grid.width && y in 0 until grid.height) onCell(x, y)
                }
            }
    ) {
        val base = min(size.width / grid.width, size.height / grid.height)
        val cell = base * scale
        val contentWidth = cell * grid.width
        val contentHeight = cell * grid.height
        val originX = (size.width - contentWidth) / 2f + pan.x
        val originY = (size.height - contentHeight) / 2f + pan.y

        // Draw only cells that are actually visible. This removes most of the work
        // while zoomed and makes page scrolling / zooming substantially smoother.
        val firstX = max(0, floor((-originX / cell).toDouble()).toInt())
        val lastX = min(grid.width - 1, ceil(((size.width - originX) / cell).toDouble()).toInt())
        val firstY = max(0, floor((-originY / cell).toDouble()).toInt())
        val lastY = min(grid.height - 1, ceil(((size.height - originY) / cell).toDouble()).toInt())

        if (firstX <= lastX && firstY <= lastY) {
            for (y in firstY..lastY) {
                for (x in firstX..lastX) {
                    val c = grid.cells[y * grid.width + x]
                    val left = originX + x * cell
                    val top = originY + y * cell
                    val center = Offset(left + cell / 2, top + cell / 2)
                    drawCircle(Color(grid.palette[c.colorIndex].argb), cell * 0.46f, center)
                    drawCircle(
                        if (c.completed) Color.Black else Color.Gray,
                        cell * 0.46f,
                        center,
                        style = Stroke(if (c.completed) cell * 0.16f else 1f)
                    )
                }
            }
        }
    }
}

