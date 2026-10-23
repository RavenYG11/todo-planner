package id.rama.todoplanner.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.data.AppPrefs
import id.rama.todoplanner.domain.Dates
import id.rama.todoplanner.reminder.ToneResolver
import id.rama.todoplanner.ui.AppViewModel
import id.rama.todoplanner.ui.components.CategoryPill
import id.rama.todoplanner.ui.components.GradientButton
import id.rama.todoplanner.ui.components.SoftCard
import id.rama.todoplanner.ui.components.ringtoneTitle
import id.rama.todoplanner.ui.theme.Ink
import java.time.LocalDate

private fun repeatLabel(rule: String) = when (rule) {
    "DAILY" -> "Harian"; "WEEKLY" -> "Mingguan"; "MONTHLY" -> "Bulanan"; "YEARLY" -> "Tahunan"; else -> "Tidak"
}
private fun reminderLabel(min: Int?) = when (min) {
    // Perilaku ronde 1: setiap reminder aktif juga berbunyi pas jam mulai.
    null -> "Tanpa reminder"
    0 -> "Pas jam mulai" // data lama dari opsi "Tepat waktu" yang sudah dihapus
    15 -> "15 mnt sebelum + pas mulai"
    60 -> "1 jam sebelum + pas mulai"
    1440 -> "1 hari sebelum + pas mulai"
    else -> "$min mnt sebelum + pas mulai"
}

/**
 * Detail Kegiatan: isi lengkap + nada terpilih + selesai/tunda/edit/hapus.
 * RONDE 3 (permintaan Rama): "Tandai Selesai" dan "Tunda 1 Hari" — setelah
 * update BERHASIL — langsung kembali ke BERANDA ([onGoHome]), tidak lagi
 * diam di halaman Detail. Samasekali tidak mengubah Back HP (induk tetap).
 */
@Composable
fun DetailScreen(
    vm: AppViewModel,
    activityId: Long,
    dateEpochDay: Long,
    onEdit: () -> Unit,
    onAfterDelete: () -> Unit,
    onGoHome: () -> Unit
) {
    val activities by vm.activities.collectAsState()
    val profile by vm.profile.collectAsState()
    val context = LocalContext.current
    val activity = activities.firstOrNull { it.id == activityId }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF4F9FB))
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("📋 Detail Kegiatan", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))
        if (activity == null) {
            Text("Kegiatan tidak ditemukan (mungkin sudah dihapus).")
            return@Column
        }
        val date = LocalDate.ofEpochDay(dateEpochDay)
        val done = activity.isDoneOn(dateEpochDay)
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(activity.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                    CategoryPill(activity.category)
                }
                Spacer(Modifier.height(8.dp))
                DetailLine("Tanggal", date.format(Dates.fullDate))
                DetailLine("Jam", Dates.formatTime(activity.startMinutes) + " – " + Dates.formatTime(activity.endMinutes))
                DetailLine("Template", activity.template)
                DetailLine("Ulangi", repeatLabel(activity.repeatRule))
                DetailLine("Reminder", reminderLabel(activity.reminderMinutesBefore))
                DetailLine(
                    "Nada Dering",
                    when {
                        activity.ringtoneUri == AppPrefs.RINGTONE_DEFAULT -> "Bawaan HP"
                        activity.ringtoneUri != null -> ringtoneTitle(context, activity.ringtoneUri)
                        else -> ringtoneTitle(context, ToneResolver.effectiveToneUri(activity.ringtoneUri, profile.globalToneUri)) + " (global)"
                    }
                )
                DetailLine("Prioritas", activity.priority)
                DetailLine("Status", if (done) "Selesai ✓" else "Belum selesai")
                if (activity.note.isNotBlank()) DetailLine("Catatan", activity.note)
            }
        }
        Spacer(Modifier.height(14.dp))
        GradientButton(
            text = if (done) "↩️ Batalkan Selesai" else "✓ Tandai Selesai",
            onClick = { vm.toggleDone(activity, dateEpochDay, !done) { onGoHome() } },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { vm.snoozeActivity(activity) { onGoHome() } },
                modifier = Modifier.weight(1f)
            ) { Text("⏰ Tunda 1 Hari") }
            Spacer(Modifier.padding(4.dp))
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("✏️ Edit") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.fillMaxWidth()) {
            Text("🗑️ Hapus", color = Color(0xFFB3261E))
        }
    }

    if (showDeleteConfirm && activity != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Hapus kegiatan ini?") },
            text = { Text(activity.title) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.deleteActivity(activity)
                    onAfterDelete()
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = Color(0xFF5A6973))
        Text(value, modifier = Modifier.weight(2f))
    }
}
