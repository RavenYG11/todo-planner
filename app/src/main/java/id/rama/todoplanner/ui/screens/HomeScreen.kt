package id.rama.todoplanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.domain.Dates
import id.rama.todoplanner.domain.DayPhase
import id.rama.todoplanner.domain.Occurrence
import id.rama.todoplanner.domain.occurrencesBetween
import id.rama.todoplanner.domain.nextReminders
import id.rama.todoplanner.ui.AppViewModel
import id.rama.todoplanner.ui.Screen
import id.rama.todoplanner.ui.components.AvatarView
import id.rama.todoplanner.ui.components.CategoryPill
import id.rama.todoplanner.ui.components.GradientButton
import id.rama.todoplanner.ui.components.activityColor
import id.rama.todoplanner.ui.components.priorityColor
import id.rama.todoplanner.ui.components.PhaseScenery
import id.rama.todoplanner.ui.components.PastelPill
import id.rama.todoplanner.ui.components.PriorityFadePill
import id.rama.todoplanner.ui.components.RingProgress
import id.rama.todoplanner.ui.components.SoftCard
import id.rama.todoplanner.ui.theme.Ink
import id.rama.todoplanner.ui.theme.SkyBlueDark
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Beranda / Hari Ini — RONDE 2: design-fidelity pass mengikuti mockup final
 * PERSIS (mockup-varian-1-revisi.html layar 1):
 *  - Header gradasi fajar + ILUSTRASI matahari terbit/awan/bukit (Canvas),
 *    sapaan + emoji fase hari + tagline dinamis per fase, chip jam pill kanan atas.
 *  - Kartu progres: ring gradasi tebal, % besar + caption "hari ini" di tengah
 *    ring, judul dinamis mengikuti %, pill Streak + pill aksen fase.
 *  - "Kegiatan Berikutnya" & "Schedule Warning" = kartu TERPISAH berikon judul;
 *    item kegiatan bergaris aksen kategori + lingkaran centang; reminder bullet
 *    jam + link "Lihat semua reminder".
 *  - Grid "Mulai dari Template" 2x2 seperti mockup.
 * Header tetap TANPA pintu ketuk ke Profil (keputusan Rama #82).
 */
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onNavigate: (Screen) -> Unit,
    onOpenDetail: (Occurrence) -> Unit,
    // RONDE 9: Beranda butuh jalur Edit langsung (menu aksi lingkaran
    // Ritual) tanpa lewat Detail dulu; TodoApp meneruskannya ke Form.
    onEditActivity: (Long) -> Unit = {}
) {
    val activities by vm.activities.collectAsState()
    val profile by vm.profile.collectAsState()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(30_000) } }

    val today = now.toLocalDate()
    val todays = remember(activities, today) { activities.occurrencesBetween(today, today) }
    val doneCount = todays.count { it.done }
    val total = todays.size
    val progress = if (total == 0) 0f else doneCount.toFloat() / total
    val pct = (progress * 100).toInt()
    // RONDE 9 — card "Kegiatan Hari Ini" (nama baru dari "Kegiatan
    // Berikutnya", poin 2): HANYA Todo Utama, TAPI kini menampilkan
    // SEMUA Todo Utama hari ini (bukan lagi take(2) yang belum selesai
    // saja) dengan MEKANISME SORTIR poin 3:
    //  - BELUM selesai dulu, paling mepet jamnya ke atas (jam mulai yang
    //    masih di depan didahulukan, lalu yang sudah lewat, masing-masing
    //    urut jam naik) — "mepet" dibaca dari jam mulai, bukan dari jam
    //    selesai (yang belum selesai belum punya jam selesai).
    //  - SUDAH selesai menumpuk di BAWAH, sesama selesai diurutkan
    //    berdasarkan WAKTU SELESAI naik (tanpa timestamp = paling akhir,
    //    lalu jam mulai). Begitu user mengetuk lingkaran "Selesai" pada
    //    sebuah baris, toggleDone mengubah done-nya dan baris itu PINDAH
    //    ke bawah secara otomatis lewat sortir ini (recompose dari Flow).
    // Berlaku untuk card ini saja; Ritual tidak berubah semantiknya.
    val upcoming = remember(todays, now) {
        val nowMin = now.hour * 60 + now.minute
        val todos = todays.filter { !it.activity.isChecklist }
        val undone = todos.filter { !it.done }.sortedWith(
            compareBy(
                { if ((it.activity.startMinutes ?: 0) >= nowMin) 0 else 1 },
                { it.activity.startMinutes ?: 0 }
            )
        )
        val done = todos.filter { it.done }.sortedWith(
            compareBy(
                { it.completedAtMillis ?: Long.MAX_VALUE },
                { it.activity.startMinutes ?: 0 }
            )
        )
        undone + done
    }
    // RONDE 4: SATU entri per kegiatan (kemunculan berikutnya saja) — dulu
    // ekspansi 14 hari membuat kegiatan harian mendominasi daftar.
    // RONDE 6: jendela Daily Reminder mengikuti setting hari aktif user (profile).
    // RONDE 8 — daftar mendatang dipecah dua di semua kartu Beranda:
    // Todo Utama -> "Kegiatan Berikutnya" & "Schedule Warning";
    // Daily Reminder -> kartu baru "Ritual" (di bawah). Layar Reminder
    // (OtherScreens) sudah terpisah sejak ronde 6, jadi tidak diubah
    // strukturnya — hanya emoji penandanya yang dikoreksi ke ✨.
    // Koreksi auditor ronde 8: limit diambil 10 lalu dipangkas per jenis
    // (bukan limit 3 campuran) — kalau tidak, 3 Todo Utama tercepat bisa
    // mendesak Daily Reminder keluar dari kartu Ritual (atau sebaliknya).
    val upcomingReminders = remember(activities, now, profile.dayStartMinutes, profile.dayEndMinutes) {
        activities.nextReminders(
            now, limit = 10,
            windowStart = java.time.LocalTime.of(profile.dayStartMinutes / 60, profile.dayStartMinutes % 60),
            windowEnd = java.time.LocalTime.of(profile.dayEndMinutes / 60, profile.dayEndMinutes % 60)
        )
    }
    val upcomingReminderTodos = upcomingReminders.filter { !it.first.isChecklist }.take(3)
    val upcomingRituals = upcomingReminders.filter { it.first.isChecklist }.take(3)
    // Streak: hari beruntun (mundur dari hari ini/kemarin) yang semua kegiatannya selesai & ada kegiatan.
    val streak = remember(activities, today) {
        var count = 0
        var d = today
        val todayOccs = activities.occurrencesBetween(d, d)
        if (!(todayOccs.isNotEmpty() && todayOccs.all { it.done })) d = d.minusDays(1)
        while (true) {
            val occs = activities.occurrencesBetween(d, d)
            if (occs.isNotEmpty() && occs.all { it.done }) { count++; d = d.minusDays(1) } else break
        }
        count
    }
    val zoneLabel = remember {
        try {
            java.time.ZoneId.systemDefault()
                .getDisplayName(TextStyle.SHORT, Locale("id", "ID")) ?: ""
        } catch (e: Exception) { "" }
    }
    val progressTitle = when {
        total == 0 -> "Belum ada kegiatan hari ini"
        pct >= 100 -> "Semua selesai hari ini 🎉"
        pct >= 50 -> "Hari ini hampir selesai"
        pct > 0 -> "Hari ini sedang berjalan"
        else -> "Hari ini baru mulai"
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF4F9FB))
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        // ===== Header + ilustrasi PER FASE HARI (ronde 3) =====
        // Dulu ilustrasi selalu fajar walau sudah malam. Kini gradasi + art + warna
        // teks mengikuti fase jam HP: PAGI fajar / SIANG biru terang / SORE sunset /
        // MALAM indigo-navy dengan bulan sabit & bintang (teks terang kontras).
        val phase = Dates.phaseOf(now.hour)
        val heroGradient = when (phase) {
            DayPhase.PAGI -> listOf(Color(0xFFCDEBFF), Color(0xFFEAF7FF), Color(0xFFFFE7CF), Color(0xFFFFEDDC))
            DayPhase.SIANG -> listOf(Color(0xFF9ED8FF), Color(0xFFCDEFFF), Color(0xFFEAF9FF), Color(0xFFFFF3D0))
            DayPhase.SORE -> listOf(Color(0xFFFFD9A0), Color(0xFFFFB37E), Color(0xFFF78FB0), Color(0xFFFFC9B8))
            DayPhase.MALAM -> listOf(Color(0xFF1D2742), Color(0xFF2E3A5C), Color(0xFF4A3A6B), Color(0xFF2A3358))
        }
        val heroInk = if (phase == DayPhase.MALAM) Color.White else Color(0xFF1E3A47)
        val heroSub = when (phase) {
            DayPhase.MALAM -> Color(0xFFC7D2E8)
            DayPhase.SORE -> Color(0xFF7A4450)
            else -> Color(0xFF58707C)
        }
        val chipBg = if (phase == DayPhase.MALAM) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.65f)
        val chipFg = when (phase) {
            DayPhase.MALAM -> Color(0xFFE8EEFA)
            DayPhase.SORE -> Color(0xFF8A3B2E)
            else -> Color(0xFF2B6E91)
        }
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(heroGradient))
        ) {
            Column {
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            today.format(Dates.fullDate), color = heroSub,
                            fontSize = 12.5.sp, modifier = Modifier.weight(1f)
                        )
                        // Chip jam pill kanan atas (mockup: latar putih 65%)
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                .background(chipBg)
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "🕐 " + now.format(DateTimeFormatter.ofPattern("HH.mm")) +
                                    if (zoneLabel.isNotBlank()) " $zoneLabel" else "",
                                color = chipFg, fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarView(profile.photoPath, profile.name, profile.emoji, profile.colorIndex, 46.dp)
                        Spacer(Modifier.width(11.dp))
                        Column {
                            Text(
                                "${Dates.greeting(now.hour)} ${Dates.phaseEmoji(now.hour)}",
                                color = heroSub, fontSize = 12.sp
                            )
                            Text(
                                "Halo, ${profile.name}!", color = heroInk,
                                fontSize = 21.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(Dates.phaseTagline(now.hour), color = heroSub, fontSize = 12.5.sp)
                }
                PhaseScenery(phase)
            }
        }
        Spacer(Modifier.height(12.dp))

        // ===== Kartu progres (mockup: ring tebal + judul dinamis + pill) =====
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RingProgress(progress, size = 96.dp, stroke = 10.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$pct%", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color(0xFF1E3A47))
                        Text("hari ini", fontSize = 9.5.sp, color = Color(0xFF7A8E97), fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(progressTitle, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = Ink)
                    Text(
                        if (total == 0) "Ketuk tombol + untuk menambah kegiatan pertamamu."
                        else "$doneCount dari $total kegiatan selesai." +
                            if (total - doneCount > 0) "\nSemangat, tinggal ${total - doneCount} lagi!" else "",
                        fontSize = 12.5.sp, color = Color(0xFF70858E)
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PastelPill("🔥 Streak $streak hari", Color(0xFFDFF5E9), Color(0xFF22714F))
                        PastelPill(Dates.phaseAccentChip(now.hour), Color(0xFFFCF0D8), Color(0xFF8A5E14))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ===== Kegiatan Hari Ini (RONDE 9: nama baru "Kegiatan
        // Berikutnya"; isi = semua Todo Utama hari ini + sortir poin 3) =====
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("⏰ Kegiatan Hari Ini", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                if (upcoming.isEmpty()) {
                    Text(
                        "Tidak ada kegiatan hari ini", fontSize = 12.5.sp,
                        color = Color(0xFF70858E), modifier = Modifier.padding(top = 6.dp)
                    )
                } else {
                    upcoming.forEach { occ ->
                        UpcomingItem(
                            occ,
                            onToggleDone = { vm.toggleDone(occ.activity, occ.date.toEpochDay(), it) },
                            onClick = { onOpenDetail(occ) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ===== Ritual (kartu BARU ronde 8 — HANYA Daily Reminder;
        // RONDE 9: barisnya jadi RitualRow — countdown kanan, judul+pill
        // kiri, lingkaran checklist bermenu aksi; ✨ di sini sudah diganti) =====
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🪄 Ritual", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                if (upcomingRituals.isEmpty()) {
                    Text(
                        "Belum ada Daily Reminder terjadwal",
                        fontSize = 12.5.sp, color = Color(0xFF70858E),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                } else {
                    upcomingRituals.forEach { (activity, date, time) ->
                        RitualRow(
                            activity = activity, date = date, trigger = time, today = today,
                            dailyReminderColor = profile.dailyReminderColor,
                            onOpenDetail = { onOpenDetail(Occurrence(activity, date)) },
                            onEdit = { onEditActivity(activity.id) },
                            onDelete = { vm.deleteActivity(activity) },
                            onMarkDone = { vm.toggleDone(activity, date.toEpochDay(), true) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ===== Schedule Warning (kartu TERPISAH, mockup; ronde 8: HANYA Todo Utama) =====
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🔔 Schedule Warning", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                if (upcomingReminderTodos.isEmpty()) {
                    Text(
                        "Belum ada pengingat terjadwal", fontSize = 12.5.sp,
                        color = Color(0xFF70858E), modifier = Modifier.padding(top = 6.dp)
                    )
                } else {
                    // RONDE 8: kartu ini HANYA Todo Utama; Daily Reminder
                    // sudah pindah ke kartu "Ritual" di atas.
                    // RONDE 9: pill Daily Reminder tidak ada di sini (hanya
                    // Todo Utama), tapi warna pill global tetap diteruskan
                    // supaya baris ini konsisten bila dipakai ulang.
                    upcomingReminderTodos.forEach { (activity, date, time) ->
                        NextReminderRow(activity, date, time, today, profile.dailyReminderColor)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Lihat Schedule Warning →", color = SkyBlueDark, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, modifier = Modifier.clickable { onNavigate(Screen.Reminder) }
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // RONDE 9 (poin 2a): section "Kegiatan Hari Ini" (daftar lengkap
        // seharian) DIHAPUS dari Beranda — datanya tetap tersimpan di
        // Room, riwayat penandaan tetap tampil di Detail, dan daftar
        // harian lengkap tetap ada di layar Template Harian. Card
        // "Kegiatan Hari Ini" di atas (bekas "Kegiatan Berikutnya")
        // adalah satu-satunya daftar kegiatan Beranda kini.

        // ===== Mulai dari Template (grid 2x2 mockup) =====
        Text("Mulai dari Template", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TemplateTile("🌤️", "Harian", "Time-block per jam + Daily Reminder", listOf(Color(0xFFFFD98A), Color(0xFFF6A56B)), Modifier.weight(1f)) { onNavigate(Screen.Daily) }
            TemplateTile("🗓️", "Mingguan", "Senin–Minggu satu pandangan", listOf(Color(0xFF8FD0F0), Color(0xFF6FB3DC)), Modifier.weight(1f)) { onNavigate(Screen.Weekly) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TemplateTile("📅", "Bulanan", "Grid bulan + daftar kegiatan", listOf(Color(0xFFB7A8E8), Color(0xFF9B8BD4)), Modifier.weight(1f)) { onNavigate(Screen.Monthly) }
            TemplateTile("🎯", "Tahunan", "12 bulan, target & resolusi", listOf(Color(0xFF8FD0A8), Color(0xFF58A97C)), Modifier.weight(1f)) { onNavigate(Screen.Yearly) }
        }
        Spacer(Modifier.height(12.dp))
        GradientButton(
            text = "📄 Export PDF",
            onClick = { onNavigate(Screen.Export) },
            modifier = Modifier.fillMaxWidth(),
            mint = true
        )
    }
}

/**
 * Item card "Kegiatan Hari Ini" (RONDE 9): garis aksen + lingkaran
 * centang + judul + pill kategori + sub-info (mockup .task).
 *  - RONDE 9: lingkaran kini BISA DIKETUK = toggle Selesai (dulu hanya
 *    hiasan); ketuk = baris pindah ke bawah lewat sortir card (poin 3).
 *    Baris selesai: lingkaran terisi hijau + ✓, judul dicoret abu-abu.
 *  - RONDE 9: aksen & pill kategori memakai warna EFEKTIF
 *    ([activityColor] = custom user bila ada, else warna kategori lama).
 *  - RONDE 9 (amendemen 5): kata prioritas di subline memakai warna
 *    prioritas TETAP ([priorityColor] — hijau/biru/merah), BUKAN warna
 *    custom kategori; segmen lain subline tetap abu-abu.
 */
@Composable
private fun UpcomingItem(occ: Occurrence, onToggleDone: (Boolean) -> Unit, onClick: () -> Unit) {
    val accent = activityColor(occ.activity)
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // RONDE 8: garis aksen NEON shimmer/shine sweep (lihat ShimmerAccentBar).
        ShimmerAccentBar(accent)
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.width(22.dp).height(22.dp)
                .clip(CircleShape)
                .background(
                    if (occ.done) id.rama.todoplanner.ui.theme.GreenDeep else Color.Transparent
                )
                .border(2.dp, id.rama.todoplanner.ui.theme.GreenDeep, CircleShape)
                .clickable { onToggleDone(!occ.done) },
            contentAlignment = Alignment.Center
        ) {
            if (occ.done) {
                Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    occ.activity.displayTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = if (occ.done) Color(0xFF9FB0B8) else Ink,
                    textDecoration = if (occ.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                CategoryPill(occ.activity.category, accent)
            }
            val end = occ.activity.endMinutes
            val timeText = if (end != null) {
                "${Dates.formatTime(occ.activity.startMinutes)}–${Dates.formatTime(end)}"
            } else {
                Dates.formatTime(occ.activity.startMinutes)
            }
            // RONDE 10: prioritas BUKAN lagi teks "Prioritas X" — jadi
            // LABEL berlatar persis label kategori + efek FADE IN & OUT,
            // teks hanya "Rendah"/"Sedang"/"Tinggi" (tanpa kata "Prioritas").
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$timeText • ${occ.activity.category}", fontSize = 12.sp, color = Color(0xFF70858E))
                Spacer(Modifier.width(6.dp))
                PriorityFadePill(occ.activity.priority)
                if (occ.done) {
                    val doneLabel = Dates.completedLabel(occ.completedAtMillis)
                    if (doneLabel.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text("• $doneLabel", fontSize = 12.sp, color = Color(0xFF70858E))
                    }
                }
            }
        }
    }
}

/**
 * RONDE 8 — garis aksen NEON dengan efek "shimmer/shine sweep": sapuan
 * cahaya gradasi SILVER yang bergerak melintasi garis, berulang, halus
 * (bukan kedip opacity). Cara kerja: garis warna kategori diam sebagai
 * dasar; sebuah segmen pendek bergradasi transparan–perak–transparan
 * meluncur dari atas ke bawah garis (siklus 2,2 detik, lalu mengulang).
 * Bila animator sistem mati (reduce motion), garis tampil statis tanpa
 * sapuan — efek tidak pernah memaksa gerak.
 */
@Composable
private fun ShimmerAccentBar(accent: Color) {
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
        val transition = rememberInfiniteTransition(label = "shimmerAccent")
        val animated by transition.animateFloat(
            initialValue = 0f, targetValue = 1f, label = "shimmerSweep",
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200),
                repeatMode = RepeatMode.Restart
            )
        )
        animated
    }
    Box(
        modifier = Modifier.width(4.dp).height(36.dp)
            .clip(RoundedCornerShape(4.dp)).background(accent)
    ) {
        if (!reduceMotion) {
            // Segmen cahaya 14 dp meluncur -14 -> +36 dp (keluar di kedua ujung,
            // jadi geraknya tampak mulus, bukan melompat).
            val sweepY = (-14f + progress * 50f).dp
            Box(
                modifier = Modifier.width(4.dp).height(14.dp)
                    .offset(y = sweepY)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xFFE8ECEF),
                                Color(0xFFF7F9FA),
                                Color(0xFFE8ECEF),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

/** Ubin template Beranda: pita gradasi atas + ikon + nama + deskripsi (mockup .tpl). */
@Composable
private fun TemplateTile(
    icon: String, title: String, desc: String, band: List<Color>,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    SoftCard(modifier = modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier.fillMaxWidth().height(6.dp)
                .background(Brush.horizontalGradient(band))
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Text(icon, fontSize = 22.sp)
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink, modifier = Modifier.padding(top = 4.dp))
            Text(desc, fontSize = 12.sp, color = Color(0xFF70858E))
        }
    }
}
