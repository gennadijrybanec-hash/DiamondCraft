package com.craftengine.diamondcraft

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

private enum class AppLang { SYSTEM, RU, UK, EN }

private fun selectedLang(context: Context): AppLang {
    val saved = context.getSharedPreferences("diamondcraft_prefs", Context.MODE_PRIVATE)
        .getString("app_language", "SYSTEM") ?: "SYSTEM"
    val explicit = runCatching { AppLang.valueOf(saved) }.getOrDefault(AppLang.SYSTEM)
    if (explicit != AppLang.SYSTEM) return explicit
    return when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
        "ru" -> AppLang.RU
        "uk", "ua" -> AppLang.UK
        else -> AppLang.EN
    }
}

private fun tr(context: Context, ru: String, uk: String, en: String): String = when (selectedLang(context)) {
    AppLang.RU -> ru
    AppLang.UK -> uk
    AppLang.EN -> en
    AppLang.SYSTEM -> en
}

private fun saveLang(context: Context, lang: AppLang) {
    context.getSharedPreferences("diamondcraft_prefs", Context.MODE_PRIVATE)
        .edit().putString("app_language", lang.name).apply()
}

private fun languageLabel(context: Context): String {
    val stored = context.getSharedPreferences("diamondcraft_prefs", Context.MODE_PRIVATE)
        .getString("app_language", "SYSTEM") ?: "SYSTEM"
    return when (runCatching { AppLang.valueOf(stored) }.getOrDefault(AppLang.SYSTEM)) {
        AppLang.SYSTEM -> tr(context, "Системный", "Системна", "System")
        AppLang.RU -> "Русский"
        AppLang.UK -> "Українська"
        AppLang.EN -> "English"
    }
}

private fun profileName(context: Context, p: ImageProfile): String = when (p) {
    ImageProfile.AUTO -> tr(context, "Авто", "Авто", "Auto")
    ImageProfile.PORTRAIT -> tr(context, "Портрет", "Портрет", "Portrait")
    ImageProfile.OBJECT -> tr(context, "Предмет", "Предмет", "Object")
    ImageProfile.LANDSCAPE -> tr(context, "Пейзаж", "Пейзаж", "Landscape")
}

private fun colorStyleName(context: Context, p: ColorStyle): String = when (p) {
    ColorStyle.NATURAL -> tr(context, "Естественный", "Природний", "Natural")
    ColorStyle.BRIGHT -> tr(context, "Яркий", "Яскравий", "Bright")
    ColorStyle.VIVID -> tr(context, "Насыщенный", "Насичений", "Vivid")
}

private fun drillShapeName(context: Context, p: DrillShape): String = when (p) {
    DrillShape.SQUARE -> tr(context, "Квадратные", "Квадратні", "Square")
    DrillShape.ROUND -> tr(context, "Круглые", "Круглі", "Round")
}

private fun openShopSearch(context: Context, query: String) {
    val url = "https://www.google.com/search?q=" + Uri.encode(query)
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

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
    var colorCount by remember { mutableFloatStateOf(60f) }
    var reserve by remember { mutableFloatStateOf(10f) }
    var drillShape by remember { mutableStateOf(DrillShape.SQUARE) }
    var imageProfile by remember { mutableStateOf(ImageProfile.AUTO) }
    var colorStyle by remember { mutableStateOf(ColorStyle.BRIGHT) }
    var status by remember { mutableStateOf(tr(context, "Выберите фотографию", "Оберіть фотографію", "Choose a photo")) }
    var savedRefresh by remember { mutableIntStateOf(0) }
    var shoppingListText by remember { mutableStateOf<String?>(null) }
    var sourceImage by remember { mutableStateOf<CraftImage?>(null) }
    var showNewProjectConfirm by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<SavedProjectInfo?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var showProDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var saveAsName by remember { mutableStateOf("") }
    var renameCandidate by remember { mutableStateOf<SavedProjectInfo?>(null) }
    var renameText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var busyTitle by remember { mutableStateOf(tr(context, "Создаём схему…", "Створюємо схему…", "Creating pattern…")) }
    var showSetup by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val undoStack = remember { mutableStateListOf<CraftGrid>() }
    val redoStack = remember { mutableStateListOf<CraftGrid>() }
    // Do not unlock Pro in debug builds: test the same entitlement rules as release.
    val billing = remember { PlayBillingController(context.applicationContext) }
    val isPro = billing.isPro

    val savedProjects = remember(savedRefresh) { listSavedProjects(context) }
    val maxWidth = if (isPro) CommercialLimits.PRO_MAX_WIDTH else CommercialLimits.FREE_MAX_WIDTH
    val maxColors = if (isPro) CommercialLimits.PRO_MAX_COLORS else CommercialLimits.FREE_MAX_COLORS

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val p = project ?: return@rememberLauncherForActivityResult
        if (uri != null) runCatching {
            check(isPro) { "Pro required" }
            val estimate = materialEstimate(p, drillShape, reserve.toInt())
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(materialsCsv(p, estimate))
            } ?: error("Output stream unavailable")
        }.onSuccess { status = tr(context, "CSV сохранён", "CSV збережено", "CSV saved") }
            .onFailure { status = tr(context, "Не удалось сохранить CSV", "Не вдалося зберегти CSV", "Could not save CSV") }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val p = project ?: return@rememberLauncherForActivityResult
        if (uri != null) runCatching {
            check(isPro) { "Pro required" }
            val estimate = materialEstimate(p, drillShape, reserve.toInt())
            context.contentResolver.openOutputStream(uri)?.use { output ->
                writePatternPdf(output, p, estimate)
            } ?: error("Output stream unavailable")
        }.onSuccess { status = tr(context, "PDF схемы сохранён", "PDF схеми збережено", "Pattern PDF saved") }
            .onFailure { status = tr(context, "Не удалось сохранить PDF схемы", "Не вдалося зберегти PDF схеми", "Could not save pattern PDF") }
    }

    val materialsPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val p = project ?: return@rememberLauncherForActivityResult
        if (uri != null) runCatching {
            check(isPro) { "Pro required" }
            val estimate = materialEstimate(p, drillShape, reserve.toInt())
            context.contentResolver.openOutputStream(uri)?.use { output ->
                writeMaterialsPdf(output, p, estimate)
            } ?: error("Output stream unavailable")
        }.onSuccess { status = tr(context, "Расходники PDF сохранены", "Матеріали PDF збережено", "Materials PDF saved") }
            .onFailure { status = tr(context, "Не удалось сохранить PDF материалов", "Не вдалося зберегти PDF матеріалів", "Could not save materials PDF") }
    }

    val pngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val p = project ?: return@rememberLauncherForActivityResult
        if (uri != null) runCatching {
            check(isPro) { "Pro required" }
            context.contentResolver.openOutputStream(uri)?.use { output ->
                writePatternPng(output, p)
            } ?: error("Output stream unavailable")
        }.onSuccess { status = tr(context, "PNG сохранён", "PNG збережено", "PNG saved") }
            .onFailure { status = tr(context, "Не удалось сохранить PNG", "Не вдалося зберегти PNG", "Could not save PNG") }
    }

    val projectExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val p = project ?: return@rememberLauncherForActivityResult
        if (uri != null) runCatching {
            check(isPro) { "Pro required" }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(ProjectCodec.encode(p.copy(updatedAt = System.currentTimeMillis())))
            } ?: error("Output stream unavailable")
        }.onSuccess { status = tr(context, "Файл проекта экспортирован", "Файл проєкту експортовано", "Project file exported") }
            .onFailure { status = tr(context, "Не удалось экспортировать проект", "Не вдалося експортувати проєкт", "Could not export project") }
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
            status = tr(context, "Проект импортирован: ${restored.name}", "Проєкт імпортовано: ${restored.name}", "Project imported: ${restored.name}")
            showSetup = false
        }.onFailure { status = tr(context, "Не удалось импортировать файл проекта", "Не вдалося імпортувати файл проєкту", "Could not import project file") }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busyTitle = tr(context, "Загружаем фотографию…", "Завантажуємо фото…", "Loading photo…")
            busy = true
            status = tr(context, "Загружаем фотографию…", "Завантажуємо фото…", "Loading photo…")
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { loadCraftImage(context, uri) }
                }.onSuccess { source ->
                    sourceImage = source
                    showOriginal = false
                    showSetup = true
                    status = tr(context, "Фотография выбрана. Настройте схему и нажмите «Создать схему».", "Фото обрано. Налаштуйте схему та натисніть «Створити схему».", "Photo selected. Adjust the pattern and tap Create pattern.")
                }.onFailure {
                    status = tr(context, "Не удалось открыть изображение", "Не вдалося відкрити зображення", "Could not open image")
                }
                busy = false
            }
        }
    }

    fun generateFromSource() {
        val source = sourceImage ?: return
        if (busy) return
        busyTitle = tr(context, "Создаём схему…", "Створюємо схему…", "Creating pattern…")
        busy = true
        status = tr(context, "Создаём схему…", "Створюємо схему…", "Creating pattern…")
        scope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    val targetW = width.toInt().coerceIn(30, maxWidth)
                    val targetH = (targetW * source.height.toFloat() / source.width).toInt().coerceIn(30, 280)
                    ImageEngine.toAdaptiveGrid(
                        image = source,
                        targetWidth = targetW,
                        targetHeight = targetH,
                        requestedColors = colorCount.toInt().coerceAtMost(maxColors),
                        profile = imageProfile,
                        colorStyle = colorStyle
                    )
                }
            }.onSuccess { grid ->
                undoStack.clear(); redoStack.clear()
                project = CraftProject(
                    id = project?.id ?: UUID.randomUUID().toString(),
                    name = project?.name ?: tr(context, "Моя алмазная картина", "Моя алмазна картина", "My diamond painting"),
                    mode = CraftMode.DIAMOND_PAINTING,
                    grid = grid,
                    updatedAt = System.currentTimeMillis()
                )
                status = tr(context, "Схема создана: ${grid.width} × ${grid.height} • ${grid.palette.size} цветов", "Схему створено: ${grid.width} × ${grid.height} • ${grid.palette.size} кольорів", "Pattern created: ${grid.width} × ${grid.height} • ${grid.palette.size} colors")
                showSetup = false
            }.onFailure {
                status = tr(context, "Не удалось создать схему", "Не вдалося створити схему", "Could not create pattern")
            }
            busy = false
        }
    }


    shoppingListText?.let { text ->
        AlertDialog(
            onDismissRequest = { shoppingListText = null },
            title = { Text(tr(context, "Список покупок", "Список покупок", "Shopping list")) },
            text = {
                Box(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text(text)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("DiamondCraft — список покупок", text))
                    status = tr(context, "Список покупок скопирован", "Список покупок скопійовано", "Shopping list copied")
                    shoppingListText = null
                }) { Text(tr(context, "Копировать", "Копіювати", "Copy")) }
            },
            dismissButton = {
                TextButton(onClick = { shoppingListText = null }) { Text(tr(context, "Закрыть", "Закрити", "Close")) }
            }
        )
    }

    if (showNewProjectConfirm) {
        AlertDialog(
            onDismissRequest = { showNewProjectConfirm = false },
            title = { Text(tr(context, "Новый проект", "Новий проєкт", "New project"), maxLines = 1) },
            text = { Text(tr(context, "Очистить текущую схему и выбрать новую фотографию? Несохранённые отметки будут потеряны.", "Очистити поточну схему та вибрати нове фото? Незбережені позначки буде втрачено.", "Clear the current pattern and choose a new photo? Unsaved marks will be lost.")) },
            confirmButton = {
                TextButton(onClick = {
                    project = null
                    undoStack.clear(); redoStack.clear()
                    sourceImage = null
                    showOriginal = false
                    shoppingListText = null
                    status = tr(context, "Выберите фотографию", "Оберіть фотографію", "Choose a photo")
                    showSetup = true
                    showNewProjectConfirm = false
                }) { Text(tr(context, "Очистить", "Очистити", "Clear")) }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectConfirm = false }) { Text(tr(context, "Отмена", "Скасувати", "Cancel")) }
            }
        )
    }

    deleteCandidate?.let { saved ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(tr(context, "Удалить сохранённый проект?", "Видалити збережений проєкт?", "Delete saved project?")) },
            text = { Text("${saved.project.name} • ${saved.project.grid.width}×${saved.project.grid.height}", maxLines = 2) },
            confirmButton = {
                TextButton(onClick = {
                    if (saved.file.delete()) {
                        savedRefresh++
                        status = tr(context, "Сохранённый проект удалён", "Збережений проєкт видалено", "Saved project deleted")
                    } else {
                        status = tr(context, "Не удалось удалить проект", "Не вдалося видалити проєкт", "Could not delete project")
                    }
                    deleteCandidate = null
                }) { Text(tr(context, "Удалить", "Видалити", "Delete"), maxLines = 1) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text(tr(context, "Отмена", "Скасувати", "Cancel")) }
            }
        )
    }

    if (showProDialog) {
        AlertDialog(
            onDismissRequest = { showProDialog = false },
            title = { Text("DiamondCraft Pro") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr(context, "Бесплатная версия", "Безкоштовна версія", "Free version"), fontWeight = FontWeight.Bold)
                    Text(tr(context, "• схемы до ${CommercialLimits.FREE_MAX_WIDTH} страз по ширине", "• схеми до ${CommercialLimits.FREE_MAX_WIDTH} стразів завширшки", "• patterns up to ${CommercialLimits.FREE_MAX_WIDTH} drills wide"))
                    Text(tr(context, "• до ${CommercialLimits.FREE_MAX_COLORS} цветов", "• до ${CommercialLimits.FREE_MAX_COLORS} кольорів", "• up to ${CommercialLimits.FREE_MAX_COLORS} colors"))
                    Text(tr(context, "• сохранение проектов и прогресса", "• збереження проєктів і прогресу", "• project saving and progress tracking"))
                    HorizontalDivider()
                    Text("DiamondCraft Pro", fontWeight = FontWeight.Bold)
                    Text(tr(context, "• схемы до ${CommercialLimits.PRO_MAX_WIDTH} страз", "• схеми до ${CommercialLimits.PRO_MAX_WIDTH} стразів", "• patterns up to ${CommercialLimits.PRO_MAX_WIDTH} drills"))
                    Text(tr(context, "• до ${CommercialLimits.PRO_MAX_COLORS} цветов", "• до ${CommercialLimits.PRO_MAX_COLORS} кольорів", "• up to ${CommercialLimits.PRO_MAX_COLORS} colors"))
                    Text(tr(context, "• экспорт PNG, PDF и CSV", "• експорт PNG, PDF і CSV", "• PNG, PDF and CSV export"))
                    Text(tr(context, "• импорт и экспорт проектов .diamondcraft", "• імпорт і експорт проєктів .diamondcraft", "• .diamondcraft project import and export"))
                    Text(tr(context, "• расширенные профили обработки", "• розширені профілі обробки", "• advanced processing profiles"))
                    Text(tr(context, "• расчёт материалов и поиск в магазинах", "• розрахунок матеріалів і пошук у магазинах", "• material calculation and store search"))
                    if (!isPro) {
                        HorizontalDivider()
                        Text(billing.status ?: tr(context, "Google Play Billing недоступен", "Google Play Billing недоступний", "Google Play Billing unavailable"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                if (isPro) {
                    TextButton(onClick = { showProDialog = false }) { Text(tr(context, "Понятно", "Зрозуміло", "OK")) }
                } else {
                    TextButton(onClick = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            billing.launchPurchase(activity)
                        } else {
                            status = "Не удалось открыть окно Google Play: Activity не найдена"
                        }
                    }) { Text(tr(context, "Получить Pro", "Отримати Pro", "Get Pro")) }
                }
            },
            dismissButton = {
                if (!isPro) {
                    TextButton(onClick = { billing.refresh() }) { Text(tr(context, "Восстановить покупку", "Відновити покупку", "Restore purchase")) }
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
                    Text(tr(context, "Версия ${BuildConfig.VERSION_NAME}", "Версія ${BuildConfig.VERSION_NAME}", "Version ${BuildConfig.VERSION_NAME}"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(tr(context, "Создавайте схемы алмазной мозаики из фотографий и ведите проект от изображения до списка материалов.", "Створюйте схеми алмазної мозаїки з фотографій і ведіть проєкт від зображення до списку матеріалів.", "Create diamond-painting patterns from photos and manage the project from image to material list."))
                    HorizontalDivider()
                    Text(tr(context, "Возможности", "Можливості", "Features"), fontWeight = FontWeight.Bold)
                    Text(tr(context, "• фото → схема", "• фото → схема", "• photo → pattern"))
                    Text(tr(context, "• квадратные и круглые стразы", "• квадратні та круглі стрази", "• square and round drills"))
                    Text(tr(context, "• масштабирование, перемещение и отметка прогресса", "• масштабування, переміщення та відмітка прогресу", "• zoom, pan and progress marking"))
                    Text(tr(context, "• сохранение, импорт и экспорт проектов", "• збереження, імпорт та експорт проєктів", "• project save, import and export"))
                    Text(tr(context, "• PNG, PDF и CSV", "• PNG, PDF і CSV", "• PNG, PDF and CSV"))
                    Text(tr(context, "• расчёт основы, страз и запаса", "• розрахунок основи, стразів і запасу", "• canvas, drill and reserve calculation"))
                    Text(tr(context, "• список покупок и поиск материалов в магазинах", "• список покупок і пошук матеріалів у магазинах", "• shopping list and material search in stores"))
                    OutlinedButton(onClick = { showLanguageDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr(context, "Язык приложения: ${languageLabel(context)}", "Мова застосунку: ${languageLabel(context)}", "App language: ${languageLabel(context)}"))
                    }
                    HorizontalDivider()
                    Text(
                        tr(context,
                            "Фотографии и проекты обрабатываются локально на устройстве. Покупка Pro выполняется через Google Play.",
                            "Фотографії та проєкти обробляються локально на пристрої. Купівля Pro виконується через Google Play.",
                            "Photos and projects are processed locally on the device. Pro purchase is handled through Google Play."),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text(tr(context, "Закрыть", "Закрити", "Close")) }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(tr(context, "Язык приложения", "Мова застосунку", "App language")) },
            text = {
                Column {
                    listOf(
                        AppLang.SYSTEM to tr(context, "Системный", "Системна", "System"),
                        AppLang.RU to "Русский",
                        AppLang.UK to "Українська",
                        AppLang.EN to "English"
                    ).forEach { (lang, label) ->
                        TextButton(onClick = {
                            saveLang(context, lang)
                            showLanguageDialog = false
                            context.findActivity()?.recreate()
                        }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showLanguageDialog = false }) { Text(tr(context, "Закрыть", "Закрити", "Close")) } }
        )
    }

    if (showSaveAsDialog) {
        AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            title = { Text(tr(context, "Сохранить проект как", "Зберегти проєкт як", "Save project as")) },
            text = {
                OutlinedTextField(
                    value = saveAsName,
                    onValueChange = { saveAsName = it },
                    singleLine = true,
                    label = { Text(tr(context, "Название проекта", "Назва проєкту", "Project name")) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = project ?: return@TextButton
                    val name = saveAsName.trim().ifBlank { tr(context, "Проект ${savedProjects.size + 1}", "Проєкт ${savedProjects.size + 1}", "Project ${savedProjects.size + 1}") }
                    val named = p.copy(name = name, updatedAt = System.currentTimeMillis())
                    project = named
                    saveProject(context, named)
                    savedRefresh++
                    status = tr(context, "Проект сохранён: $name", "Проєкт збережено: $name", "Project saved: $name")
                    showSaveAsDialog = false
                }) { Text(tr(context, "Сохранить", "Зберегти", "Save"), maxLines = 1) }
            },
            dismissButton = { TextButton(onClick = { showSaveAsDialog = false }) { Text(tr(context, "Отмена", "Скасувати", "Cancel")) } }
        )
    }

    renameCandidate?.let { saved ->
        AlertDialog(
            onDismissRequest = { renameCandidate = null },
            title = { Text(tr(context, "Переименовать проект", "Перейменувати проєкт", "Rename project")) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text(tr(context, "Название проекта", "Назва проєкту", "Project name")) }
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
                        status = tr(context, "Проект переименован: $name", "Проєкт перейменовано: $name", "Project renamed: $name")
                    }
                    renameCandidate = null
                }) { Text(tr(context, "Переименовать", "Перейменувати", "Rename")) }
            },
            dismissButton = { TextButton(onClick = { renameCandidate = null }) { Text(tr(context, "Отмена", "Скасувати", "Cancel")) } }
        )
    }

    val compactUi = LocalConfiguration.current.screenWidthDp <= 400
    val screenPadding = if (compactUi) 8.dp else 12.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DiamondCraft", color = MaterialTheme.colorScheme.primary, maxLines = 1) },
                actions = {
                    TextButton(onClick = { showProDialog = true }) { Text(if (isPro) "PRO ✓" else "PRO", maxLines = 1) }
                    TextButton(onClick = { showLanguageDialog = true }) { Text("🌐", maxLines = 1) }
                    TextButton(onClick = { showAboutDialog = true }) { Text("ⓘ", maxLines = 1) }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (showSetup || project == null) {
                DiamondSetupScreen(
                    modifier = Modifier.fillMaxSize(),
                    compactUi = compactUi,
                    screenPadding = screenPadding,
                    isPro = isPro,
                    maxWidth = maxWidth,
                    maxColors = maxColors,
                    width = width,
                    colorCount = colorCount,
                    imageProfile = imageProfile,
                    colorStyle = colorStyle,
                    sourceSelected = sourceImage != null,
                    busy = busy,
                    status = status,
                    savedProjects = savedProjects,
                    onWidth = { width = it },
                    onColorCount = { colorCount = it },
                    onProfile = { imageProfile = it },
                    onColorStyle = { colorStyle = it },
                    onPick = { picker.launch("image/*") },
                    onGenerate = { generateFromSource() },
                    onImport = {
                        if (isPro) projectImportLauncher.launch(arrayOf("*/*")) else showProDialog = true
                    },
                    onOpenSaved = { saved ->
                        project = saved.project
                        undoStack.clear(); redoStack.clear()
                        sourceImage = null
                        showOriginal = false
                        showSetup = false
                        width = saved.project.grid.width.toFloat().coerceIn(30f, maxWidth.toFloat())
                        colorCount = saved.project.grid.palette.size.toFloat().coerceIn(24f, maxColors.toFloat())
                        status = tr(context, "Проект восстановлен: ${saved.project.name}", "Проєкт відновлено: ${saved.project.name}", "Project restored: ${saved.project.name}")
                    },
                    onRename = { saved -> renameCandidate = saved; renameText = saved.project.name },
                    onDelete = { saved -> deleteCandidate = saved }
                )
            } else {
                val p = project!!
                DiamondWorkScreen(
                    modifier = Modifier.fillMaxSize(),
                    project = p,
                    sourceImage = sourceImage,
                    showOriginal = showOriginal,
                    drillShape = drillShape,
                    reserve = reserve,
                    isPro = isPro,
                    status = status,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                    onShowOriginal = { showOriginal = it },
                    onToggle = { x, y ->
                        undoStack.add(p.grid)
                        if (undoStack.size > 50) undoStack.removeAt(0)
                        redoStack.clear()
                        project = p.copy(grid = ProgressEngine.toggle(p.grid, x, y), updatedAt = System.currentTimeMillis())
                    },
                    onUndo = {
                        if (undoStack.isNotEmpty()) {
                            redoStack.add(p.grid)
                            val previous = undoStack.removeAt(undoStack.lastIndex)
                            project = p.copy(grid = previous, updatedAt = System.currentTimeMillis())
                        }
                    },
                    onRedo = {
                        if (redoStack.isNotEmpty()) {
                            undoStack.add(p.grid)
                            val next = redoStack.removeAt(redoStack.lastIndex)
                            project = p.copy(grid = next, updatedAt = System.currentTimeMillis())
                        }
                    },
                    onClearProgress = {
                        undoStack.add(p.grid)
                        if (undoStack.size > 50) undoStack.removeAt(0)
                        redoStack.clear()
                        project = p.copy(grid = ProgressEngine.clear(p.grid), updatedAt = System.currentTimeMillis())
                    },
                    onSave = {
                        if (p.name in setOf("Моя алмазная картина", "Моя алмазна картина", "My diamond painting")) {
                            saveAsName = ""
                            showSaveAsDialog = true
                        } else {
                            val saved = p.copy(updatedAt = System.currentTimeMillis())
                            project = saved
                            saveProject(context, saved)
                            savedRefresh++
                            status = tr(context, "Проект сохранён: ${saved.name}", "Проєкт збережено: ${saved.name}", "Project saved: ${saved.name}")
                        }
                    },
                    onNewProject = { showNewProjectConfirm = true },
                    onEditSettings = {
                        if (sourceImage != null) showSetup = true else status = tr(context, "Для изменения настроек исходная фотография недоступна", "Початкове фото недоступне для зміни налаштувань", "Original photo is unavailable for editing settings")
                    },
                    onDrillShape = { drillShape = it },
                    onReserve = { reserve = it },
                    onExportProject = {
                        if (isPro) projectExportLauncher.launch("DiamondCraft_${p.grid.width}x${p.grid.height}.diamondcraft") else showProDialog = true
                    },
                    onPng = {
                        if (isPro) pngLauncher.launch("DiamondCraft_${p.grid.width}x${p.grid.height}_pattern.png") else showProDialog = true
                    },
                    onPdf = {
                        if (isPro) pdfLauncher.launch("DiamondCraft_${p.grid.width}x${p.grid.height}_pattern.pdf") else showProDialog = true
                    },
                    onMaterialsPdf = {
                        if (isPro) materialsPdfLauncher.launch("DiamondCraft_${p.grid.width}x${p.grid.height}_materials.pdf") else showProDialog = true
                    },
                    onCsv = {
                        if (isPro) csvLauncher.launch("DiamondCraft_${p.grid.width}x${p.grid.height}_materials.csv") else showProDialog = true
                    },
                    onShoppingList = { shoppingListText = buildShoppingList(context, p, materialEstimate(p, drillShape, reserve.toInt())) }
                )
            }

            if (busy) {
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.28f)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, shadowElevation = 8.dp) {
                        Row(
                            Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                            Column {
                                Text(busyTitle, style = MaterialTheme.typography.titleMedium)
                                Text(busyTitle, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiamondSetupScreen(
    modifier: Modifier = Modifier,
    compactUi: Boolean,
    screenPadding: androidx.compose.ui.unit.Dp,
    isPro: Boolean,
    maxWidth: Int,
    maxColors: Int,
    width: Float,
    colorCount: Float,
    imageProfile: ImageProfile,
    colorStyle: ColorStyle,
    sourceSelected: Boolean,
    busy: Boolean,
    status: String,
    savedProjects: List<SavedProjectInfo>,
    onWidth: (Float) -> Unit,
    onColorCount: (Float) -> Unit,
    onProfile: (ImageProfile) -> Unit,
    onColorStyle: (ColorStyle) -> Unit,
    onPick: () -> Unit,
    onGenerate: () -> Unit,
    onImport: () -> Unit,
    onOpenSaved: (SavedProjectInfo) -> Unit,
    onRename: (SavedProjectInfo) -> Unit,
    onDelete: (SavedProjectInfo) -> Unit
) {
    val context = LocalContext.current
    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(screenPadding),
                verticalArrangement = Arrangement.spacedBy(if (compactUi) 8.dp else 10.dp)
            ) {
                Text(tr(context, "💎 Фото → схема алмазной мозаики", "💎 Фото → схема алмазної мозаїки", "💎 Photo → diamond painting pattern"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    if (isPro) "DiamondCraft Pro" else tr(context, "Бесплатно • до ${CommercialLimits.FREE_MAX_WIDTH} страз / ${CommercialLimits.FREE_MAX_COLORS} цветов", "Безкоштовно • до ${CommercialLimits.FREE_MAX_WIDTH} стразів / ${CommercialLimits.FREE_MAX_COLORS} кольорів", "Free • up to ${CommercialLimits.FREE_MAX_WIDTH} drills / ${CommercialLimits.FREE_MAX_COLORS} colors"),
                    style = MaterialTheme.typography.bodySmall
                )

                Button(onClick = onPick, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text(if (sourceSelected) tr(context, "Выбрать другую фотографию", "Обрати інше фото", "Choose another photo") else tr(context, "Выбрать фотографию", "Обрати фото", "Choose photo"))
                }
                Text(if (sourceSelected) tr(context, "Фотография выбрана ✓", "Фото обрано ✓", "Photo selected ✓") else tr(context, "Фотография не выбрана", "Фото не обрано", "Photo not selected"), style = MaterialTheme.typography.bodySmall)

                Text(tr(context, "Ширина схемы: ${width.toInt()} страз", "Ширина схеми: ${width.toInt()} стразів", "Pattern width: ${width.toInt()} drills"))
                Slider(width, onWidth, valueRange = 30f..maxWidth.toFloat(), steps = 16, enabled = !busy)
                Text(tr(context, "Детализация цвета: ${colorCount.toInt()} цветов", "Деталізація кольору: ${colorCount.toInt()} кольорів", "Color detail: ${colorCount.toInt()} colors"))
                Slider(colorCount, onColorCount, valueRange = 24f..maxColors.toFloat(), steps = 7, enabled = !busy)
                Text(tr(context, "Подсказка: для портретов обычно достаточно 100–140 страз по ширине, для пейзажей — 120–180.", "Підказка: для портретів зазвичай достатньо 100–140 стразів завширшки, для пейзажів — 120–180.", "Tip: portraits usually work well at 100–140 drills wide; landscapes at 120–180."), style = MaterialTheme.typography.bodySmall)

                Text(tr(context, "Профиль обработки", "Профіль обробки", "Processing profile"))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ImageProfile.entries.forEachIndexed { index, profile ->
                        SegmentedButton(selected = imageProfile == profile, onClick = { onProfile(profile) }, enabled = !busy, shape = SegmentedButtonDefaults.itemShape(index, ImageProfile.entries.size)) {
                            Text(profileName(context, profile), maxLines = 1)
                        }
                    }
                }

                Text(tr(context, "Цветопередача", "Передача кольору", "Color rendering"))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ColorStyle.entries.forEachIndexed { index, style ->
                        SegmentedButton(selected = colorStyle == style, onClick = { onColorStyle(style) }, enabled = !busy, shape = SegmentedButtonDefaults.itemShape(index, ColorStyle.entries.size)) {
                            Text(colorStyleName(context, style), maxLines = 1)
                        }
                    }
                }
                Text(tr(context, "Яркий — рекомендуемый режим для алмазной мозаики.", "Яскравий — рекомендований режим для алмазної мозаїки.", "Vivid is the recommended mode for diamond painting."), style = MaterialTheme.typography.bodySmall)

                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text(tr(context, "Импорт проекта (.diamondcraft)", "Імпорт проєкту (.diamondcraft)", "Import project (.diamondcraft)")) }
                Text(status, style = MaterialTheme.typography.bodySmall)

                if (savedProjects.isNotEmpty()) {
                    HorizontalDivider()
                    Text(tr(context, "Сохранённые проекты", "Збережені проєкти", "Saved projects"), style = MaterialTheme.typography.titleMedium)
                    savedProjects.take(5).forEach { saved ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { onOpenSaved(saved) }, modifier = Modifier.weight(1f)) {
                                Text("${saved.project.name} • ${saved.project.grid.width}×${saved.project.grid.height}", maxLines = if (compactUi) 2 else 1)
                            }
                            TextButton(onClick = { onRename(saved) }) { Text(tr(context, "Имя", "Назва", "Name"), maxLines = 1) }
                            TextButton(onClick = { onDelete(saved) }) { Text(tr(context, "Удалить", "Видалити", "Delete"), maxLines = 1) }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
                Button(
                    onClick = onGenerate,
                    enabled = sourceSelected && !busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = screenPadding, vertical = if (compactUi) 8.dp else 10.dp)
                ) { Text(if (busy) tr(context, "Создаём схему…", "Створюємо схему…", "Creating pattern…") else tr(context, "Создать схему", "Створити схему", "Create pattern")) }
            }
        }
    }
}

@Composable
private fun DiamondWorkScreen(
    modifier: Modifier = Modifier,
    project: CraftProject,
    sourceImage: CraftImage?,
    showOriginal: Boolean,
    drillShape: DrillShape,
    reserve: Float,
    isPro: Boolean,
    status: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onShowOriginal: (Boolean) -> Unit,
    onToggle: (Int, Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearProgress: () -> Unit,
    onSave: () -> Unit,
    onNewProject: () -> Unit,
    onEditSettings: () -> Unit,
    onDrillShape: (DrillShape) -> Unit,
    onReserve: (Float) -> Unit,
    onExportProject: () -> Unit,
    onPng: () -> Unit,
    onPdf: () -> Unit,
    onMaterialsPdf: () -> Unit,
    onCsv: () -> Unit,
    onShoppingList: () -> Unit
) {
    val context = LocalContext.current
    val compact = LocalConfiguration.current.screenWidthDp <= 400
    val pagePadding = if (compact) 8.dp else 12.dp
    val stats = remember(project.grid) { DiamondEngine.stats(project.grid) }
    val estimate = remember(project.grid, drillShape, reserve.toInt()) { materialEstimate(project, drillShape, reserve.toInt()) }
    var showMaterials by remember(project.id) { mutableStateOf(false) }
    var zoomCommand by remember(project.id) { mutableFloatStateOf(1f) }
    var resetKey by remember(project.id) { mutableIntStateOf(0) }

    Column(
        modifier.padding(horizontal = pagePadding, vertical = if (compact) 6.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(tr(context, "${project.grid.width} × ${project.grid.height} • ${project.grid.palette.size} цветов", "${project.grid.width} × ${project.grid.height} • ${project.grid.palette.size} кольорів", "${project.grid.width} × ${project.grid.height} • ${project.grid.palette.size} colors"), style = MaterialTheme.typography.bodySmall)
            }
            Text("${stats.completedDrills}/${stats.totalDrills}", style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(progress = { (project.grid.progressPercentExact() / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onSave, modifier = Modifier.weight(2f)) { Text(tr(context, "Сохранить", "Зберегти", "Save"), maxLines = 1) }
                OutlinedButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.weight(1f)) { Text("↶") }
                OutlinedButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.weight(1f)) { Text("↷") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { zoomCommand = (zoomCommand / 1.6f).coerceAtLeast(1f) }, modifier = Modifier.weight(1f)) { Text("−") }
                OutlinedButton(onClick = { zoomCommand = 1f; resetKey++ }, modifier = Modifier.weight(2f)) { Text(tr(context, "По размеру", "За розміром", "Fit"), maxLines = 1) }
                OutlinedButton(onClick = { zoomCommand = (zoomCommand * 1.6f).coerceAtMost(12f) }, modifier = Modifier.weight(1f)) { Text("+") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (sourceImage != null) {
                    OutlinedButton(onClick = onEditSettings, modifier = Modifier.weight(1f)) { Text(tr(context, "Изменить настройки", "Змінити налаштування", "Edit settings"), maxLines = 1) }
                }
                OutlinedButton(onClick = { showMaterials = !showMaterials }, modifier = Modifier.weight(1f)) {
                    Text(if (showMaterials) tr(context, "← К схеме", "← До схеми", "← Pattern") else tr(context, "Материалы", "Матеріали", "Materials"), maxLines = 1)
                }
            }
        }

        if (sourceImage != null) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !showOriginal, onClick = { onShowOriginal(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(tr(context, "Схема", "Схема", "Pattern"), maxLines = 1) }
                SegmentedButton(selected = showOriginal, onClick = { onShowOriginal(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(tr(context, "Оригинал", "Оригінал", "Original"), maxLines = 1) }
            }
        }

        if (!showMaterials) {
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                if (showOriginal && sourceImage != null) {
                    OriginalImagePreview(sourceImage, Modifier.fillMaxSize())
                } else {
                    DiamondGrid(project.grid, externalScale = zoomCommand, resetKey = resetKey, modifier = Modifier.fillMaxSize(), onCell = onToggle)
                }
            }
        }

        if (showMaterials) {
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Button(onClick = { showMaterials = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr(context, "← Вернуться к схеме", "← Повернутися до схеми", "← Back to pattern"))
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr(context, "Расходники", "Матеріали", "Supplies"), style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        DrillShape.entries.forEachIndexed { index, shape ->
                            SegmentedButton(selected = drillShape == shape, onClick = { onDrillShape(shape) }, shape = SegmentedButtonDefaults.itemShape(index, DrillShape.entries.size)) { Text(drillShapeName(context, shape), maxLines = 1) }
                        }
                    }
                    Text(tr(context, "Запас страз: ${reserve.toInt()}%", "Запас стразів: ${reserve.toInt()}%", "Drill reserve: ${reserve.toInt()}%"))
                    Slider(reserve, onReserve, valueRange = 5f..20f, steps = 2)
                    Text(tr(context, "Картина: ${cm(estimate.pictureWidthCm)} × ${cm(estimate.pictureHeightCm)} см", "Картина: ${cm(estimate.pictureWidthCm)} × ${cm(estimate.pictureHeightCm)} см", "Picture: ${cm(estimate.pictureWidthCm)} × ${cm(estimate.pictureHeightCm)} cm"))
                    Text(tr(context, "Основа: ${cm(estimate.canvasWidthCm)} × ${cm(estimate.canvasHeightCm)} см", "Основа: ${cm(estimate.canvasWidthCm)} × ${cm(estimate.canvasHeightCm)} см", "Canvas: ${cm(estimate.canvasWidthCm)} × ${cm(estimate.canvasHeightCm)} cm"))
                    Text(tr(context,
                        "Стразы: ${estimate.totalRequiredDrills} шт. • примерно ${estimate.totalBags} пак.",
                        "Стрази: ${estimate.totalRequiredDrills} шт. • приблизно ${estimate.totalBags} пак.",
                        "Drills: ${estimate.totalRequiredDrills} pcs • about ${estimate.totalBags} bags"))
                    OutlinedButton(onClick = {
                        openShopSearch(context, tr(context,
                            "клеевая основа для алмазной мозаики ${cm(estimate.canvasWidthCm)}x${cm(estimate.canvasHeightCm)} см",
                            "клейова основа для алмазної мозаїки ${cm(estimate.canvasWidthCm)}x${cm(estimate.canvasHeightCm)} см",
                            "adhesive canvas diamond painting ${cm(estimate.canvasWidthCm)}x${cm(estimate.canvasHeightCm)} cm"))
                    }, modifier = Modifier.fillMaxWidth()) { Text(tr(context, "Найти основу в магазинах", "Знайти основу в магазинах", "Find canvas in stores")) }
                    Text(tr(context, "Палитра", "Палітра", "Palette"), style = MaterialTheme.typography.titleSmall)
                    estimate.colors.forEachIndexed { i, item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Box(Modifier.size(20.dp).background(Color(item.color.argb)))
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tr(context,
                                    "${i + 1}. ${item.color.id} — ${item.exactCount}; купить ${item.requiredCount} (${item.bags} пак.)",
                                    "${i + 1}. ${item.color.id} — ${item.exactCount}; купити ${item.requiredCount} (${item.bags} пак.)",
                                    "${i + 1}. ${item.color.id} — ${item.exactCount}; buy ${item.requiredCount} (${item.bags} bags)"), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = {
                                    openShopSearch(context, tr(context,
                                        "стразы для алмазной мозаики ${drillShapeName(context, drillShape).lowercase()} цвет ${item.color.id} ${item.requiredCount} шт",
                                        "стрази для алмазної мозаїки ${drillShapeName(context, drillShape).lowercase()} колір ${item.color.id} ${item.requiredCount} шт",
                                        "diamond painting ${drillShapeName(context, drillShape).lowercase()} drills color ${item.color.id} ${item.requiredCount} pcs"))
                                }) { Text(tr(context, "Найти в магазинах", "Знайти в магазинах", "Find in stores")) }
                            }
                        }
                    }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onPdf, modifier = Modifier.weight(1f)) { Text(tr(context, "Схема PDF", "Схема PDF", "Pattern PDF"), maxLines = 1) }
                OutlinedButton(onClick = onMaterialsPdf, modifier = Modifier.weight(1f)) { Text(tr(context, "Материалы PDF", "Матеріали PDF", "Materials PDF"), maxLines = 1) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onExportProject, modifier = Modifier.weight(1f)) { Text(if (isPro) tr(context, "Проект", "Проєкт", "Project") else tr(context, "Проект • PRO", "Проєкт • PRO", "Project • PRO"), maxLines = 1) }
                OutlinedButton(onClick = onPng, modifier = Modifier.weight(1f)) { Text(if (isPro) "PNG" else "PNG • PRO", maxLines = 1) }
                OutlinedButton(onClick = onCsv, modifier = Modifier.weight(1f)) { Text(if (isPro) "CSV" else "CSV • PRO", maxLines = 1) }
            }
            Button(onClick = onShoppingList, modifier = Modifier.fillMaxWidth()) { Text(tr(context, "Список покупок", "Список покупок", "Shopping list"), maxLines = 1) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onClearProgress, modifier = Modifier.weight(1f)) { Text(tr(context, "Снять отметки", "Зняти позначки", "Clear marks"), maxLines = 1) }
                OutlinedButton(onClick = onNewProject, modifier = Modifier.weight(1f)) { Text(tr(context, "Новый проект", "Новий проєкт", "New project"), maxLines = 1) }
            }
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, maxLines = 2)
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

private fun buildShoppingList(context: Context, project: CraftProject, estimate: DiamondMaterialEstimate): String = buildString {
    appendLine(tr(context, "DiamondCraft — список покупок", "DiamondCraft — список покупок", "DiamondCraft — shopping list"))
    appendLine(tr(context, "Проект: ${project.name}", "Проєкт: ${project.name}", "Project: ${project.name}"))
    appendLine(tr(context, "Картина: ${cm(estimate.pictureWidthCm)} × ${cm(estimate.pictureHeightCm)} см", "Картина: ${cm(estimate.pictureWidthCm)} × ${cm(estimate.pictureHeightCm)} см", "Picture: ${cm(estimate.pictureWidthCm)} × ${cm(estimate.pictureHeightCm)} cm"))
    appendLine(tr(context, "Клеевая основа: ${cm(estimate.canvasWidthCm)} × ${cm(estimate.canvasHeightCm)} см", "Клейова основа: ${cm(estimate.canvasWidthCm)} × ${cm(estimate.canvasHeightCm)} см", "Adhesive canvas: ${cm(estimate.canvasWidthCm)} × ${cm(estimate.canvasHeightCm)} cm"))
    appendLine(tr(context, "Стразы: ${drillShapeName(context, estimate.drillShape).lowercase()}", "Стрази: ${drillShapeName(context, estimate.drillShape).lowercase()}", "Drills: ${drillShapeName(context, estimate.drillShape).lowercase()}"))
    appendLine(tr(context, "Запас: ${estimate.reservePercent}%", "Запас: ${estimate.reservePercent}%", "Reserve: ${estimate.reservePercent}%"))
    appendLine(tr(context, "Всего купить: ${estimate.totalRequiredDrills} шт. (~${estimate.totalBags} пак. по 200 шт.)", "Усього купити: ${estimate.totalRequiredDrills} шт. (~${estimate.totalBags} пак. по 200 шт.)", "Total to buy: ${estimate.totalRequiredDrills} pcs (~${estimate.totalBags} bags of 200)"))
    appendLine()
    appendLine(tr(context, "По цветам:", "За кольорами:", "By color:"))
    estimate.colors.forEachIndexed { index, item ->
        appendLine(tr(context, "${index + 1}. ${item.color.id}: ${item.requiredCount} шт. (${item.bags} пак.)", "${index + 1}. ${item.color.id}: ${item.requiredCount} шт. (${item.bags} пак.)", "${index + 1}. ${item.color.id}: ${item.requiredCount} pcs (${item.bags} bags)"))
    }
    appendLine()
    appendLine(tr(context, "Дополнительно: клеевая основа, лоток, стилус, воск/клей.", "Додатково: клейова основа, лоток, стилус, віск/клей.", "Also: adhesive canvas, tray, stylus, wax/glue."))
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
private fun OriginalImagePreview(image: CraftImage, modifier: Modifier = Modifier) {
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
        modifier = modifier.background(Color(0xFFF5F5F5)),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun DiamondGrid(
    grid: CraftGrid,
    externalScale: Float = 1f,
    resetKey: Int = 0,
    modifier: Modifier = Modifier,
    onCell: (Int, Int) -> Unit
) {
    var scale by remember(grid.width, grid.height) { mutableFloatStateOf(1f) }
    var pan by remember(grid.width, grid.height) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(externalScale) {
        scale = externalScale.coerceIn(1f, 12f)
    }
    LaunchedEffect(resetKey) {
        scale = 1f
        pan = Offset.Zero
    }

    val fastPreview = remember(grid) {
        val bmp = android.graphics.Bitmap.createBitmap(grid.width, grid.height, android.graphics.Bitmap.Config.ARGB_8888)
        val pixels = IntArray(grid.width * grid.height)
        grid.cells.forEachIndexed { index, cell ->
            val base = grid.palette[cell.colorIndex].argb
            pixels[index] = if (cell.completed) {
                val r = (android.graphics.Color.red(base) * 0.42f).toInt()
                val g = (android.graphics.Color.green(base) * 0.42f).toInt()
                val b = (android.graphics.Color.blue(base) * 0.42f).toInt()
                android.graphics.Color.rgb(r, g, b)
            } else base
        }
        bmp.setPixels(pixels, 0, grid.width, 0, 0, grid.width, grid.height)
        bmp.asImageBitmap()
    }

    Canvas(
        modifier
            .background(Color(0xFFF5F5F5))
            .clipToBounds()
            .pointerInput(grid) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2) {
                            // Two fingers: zoom + pan, keeping the content inside its bounds.
                            val zoom = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(1f, 12f)
                            val base = min(size.width.toFloat() / grid.width.toFloat(), size.height.toFloat() / grid.height.toFloat())
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
                        } else if (scale > 1.001f) {
                            // One finger on an enlarged pattern: move the pattern itself.
                            // At fit scale the event is not consumed, so the outer page can scroll.
                            val change = pressed.first()
                            val delta = change.position - change.previousPosition
                            if (delta != Offset.Zero) {
                                val base = min(size.width.toFloat() / grid.width.toFloat(), size.height.toFloat() / grid.height.toFloat())
                                val contentWidth = base * scale * grid.width
                                val contentHeight = base * scale * grid.height
                                val maxPanX = max(0f, (contentWidth - size.width) / 2f)
                                val maxPanY = max(0f, (contentHeight - size.height) / 2f)
                                pan = Offset(
                                    (pan.x + delta.x).coerceIn(-maxPanX, maxPanX),
                                    (pan.y + delta.y).coerceIn(-maxPanY, maxPanY)
                                )
                                change.consume()
                            }
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

        if (cell < 7f) {
            drawImage(
                image = fastPreview,
                dstOffset = IntOffset(originX.toInt(), originY.toInt()),
                dstSize = IntSize(contentWidth.toInt().coerceAtLeast(1), contentHeight.toInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.None
            )
        } else {
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
                            style = Stroke(if (c.completed) max(1f, cell * 0.16f) else 1f)
                        )
                    }
                }
            }
        }
    }
}

private fun loadCraftImage(context: Context, uri: Uri): CraftImage {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Некорректное изображение" }

    var sample = 1
    val maxSide = 2200
    while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2

    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("Не удалось декодировать изображение")
    return try {
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        CraftImage(bmp.width, bmp.height, pixels)
    } finally {
        bmp.recycle()
    }
}

