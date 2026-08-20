@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
com.craftengine.diamondcraft

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import java.util.UUID
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme { DiamondApp() } } }
}

private val demoPalette = listOf(
    CraftColor("01","Белый",0xFFFFFFFF.toInt()), CraftColor("02","Чёрный",0xFF202020.toInt()),
    CraftColor("03","Красный",0xFFD84A4A.toInt()), CraftColor("04","Оранжевый",0xFFF19A3E.toInt()),
    CraftColor("05","Жёлтый",0xFFF1D54A.toInt()), CraftColor("06","Зелёный",0xFF4F9A67.toInt()),
    CraftColor("07","Голубой",0xFF5BA7D9.toInt()), CraftColor("08","Синий",0xFF4D63B8.toInt()),
    CraftColor("09","Фиолетовый",0xFF8D62B5.toInt()), CraftColor("10","Розовый",0xFFD77EA3.toInt()),
    CraftColor("11","Коричневый",0xFF8A6546.toInt()), CraftColor("12","Бежевый",0xFFD7BC92.toInt())
)

@Composable private fun DiamondApp() {
    val context = LocalContext.current
    var project by remember { mutableStateOf<CraftProject?>(null) }
    var width by remember { mutableStateOf(40f) }
    var status by remember { mutableStateOf("Выберите фотографию") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri).use { input -> BitmapFactory.decodeStream(input) }!!
        }.onSuccess { bmp ->
            val maxSide = width.toInt().coerceIn(20,100)
            val h = (maxSide * bmp.height.toFloat() / bmp.width).toInt().coerceIn(20,120)
            val px = IntArray(bmp.width*bmp.height); bmp.getPixels(px,0,bmp.width,0,0,bmp.width,bmp.height)
            val grid = ImageEngine.toGrid(CraftImage(bmp.width,bmp.height,px), ImageConversionOptions(maxSide,h,demoPalette))
            project = CraftProject(UUID.randomUUID().toString(),"Моя алмазная картина",CraftMode.DIAMOND_PAINTING,grid,System.currentTimeMillis())
            status = "Схема создана: ${grid.width} × ${grid.height}"
        }.onFailure { status = "Не удалось открыть изображение" }
    }
    Scaffold(topBar={ TopAppBar(title={Text("💎 DiamondCraft 0.3")}) }) { pad ->
        Column(Modifier.padding(pad).padding(12.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Фото → схема алмазной мозаики", style=MaterialTheme.typography.titleMedium)
            Text("Ширина схемы: ${width.toInt()} страз")
            Slider(width,{width=it}, valueRange=20f..100f, steps=7)
            Button(onClick={picker.launch("image/*")}, modifier=Modifier.fillMaxWidth()){Text("Выбрать фотографию")}
            Text(status)
            project?.let { p ->
                val stats=DiamondEngine.stats(p.grid)
                Text("Установлено: ${stats.completedDrills} / ${stats.totalDrills} • ${p.grid.progressPercent()}%")
                DiamondGrid(p.grid) { x,y -> project=p.copy(grid=ProgressEngine.toggle(p.grid,x,y),updatedAt=System.currentTimeMillis()) }
                Text("Палитра", style=MaterialTheme.typography.titleMedium)
                p.grid.palette.forEachIndexed { i,c ->
                    val count=stats.byColor[c.id]?:0
                    Row(Modifier.fillMaxWidth().padding(vertical=2.dp)) {
                        Box(Modifier.size(22.dp).background(Color(c.argb)))
                        Spacer(Modifier.width(8.dp)); Text("${i+1}. ${c.name} — $count шт.")
                    }
                }
            }
        }
    }
}

@Composable private fun DiamondGrid(grid:CraftGrid,onCell:(Int,Int)->Unit) {
    var scale by remember { mutableStateOf(1f) }; var pan by remember { mutableStateOf(Offset.Zero) }
    Canvas(Modifier.fillMaxWidth().height(430.dp).background(Color(0xFFF5F5F5)).pointerInput(grid,scale,pan){
        detectTransformGestures { _,panChange,zoom,_ -> scale=(scale*zoom).coerceIn(0.8f,6f); pan+=panChange }
    }.pointerInput(grid,scale,pan){
        awaitPointerEventScope { while(true){ val e=awaitPointerEvent(); val ch=e.changes.firstOrNull()?:continue; if(!ch.pressed && ch.previousPressed){
            val base=min(size.width/grid.width.toFloat(),size.height/grid.height.toFloat()); val cell=base*scale
            val x=((ch.position.x-pan.x)/cell).toInt(); val y=((ch.position.y-pan.y)/cell).toInt(); if(x in 0 until grid.width && y in 0 until grid.height) onCell(x,y)
        } } }
    }) {
        val base=min(size.width/grid.width,size.height/grid.height); val cell=base*scale
        grid.cells.forEachIndexed { idx,c -> val x=idx%grid.width; val y=idx/grid.width; val left=pan.x+x*cell; val top=pan.y+y*cell
            drawCircle(Color(grid.palette[c.colorIndex].argb),cell*0.38f,Offset(left+cell/2,top+cell/2))
            drawCircle(if(c.completed) Color.Black else Color.Gray,cell*0.38f,Offset(left+cell/2,top+cell/2),style=Stroke(if(c.completed) cell*0.16f else 1f))
        }
    }
}
