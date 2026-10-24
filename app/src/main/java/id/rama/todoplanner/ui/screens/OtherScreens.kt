package id.rama.todoplanner.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import id.rama.todoplanner.backup.BackupManager
import id.rama.todoplanner.data.AppPrefs
import id.rama.todoplanner.domain.occurrencesBetween
import id.rama.todoplanner.domain.nextReminders
import id.rama.todoplanner.reminder.ReminderChannels
import id.rama.todoplanner.reminder.ReminderScheduler
import id.rama.todoplanner.reminder.ReminderSoundPlayer
import id.rama.todoplanner.ui.AppViewModel
import id.rama.todoplanner.ui.Screen
import id.rama.todoplanner.ui.components.CategoryColors
import id.rama.todoplanner.ui.components.GradientBar
import id.rama.todoplanner.ui.components.GradientButton
import id.rama.todoplanner.ui.components.PastelPill
import id.rama.todoplanner.ui.components.PeakIllustration
import id.rama.todoplanner.ui.components.RingProgress
import id.rama.todoplanner.ui.components.SoftCard
import id.rama.todoplanner.ui.components.ToneField
import id.rama.todoplanner.ui.theme.Ink
import id.rama.todoplanner.ui.theme.Mint
import id.rama.todoplanner.ui.theme.Peach
import id.rama.todoplanner.ui.theme.SkyBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/* ================= TOMBOL TES REMINDER (ronde 2) ================= */

/**
 * Dua tombol verifikasi mandiri Rama (dipakai di layar Reminder & Pengaturan):
 *  - "Tes Reminder 10 Detik": jadwalkan alarm tes +10 detik dengan nada global.
 *  - "Buka Pengaturan Notifikasi": deep link langsung ke pengaturan channel
 *    aktif (Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).
 */
@Composable
fun ReminderTestButtons(globalToneUri: String?) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    Column {
        // RONDE 3: tombol aksi utama memakai gradasi mockup, bukan biru Material polos.
        // RONDE 4: alarm tes berjalan lewat jalur yang PERSIS sama dengan
        // reminder asli (AlarmManager -> ReminderReceiver -> ReminderSoundService
        // -> ReminderSoundPlayer.play), dan Toast melaporkan hasVibrator HP ini.
        id.rama.todoplanner.ui.components.GradientButton(
            text = "🔔 Tes Reminder 10 Detik",
            onClick = {
                ReminderScheduler.scheduleTest(context, prefs)
                val hasVib = ReminderSoundPlayer.hasVibrator(context)
                Toast.makeText(
                    context,
                    "Tes dijadwalkan ±10 detik lagi (jalur play() asli) • hasVibrator: $hasVib — kunci layar sekarang",
                    Toast.LENGTH_LONG
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
            mint = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { openChannelSettings(context, globalToneUri) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("⚙️ Buka Pengaturan Notifikasi") }
        Spacer(Modifier.height(8.dp))
        // ADDENDUM RONDE 5: Tes Getar Langsung — one-shot 2 detik dari proses
        // UI (API berbeda dari jalur reminder) untuk bisect service-vs-device.
        OutlinedButton(
            onClick = {
                ReminderSoundPlayer.vibrateDirectTest(context)
                Toast.makeText(
                    context,
                    "Tes getar langsung 2 detik — rasakan HP-nya, lalu baca baris Diagnostik getar",
                    Toast.LENGTH_LONG
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("📳 Tes Getar Langsung (2 detik)") }
    }
}

/**
 * RONDE 4 — baris diagnostik getar: hasil upaya getar TERAKHIR yang disimpan
 * ReminderSoundPlayer ke AppPrefs (sukses/gagal + pesan + hasVibrator + jam).
 * Ditampilkan kecil di layar Reminder & Pengaturan agar uji HP berbasis bukti.
 */
@Composable
fun VibrationDiagnosticLine() {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    // Dibaca ulang tiap layar tersusun (layar ini dibuka lagi setelah tes bunyi).
    val diag = remember { prefs.vibrationDiagnostic }
    Text(
        "Diagnostik getar: " + if (diag.isBlank()) "belum ada upaya getar sejak update ini — jalankan Tes Reminder." else diag,
        fontSize = 11.sp, color = Color(0xFF70858E)
    )
}

private fun openChannelSettings(context: Context, toneUri: String?) {
    val channelId = ReminderChannels.ensureChannel(context, toneUri)
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback: pengaturan notifikasi aplikasi.
        try {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e2: Exception) {
            Toast.makeText(context, "Buka Pengaturan HP → Aplikasi → Todo Planner → Notifikasi", Toast.LENGTH_LONG).show()
        }
    }
}

/* ================= REMINDER (global) ================= */

/**
 * RONDE 5 — layar Reminder disamakan dengan mockup (layar 9 Reminder):
 *  1. Kartu "Mendatang": daftar reminder gaya mockup (titik warna kategori,
 *     judul "Hari ini HH.mm — ...", sublabel "15 mnt sebelum • Kategori");
 *     baris DAILY REMINDER dibedakan (ikon ✓ + pill "Daily Reminder" + "tiap {interval}").
 *  2. Kartu "⚙️ Pengaturan Reminder": badge status AKTIF untuk Suara & Getar
 *     (gaya .toggle mockup), baris nada global + tombol Ganti (ToneField).
 * Fitur nyata ronde sebelumnya DIPERTAHANKAN di dalam struktur ini:
 * Izin Notifikasi, Tes Reminder 10 Detik, Tes Getar Langsung (addendum),
 * Diagnostik getar, pemilih nada global, Buka Pengaturan Notifikasi.
 */
@Composable
fun ReminderScreen(vm: AppViewModel) {
    // RONDE 7 — layar ini kini bernama "Schedule Warning" dan isinya HANYA
    // dua section mendatang. Kartu Pengaturan Reminder + Tes & Diagnostik
    // PINDAH ke layar Pengaturan (SettingsScreen di file ini juga).
    val activities by vm.activities.collectAsState()
    val profile by vm.profile.collectAsState()

    // RONDE 4/5: SATU entri per kegiatan (kemunculan berikutnya saja),
    // urut waktu trigger; occurrence selesai tersingkir — dari domain.
    // RONDE 6: daftar dipecah dua section (Todo Utama vs Daily Reminder di bawah)
    // dan jendela Daily Reminder mengikuti setting hari aktif user.
    val upcoming = remember(activities, profile.dayStartMinutes, profile.dayEndMinutes) {
        activities.nextReminders(
            LocalDateTime.now(), limit = 20,
            windowStart = java.time.LocalTime.of(profile.dayStartMinutes / 60, profile.dayStartMinutes % 60),
            windowEnd = java.time.LocalTime.of(profile.dayEndMinutes / 60, profile.dayEndMinutes % 60)
        )
    }
    val upcomingTodos = upcoming.filter { !it.first.isChecklist }
    val upcomingChecklists = upcoming.filter { it.first.isChecklist }
    val today = LocalDate.now()

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF4F9FB))
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("🔔 Schedule Warning", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))

        // ===== RONDE 6: Section Todo Utama (sub-kartu terpisah) =====
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Mendatang — 📌 Todo Utama", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                if (upcomingTodos.isEmpty()) {
                    Text(
                        "Tidak ada todo utama terjadwal. Todo sekali jalan di masa depan akan muncul di sini.",
                        fontSize = 12.5.sp, color = Color(0xFF70858E), modifier = Modifier.padding(top = 6.dp)
                    )
                } else {
                    upcomingTodos.forEach { (activity, date, time) ->
                        NextReminderRow(activity, date, time, today)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ===== RONDE 6: Section Daily Reminder (sub-kartu terpisah) =====
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Mendatang — ✨ Daily Reminder", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                if (upcomingChecklists.isEmpty()) {
                    Text(
                        "Tidak ada Daily Reminder terjadwal. Daily Reminder ber-interval akan muncul di sini.",
                        fontSize = 12.5.sp, color = Color(0xFF70858E), modifier = Modifier.padding(top = 6.dp)
                    )
                } else {
                    upcomingChecklists.forEach { (activity, date, time) ->
                        NextReminderRow(activity, date, time, today)
                    }
                }
            }
        }
    }
}

/* ================= PROGRESS / STATISTIK ================= */

@Composable
fun StatsScreen(vm: AppViewModel) {
    val activities by vm.activities.collectAsState()
    val today = LocalDate.now()
    val dayOccs = remember(activities) { activities.occurrencesBetween(today, today) }
    val weekOccs = remember(activities) { activities.occurrencesBetween(today.minusDays(6), today) }
    val monthOccs = remember(activities) { activities.occurrencesBetween(today.withDayOfMonth(1), today) }
    // Streak: hari beruntun (mundur dari hari ini/kemarin) yang SEMUA kegiatannya selesai dan ada kegiatan.
    val streak = remember(activities) {
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
    fun pctOf(done: Int, total: Int) = if (total == 0) 0 else done * 100 / total
    val dayPct = pctOf(dayOccs.count { it.done }, dayOccs.size)
    val weekPct = pctOf(weekOccs.count { it.done }, weekOccs.size)
    val monthPct = pctOf(monthOccs.count { it.done }, monthOccs.size)

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF4F9FB))
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("📊 Progress / Statistik", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))

        // Kartu puncak ala mockup Statistik (ilustrasi gunung + matahari + bendera target)
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                PeakIllustration()
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Puncak capaianmu", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                    Text(
                        if (monthOccs.isEmpty()) "Belum ada kegiatan bulan ini — mulai pendakianmu hari ini."
                        else "Bulan ini $monthPct% kegiatan selesai (${monthOccs.count { it.done }}/${monthOccs.size}). Bendera = target 100%.",
                        fontSize = 12.5.sp, color = Color(0xFF70858E)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // Grid 2x2 mini seperti mockup: % besar + bar gradasi per periode
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatMini(
                title = "Harian", pct = dayPct,
                sub = "${dayOccs.count { it.done }}/${dayOccs.size} selesai",
                colors = listOf(SkyBlue, Peach), modifier = Modifier.weight(1f)
            )
            StatMini(
                title = "Mingguan", pct = weekPct,
                sub = "${weekOccs.count { it.done }}/${weekOccs.size} selesai",
                colors = listOf(Mint, Color(0xFF58A97C)), modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatMini(
                title = "Bulanan", pct = monthPct,
                sub = "${monthOccs.count { it.done }}/${monthOccs.size} selesai",
                colors = listOf(Color(0xFFB7A8E8), Color(0xFF9B8BD4)), modifier = Modifier.weight(1f)
            )
            SoftCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🔥 $streak hari", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("Streak berturut-turut\nsemua kegiatan selesai", fontSize = 12.sp, color = Color(0xFF70858E))
                    Spacer(Modifier.height(8.dp))
                    Box(contentAlignment = Alignment.Center) {
                        RingProgress(
                            if (monthOccs.isEmpty()) 0f else monthPct / 100f,
                            size = 44.dp, stroke = 5.dp
                        )
                        Text("$monthPct%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Per kategori: titik warna + % + bar warna kategori (mockup)
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Capaian per Kategori (bulan ini)", fontWeight = FontWeight.Bold, color = Ink)
                val byCat = monthOccs.groupBy { it.activity.category }
                if (byCat.isEmpty()) {
                    Text("Belum ada kegiatan bulan ini.", fontSize = 12.5.sp, color = Color(0xFF70858E), modifier = Modifier.padding(top = 6.dp))
                }
                byCat.forEach { (cat, occs) ->
                    val done = occs.count { it.done }
                    val pct = pctOf(done, occs.size)
                    val color = CategoryColors[cat] ?: Color.Gray
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text("$cat — $pct%", fontSize = 12.5.sp, color = Color(0xFF3D5561), modifier = Modifier.weight(1f))
                        Text("$done/${occs.size}", fontSize = 12.sp, color = Color(0xFF70858E))
                    }
                    GradientBar(pct / 100f, listOf(color.copy(alpha = 0.65f), color), Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}

@Composable
private fun StatMini(title: String, pct: Int, sub: String, colors: List<Color>, modifier: Modifier = Modifier) {
    SoftCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("$pct%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(title, fontSize = 12.5.sp, color = Color(0xFF3D5561))
            Text(sub, fontSize = 12.sp, color = Color(0xFF70858E))
            Spacer(Modifier.height(7.dp))
            GradientBar(pct / 100f, colors)
        }
    }
}

/* ================= PENGATURAN ================= */

@Composable
fun SettingsScreen(vm: AppViewModel, onNavigate: (Screen) -> Unit) {
    val profile by vm.profile.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // RONDE 7 — izin notifikasi kini dikelola di sini (ikut kartu Pengaturan
    // Reminder yang pindah dari layar Schedule Warning).
    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }
    var showThemeDialog by remember { mutableStateOf(false) }
    // RONDE 6: picker jendela hari aktif dari Pengaturan (selain layar Harian).
    var pickingWindow by remember { mutableStateOf<String?>(null) } // "start" | "end"

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val acts = withContext(Dispatchers.IO) { vm.activities.value }
                val tgts = withContext(Dispatchers.IO) { vm.targets.value }
                val json = BackupManager.exportJson(acts, tgts)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
                Toast.makeText(context, "Backup tersimpan ✓", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                    }
                    val (acts, tgts) = BackupManager.importJson(text)
                    vm.replaceAll(acts, tgts) {
                        Toast.makeText(context, "Data dipulihkan ✓", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "File backup tidak valid", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF4F9FB))
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("⚙️ Pengaturan", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        SettingRow("👤 Profil & Personalisasi — Halo, ${profile.name}") { onNavigate(Screen.Profile) }
        SettingRow("🎨 Tema — ${profile.themeMode}") { showThemeDialog = true }
        // RONDE 6: jendela hari aktif (slot Harian + jendela Daily Reminder).
        SettingRow("🕐 Hari aktif mulai — %02d.%02d".format(profile.dayStartMinutes / 60, profile.dayStartMinutes % 60)) { pickingWindow = "start" }
        SettingRow("🕐 Hari aktif selesai — %02d.%02d".format(profile.dayEndMinutes / 60, profile.dayEndMinutes % 60)) { pickingWindow = "end" }
        SettingRow("🔔 Schedule Warning") { onNavigate(Screen.Reminder) }
        // ===== RONDE 7: Kartu Pengaturan Reminder (PINDAHAN dari layar jadwal) =====
        SoftCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("⚙️ Pengaturan Reminder", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Suara notifikasi", modifier = Modifier.weight(1f), fontSize = 13.5.sp)
                    PastelPill("AKTIF", Color(0xFF58A97C), Color.White)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Getar", modifier = Modifier.weight(1f), fontSize = 13.5.sp)
                    PastelPill("AKTIF", Color(0xFF58A97C), Color.White)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Izin Notifikasi", modifier = Modifier.weight(1f), fontSize = 13.5.sp)
                    if (notifGranted) {
                        PastelPill("AKTIF", Color(0xFF58A97C), Color.White)
                    } else {
                        PastelPill("BELUM", Color(0xFFFDE9E4), Color(0xFFA63E2F))
                    }
                }
                if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Spacer(Modifier.height(8.dp))
                    GradientButton(
                        text = "Izinkan Notifikasi",
                        onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth(), mint = true
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Nada Dering Reminder (global) — ${profile.globalToneName}", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Ink)
                Spacer(Modifier.height(6.dp))
                ToneField(
                    currentUri = profile.globalToneUri, label = "Nada Dering Reminder",
                    showDefaultOption = true
                ) { uri, name ->
                    vm.setGlobalTone(uri, name)
                }
            }
        }
        // ===== RONDE 7: Kartu Tes & Diagnostik (PINDAHAN dari layar jadwal) =====
        SoftCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🧪 Tes & Diagnostik Reminder", fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(8.dp))
                ReminderTestButtons(globalToneUri = profile.globalToneUri)
                Spacer(Modifier.height(8.dp))
                VibrationDiagnosticLine()
            }
        }
        SettingRow("📄 Export PDF") { onNavigate(Screen.Export) }
        SettingRow("💾 Backup — Export Data") { exportLauncher.launch("todo-planner-backup.json") }
        SettingRow("📥 Import / Pulihkan Data") { importLauncher.launch(arrayOf("application/json")) }
        SoftCard(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Tentang", fontWeight = FontWeight.Bold, color = Ink)
                Text("Todo Planner v1.0.5 — full lokal & offline.")
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Tema") },
            text = {
                Column {
                    listOf("Terang", "Gelap", "Sistem").forEach { mode ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { vm.setThemeMode(mode); showThemeDialog = false }) {
                            RadioButton(selected = profile.themeMode == mode, onClick = { vm.setThemeMode(mode); showThemeDialog = false })
                            Text(mode)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Tutup") } }
        )
    }

    // RONDE 6: dialog picker jendela hari aktif.
    pickingWindow?.let { which ->
        id.rama.todoplanner.ui.components.SlideTimePickerDialog(
            title = if (which == "start") "Jam Mulai Hari Aktif" else "Jam Selesai Hari Aktif",
            initialMinutes = if (which == "start") profile.dayStartMinutes else profile.dayEndMinutes,
            onDismiss = { pickingWindow = null },
            onConfirm = { value ->
                if (which == "start") vm.setDayWindow(value, profile.dayEndMinutes)
                else vm.setDayWindow(profile.dayStartMinutes, value)
                pickingWindow = null
            }
        )
    }
}

@Composable
private fun SettingRow(label: String, onClick: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onClick() }) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Text("›")
        }
    }
}

/* ================= MENU / SEMUA LAYAR ================= */

@Composable
fun MenuScreen(onNavigate: (Screen) -> Unit) {
    val items = listOf(
        "🏠 Beranda / Hari Ini" to Screen.Home,
        "🌤️ Template Harian" to Screen.Daily,
        "🗓️ Template Mingguan" to Screen.Weekly,
        "📆 Template Bulanan" to Screen.Monthly,
        "🌅 Template Tahunan" to Screen.Yearly,
        "🗓️ Kalender" to Screen.Calendar,
        "🔔 Schedule Warning" to Screen.Reminder,
        "📊 Progress / Statistik" to Screen.Stats,
        "⚙️ Pengaturan" to Screen.Settings,
        "👤 Profil & Personalisasi" to Screen.Profile,
        "📄 Export PDF" to Screen.Export
    )
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF4F9FB))
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("📋 Semua", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        items.forEach { (label, target) -> SettingRow(label) { onNavigate(target) } }
        // Detail, Form & Preview PDF dijangkau dari daftar/kegiatan, bukan dari menu ini.
    }
}
