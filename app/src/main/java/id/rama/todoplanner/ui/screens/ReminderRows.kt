package id.rama.todoplanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import id.rama.todoplanner.ui.components.DailyReminderDefaultColor
import id.rama.todoplanner.ui.components.PastelPill
import id.rama.todoplanner.ui.components.activityColor
import id.rama.todoplanner.ui.components.darkenColor
import id.rama.todoplanner.ui.theme.GreenDeep
import id.rama.todoplanner.ui.theme.Ink
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

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
            // RONDE 10: glow DIPERKUAT supaya benar-benar terlihat (Rama:
            // dot card Schedule Warning "kurang Glow") — cincin lebih
            // tebal & lebih pekat + halo lembut yang ikut mengembang.
            // Cincin mengembang 10 -> 30 dp sambil memudar ke transparan.
            val ringSize = (10f + progress * 20f).dp
            Box(
                modifier = Modifier.size(ringSize).clip(CircleShape)
                    .background(color.copy(alpha = (1f - progress) * 0.22f))
                    .border(2.5.dp, color.copy(alpha = (1f - progress) * 0.9f), CircleShape)
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
    today: LocalDate,
    // RONDE 9 (amendemen 6): warna pill "Daily Reminder" global; default =
    // hijau lama supaya pemanggil lama yang belum meneruskan profil tetap benar.
    dailyReminderColor: Color = DailyReminderDefaultColor
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
            // RONDE 9: baris ini TETAP dipakai Schedule Warning & layar
            // Reminder — ✨ di sini TIDAK berubah; yang diganti lingkaran
            // hanya card Ritual Beranda (RitualRow di bawah).
            Text("✨", fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
        } else {
            // RONDE 8: dot kategori EXPANDING GLOW (dot diam, cincin mengembang).
            // RONDE 9: dot mengikuti warna custom user (activityColor).
            ExpandingGlowCategoryDot(activityColor(activity))
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
                    PastelPill(
                        "Daily Reminder",
                        dailyReminderColor.copy(alpha = 0.20f),
                        darkenColor(dailyReminderColor)
                    )
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

/**
 * RONDE 9 — hitung mundur ke bunyi berikutnya (dipakai HANYA card Ritual):
 *  - bunyi berikutnya BUKAN hari ini -> "Besok HH:mm" (atau tanggal singkat
 *    bila lebih jauh lagi — pertahanan, karena trigger mingguan bisa > besok).
 *  - hari ini & >= 1 jam -> "X jam Y mnt lagi"; >= 1 menit -> "Z mnt lagi";
 *    < 1 menit -> "W dtk lagi"; sudah lewat -> "Sebentar lagi".
 * Format jam memakai titik (HH.mm) seperti seluruh app, bukan titik dua.
 */
fun formatCountdown(trigger: LocalDateTime, now: LocalDateTime, today: LocalDate): String {
    val date = trigger.toLocalDate()
    if (date != today) {
        val jam = trigger.format(DateTimeFormatter.ofPattern("HH.mm"))
        return if (date == today.plusDays(1)) "Besok $jam"
        else "${date.format(Dates.shortDate)} $jam"
    }
    val seconds = Duration.between(now, trigger).seconds
    if (seconds <= 0) return "Sebentar lagi"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "$hours jam $minutes mnt lagi"
        minutes > 0 -> "$minutes mnt lagi"
        else -> "$secs dtk lagi"
    }
}

/**
 * RONDE 9 — baris card Ritual Beranda (MENGGANTIKAN NextReminderRow di
 * card ini saja; Schedule Warning & layar Reminder tetap memakai
 * NextReminderRow dengan ✨-nya):
 *  - PALING KIRI rapat: judul kegiatan + pill "Daily Reminder" (warna
 *    pill = warna global amendemen 6, bukan hijau hardcoded).
 *  - PALING KANAN: hitung mundur ke bunyi berikutnya (diperbarui tiap
 *    detik oleh loop lokal baris ini — bukan tick 30 detik Beranda —
 *    supaya "W dtk lagi" benar-benar bergerak).
 *  - Logo ✨ DIGANTI lingkaran checklist (seperti Todo Utama): lingkaran
 *    terisi hijau + ✓ bila hari ini sudah pernah ditandai; KLIK lingkaran
 *    membuka MENU AKSI yang sama seperti Todo Utama di Detail:
 *    Edit, Hapus (dengan konfirmasi), Tandai Selesai.
 *  - Subline tetap: interval • kategori • terakhir ditandai (tanpa ✨
 *    di ekor subline — logo sparkles card ini sudah diganti lingkaran;
 *    ✨ di Schedule Warning/Detail/lainnya tidak berubah).
 *  - Ketuk baris (di luar lingkaran) = buka Detail, seperti baris Todo Utama.
 */
@Composable
fun RitualRow(
    activity: ActivityEntity,
    date: LocalDate,
    trigger: LocalDateTime,
    today: LocalDate,
    dailyReminderColor: Color,
    onOpenDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMarkDone: () -> Unit
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(1_000) } }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val markedToday = activity.lastDailyCompletion(today.toEpochDay()) != null
    val last = activity.lastDailyCompletion(date.toEpochDay())

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpenDetail() }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Lingkaran checklist — klik = menu aksi (bukan langsung tandai).
        Box {
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape)
                    .background(if (markedToday) GreenDeep else Color.Transparent)
                    .border(2.dp, GreenDeep, CircleShape)
                    .clickable { showMenu = true },
                contentAlignment = Alignment.Center
            ) {
                if (markedToday) {
                    Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("✏️ Edit") },
                    onClick = { showMenu = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("🗑️ Hapus") },
                    onClick = { showMenu = false; showDeleteConfirm = true }
                )
                DropdownMenuItem(
                    text = { Text("✨ Tandai Selesai") },
                    onClick = { showMenu = false; onMarkDone() }
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        // Judul + pill rapat ke PALING KIRI (kolom weight 1 mendorong
        // countdown ke sisi paling kanan baris).
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    activity.displayTitle, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
                    color = Ink, modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                PastelPill(
                    "Daily Reminder",
                    dailyReminderColor.copy(alpha = 0.20f),
                    darkenColor(dailyReminderColor)
                )
            }
            Text(
                "tiap ${activity.intervalLabel()} • ${activity.category}" +
                    if (last != null) " • terakhir ditandai ${Dates.formatCompletedTime(last)}" else "",
                fontSize = 12.sp, color = Color(0xFF70858E)
            )
        }
        // Countdown di SISI PALING KANAN card (di luar kolom kiri).
        Spacer(Modifier.width(8.dp))
        Text(
            formatCountdown(trigger, now, today),
            fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Ink,
            maxLines = 1
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Hapus kegiatan ini?") },
            text = { Text(activity.title) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Batal") } }
        )
    }
}
