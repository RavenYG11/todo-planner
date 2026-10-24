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
import id.rama.todoplanner.ui.components.SoftCard
import id.rama.todoplanner.ui.components.activityColor
import id.rama.todoplanner.ui.components.priorityColor
import id.rama.todoplanner.ui.theme.GreenDeep

/**
 * Satu baris kegiatan persis bahasa mockup: garis aksen warna kategori di
 * tepi kiri + lingkaran centang kosong (hijau saat selesai + ✓) + judul
 * tebal + sub-info (jam • kategori • prioritas). Dipakai semua daftar.
 */
@Composable
fun ActivityRow(occ: Occurrence, onToggleDone: (Boolean) -> Unit, onClick: () -> Unit) {
    // RONDE 9: aksen kategori mengikuti warna custom user (activityColor).
    val accent = activityColor(occ.activity)
    // RONDE 8 — Daily Reminder: BUKAN state selesai. Penanda = emoji SPARKLES (✨, telanjang,
    // tanpa lingkaran latar) bila hari ini sudah pernah ditandai; menggantikan WINK ronde 7.
    // bila hari ini sudah pernah ditandai; judul TIDAK dicoret; daftar jam
    // penandaan hari itu ditampilkan. Todo Utama memakai semantik lama.
    val isDaily = occ.activity.isChecklist
    val shownDone = if (isDaily) false else occ.done
    val dailyTimes = if (isDaily) occ.dailyCompletions else emptyList()
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
            // Lingkaran penanda ala mockup (.check); RONDE 7: untuk Daily
            // Reminder isinya emoji WINK (bukan centang), ketuk = catat
            // penandaan baru (ViewModel membedakan semantiknya).
            if (isDaily) {
                // RONDE 8: penanda Daily Reminder = emoji SPARKLES TELANJANG —
                // tanpa lingkaran/border latar sama sekali; area ketuk tetap 24 dp.
                Box(
                    modifier = Modifier.size(24.dp)
                        .clickable { onToggleDone(true) },
                    contentAlignment = Alignment.Center
                ) {
                    if (dailyTimes.isNotEmpty()) Text("✨", fontSize = 16.sp)
                }
            } else {
                Box(
                    modifier = Modifier.size(24.dp).clip(CircleShape)
                        .background(if (shownDone) GreenDeep else Color.Transparent)
                        .border(2.dp, GreenDeep, CircleShape)
                        .clickable { onToggleDone(!occ.done) },
                    contentAlignment = Alignment.Center
                ) {
                    if (occ.done) {
                        Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = occ.activity.displayTitle,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    textDecoration = if (shownDone) TextDecoration.LineThrough else null,
                    color = if (shownDone) Color(0xFF9FB0B8) else Color(0xFF22333D)
                )
                val end = occ.activity.endMinutes
                val timeText = if (end != null) {
                    "${Dates.formatTime(occ.activity.startMinutes)}–${Dates.formatTime(end)}"
                } else {
                    Dates.formatTime(occ.activity.startMinutes)
                }
                if (isDaily) {
                    // RONDE 7: info Daily Reminder — interval + bunyi berikutnya
                    Text(
                        text = "Daily Reminder • tiap ${occ.activity.intervalLabel()} • ${occ.activity.category}",
                        fontSize = 12.sp, color = Color(0xFF70858E)
                    )
                    if (dailyTimes.isNotEmpty()) {
                        // RONDE 10: HANYA jam terakhir ditandai selesai
                        // (daftar lengkap ronde 7 dinilai menuh-menuhin).
                        Text(
                            text = "Terakhir ditandai: " + Dates.formatCompletedTime(dailyTimes.last()),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenDeep
                        )
                        val nextMs = dailyTimes.last() + (occ.activity.intervalMillis() ?: 0L)
                        Text(
                            text = "Reminder Berikutnya: " + Dates.formatCompletedTime(nextMs),
                            fontSize = 12.sp, color = Color(0xFF22714F)
                        )
                    }
                } else {
                    // RONDE 9 (amendemen 5): kata prioritas berwarna tetap
                    // (hijau/biru/merah), bukan warna custom kategori.
                    val subline = androidx.compose.ui.text.buildAnnotatedString {
                        append("$timeText • ${occ.activity.category} • ")
                        pushStyle(
                            androidx.compose.ui.text.SpanStyle(
                                color = priorityColor(occ.activity.priority),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        append("Prioritas ${occ.activity.priority}")
                        pop()
                        if (occ.done) append(" • " + Dates.completedLabel(occ.completedAtMillis))
                    }
                    Text(
                        // RONDE 5: occurrence selesai menampilkan jam selesainya
                        text = subline,
                        fontSize = 12.sp,
                        color = if (occ.done) Color(0xFF22714F) else Color(0xFF70858E)
                    )
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
}
