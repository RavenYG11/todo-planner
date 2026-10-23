package id.rama.todoplanner.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import id.rama.todoplanner.domain.Occurrence
import id.rama.todoplanner.ui.screens.CalendarScreen
import id.rama.todoplanner.ui.screens.DailyScreen
import id.rama.todoplanner.ui.screens.DetailScreen
import id.rama.todoplanner.ui.screens.ExportScreen
import id.rama.todoplanner.ui.screens.FormScreen
import id.rama.todoplanner.ui.screens.HomeScreen
import id.rama.todoplanner.ui.screens.MenuScreen
import id.rama.todoplanner.ui.screens.MonthlyScreen
import id.rama.todoplanner.ui.screens.PdfPreviewScreen
import id.rama.todoplanner.ui.screens.ProfileScreen
import id.rama.todoplanner.ui.screens.ReminderScreen
import id.rama.todoplanner.ui.screens.SettingsScreen
import id.rama.todoplanner.ui.screens.StatsScreen
import id.rama.todoplanner.ui.screens.WeeklyScreen
import id.rama.todoplanner.ui.screens.YearlyScreen
import id.rama.todoplanner.ui.theme.Coral
import id.rama.todoplanner.ui.theme.Peach
import id.rama.todoplanner.ui.theme.SkyBlue

/**
 * Akar UI: memegang layar aktif dan menjalankan aturan tombol Back HP.
 *
 * ATURAN BACK (keputusan Rama, mockup #81) — peta induk TETAP, bukan undo sesi:
 * menu top-level -> Beranda; Preview PDF -> Export; Profil -> Pengaturan; Detail -> daftar induk;
 * Form -> Beranda (konfirmasi bila terisi); Beranda -> tekan 2x keluar.
 * BackHandler di Compose berjalan di atas OnBackPressedDispatcher Android.
 */
@Composable
fun TodoApp(vm: AppViewModel) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var formDirty by remember { mutableStateOf(false) }
    var showFormExitConfirm by remember { mutableStateOf(false) }
    var lastHomeBackMs by remember { mutableLongStateOf(0L) }

    fun navigate(target: Screen) {
        if (target !is Screen.Form) formDirty = false
        screen = target
    }

    fun handleBack() {
        val current = screen
        when {
            current is Screen.Form && formDirty -> showFormExitConfirm = true
            current is Screen.Home -> {
                val now = System.currentTimeMillis()
                if (now - lastHomeBackMs < 2_000) {
                    (context as? Activity)?.finish()
                } else {
                    lastHomeBackMs = now
                    Toast.makeText(context, "Tekan Back sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
                }
            }
            else -> navigate(parentOf(current))
        }
    }
    BackHandler { handleBack() }

    // Izin notifikasi Android 13+ diminta sekali saat aplikasi pertama dibuka.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Audit ronde 2: tiap aplikasi dibuka, jadwalkan ulang semua reminder —
    // kegiatan lama (dibuat sebelum update) ikut pindah ke skema alarm terbaru.
    LaunchedEffect(Unit) { vm.rescheduleAllReminders() }

    // RONDE 4: notifikasi reminder (fullScreenIntent / ketukan) meminta buka
    // Detail kegiatan — navigasi ke Detail lalu permintaan dibersihkan.
    val pendingDetail by vm.pendingDetail.collectAsState()
    LaunchedEffect(pendingDetail) {
        pendingDetail?.let { (activityId, epochDay) ->
            navigate(Screen.Detail(activityId, epochDay))
            vm.consumePendingDetail()
        }
    }

    Scaffold(
        bottomBar = {
            if (screen !is Screen.Form && screen !is Screen.PdfPreview) {
                BottomBar(screen = screen, onNavigate = { navigate(it) }, onAdd = { navigate(Screen.Form()) })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            val openDetail: (Occurrence) -> Unit = { occ ->
                navigate(Screen.Detail(occ.activity.id, occ.date.toEpochDay()))
            }
            when (val s = screen) {
                is Screen.Home -> HomeScreen(vm, onNavigate = { navigate(it) }, onOpenDetail = openDetail)
                is Screen.Daily -> DailyScreen(vm, onOpenDetail = openDetail,
                    onAddAt = { date, hour -> navigate(Screen.Form(presetDateEpochDay = date, presetStartMinutes = hour, presetTemplate = "Harian")) },
                    onExport = { navigate(Screen.Export) })
                is Screen.Weekly -> WeeklyScreen(vm, onOpenDetail = openDetail,
                    onAddAt = { date -> navigate(Screen.Form(presetDateEpochDay = date, presetTemplate = "Mingguan")) },
                    onExport = { navigate(Screen.Export) })
                is Screen.Monthly -> MonthlyScreen(vm, onOpenDetail = openDetail,
                    onAddAt = { date -> navigate(Screen.Form(presetDateEpochDay = date, presetTemplate = "Bulanan")) },
                    onExport = { navigate(Screen.Export) })
                is Screen.Yearly -> YearlyScreen(vm, onOpenDetail = openDetail,
                    onAddAt = { date -> navigate(Screen.Form(presetDateEpochDay = date, presetTemplate = "Tahunan")) },
                    onExport = { navigate(Screen.Export) })
                is Screen.Calendar -> CalendarScreen(vm, onOpenDetail = openDetail,
                    onAddAt = { date -> navigate(Screen.Form(presetDateEpochDay = date)) })
                is Screen.Menu -> MenuScreen(onNavigate = { navigate(it) })
                is Screen.Reminder -> ReminderScreen(vm)
                is Screen.Stats -> StatsScreen(vm)
                is Screen.Settings -> SettingsScreen(vm, onNavigate = { navigate(it) })
                is Screen.Export -> ExportScreen(onPreview = { period, anchor, cats -> navigate(Screen.PdfPreview(period, anchor, cats)) })
                is Screen.PdfPreview -> PdfPreviewScreen(vm, period = s.period, anchorEpochDay = s.anchorEpochDay, categories = s.categories,
                    onBack = { navigate(Screen.Export) })
                is Screen.Detail -> DetailScreen(vm, activityId = s.activityId, dateEpochDay = s.dateEpochDay,
                    onEdit = { navigate(Screen.Form(editId = s.activityId)) },
                    onAfterDelete = { navigate(parentOf(s)) },
                    // RONDE 3: Tandai Selesai & Tunda 1 Hari -> langsung Beranda
                    // (navigate mengganti layar aktif; Detail ter-pop dari tumpukan
                    // logis dan bottom nav kembali menandai Beranda aktif).
                    onGoHome = { navigate(Screen.Home) })
                is Screen.Form -> FormScreen(vm, editId = s.editId, presetDateEpochDay = s.presetDateEpochDay,
                    presetStartMinutes = s.presetStartMinutes, presetTemplate = s.presetTemplate,
                    onDirtyChange = { formDirty = it },
                    onSaved = { navigate(parentOf(s)) })
                is Screen.Profile -> ProfileScreen(vm, onSaved = { navigate(Screen.Home) })
            }
        }
    }

    if (showFormExitConfirm) {
        AlertDialog(
            onDismissRequest = { showFormExitConfirm = false },
            title = { Text("Keluar tanpa menyimpan?") },
            text = { Text("Form sudah diisi.") },
            confirmButton = {
                TextButton(onClick = {
                    showFormExitConfirm = false
                    formDirty = false
                    navigate(Screen.Home)
                }) { Text("Keluar") }
            },
            dismissButton = { TextButton(onClick = { showFormExitConfirm = false }) { Text("Tetap di Sini") } }
        )
    }
}

@Composable
private fun BottomBar(screen: Screen, onNavigate: (Screen) -> Unit, onAdd: () -> Unit) {
    // Gaya mockup ronde 2: ikon berlabel, item aktif dalam pill pastel + label biru tebal,
    // tombol + tengah lebih besar, berbingkai putih dan berpendar (shadow coral).
    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE7EEF2)))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarItem("Beranda", "🏠", screen is Screen.Home, Modifier.weight(1f)) { onNavigate(Screen.Home) }
            BarItem("Harian", "🌤️", screen is Screen.Daily, Modifier.weight(1f)) { onNavigate(Screen.Daily) }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(54.dp)
                        .shadow(12.dp, CircleShape, ambientColor = Coral, spotColor = Coral)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Peach, Coral, SkyBlue)))
                        .border(3.dp, Color.White, CircleShape)
                        .clickable { onAdd() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add, contentDescription = "Tambah kegiatan",
                        tint = Color.White, modifier = Modifier.size(26.dp)
                    )
                }
            }
            BarItem("Kalender", "🗓️", screen is Screen.Calendar, Modifier.weight(1f)) { onNavigate(Screen.Calendar) }
            BarItem("Semua", "☰", screen is Screen.Menu, Modifier.weight(1f)) { onNavigate(Screen.Menu) }
        }
    }
}

@Composable
private fun BarItem(label: String, emoji: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // Ikon bergaya mockup = glif emoji (🏠 / 🌤️ / 🗓️ / ☰), bukan ikon Material polos.
    val tint = if (selected) Color(0xFF2B7BA6) else Color(0xFF7A8E97)
    Column(
        modifier = modifier.clickable { onClick() }.padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(16.dp))
                .background(if (selected) Color(0xFFE1F2FB) else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 20.sp)
        }
        Text(
            label, color = tint, fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
