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
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
 * RONDE 7 — titik kategori BERDETAK untuk baris Todo Utama: GLOW (halo
 * lembut menyebar mengikuti warna kategori) + denyut skala pola detak
 * jantung lub-dub (dobel denyut lalu jeda panjang), infinite, BUKAN kedip
 * opacity. Bila animator sistem mati (reduce motion / skala animator 0),
 * titik tampil statis ber-glow tanpa animasi.
 */
@Composable
fun HeartbeatCategoryDot(color: Color) {
    val context = LocalContext.current
    val animatorScale = try {
        android.provider.Settings.Global.getFloat(
            context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        )
    } catch (e: Exception) { 1f }
    val reduceMotion = animatorScale == 0f
    val scale: Float = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "heartbeatDot")
        val animated by transition.animateFloat(
            initialValue = 1f, targetValue = 1f, label = "heartbeatScale",
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1300
                    1f at 0
                    1.38f at 110   // lub
                    1.08f at 230
                    1.30f at 350   // dub
                    1f at 500      // jeda panjang sampai siklus berikutnya
                    1f at 1300
                },
                repeatMode = RepeatMode.Restart
            )
        )
        animated
    }
    Box(modifier = Modifier.padding(top = 2.dp).size(20.dp), contentAlignment = Alignment.Center) {
        // Halo glow (ikut berdenyut, alpha rendah)
        Box(
            modifier = Modifier.size(18.dp).scale(scale).clip(CircleShape)
                .background(color.copy(alpha = 0.28f))
        )
        Box(
            modifier = Modifier.size(10.dp).scale(scale).clip(CircleShape).background(color)
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
            // RONDE 7: ikon Daily Reminder = lingkaran hijau berisi emoji WINK
            Box(
                modifier = Modifier.size(22.dp).clip(CircleShape)
                    .background(Color(0xFF58A97C)),
                contentAlignment = Alignment.Center
            ) {
                Text("😉", fontSize = 12.sp)
            }
        } else {
            // RONDE 7: titik kategori GLOW + BERDETAK (lub-dub)
            HeartbeatCategoryDot(CategoryColors[activity.category] ?: Color.Gray)
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
                        if (last != null) " • terakhir ditandai ${id.rama.todoplanner.domain.Dates.formatCompletedTime(last)} 😉" else ""
                } else {
                    "${todoReminderLabel(activity)} • ${activity.category}"
                },
                fontSize = 12.sp, color = Color(0xFF70858E)
            )
        }
    }
}
