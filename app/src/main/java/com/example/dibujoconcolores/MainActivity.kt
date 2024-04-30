package com.example.dibujoconcolores
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dibujoconcolores.ui.theme.DibujoConColoresTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class MainActivity : ComponentActivity() {

    @SuppressLint("UnrememberedMutableState")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DibujoConColoresTheme { App(this) } }
    }

    companion object {
        private const val PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 1
    }

    fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE
        )
    }

    fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun App(activity: MainActivity) {
    // ViewModel initialization
    val colorPickerViewModel = viewModel<ColorPickerViewModel>()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Dibuja Algo",
            style = MaterialTheme.typography.titleLarge.copy(color = Color.White), // Color blanco para que resalte en el fondo azul
            modifier = Modifier
                .padding(16.dp) // Padding alrededor del texto
              //  .background(Color.Blue) // Fondo azul
        )
        ColorPicker(colorPickerViewModel)
        DrawCanvas(colorPickerViewModel, activity)
    }
}


data class Stroke(val path: Path, val color: Color, val width: Float)

class ColorPickerViewModel : ViewModel() {
    val red = mutableFloatStateOf(0f)
    val green = mutableFloatStateOf(0f)
    val blue = mutableFloatStateOf(0f)

    val color: Color
        get() = Color(red.value, green.value, blue.value)
}

@Composable
fun DrawCanvas(viewModel: ColorPickerViewModel, activity: MainActivity) {
    val currentStrokeWidth = remember { mutableStateOf(4f) }

    Column(modifier = Modifier.fillMaxSize()) {
        var currentPath by remember { mutableStateOf(Path()) }
        val strokes = remember { mutableStateListOf<Stroke>() }
        var currentStrokeColor by remember { mutableStateOf(Color.Black) }
        val currentStrokePoints = remember { mutableStateListOf<Offset>() }

        Canvas(
                modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPath = Path()
                                currentPath.moveTo(offset.x, offset.y)
                                currentStrokeColor = viewModel.color
                                currentStrokePoints.clear()
                                currentStrokePoints.add(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val currentX = change.position.x
                                val currentY = change.position.y
                                currentPath.lineTo(currentX, currentY)
                                currentStrokePoints.add(Offset(currentX, currentY))
                            },
                            onDragEnd = {
                                strokes.add(
                                    Stroke(
                                        currentPath,
                                        currentStrokeColor,
                                        currentStrokeWidth.value
                                    )
                                )
                            }
                        )
                    }
        ) {
            strokes.forEach { stroke ->
                drawPath(
                        path = stroke.path,
                        color = stroke.color,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke.width)
                )
            }

            if (currentStrokePoints.isNotEmpty()) {
                currentStrokePoints.forEach { point ->
                    drawCircle(
                            color = currentStrokeColor,
                            radius = currentStrokeWidth.value / 2,
                            center = point
                    )
                }
            }
        }

        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { strokes.clear() }) { Text("Limpiar") }

            Slider(
                    value = currentStrokeWidth.value,
                    onValueChange = { newValue -> currentStrokeWidth.value = newValue },
                    valueRange = 1f..20f,
                    steps = 100,
                    modifier = Modifier.weight(1f)
            )
            Button(
                    onClick = {
                        val bitmap = saveCanvasToBitmap(strokes)

                        if (activity.hasStoragePermission()) {
                            // Permiso concedido, guardar el dibujo
                            saveBitmapToFile(bitmap, "drawing_${System.currentTimeMillis()}.png")
                        } else {
                            activity.requestStoragePermission()
                            saveBitmapToFile(bitmap, "drawing_${System.currentTimeMillis()}.png")

                        }
                    }
            ) { Text("Guardar") }
        }
    }
}

@Composable
fun ColorPicker(viewModel: ColorPickerViewModel) {
    val color by remember {
        derivedStateOf { Color(viewModel.red.value, viewModel.green.value, viewModel.blue.value) }
    }

    Column {
        Row {
            Box(
                    modifier =
                            Modifier.padding(10.dp, 0.dp)
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .background(color, shape = MaterialTheme.shapes.large)
            )
        }
        Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ColorSlider("R", viewModel.red, Color.Red)
            ColorSlider("G", viewModel.green, Color.Green)
            ColorSlider("B", viewModel.blue, Color.Blue)
        }
    }
}

@Composable
fun ColorSlider(label: String, valueState: MutableState<Float>, color: Color) {
    Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = label)
        Slider(
                value = valueState.value,
                onValueChange = { newValue -> valueState.value = newValue },
                colors = SliderDefaults.colors(activeTrackColor = color),
                modifier = Modifier.weight(1f)
        )
        Text(
                text = valueState.value.toColorInt().toString(),
                modifier = Modifier.width(25.dp),
                style = MaterialTheme.typography.bodySmall
        )
    }
}

fun Float.toColorInt(): Int = (this * 255 + 0.5f).toInt()

// Función para guardar el dibujo como un archivo PNG
fun saveBitmapToFile(bitmap: Bitmap, filename: String) {
    val outputStream: OutputStream
    try {
        outputStream = FileOutputStream(File(Environment.getExternalStorageDirectory(), filename))
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.close()
        println(Environment.getExternalStorageDirectory())
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

// Función para convertir el lienzo en un bitmap
fun saveCanvasToBitmap(strokes: SnapshotStateList<Stroke>): Bitmap {
    val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    strokes.forEach { stroke ->
        canvas.drawPath(
                stroke.path.asAndroidPath(),
                Paint().apply { color = stroke.color.toArgb().toInt() }
        )
    }
    return bitmap
}
