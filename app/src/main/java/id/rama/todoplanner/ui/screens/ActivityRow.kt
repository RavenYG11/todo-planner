package id.rama.todoplanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.domain.Dates
import id.rama.todoplanner.domain.Occurrence
import id.rama.todoplanner.ui.components.CategoryColors
import id.rama.todoplanner.ui.components.SoftCard
import id.rama.todoplanner.ui.theme.GreenDeep

/**
 * Satu baris kegiatan persis bahasa mockup: garis aksen warna kategori di
 * tepi kiri + lingkaran centang kosong (hijau saat selesai + ✓) + judul
 * tebal + sub-info (jam • kategori • prioritas). Dipakai semua daftar.
 */
@Composable
fun ActivityRow(occ: Occurrence, onToggleDone: (Boolean) -> Unit, onClick: () -> Unit) {
    val accent = CategoryColors[occ.activity.category] ?: Color.Gray
    SoftCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onClick() }) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Garis aksen kategori (catbar mockup)
            Box(
                modifier = Modifier.width(4.dp).height(38.dp)
                    .clip(RoundedCornerShape(4.dp)).background(accent)
            )
            Spacer(Modifier.width(10.dp))
            // Lingkaran centang ala mockup (.check)
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape)
                    .background(if (occ.done) GreenDeep else Color.Transparent)
                    .border(2.dp, GreenDeep, CircleShape)
                    .clickable { onToggleDone(!occ.done) },
                contentAlignment = Alignment.Center
            ) {
                if (occ.done) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = occ.activity.title,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    textDecoration = if (occ.done) TextDecoration.LineThrough else null,
                    color = if (occ.done) Color(0xFF9FB0B8) else Color(0xFF22333D)
                )
                val end = occ.activity.endMinutes
                val timeText = if (end != null) {
                    "${Dates.formatTime(occ.activity.startMinutes)}–${Dates.formatTime(end)}"
                } else {
                    Dates.formatTime(occ.activity.startMinutes)
                }
                Text(
                    // RONDE 5: occurrence selesai menampilkan jam selesainya
                    // ("Selesai • HH:mm" dari timestamp; data lama: "Selesai").
                    text = "$timeText • ${occ.activity.category} • Prioritas ${occ.activity.priority}" +
                        if (occ.done) " • " + Dates.completedLabel(occ.completedAtMillis) else "",
                    fontSize = 12.sp,
                    color = if (occ.done) Color(0xFF22714F) else Color(0xFF70858E)
                )
                // RONDE 5: waktu selesai per occurrence (dari timestamp).
                if (occ.done) {
                    Text(
                        text = Dates.completedLabel(occ.completedAtMillis),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenDeep
                    )
                }
            }
        }
    }
}
