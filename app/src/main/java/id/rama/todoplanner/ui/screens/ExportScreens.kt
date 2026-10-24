package id.rama.todoplanner.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import id.rama.todoplanner.domain.buildPdfBlocks
import id.rama.todoplanner.domain.occurrencesBetween
import id.rama.todoplanner.domain.onlyTodoUtama
import id.rama.todoplanner.pdf.PdfExporter
import id.rama.todoplanner.ui.AppViewModel
import id.rama.todoplanner.ui.components.Categories
import id.rama.todoplanner.ui.components.CategoryPill
import id.rama.todoplanner.ui.components.DawnScenery
import id.rama.todoplanner.ui.components.GradientButton
import id.rama.todoplanner.ui.components.SoftCard
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

/** Label rentang untuk subjudul PDF/preview, mis. "1–31 Oktober 2026". */
fun periodRangeLabel(period: String, anchor: LocalDate): String {
    val (from, to) = periodRange(period, anchor)
    return when (period) {
        "Mingguan" -> "${from.format(DateTimeFormatter.ofPattern("d MMM", idLocale))} – ${to.format(DateTimeFormatter.ofPattern("d MMM yyyy", idLocale))}"
        "Bulanan" -> "1–${to.dayOfMonth} " + to.format(Dates.monthTitle)
        "Tahunan" -> "1 Jan – 31 Des ${anchor.year}"
        else -> from.format(Dates.fullDate)
    }
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

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF4F9FB))
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("📄 Export PDF", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("1️⃣ Pilih Periode Laporan", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        // Ubin periode 2x2 ala mockup Export (pita gradasi + ikon + label + rentang),
        // terpilih = bingkai oranye. Menggantikan chip polos ronde 1-2.
        val periodIcons = listOf("📅" to listOf(Color(0xFFFFD98A), Color(0xFFF6A56B)), "🗓️" to listOf(Color(0xFF8FD0F0), Color(0xFF6FB3DC)), "📆" to listOf(Color(0xFFB7A8E8), Color(0xFF9B8BD4)), "🎯" to listOf(Color(0xFF8FD0A8), Color(0xFF58A97C)))
        for (rowIdx in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (colIdx in 0..1) {
                    val idx = rowIdx * 2 + colIdx
                    val p = EXPORT_PERIODS[idx]
                    val (icon, band) = periodIcons[idx]
                    SoftCard(
                        modifier = Modifier.weight(1f)
                            .then(
                                if (period == p) Modifier.border(2.dp, Color(0xFFF2A65A), androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                                else Modifier
                            )
                            .clickable { period = p }
                    ) {
                        Box(Modifier.fillMaxWidth().height(6.dp).background(Brush.horizontalGradient(band)))
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("$icon $p", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(periodRangeLabel(p, anchor), fontSize = 11.5.sp, color = Color(0xFF70858E))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
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
fun PdfPreviewScreen(vm: AppViewModel, period: String, anchorEpochDay: Long, categories: Set<String>, onBack: () -> Unit = {}) {
    val activities by vm.activities.collectAsState()
    val profile by vm.profile.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val anchor = LocalDate.ofEpochDay(anchorEpochDay)
    val (from, to) = periodRange(period, anchor)
    val title = periodTitle(period, anchor)
    val rangeLabel = periodRangeLabel(period, anchor)
    val catLabel = if (categories.size == Categories.size) "Semua kategori" else categories.joinToString(", ")
    val periodLabel = "$rangeLabel • $catLabel"
    // RONDE 5: PDF & Preview hanya memuat TODO UTAMA — Daily Reminder dikeluarkan.
    val occs = remember(activities, from, to, categories) {
        activities.occurrencesBetween(from, to).filter { it.activity.category in categories }.onlyTodoUtama()
    }
    // RONDE 5: struktur kelompok sama persis dengan PdfExporter.
    val blocks = remember(occs, period) { buildPdfBlocks(period, occs) }
    val done = occs.count { it.done }
    val pct = if (occs.isEmpty()) 0 else done * 100 / occs.size
    val today = LocalDate.now()
    val printed = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy, HH.mm", idLocale))

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF4F9FB))
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("👁 Preview PDF", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        // Lembar A4 — RONDE 3: pratinjau MENIRU PdfExporter & mockup persis:
        // band gradasi fajar + art, judul, subjudul, kartu Ringkasan Progress,
        // tabel dengan pill kategori, footer tanggal cetak + nomor halaman.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFCDEBFF), Color(0xFFEAF7FF), Color(0xFFFFE7CF))
                            )
                        )
                ) {
                    Column {
                        DawnScenery(Modifier.fillMaxWidth().height(44.dp))
                        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
                            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF1E3A47))
                            Text(
                                "Todo Planner • ${profile.name} • Periode: $periodLabel",
                                fontSize = 11.sp, color = Color(0xFF58707C)
                            )
                        }
                    }
                }
                Column(modifier = Modifier.padding(14.dp)) {
                    // Kartu Ringkasan Progress
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .background(Color(0xFFF4F9FB))
                            .border(1.dp, Color(0xFFEAF1F4), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("📌 Ringkasan Progress", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Total kegiatan: ${occs.size} • Selesai: $done ($pct%)", fontSize = 12.5.sp)
                            if (occs.isNotEmpty()) {
                                val topCat = occs.groupBy { it.activity.category }.maxByOrNull { it.value.size }
                                if (topCat != null) {
                                    val cp = topCat.value.count { it.done } * 100 / topCat.value.size
                                    Text("Kategori teraktif: ${topCat.key} ($cp%)", fontSize = 12.5.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Kepala kolom (dengan No. penomoran per hari)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("No.", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF58707C), modifier = Modifier.weight(0.22f))
                        Text("Kegiatan", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF58707C), modifier = Modifier.weight(1.5f))
                        Text("Tanggal", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF58707C), modifier = Modifier.weight(0.55f))
                        Text("Kategori", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF58707C), modifier = Modifier.weight(0.75f))
                        Text("Status", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF58707C), modifier = Modifier.weight(0.85f))
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE1EBEF)))
                    // RONDE 5: kelompok bertingkat per periode (bulan->minggu->hari)
                    // dengan penomoran yang mulai ulang tiap hari.
                    blocks.forEach { block ->
                        when (block.level) {
                            0 -> Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF9B8BD4), Color(0xFF6FB3DC))))
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📅 ${block.label}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
                                    block.sublabel?.let { Text(it, fontSize = 10.5.sp, color = Color.White) }
                                }
                            }
                            1 -> Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFCF0D8))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🗓️ ${block.label}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF7A5610), modifier = Modifier.weight(1f))
                                    block.sublabel?.let { Text(it, fontSize = 10.5.sp, color = Color(0xFF7A5610)) }
                                }
                            }
                            2 -> Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 2.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
                                    .background(Color(0xFFE1F2FB))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(block.label, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = Color(0xFF1B5475), modifier = Modifier.weight(1f))
                                    block.sublabel?.let { Text(it, fontSize = 10.sp, color = Color(0xFF58707C)) }
                                }
                            }
                            else -> {
                                val occ = block.occurrence!!
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${block.number}.", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2B6E91), modifier = Modifier.weight(0.22f))
                                    Column(modifier = Modifier.weight(1.5f)) {
                                        Text(occ.activity.title, fontSize = 12.sp)
                                        if (occ.activity.note.isNotBlank()) {
                                            Text(
                                                occ.activity.note,
                                                fontSize = 10.5.sp,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                color = Color(0xFF70858E)
                                            )
                                        }
                                    }
                                    Text(occ.date.format(DateTimeFormatter.ofPattern("d MMM", idLocale)), fontSize = 12.sp, modifier = Modifier.weight(0.55f))
                                    Box(modifier = Modifier.weight(0.75f)) { CategoryPill(occ.activity.category, id.rama.todoplanner.ui.components.activityColor(occ.activity)) }
                                    Text(
                                        when {
                                            occ.done && period == "Harian" ->
                                                occ.completedAtMillis?.let { "Diselesaikan " + id.rama.todoplanner.domain.Dates.formatCompletedTime(it) } ?: "Selesai"
                                            occ.done ->
                                                occ.completedAtMillis?.let { "✓ Selesai • " + id.rama.todoplanner.domain.Dates.formatCompletedTime(it) } ?: "✓ Selesai"
                                            occ.date.isBefore(today) -> "Belum selesai"
                                            else -> "Mendatang"
                                        },
                                        fontSize = 11.5.sp,
                                        color = if (occ.done) Color(0xFF22714F) else Color(0xFF58707C),
                                        modifier = Modifier.weight(0.85f)
                                    )
                                }
                                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F4F6)))
                            }
                        }
                    }
                    if (occs.isEmpty()) Text("Tidak ada kegiatan pada periode & kategori ini.", fontSize = 12.5.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Dicetak $printed WIB • Todo Planner (full lokal & offline)",
                            fontSize = 10.sp, color = Color(0xFF78828C), modifier = Modifier.weight(1f)
                        )
                        Text("Halaman 1 dari 1+", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF58707C))
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        GradientButton(
            text = "⬇️ Simpan PDF",
            onClick = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        PdfExporter.saveToDownloads(context, profile.name, title, periodLabel, occs, period)
                    }
                    Toast.makeText(context, "PDF tersimpan: ${result.location}", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                scope.launch {
                    val shareUri = withContext(Dispatchers.IO) {
                        PdfExporter.writeCacheCopy(context, profile.name, title, periodLabel, occs, "TodoPlanner.pdf", period)
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
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("‹ Ubah Periode / Kategori") }
    }
}
