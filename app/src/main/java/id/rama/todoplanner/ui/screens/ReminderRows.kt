package id.rama.todoplanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.data.ActivityEntity
import id.rama.todoplanner.domain.Dates
import id.rama.todoplanner.ui.components.CategoryColors
import id.rama.todoplanner.ui.components.PastelPill
import id.rama.todoplanner.ui.theme.Ink
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * RONDE 8 — baris "Schedule Warning / Mendatang" (penanda Daily Reminder kini ✨ telanjang,
 * dot Todo Utama kini ExpandingGlow; pembaruan RONDE 5 di bawah dipertahankan)
 * RONDE 5 — baris "Schedule Warning / Mendatang" yang dipakai BERSAMA oleh
 * kartu Beranda dan layar Reminder (satu kartu, dua desain baris):
 *  - TODO UTAMA: titik warna kategori + judul "Hari ini HH.mm — Judul" +
 *    sublabel "15 mnt sebelum • Kategori" (gaya mockup layar Reminder).
 *  - DAILY REMINDER: ikon ✓ dalam lingkaran hijau + pill "Daily Reminder" +
 *    sublabel "tiap {interval} • Kategori".
 * Urutan & isi daftar ditentukan pemanggil (nextReminders: 1 entri per
 * kegiatan, urut waktu trigger berikutnya, occurrence selesai tersingkir).
 */

fun reminderDayPrefix(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hari ini"
    today.plusDays(1) -> "Besok"
    else -> date.format(Dates.shortDate)
}

fun todoReminderLabel(activity: ActivityEntity): String = when (activity.reminderMinutesBefore) {
    15 -> "15 mnt sebelum"
    60 -> "1 jam sebelum"
    1440 -> "1 hari sebelum"
    0 -> "Pas jam mulai"
    null -> "Tanpa reminder"
    else -> "${activity.reminderMinutesBefore} mnt sebelum"
}

/**
 * RONDE 8 — titik kategori EXPANDING GLOW untuk baris Todo Utama:
 * dot TETAP DIAM; sebuah cincin/halo cahaya warna kategori MENGEMBANG
 * keluar dari dot lalu MEMUDAR hingga transparan, berulang (efek
 * ripple/sonar). Menggantikan animasi skala ronde 7 yang dinilai
 * salah oleh Rama. Bila animator sistem mati (reduce motion / skala
 * animator 0), dot tampil statis dengan halo lembut tanpa animasi.
 */
@Composable
fun ExpandingGlowCategoryDot(color: Color) {
    val context = LocalContext.current
    val animatorScale = try {
        android.provider.Settings.Global.getFloat(
            context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        )
    } catch (e: Exception) { 1f }
    val reduceMotion = animatorScale == 0f
    val progress: Float = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "expandingGlowDot")
        val animated by transition.animateFloat(
            initialValue = 0f, targetValue = 1f, label = "expandingGlowProgress",
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2000),
                repeatMode = RepeatMode.Restart
            )
        )
        animated
    }
    Box(modifier = Modifier.padding(top = 2.dp).size(30.dp), contentAlignment = Alignment.Center) {
        if (reduceMotion) {
            // Halo statis lembut, tanpa gerak.
            Box(
                modifier = Modifier.size(18.dp).clip(CircleShape)
                    .background(color.copy(alpha = 0.28f))
            )
        } else {
            // Cincin mengembang 10 -> 30 dp sambil memudar ke transparan.
            val ringSize = (10f + progress * 20f).dp
            Box(
                modifier = Modifier.size(ringSize).clip(CircleShape)
                    .border(2.dp, color.copy(alpha = (1f - progress) * 0.6f), CircleShape)
            )
        }
        // Dot diam di tengah — tidak pernah diskala.
        Box(
            modifier = Modifier.size(10.dp).clip(CircleShape).background(color)
        )
    }
}

@Composable
fun NextReminderRow(
    activity: ActivityEntity,
    date: LocalDate,
    trigger: LocalDateTime,
    today: LocalDate
) {
    val jam = trigger.format(DateTimeFormatter.ofPattern("HH.mm"))
    val headline = "${reminderDayPrefix(date, today)} $jam — ${activity.displayTitle}"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (activity.isChecklist) {
            // RONDE 8: penanda Daily Reminder = emoji SPARKLES TELANJANG —
            // tanpa lingkaran/chip latar sama sekali (koreksi atas wink ronde 7).
            Text("✨", fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
        } else {
            // RONDE 8: dot kategori EXPANDING GLOW (dot diam, cincin mengembang).
            ExpandingGlowCategoryDot(CategoryColors[activity.category] ?: Color.Gray)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    headline, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Ink,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (activity.isChecklist) {
                    Spacer(Modifier.width(6.dp))
                    PastelPill("Daily Reminder", Color(0xFFDFF5E9), Color(0xFF22714F))
                }
            }
            Text(
                if (activity.isChecklist) {
                    // RONDE 7: jam tampil di headline = bunyi berikutnya
                    // (penandaan terakhir + interval, dari domain); sebutkan
                    // penandaan terakhir bila sudah ada hari ini.
                    val last = activity.lastDailyCompletion(date.toEpochDay())
                    "tiap ${activity.intervalLabel()} • ${activity.category}" +
                        if (last != null) " • terakhir ditandai ${id.rama.todoplanner.domain.Dates.formatCompletedTime(last)} ✨" else ""
                } else {
                    "${todoReminderLabel(activity)} • ${activity.category}"
                },
                fontSize = 12.sp, color = Color(0xFF70858E)
            )
        }
    }
}
