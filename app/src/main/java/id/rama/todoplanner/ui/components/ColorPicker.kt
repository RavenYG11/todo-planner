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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/**
 * RONDE 11 — pemilih warna, dipakai dua tempat:
 *  - Form Tambah/Edit: section "Pilih Warna" (preset + swatch mix + picker kotak).
 *  - Pengaturan: warna label "Daily Reminder" global (preset + picker yang sama;
 *    tombol kembalinya berarti "kembali ke hijau default", bukan "ikuti kategori").
 *
 * Aturan ronde 11 (MENGGANTIKAN circle wheel ronde 10 — wheel DIHAPUS total):
 *  - PRESET: deretan lingkaran warna; ketuk preset = warna itu LANGSUNG
 *    terpilih — picker TIDAK PERNAH muncul karena klik preset.
 *  - SWATCH MIX WARNA: satu lingkaran pelangi di ujung deretan preset;
 *    HANYA klik swatch inilah yang membuka picker kotak. Bila warna
 *    terpilih saat ini bukan preset (custom), swatch mix mendapat
 *    bingkai tebal sebagai penanda "custom aktif".
 *  - PICKER KOTAK (persis gambar referensi Rama): kotak saturasi/value
 *    besar + slider hue horizontal di bawahnya + tampilan hex & RGB.
 *    Pemilihan berlaku LANGSUNG saat ketuk/geser — TANPA tombol apply
 *    apa pun. Menggeser kotak mengubah saturasi/value (hue tetap);
 *    menggeser slider mengubah hue (saturasi/value tetap).
 *  - Tombol reset ("Ikuti warna kategori" / "Kembali ke hijau default")
 *    TETAP dipertahankan; menekannya juga menutup picker.
 *
 * Desain sengaja sederhana & mudah dipahami junior: picker tidak menyimpan
 * state warna sendiri — ia selalu menggambar dari warna terpilih saat ini
 * dan melaporkan setiap ketuk/geser lewat [onColorChange]; parent-lah yang
 * menyimpan pilihan (hex). Jadi tidak ada state ganda yang bisa beradu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetSwatchRow(
    selectedHex: String?,
    onSelect: (String) -> Unit,
    onMixClick: () -> Unit = {}
) {
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
        // RONDE 11 — swatch MIX WARNA (pelangi): satu-satunya jalan membuka
        // picker kotak. Bingkai tebal bila warna terpilih bukan preset.
        val mixSelected = selectedHex != null &&
            ColorPresets.none { it.equals(selectedHex, ignoreCase = true) }
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape)
                .background(Brush.sweepGradient(RainbowStops))
                .then(
                    if (mixSelected) Modifier.border(3.dp, Ink, CircleShape)
                    else Modifier.border(1.dp, Color(0xFFD5DEE3), CircleShape)
                )
                .clickable { onMixClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

/** Warna pelangi untuk swatch mix (sweep penuh kembali ke merah). */
private val RainbowStops: List<Color> = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
)

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
    // RONDE 11: picker kotak tertutup sampai user mengklik swatch MIX
    // (klik preset TIDAK PERNAH membukanya — preset langsung terpilih).
    var pickerVisible by remember { mutableStateOf(false) }

    Column {
        Text("Preset", fontSize = 12.sp, color = Color(0xFF70858E))
        PresetSwatchRow(
            selectedHex = selectedHex,
            onSelect = { hex ->
                onSelect(hex)
                pickerVisible = false
            },
            onMixClick = { pickerVisible = true }
        )
        if (pickerVisible) {
            Spacer(Modifier.padding(4.dp))
            Text("Warna campuran", fontSize = 12.sp, color = Color(0xFF70858E))
            val current = parseHexColor(selectedHex)
                ?: parseHexColor(categoryHex) ?: Color.Gray
            RectColorPicker(
                color = current,
                onColorChange = { onSelect(colorToHex(it)) }
            )
            Text(
                "Ketuk / geser di kotak (terang-gelap) atau slider hue — " +
                    "warna langsung berubah, tanpa tombol.",
                fontSize = 11.sp, color = Color(0xFF70858E)
            )
        }
        Row {
            TextButton(
                onClick = {
                    onFollowCategory()
                    pickerVisible = false
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
 * RONDE 11 — picker KOTAK persis gambar referensi Rama (MENGGANTIKAN
 * circle wheel ronde 10 yang DIHAPUS total):
 *  - Kotak saturasi/value besar: gradasi horizontal putih -> warna hue
 *    penuh, ditimpa gradasi vertikal transparan -> hitam; indikator
 *    lingkaran kecil berbingkai putih di posisi (saturasi, value).
 *  - Slider hue horizontal di bawah kotak: gradasi pelangi penuh;
 *    indikator lingkaran berisi warna terpilih di posisi hue.
 *  - Tampilan hex & RGB di bawah slider (hanya tampil, bukan input —
 *    pemilihan berlaku langsung saat ketuk/geser, tanpa tombol apply).
 *  - Interaksi: satu loop pointer mentah per area (down + gerak, tanpa
 *    menunggu ambang drag) supaya KETUK langsung melompat ke warna itu
 *    dan GESER mengalir. Warna terbaru selalu dibaca lewat
 *    [rememberUpdatedState] agar gestur yang sedang berjalan tidak
 *    dibatalkan oleh recomposition saat warna berubah.
 */
@Composable
fun RectColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestColor by rememberUpdatedState(color)
    val latestOnChange by rememberUpdatedState(onColorChange)

    fun hsvOf(c: Color): FloatArray {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.rgb(
                (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()
            ), hsv
        )
        return hsv
    }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        val hsv = hsvOf(color)
        val hue = hsv[0]
        val sat = hsv[1]
        val value = hsv[2]
        val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        // Kotak SV: ketuk/geser -> saturasi (x) & value (y, atas = 1).
        Canvas(
            modifier = Modifier.fillMaxWidth().aspectRatio(1.6f)
                .clip(RoundedCornerShape(12.dp)).pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull { it.pressed }?.let { change ->
                                val s = (change.position.x / size.width).coerceIn(0f, 1f)
                                val v = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                val h = hsvOf(latestColor)[0]
                                latestOnChange(
                                    Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
                                )
                                change.consume()
                            }
                        }
                    }
                }
        ) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.White, hueColor))
            )
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
            )
            val dot = Offset(sat * size.width, (1f - value) * size.height)
            drawCircle(color = color, radius = 11.dp.toPx(), center = dot)
            drawCircle(
                color = Color.White, radius = 11.dp.toPx(), center = dot,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
        Spacer(Modifier.padding(4.dp))
        // Slider hue: ketuk/geser -> hue (saturasi/value tetap).
        Canvas(
            modifier = Modifier.fillMaxWidth().height(22.dp)
                .clip(RoundedCornerShape(11.dp)).pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull { it.pressed }?.let { change ->
                                val newHue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                                val h = hsvOf(latestColor)
                                latestOnChange(
                                    Color(
                                        android.graphics.Color.HSVToColor(
                                            floatArrayOf(newHue, h[1], h[2])
                                        )
                                    )
                                )
                                change.consume()
                            }
                        }
                    }
                }
        ) {
            drawRect(brush = Brush.horizontalGradient(RainbowStops))
            val dotX = (hue / 360f) * size.width
            drawCircle(
                color = color, radius = 12.dp.toPx(),
                center = Offset(dotX, size.height / 2f)
            )
            drawCircle(
                color = Color.White, radius = 12.dp.toPx(),
                center = Offset(dotX, size.height / 2f),
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
        Spacer(Modifier.padding(4.dp))
        // Tampilan hex & RGB (bukan input — sesuai referensi yang
        // menampilkan nilai warna terpilih saat ini).
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp))
                    .background(color)
                    .border(1.dp, Color(0xFFD5DEE3), RoundedCornerShape(5.dp))
            )
            Spacer(Modifier.padding(4.dp))
            Text(
                "${colorToHex(color)}  •  R $r  G $g  B $b",
                fontSize = 12.sp, color = Color(0xFF70858E), fontWeight = FontWeight.SemiBold
            )
        }
    }
}
