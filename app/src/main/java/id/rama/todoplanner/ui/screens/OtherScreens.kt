package id.rama.todoplanner.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import id.rama.todoplanner.backup.BackupManager
import id.rama.todoplanner.domain.occurrencesBetween
import id.rama.todoplanner.domain.reminderDateTime
import id.rama.todoplanner.ui.AppViewModel
import id.rama.todoplanner.ui.Screen
import id.rama.todoplanner.ui.components.ToneField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/* ================= REMINDER (global) ================= */

@Composable
fun ReminderScreen(vm: AppViewModel) {
    val activities by vm.activities.collectAsState()
    val profile by vm.profile.collectAsState()
    val context = LocalContext.current
    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }

    val upcoming = remember(activities) {
        val now = LocalDateTime.now()
        activities.flatMap { a ->
            (0..13).mapNotNull { off ->
                val d = LocalDate.now().plusDays(off.toLong())
                a.reminderDateTime(d)?.let { t -> Triple(a.title, d, t) }
            }
        }.filter { it.third.isAfter(now) }.sortedBy { it.third }.take(10)
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("🔔 Reminder", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Izin Notifikasi", fontWeight = FontWeight.Bold)
                Text(if (notifGranted) "Aktif ✓" else "Belum diberikan")
                if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Button(onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Izinkan Notifikasi") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Nada Dering Reminder (global)", fontWeight = FontWeight.Bold)
        ToneField(currentUri = profile.globalToneUri, label = "Nada Dering Reminder") { uri, name ->
            vm.setGlobalTone(uri, name)
        }
        Spacer(Modifier.height(14.dp))
        Text("Reminder Terdekat", fontWeight = FontWeight.Bold)
        if (upcoming.isEmpty()) Text("Belum ada reminder terjadwal.")
        upcoming.forEach { (title, _, time) ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text("$title • ${time.format(DateTimeFormatter.ofPattern("d MMM HH:mm"))}", modifier = Modifier.padding(12.dp))
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

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("📊 Progress / Statistik", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        StatCard("Hari Ini", dayOccs.count { it.done }, dayOccs.size)
        StatCard("7 Hari Terakhir", weekOccs.count { it.done }, weekOccs.size)
        StatCard("Bulan Ini", monthOccs.count { it.done }, monthOccs.size)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🔥 Streak", fontWeight = FontWeight.Bold)
                Text("$streak hari beruntun semua kegiatan selesai")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Per Kategori (bulan ini)", fontWeight = FontWeight.Bold)
        monthOccs.groupBy { it.activity.category }.forEach { (cat, occs) ->
            Text("$cat: ${occs.count { it.done }}/${occs.size} selesai")
        }
    }
}

@Composable
private fun StatCard(title: String, done: Int, total: Int) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text("$done dari $total kegiatan selesai")
            if (total > 0) LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}

/* ================= PENGATURAN ================= */

@Composable
fun SettingsScreen(vm: AppViewModel, onNavigate: (Screen) -> Unit) {
    val profile by vm.profile.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showThemeDialog by remember { mutableStateOf(false) }

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

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("⚙️ Pengaturan", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SettingRow("👤 Profil & Personalisasi — Halo, ${profile.name}") { onNavigate(Screen.Profile) }
        SettingRow("🎨 Tema — ${profile.themeMode}") { showThemeDialog = true }
        SettingRow("🔔 Notifikasi & Reminder") { onNavigate(Screen.Reminder) }
        SettingRow("🎵 Nada Dering — ${profile.globalToneName}") { onNavigate(Screen.Reminder) }
        SettingRow("📄 Export PDF") { onNavigate(Screen.Export) }
        SettingRow("💾 Backup — Export Data") { exportLauncher.launch("todo-planner-backup.json") }
        SettingRow("📥 Import / Pulihkan Data") { importLauncher.launch(arrayOf("application/json")) }
        Card(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Tentang", fontWeight = FontWeight.Bold)
                Text("Todo Planner v1.0 — full lokal & offline.")
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
}

@Composable
private fun SettingRow(label: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onClick() }) {
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
        "🔔 Reminder" to Screen.Reminder,
        "📊 Progress / Statistik" to Screen.Stats,
        "⚙️ Pengaturan" to Screen.Settings,
        "👤 Profil & Personalisasi" to Screen.Profile,
        "📄 Export PDF" to Screen.Export
    )
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("📋 Semua", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        items.forEach { (label, target) -> SettingRow(label) { onNavigate(target) } }
        // Detail, Form & Preview PDF dijangkau dari daftar/kegiatan, bukan dari menu ini.
    }
}
