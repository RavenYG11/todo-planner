package id.rama.todoplanner.ui.components

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.ui.theme.Ink

/**
 * RONDE 9 — pemilih warna, dipakai dua tempat:
 *  - Form Tambah/Edit: section "Pilih Warna" (preset + palette manual).
 *  - Pengaturan: warna label "Daily Reminder" global (hanya deretan preset).
 *
 * Desain sengaja sederhana & mudah dipahami junior:
 *  - PRESET: deretan lingkaran warna; ketuk = langsung terpilih.
 *  - PALETTE MANUAL: slider hue (0–360°, pelangi) + kolom hex. Slider
 *    hanya mengubah PRATINJAU; tombol "Pakai warna ini" yang mengunci
 *    pilihan (supaya geser tidak sengaja tidak mengubah data).
 *  - Hex ditulis 6 digit (mis. "FF5733" atau "#FF5733"); tidak valid =
 *    tombol pakai nonaktif + pesan kecil, tidak pernah crash.
 * Saturasi & value palette dikunci penuh (100%/100%) — satu slider saja
 * sudah memberi seluruh pelangi; cukup untuk kebutuhan "pilih warna
 * sendiri" tanpa membanjiri user dengan 3 slider.
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
    // Pratinjau palette: mulai dari warna terpilih saat ini.
    var hue by remember(selectedHex) {
        mutableStateOf(selectedHex?.let { hexToHue(it) } ?: 200f)
    }
    var hexText by remember(selectedHex) {
        mutableStateOf(selectedHex?.removePrefix("#")?.uppercase() ?: "")
    }
    val preview = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val hexValid = hexText.length == 6 && hexText.all { it.isDigit() || it in 'A'..'F' }

    Column {
        Text("Preset", fontSize = 12.sp, color = Color(0xFF70858E))
        PresetSwatchRow(selectedHex = selectedHex, onSelect = onSelect)
        Spacer(Modifier.padding(2.dp))
        Text("Warna bebas (palette)", fontSize = 12.sp, color = Color(0xFF70858E))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(preview)
                    .border(1.dp, Color(0xFFD5DEE3), CircleShape)
            )
            Spacer(Modifier.padding(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = hue, onValueChange = { hue = it },
                    valueRange = 0f..360f
                )
                Text("Geser untuk memilih warna pelangi", fontSize = 11.sp, color = Color(0xFF70858E))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = hexText,
                onValueChange = { hexText = it.filter { ch -> ch.isDigit() || ch in 'a'..'f' || ch in 'A'..'F' }.take(6).uppercase() },
                label = { Text("Kode hex (mis. FF5733)") },
                singleLine = true,
                prefix = { Text("#") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.padding(4.dp))
            TextButton(
                enabled = hexValid,
                onClick = { if (hexValid) onSelect("#$hexText") }
            ) { Text("Pakai hex") }
        }
        if (hexText.isNotEmpty() && !hexValid) {
            Text("Hex harus 6 digit (0–9, A–F).", color = Color.Red, fontSize = 12.sp)
        }
        Row {
            TextButton(onClick = { onSelect(colorToHex(preview)) }) { Text("Pakai warna ini") }
            TextButton(onClick = onFollowCategory) { Text(followLabel) }
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

/** Hue (0–360) dari hex — dipakai sebagai posisi awal slider palette. */
private fun hexToHue(hex: String): Float {
    val color = parseHexColor(hex) ?: return 200f
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.rgb(
            (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()
        ), hsv
    )
    return hsv[0]
}
