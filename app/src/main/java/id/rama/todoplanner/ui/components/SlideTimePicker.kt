package id.rama.todoplanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.ui.theme.Ink

/**
 * RONDE 5 — pemilih jam BARU menggantikan dial 360° Material (bukti
 * uji5-picker-jam.jpg: dial dinilai tidak nyaman).
 *
 * Modelnya SLIDE VERTIKAL per kolom (Jam 00–23, Menit 00–59):
 *  - Geser kolom ke atas/bawah: nilai berubah LANGKAH DEMI LANGKAH dengan
 *    ambang tetap (snap per langkah, tanpa nilai setengah) dan berhenti di
 *    batas rentang (clamp, tidak berputar balik).
 *  - Ketuk nilai tetangga (di atas/di bawah nilai aktif) = geser 1 langkah.
 *  - Ketuk nilai yang SEDANG AKTIF sekali lagi = kolom berubah menjadi
 *    input KETIK dengan keyboard NUMERIK saja (KeyboardType.Number);
 *    nilai dikonfirmasi dengan tombol OK / aksi Done keyboard, divalidasi
 *    rentang + clamp (di luar rentang -> dipaksa ke batas terdekat).
 * Tombol dialog tetap Batal / Pilih (sama seperti picker lama).
 */

@Composable
fun SlideTimePickerDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (minutesOfDay: Int) -> Unit
) {
    var hour by remember { mutableStateOf((initialMinutes / 60).coerceIn(0, 23)) }
    var minute by remember { mutableStateOf((initialMinutes % 60).coerceIn(0, 59)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SlideColumn(
                        label = "Jam", value = hour, max = 23,
                        onValueChange = { hour = it }
                    )
                    Text(
                        ":", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    SlideColumn(
                        label = "Menit", value = minute, max = 59,
                        onValueChange = { minute = it }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Geser kolom ↕ • ketuk angka tengah untuk mengetik",
                    fontSize = 11.5.sp, color = Color(0xFF70858E)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour * 60 + minute) }) { Text("Pilih") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/**
 * Satu kolom slide: menampilkan nilai sebelum, nilai AKTIF (besar, di dalam
 * bingkai), dan nilai sesudah. Seretan vertikal diakumulasi; tiap 34dp yang
 * terlampaui = 1 langkah (snap), arah: seret ke atas = nilai naik.
 */
@Composable
private fun SlideColumn(
    label: String,
    value: Int,
    max: Int,
    onValueChange: (Int) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var dragAccum by remember { mutableStateOf(0f) }
    // Nilai terbaru yang selalu segar di dalam gesture handler (pointerInput
    // tidak dibuat ulang tiap nilai berubah).
    val latestValue by androidx.compose.runtime.rememberUpdatedState(value)
    val latestOnChange by androidx.compose.runtime.rememberUpdatedState(onValueChange)

    fun commitEdit() {
        val parsed = editText.toIntOrNull()
        if (parsed != null) onValueChange(parsed.coerceIn(0, max))
        editing = false
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF58707C))
        Spacer(Modifier.height(6.dp))
        if (editing) {
            // Mode KETIK: keyboard numerik saja + validasi rentang & clamp.
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it.filter { ch -> ch.isDigit() }.take(2) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { commitEdit() }),
                modifier = Modifier.width(84.dp)
            )
            TextButton(onClick = { commitEdit() }) { Text("OK (0–$max)") }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF4F9FB))
                    .pointerInput(max) {
                        detectVerticalDragGestures(
                            onDragStart = { dragAccum = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                dragAccum += dragAmount
                                val stepPx = 34f
                                var v = latestValue
                                // Snap per langkah: seret ke atas (negatif) = naik,
                                // seret ke bawah (positif) = turun; clamp di rentang.
                                while (dragAccum <= -stepPx) {
                                    v = (v + 1).coerceIn(0, max)
                                    dragAccum += stepPx
                                }
                                while (dragAccum >= stepPx) {
                                    v = (v - 1).coerceIn(0, max)
                                    dragAccum -= stepPx
                                }
                                if (v != latestValue) latestOnChange(v)
                            }
                        )
                    }
                    .padding(vertical = 6.dp, horizontal = 10.dp)
            ) {
                NeighborValue(value - 1, max) { onValueChange((value - 1).coerceIn(0, max)) }
                // Nilai AKTIF: ketuk sekali lagi -> mode ketik numerik.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.5.dp, Color(0xFF6FB3DC), RoundedCornerShape(10.dp))
                        .clickable {
                            editText = "%02d".format(value)
                            editing = true
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("%02d".format(value), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                NeighborValue(value + 1, max) { onValueChange((value + 1).coerceIn(0, max)) }
            }
        }
    }
}

/** Nilai tetangga redup di atas/bawah nilai aktif; di luar rentang tampil "––". */
@Composable
private fun NeighborValue(v: Int, max: Int, onClick: () -> Unit) {
    val valid = v in 0..max
    Text(
        if (valid) "%02d".format(v) else "––",
        fontSize = 17.sp,
        color = if (valid) Color(0xFF9FB0B8) else Color(0xFFD8E2E8),
        modifier = Modifier
            .clickable(enabled = valid) { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}
