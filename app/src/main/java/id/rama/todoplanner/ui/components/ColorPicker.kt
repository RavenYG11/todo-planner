package id.rama.todoplanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.ui.theme.Ink
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * RONDE 10 — pemilih warna, dipakai dua tempat:
 *  - Form Tambah/Edit: section "Pilih Warna" (preset + circle wheel).
 *  - Pengaturan: warna label "Daily Reminder" global (preset + wheel yang sama;
 *    tombol kembalinya berarti "kembali ke hijau default", bukan "ikuti kategori").
 *
 * Aturan ronde 10 (MENGGANTIKAN palette ronde 9 — slider hue, kolom hex,
 * dan semua tombol apply manual DIHAPUS total):
 *  - PRESET: deretan lingkaran warna; ketuk preset = warna itu LANGSUNG
 *    terpilih SEKALIGUS membuka wheel untuk fine-tuning.
 *  - CIRCLE WHEEL (persis gambar referensi Rama): cincin hue melingkar di
 *    luar + kotak saturasi/value di tengah, masing-masing dengan indikator
 *    posisi (lingkaran putih di cincin, lingkaran kecil di kotak).
 *  - Wheel HANYA MUNCUL setelah preset diketik; sebelum itu form hanya
 *    menampilkan deretan preset + tombol reset.
 *  - Pemilihan berlaku LANGSUNG saat ketuk/geser di wheel — TANPA tombol
 *    apply apa pun. Menggeser cincin mengubah hue (saturasi/value tetap);
 *    menggeser kotak mengubah saturasi/value (hue tetap).
 *  - Tombol reset ("Ikuti warna kategori" / "Kembali ke hijau default")
 *    TETAP dipertahankan; menekannya juga menutup wheel.
 *
 * Desain sengaja sederhana & mudah dipahami junior: wheel tidak menyimpan
 * state warna sendiri — ia selalu menggambar dari warna terpilih saat ini
 * dan melaporkan setiap ketuk/geser lewat [onColorChange]; parent-lah yang
 * menyimpan pilihan (hex). Jadi tidak ada state ganda yang bisa beradu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetSwatchRow(selectedHex: String?, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ColorPresets.forEach { hex ->
            val color = parseHexColor(hex) ?: Color.Gray
            val selected = selectedHex?.equals(hex, ignoreCase = true) == true
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(color)
                    .then(
                        if (selected) Modifier.border(3.dp, Ink, CircleShape)
                        else Modifier.border(1.dp, Color(0xFFD5DEE3), CircleShape)
                    )
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center
            ) {
                if (selected) Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun ColorPickerSection(
    selectedHex: String?,
    categoryHex: String,
    onSelect: (String) -> Unit,
    onFollowCategory: () -> Unit,
    // RONDE 9 (amendemen 6): di Pengaturan, tombol kembali ini berarti
    // "kembali ke hijau default", bukan "ikuti warna kategori".
    followLabel: String = "Ikuti warna kategori"
) {
    // RONDE 10: wheel tertutup sampai user mengklik sebuah preset.
    var wheelVisible by remember { mutableStateOf(false) }

    Column {
        Text("Preset", fontSize = 12.sp, color = Color(0xFF70858E))
        PresetSwatchRow(
            selectedHex = selectedHex,
            onSelect = { hex ->
                onSelect(hex)
                wheelVisible = true
            }
        )
        if (wheelVisible) {
            Spacer(Modifier.padding(4.dp))
            Text("Fine-tuning warna", fontSize = 12.sp, color = Color(0xFF70858E))
            val current = parseHexColor(selectedHex)
                ?: parseHexColor(categoryHex) ?: Color.Gray
            CircleColorWheel(
                color = current,
                onColorChange = { onSelect(colorToHex(it)) }
            )
            Text(
                "Ketuk / geser di cincin (hue) atau kotak tengah (terang-gelap) — " +
                    "warna langsung berubah, tanpa tombol.",
                fontSize = 11.sp, color = Color(0xFF70858E)
            )
        }
        Row {
            TextButton(
                onClick = {
                    onFollowCategory()
                    wheelVisible = false
                }
            ) { Text(followLabel) }
        }
        if (selectedHex != null) {
            Text(
                "Warna terpilih: $selectedHex" +
                    if (selectedHex.equals(categoryHex, ignoreCase = true)) " (warna kategori)" else " (custom)",
                fontSize = 12.sp, color = Color(0xFF70858E)
            )
        }
    }
}

/**
 * RONDE 10 — circle color picker persis referensi (cincin hue + kotak SV):
 *  - Cincin: gradasi hue melingkar (tebal 19% diameter), indikator berupa
 *    lingkaran berisi warna terpilih dengan bingkai putih di atas cincin.
 *  - Kotak tengah: gradasi horizontal putih -> warna hue penuh, ditimpa
 *    gradasi vertikal transparan -> hitam; indikator lingkaran kecil
 *    berbingkai putih di posisi (saturasi, value).
 *  - Pemetaan sudut (diukur searah jarum jam dari atas, sesuai referensi:
 *    atas = merah, kanan = ungu/biru, bawah = cyan, kiri = kuning):
 *    hue = (360 - sudutDariAtas) % 360. Gradasi sweep Compose berjalan
 *    searah jarum jam mulai dari timur, jadi daftar warnanya dimulai dari
 *    hue timur (270 = ungu) dan kembali ke 270 agar sambungannya mulus.
 *
 * Interaksi: satu loop pointer mentah (down + gerak, tanpa menunggu
 * ambang drag) supaya KETUK langsung melompat ke warna itu dan GESER
 * mengalir. Warna terbaru selalu dibaca lewat [rememberUpdatedState]
 * agar gestur yang sedang berjalan tidak dibatalkan oleh recomposition
 * saat warna berubah — dan tidak pernah memakai hue basi dari gestur lama.
 */
@Composable
fun CircleColorWheel(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestColor by rememberUpdatedState(color)
    val latestOnChange by rememberUpdatedState(onColorChange)

    // Geometri wheel (satu fungsi dipakai gambar & hitung sentuhan).
    fun geometry(diameterPx: Float): WheelGeometry {
        val outer = diameterPx / 2f
        val ringWidth = diameterPx * 0.19f
        val innerRadius = outer - ringWidth
        val squareSide = innerRadius * 2f * 0.72f
        return WheelGeometry(
            center = Offset(outer, outer),
            outerRadius = outer,
            ringWidth = ringWidth,
            ringRadius = outer - ringWidth / 2f,
            squareLeft = outer - squareSide / 2f,
            squareTop = outer - squareSide / 2f,
            squareSide = squareSide
        )
    }
    fun hsvOf(c: Color): FloatArray {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.rgb(
                (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()
            ), hsv
        )
        return hsv
    }
    fun pickColorAt(position: Offset, diameterPx: Float) {
        val g = geometry(diameterPx)
        val hsv = hsvOf(latestColor)
        val hue = hsv[0]
        val sat = hsv[1]
        val value = hsv[2]
        val inSquare = position.x in g.squareLeft..(g.squareLeft + g.squareSide) &&
            position.y in g.squareTop..(g.squareTop + g.squareSide)
        val picked = if (inSquare) {
            // Kotak SV: x = saturasi (kiri 0 -> kanan 1), y = value (atas 1 -> bawah 0).
            val s = ((position.x - g.squareLeft) / g.squareSide).coerceIn(0f, 1f)
            val v = (1f - (position.y - g.squareTop) / g.squareSide).coerceIn(0f, 1f)
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v)))
        } else {
            // Cincin hue: sudut dari timur (atan2, y ke bawah = searah jarum jam),
            // diubah ke sudut dari atas, lalu ke hue (lihat pemetaan di atas).
            val thetaDeg = Math.toDegrees(
                atan2(
                    (position.y - g.center.y).toDouble(),
                    (position.x - g.center.x).toDouble()
                )
            )
            val fromTop = ((thetaDeg + 90.0 + 360.0) % 360.0).toFloat()
            val newHue = (360f - fromTop) % 360f
            Color(android.graphics.Color.HSVToColor(floatArrayOf(newHue, sat, value)))
        }
        latestOnChange(picked)
    }

    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(260.dp).pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull { it.pressed }?.let { change ->
                            pickColorAt(change.position, size.width.toFloat())
                            change.consume()
                        }
                    }
                }
            }
        ) {
            val d = size.width
            val g = geometry(d)
            val hsv = hsvOf(color)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]
            val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            // Cincin hue: warna mulai dari timur (270) searah jarum jam.
            val hueStops = listOf(270f, 240f, 180f, 120f, 60f, 30f, 0f, 300f, 270f)
                .map { h -> Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 1f, 1f))) }
            drawArc(
                brush = Brush.sweepGradient(hueStops, center = g.center),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(g.center.x - g.outerRadius, g.center.y - g.outerRadius),
                size = Size(d, d),
                style = Stroke(width = g.ringWidth)
            )
            // Latar putih di tengah cincin (ruang kotak SV).
            drawCircle(color = Color.White, radius = g.outerRadius - g.ringWidth, center = g.center)
            // Kotak saturasi/value: putih -> hue, lalu transparan -> hitam.
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.White, hueColor)),
                topLeft = Offset(g.squareLeft, g.squareTop),
                size = Size(g.squareSide, g.squareSide)
            )
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                topLeft = Offset(g.squareLeft, g.squareTop),
                size = Size(g.squareSide, g.squareSide)
            )
            // Indikator cincin: lingkaran warna terpilih + bingkai putih.
            val ringAngleRad = Math.toRadians(((360f - hue) % 360f - 90f).toDouble())
            val ringCenter = Offset(
                g.center.x + cos(ringAngleRad).toFloat() * g.ringRadius,
                g.center.y + sin(ringAngleRad).toFloat() * g.ringRadius
            )
            val ringDotRadius = g.ringWidth * 0.62f
            drawCircle(color = color, radius = ringDotRadius, center = ringCenter)
            drawCircle(
                color = Color.White, radius = ringDotRadius, center = ringCenter,
                style = Stroke(width = 2.5.dp.toPx())
            )
            // Indikator kotak: lingkaran kecil warna terpilih + bingkai putih.
            val squareDot = Offset(
                g.squareLeft + sat * g.squareSide,
                g.squareTop + (1f - value) * g.squareSide
            )
            drawCircle(color = color, radius = 7.dp.toPx(), center = squareDot)
            drawCircle(
                color = Color.White, radius = 7.dp.toPx(), center = squareDot,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

/** Geometri wheel dalam piksel — dipakai bersama oleh gambar & hitung sentuhan. */
private data class WheelGeometry(
    val center: Offset,
    val outerRadius: Float,
    val ringWidth: Float,
    val ringRadius: Float,
    val squareLeft: Float,
    val squareTop: Float,
    val squareSide: Float
)
