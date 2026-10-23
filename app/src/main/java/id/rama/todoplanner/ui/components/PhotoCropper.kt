package id.rama.todoplanner.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Layar crop foto profil manual (permintaan Rama, ronde 1):
 * geser (drag) + zoom (cubit) + putar 90°, di dalam bingkai persegi berguide lingkaran.
 * Hasil akhir dikirim sebagai bitmap kerja + [CropRectFrac] untuk disimpan ViewModel.
 * Murni Compose foundation (Canvas + detectTransformGestures) — tanpa library crop eksternal.
 */
@Composable
fun PhotoCropper(
    uri: Uri,
    onCancel: () -> Unit,
    onConfirm: (Bitmap, CropRectFrac) -> Unit
) {
    val context = LocalContext.current
    var working by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        working = withContext(Dispatchers.IO) { PhotoStore.loadOrientedBitmap(context, uri) }
    }
    val bmp = working

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1E2A32)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Atur Foto", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Geser & cubit untuk mengatur posisi foto", color = Color(0xFFB9CBD4), fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

        if (bmp == null) {
            Box(modifier = Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                Text("Memuat foto…", color = Color.White)
            }
        } else {
            CropCanvas(bmp) { rect -> onConfirm(bmp, rect) }
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val b = working ?: return@OutlinedButton
                    val m = Matrix().apply { postRotate(90f) }
                    working = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
                },
                modifier = Modifier.weight(1f)
            ) { Text("🔄 Putar", color = Color.White) }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Batal", color = Color.White)
            }
        }
    }
}

@Composable
private fun CropCanvas(bmp: Bitmap, onConfirm: (CropRectFrac) -> Unit) {
    val img = remember(bmp) { bmp.asImageBitmap() }
    val minSide = minOf(bmp.width, bmp.height).toFloat()
    var win by remember(bmp) { mutableStateOf(minSide) }
    var cx by remember(bmp) { mutableStateOf(bmp.width / 2f) }
    var cy by remember(bmp) { mutableStateOf(bmp.height / 2f) }
    var canvasPx by remember { mutableStateOf(IntSize.Zero) }

    fun clampWindow() {
        win = win.coerceIn(minSide / 4f, minSide)
        cx = cx.coerceIn(win / 2f, bmp.width - win / 2f)
        cy = cy.coerceIn(win / 2f, bmp.height - win / 2f)
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clipToBounds()
            .onSizeChanged { canvasPx = it }
            .pointerInput(bmp) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // Jendela crop digambar sebagai persegi sebesar sisi terpendek kanvas,
                    // jadi rasio geser harus terhadap sisi itu (bukan lebar penuh kanvas).
                    val side = minOf(canvasPx.width, canvasPx.height).coerceAtLeast(1)
                    val ratio = win / side
                    cx -= pan.x * ratio
                    cy -= pan.y * ratio
                    win /= zoom
                    clampWindow()
                }
            }
    ) {
        val side = size.minDimension
        val dx = (size.width - side) / 2f
        val dy = (size.height - side) / 2f
        drawImage(
            image = img,
            srcOffset = IntOffset((cx - win / 2f).toInt(), (cy - win / 2f).toInt()),
            srcSize = IntSize(win.toInt(), win.toInt()),
            dstOffset = IntOffset(dx.toInt(), dy.toInt()),
            dstSize = IntSize(side.toInt(), side.toInt())
        )
        // Guide: lingkaran avatar + bingkai persegi.
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = side / 2f,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = 2.dp.toPx())
        )
        drawRect(
            color = Color.White.copy(alpha = 0.35f),
            topLeft = Offset(dx, dy),
            size = androidx.compose.ui.geometry.Size(side, side),
            style = Stroke(width = 1.dp.toPx())
        )
    }
    Spacer(Modifier.height(14.dp))
    GradientButton(
        text = "✓ Pakai Foto Ini",
        onClick = {
            onConfirm(
                CropRectFrac(
                    left = (cx - win / 2f) / bmp.width,
                    top = (cy - win / 2f) / bmp.height,
                    sideX = win / bmp.width,
                    sideY = win / bmp.height
                )
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}
