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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * RONDE 5 — baris "Reminder Terdekat / Mendatang" yang dipakai BERSAMA oleh
 * kartu Beranda dan layar Reminder (satu kartu, dua desain baris):
 *  - TODO UTAMA: titik warna kategori + judul "Hari ini HH.mm — Judul" +
 *    sublabel "15 mnt sebelum • Kategori" (gaya mockup layar Reminder).
 *  - CHECKLIST: ikon ✓ dalam lingkaran hijau + pill "Checklist" +
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
            // Ikon ✓ checklist (bukan titik kategori)
            Box(
                modifier = Modifier.size(22.dp).clip(CircleShape)
                    .background(Color(0xFF58A97C)),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            // Titik warna kategori (mockup .catdot)
            Box(
                modifier = Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape)
                    .background(CategoryColors[activity.category] ?: Color.Gray)
                    .border(0.dp, Color.Transparent, CircleShape)
            )
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
                    PastelPill("Checklist", Color(0xFFDFF5E9), Color(0xFF22714F))
                }
            }
            Text(
                if (activity.isChecklist) {
                    "tiap ${activity.intervalLabel()} • ${activity.category}"
                } else {
                    "${todoReminderLabel(activity)} • ${activity.category}"
                },
                fontSize = 12.sp, color = Color(0xFF70858E)
            )
        }
    }
}
