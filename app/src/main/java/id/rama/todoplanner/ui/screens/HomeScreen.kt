package id.rama.todoplanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.domain.Dates
import id.rama.todoplanner.domain.Occurrence
import id.rama.todoplanner.domain.occurrencesBetween
import id.rama.todoplanner.domain.reminderDateTime
import id.rama.todoplanner.ui.AppViewModel
import id.rama.todoplanner.ui.Screen
import id.rama.todoplanner.ui.components.AvatarView
import id.rama.todoplanner.ui.components.GradientButton
import id.rama.todoplanner.ui.components.RingProgress
import id.rama.todoplanner.ui.theme.Ink
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Beranda / Hari Ini. Header = sapaan bersih (avatar + sapaan jam HP + tanggal),
 * TANPA pintu ketuk ke Profil — Profil hanya dari Menu/Pengaturan (keputusan Rama #82).
 */
@Composable
fun HomeScreen(vm: AppViewModel, onNavigate: (Screen) -> Unit, onOpenDetail: (Occurrence) -> Unit) {
    val activities by vm.activities.collectAsState()
    val profile by vm.profile.collectAsState()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(30_000) } }

    val today = now.toLocalDate()
    val todays = remember(activities, today) { activities.occurrencesBetween(today, today) }
    val doneCount = todays.count { it.done }
    val progress = if (todays.isEmpty()) 0f else doneCount.toFloat() / todays.size
    val next = todays.firstOrNull { !it.done && (it.activity.startMinutes ?: 0) >= now.hour * 60 + now.minute }
        ?: todays.firstOrNull { !it.done }
    val nearestReminder = remember(activities, now) {
        activities.flatMap { a -> (0..6).mapNotNull { off -> a.reminderDateTime(today.plusDays(off.toLong()))?.let { a to it } } }
            .filter { it.second.isAfter(now) }.minByOrNull { it.second }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Header Langit Pagi sesuai mockup final: gradasi fajar TERANG
        // (biru langit pucat -> krem peach) dengan teks tinta gelap.
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFCDEBFF), Color(0xFFEAF7FF), Color(0xFFFFE7CF))
                    )
                ).padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(today.format(Dates.fullDate), color = Ink, modifier = Modifier.weight(1f))
                    Text("🕐 " + now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Ink)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarView(profile.photoPath, profile.name, profile.emoji, profile.colorIndex, 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(Dates.greeting(now.hour), color = Ink, fontSize = 13.sp)
                        Text("Halo, ${profile.name}!", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    RingProgress(progress)
                    Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Progres Hari Ini", fontWeight = FontWeight.Bold)
                    Text("$doneCount dari ${todays.size} kegiatan selesai")
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Kegiatan Berikutnya", fontWeight = FontWeight.Bold)
                Text(next?.let { "${it.activity.title} • ${Dates.formatTime(it.activity.startMinutes)}" } ?: "Tidak ada kegiatan tersisa hari ini")
                Spacer(Modifier.height(8.dp))
                Text("Reminder Terdekat", fontWeight = FontWeight.Bold)
                Text(nearestReminder?.let { "${it.first.title} • ${it.second.format(DateTimeFormatter.ofPattern("d MMM HH:mm"))}" } ?: "Belum ada reminder terjadwal")
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("Kegiatan Hari Ini", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (todays.isEmpty()) {
            Text("Belum ada kegiatan. Ketuk tombol + untuk menambah.")
        } else {
            todays.forEach { occ ->
                ActivityRow(occ, onToggleDone = { vm.toggleDone(occ.activity, occ.date.toEpochDay(), it) }, onClick = { onOpenDetail(occ) })
            }
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
