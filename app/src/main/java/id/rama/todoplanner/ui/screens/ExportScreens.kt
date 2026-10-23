package id.rama.todoplanner.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.domain.Dates
import id.rama.todoplanner.domain.occurrencesBetween
import id.rama.todoplanner.pdf.PdfExporter
import id.rama.todoplanner.ui.AppViewModel
import id.rama.todoplanner.ui.components.Categories
import id.rama.todoplanner.ui.components.GradientButton
import id.rama.todoplanner.ui.components.PastelChip
import id.rama.todoplanner.ui.components.CategoryColors
import id.rama.todoplanner.ui.theme.Mint
import id.rama.todoplanner.ui.theme.Peach
import id.rama.todoplanner.ui.theme.SkyBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val idLocale = Locale("id", "ID")
val EXPORT_PERIODS = listOf("Harian", "Mingguan", "Bulanan", "Tahunan")

fun periodRange(period: String, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
    "Mingguan" -> anchor.with(DayOfWeek.MONDAY) to anchor.with(DayOfWeek.SUNDAY)
    "Bulanan" -> anchor.withDayOfMonth(1) to anchor.withDayOfMonth(anchor.lengthOfMonth())
    "Tahunan" -> LocalDate.of(anchor.year, 1, 1) to LocalDate.of(anchor.year, 12, 31)
    else -> anchor to anchor
}

fun periodTitle(period: String, anchor: LocalDate): String = when (period) {
    "Mingguan" -> {
        val (from, to) = periodRange(period, anchor)
        "Laporan Mingguan — ${from.format(DateTimeFormatter.ofPattern("d MMM", idLocale))}–${to.format(DateTimeFormatter.ofPattern("d MMM yyyy", idLocale))}"
    }
    "Bulanan" -> "Laporan Bulanan — " + anchor.format(Dates.monthTitle)
    "Tahunan" -> "Laporan Tahunan — ${anchor.year}"
    else -> "Laporan Harian — " + anchor.format(Dates.fullDate)
}

fun shiftAnchor(period: String, anchor: LocalDate, dir: Long): LocalDate = when (period) {
    "Mingguan" -> anchor.plusWeeks(dir)
    "Bulanan" -> anchor.plusMonths(dir)
    "Tahunan" -> anchor.plusYears(dir)
    else -> anchor.plusDays(dir)
}

/* ================= EXPORT: pilih periode + kategori ================= */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(onPreview: (String, Long, Set<String>) -> Unit) {
    var period by remember { mutableStateOf("Bulanan") }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var categories by remember { mutableStateOf(Categories.toSet()) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("📄 Export PDF", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Periode", fontWeight = FontWeight.Bold)
        // FlowRow + PastelChip (ronde 1): chip lebar mengikuti konten, membungkus rapi —
        // pola yang sama dengan form, tidak ada lagi chip terhimpit.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            EXPORT_PERIODS.forEach { p ->
                PastelChip(selected = period == p, text = p, accent = SkyBlue) { period = p }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { anchor = shiftAnchor(period, anchor, -1) }) { Text("‹") }
            Text(periodTitle(period, anchor), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { anchor = shiftAnchor(period, anchor, 1) }) { Text("›") }
        }
        Spacer(Modifier.height(10.dp))
        Text("Kategori", fontWeight = FontWeight.Bold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Categories.forEach { cat ->
                PastelChip(
                    selected = cat in categories,
                    text = cat,
                    accent = CategoryColors[cat] ?: SkyBlue
                ) { categories = if (cat in categories) categories - cat else categories + cat }
            }
        }
        Spacer(Modifier.height(16.dp))
        GradientButton(
            text = "📄 Buat PDF & Lihat Preview",
            onClick = { onPreview(period, anchor.toEpochDay(), categories) },
            modifier = Modifier.fillMaxWidth(),
            enabled = categories.isNotEmpty()
        )
    }
}

/* ================= PREVIEW A4 + simpan/bagikan ================= */

@Composable
fun PdfPreviewScreen(vm: AppViewModel, period: String, anchorEpochDay: Long, categories: Set<String>) {
    val activities by vm.activities.collectAsState()
    val profile by vm.profile.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val anchor = LocalDate.ofEpochDay(anchorEpochDay)
    val (from, to) = periodRange(period, anchor)
    val title = periodTitle(period, anchor)
    val occs = remember(activities, from, to, categories) {
        activities.occurrencesBetween(from, to).filter { it.activity.category in categories }
    }
    val done = occs.count { it.done }
    val pct = if (occs.isEmpty()) 0 else done * 100 / occs.size

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("📄 Preview PDF", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        // Lembar A4 (pratinjau visual; PDF aslinya digambar PdfExporter dengan tata letak yang sama).
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(SkyBlue, Peach, Mint))).padding(14.dp)
                ) {
                    Column {
                        Text("Todo Planner", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Laporan kegiatan — ${profile.name}", color = Color.White)
                    }
                }
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${occs.size} kegiatan • $done selesai • progres $pct%")
                    Spacer(Modifier.height(8.dp))
                    occs.take(30).forEach { occ ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(CategoryColors[occ.activity.category] ?: Color.Gray))
                            Spacer(Modifier.width(8.dp))
                            Text(occ.activity.title, modifier = Modifier.weight(1f))
                            Text(occ.date.format(DateTimeFormatter.ofPattern("d MMM", idLocale)))
                            Text(if (occ.done) " ✓" else " •")
                        }
                    }
                    if (occs.size > 30) Text("… dan ${occs.size - 30} kegiatan lain (semua masuk di PDF)")
                    if (occs.isEmpty()) Text("Tidak ada kegiatan pada periode & kategori ini.")
                    Spacer(Modifier.height(8.dp))
                    Text("Dicetak: ${LocalDate.now().format(Dates.fullDate)} • Halaman 1 dari ${maxOf(1, (occs.size + 21) / 22)}", fontSize = 11.sp, color = Color(0xFF78828C))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        PdfExporter.saveToDownloads(context, profile.name, title, occs)
                    }
                    Toast.makeText(context, "PDF tersimpan: ${result.location}", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("⬇️ Simpan PDF") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                scope.launch {
                    val shareUri = withContext(Dispatchers.IO) {
                        PdfExporter.writeCacheCopy(context, profile.name, title, occs, "TodoPlanner.pdf")
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, Uri.parse(shareUri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Bagikan PDF"))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("📤 Bagikan PDF…") }
    }
}
