package id.rama.todoplanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.data.TargetEntity
import id.rama.todoplanner.domain.Dates
import id.rama.todoplanner.domain.Occurrence
import id.rama.todoplanner.domain.occurrencesBetween
import id.rama.todoplanner.ui.AppViewModel
import id.rama.todoplanner.ui.components.CategoryColors
import id.rama.todoplanner.ui.components.CategoryPill
import id.rama.todoplanner.ui.components.DawnIllustration
import id.rama.todoplanner.ui.components.GradientBar
import id.rama.todoplanner.ui.components.GradientButton
import id.rama.todoplanner.ui.components.PastelPill
import id.rama.todoplanner.ui.components.SoftCard
import id.rama.todoplanner.ui.theme.GreenDeep
import id.rama.todoplanner.ui.theme.Ink
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val idLocale = Locale("id", "ID")
private val ScreenBg = Color(0xFFF4F9FB)
private val SubInk = Color(0xFF70858E)

@Composable
private fun ScreenTitle(title: String) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(bottom = 8.dp))
}

/** Tombol tambah kecil berbentuk lingkaran pastel (pengganti IconButton polos). */
@Composable
private fun PastelAddButton(contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(34.dp).clip(CircleShape)
            .background(Color(0xFFE1F2FB)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Add, contentDescription, tint = Color(0xFF2B6E91), modifier = Modifier.size(19.dp))
    }
}

/**
 * Baris kegiatan ringkas TANPA kartu (dipakai DI DALAM SoftCard: timeline
 * Harian & checklist) — titik/garis kategori + judul + pill kategori.
 * Ketuk lingkaran = tandai selesai; ketuk baris = buka Detail.
 */
@Composable
private fun CompactRow(occ: Occurrence, onToggleDone: (Boolean) -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape)
                .background(if (occ.done) GreenDeep else Color.Transparent)
                .border(2.dp, GreenDeep, CircleShape)
                .clickable { onToggleDone(!occ.done) },
            contentAlignment = Alignment.Center
        ) {
            if (occ.done) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                occ.activity.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = if (occ.done) Color(0xFF9FB0B8) else Ink,
                textDecoration = if (occ.done) TextDecoration.LineThrough else null
            )
            Spacer(Modifier.height(3.dp))
            CategoryPill(occ.activity.category)
        }
    }
}

/** State kosong berilustrasi (awan + matahari + bukit ronde 2) + ajakan mengisi. */
@Composable
fun IllustratedEmptyState(text: String, actionText: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DawnIllustration(modifier = Modifier.fillMaxWidth(0.72f))
        Text(text, fontSize = 12.5.sp, color = SubInk)
        Text(
            actionText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B6E91),
            modifier = Modifier.clickable { onAction() }.padding(vertical = 4.dp)
        )
    }
}

/* ================= HARIAN: time-block berkartu + timeline + checklist ================= */

@Composable
fun DailyScreen(
    vm: AppViewModel, onOpenDetail: (Occurrence) -> Unit, onAddAt: (Long, Int) -> Unit,
    onExport: () -> Unit = {}
) {
    val activities by vm.activities.collectAsState()
    var date by remember { mutableStateOf(LocalDate.now()) }
    val occs = remember(activities, date) { activities.occurrencesBetween(date, date) }
    val timed = occs.filter { it.activity.startMinutes != null }
    val checklist = occs.filter { it.activity.startMinutes == null }

    Column(
        modifier = Modifier.fillMaxSize().background(ScreenBg)
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        ScreenTitle("🌤️ Template Harian")
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { date = date.minusDays(1) }) { Icon(Icons.Filled.ChevronLeft, "Kemarin") }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(date.format(Dates.fullDate), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Ink)
                    if (date == LocalDate.now()) Text("Hari ini", color = Color(0xFF3E7CB1), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { date = date.plusDays(1) }) { Icon(Icons.Filled.ChevronRight, "Besok") }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ===== Satu kartu timeline: jam + titik warna kategori + pill kategori =====
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                (4..22).forEach { hour ->
                    val inHour = timed.filter { (it.activity.startMinutes ?: 0) / 60 == hour }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(
                            "%02d.00".format(hour), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            color = Color(0xFF58707C), modifier = Modifier.width(46.dp).padding(top = 2.dp)
                        )
                        val dotColor = inHour.firstOrNull()
                            ?.let { CategoryColors[it.activity.category] } ?: Color(0xFFDCE8ED)
                        Box(
                            modifier = Modifier.size(11.dp).clip(CircleShape).background(dotColor)
                                .align(Alignment.Top).padding(top = 4.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (inHour.isEmpty()) {
                                Text(
                                    "Slot kosong — belum ada kegiatan. Isi kegiatan →",
                                    color = Color(0xFF8A9BA5), fontSize = 13.sp,
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { onAddAt(date.toEpochDay(), hour * 60) }
                                        .padding(vertical = 2.dp)
                                )
                            } else {
                                inHour.forEach { occ ->
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable { onOpenDetail(occ) }.padding(vertical = 2.dp)
                                    ) {
                                        Text(
                                            occ.activity.title, fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp, color = Ink,
                                            textDecoration = if (occ.done) TextDecoration.LineThrough else null
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        CategoryPill(occ.activity.category)
                                    }
                                }
                            }
                        }
                    }
                    if (hour < 22) {
                        Box(modifier = Modifier.fillMaxWidth().padding(start = 46.dp).height(1.dp).background(Color(0xFFEDF3F6)))
                    }
                }
                // Legenda kategori (mockup: "Titik warna = kategori:")
                val catsPresent = timed.map { it.activity.category }.distinct()
                if (catsPresent.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Titik warna = kategori: ", fontSize = 12.sp, color = SubInk)
                        catsPresent.take(3).forEach { cat ->
                            CategoryPill(cat)
                            Spacer(Modifier.width(5.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ===== Checklist terpisah (mockup) =====
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("✅ Checklist Hari Ini", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                if (checklist.isEmpty()) {
                    Text("Belum ada kegiatan tanpa jam.", fontSize = 12.5.sp, color = SubInk, modifier = Modifier.padding(top = 6.dp))
                } else {
                    checklist.forEach { occ ->
                        CompactRow(occ, onToggleDone = { vm.toggleDone(occ.activity, occ.date.toEpochDay(), it) }, onClick = { onOpenDetail(occ) })
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GradientButton(
            text = "+ Isi Kegiatan di Jam Kosong",
            onClick = { onAddAt(date.toEpochDay(), 9 * 60) },
            modifier = Modifier.fillMaxWidth(), mint = true
        )
        Spacer(Modifier.height(8.dp))
        GradientButton(
            text = "📄 Export PDF Hari Ini",
            onClick = onExport,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/* ================= MINGGUAN: 7 hari berkartu ================= */

private val WeekBands = listOf(
    listOf(Color(0xFFFFD98A), Color(0xFFF6A56B)),
    listOf(Color(0xFF8FD0F0), Color(0xFF6FB3DC)),
    listOf(Color(0xFFB7A8E8), Color(0xFF9B8BD4)),
    listOf(Color(0xFF8FD0A8), Color(0xFF58A97C)),
    listOf(Color(0xFFF2A9C4), Color(0xFFE88BB0)),
    listOf(Color(0xFFF2CD7E), Color(0xFFE8B84B)),
    listOf(Color(0xFFF6B17A), Color(0xFFF28B82))
)

@Composable
fun WeeklyScreen(
    vm: AppViewModel, onOpenDetail: (Occurrence) -> Unit, onAddAt: (Long) -> Unit,
    onExport: () -> Unit = {}
) {
    val activities by vm.activities.collectAsState()
    var monday by remember { mutableStateOf(LocalDate.now().with(DayOfWeek.MONDAY)) }
    val week = (0..6).map { monday.plusDays(it.toLong()) }
    val occs = remember(activities, monday) { activities.occurrencesBetween(week.first(), week.last()) }

    LazyColumn(modifier = Modifier.fillMaxSize().background(ScreenBg).padding(16.dp)) {
        item {
            ScreenTitle("🗓️ Template Mingguan")
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monday = monday.minusWeeks(1) }) { Icon(Icons.Filled.ChevronLeft, "Minggu lalu") }
                Text(
                    "${week.first().format(DateTimeFormatter.ofPattern("d MMM", idLocale))} – ${week.last().format(DateTimeFormatter.ofPattern("d MMM yyyy", idLocale))}",
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Ink
                )
                IconButton(onClick = { monday = monday.plusWeeks(1) }) { Icon(Icons.Filled.ChevronRight, "Minggu depan") }
            }
        }
        items(7) { i ->
            val day = week[i]
            val dayOccs = occs.filter { it.date == day }
            SoftCard(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(Brush.horizontalGradient(WeekBands[i])))
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(day.format(Dates.dayName) + ", " + day.dayOfMonth, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                        if (day == LocalDate.now()) PastelPill("Hari ini", Color(0xFFFFE7CF), Color(0xFF8A5E14))
                        Spacer(Modifier.width(6.dp))
                        PastelAddButton("Tambah") { onAddAt(day.toEpochDay()) }
                    }
                    if (dayOccs.isEmpty()) {
                        Text(
                            "+ Isi kegiatan", color = Color(0xFF8A9BA5), fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onAddAt(day.toEpochDay()) }.padding(vertical = 6.dp)
                        )
                    }
                    dayOccs.forEach { occ ->
                        ActivityRow(occ, onToggleDone = { vm.toggleDone(occ.activity, occ.date.toEpochDay(), it) }, onClick = { onOpenDetail(occ) })
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            GradientButton(text = "📄 Export PDF Mingguan", onClick = onExport, modifier = Modifier.fillMaxWidth())
        }
    }
}

/* ================= BULANAN: grid bulan berkartu + daftar ================= */

@Composable
fun MonthlyScreen(
    vm: AppViewModel, onOpenDetail: (Occurrence) -> Unit, onAddAt: (Long) -> Unit,
    onExport: () -> Unit = {}
) {
    val activities by vm.activities.collectAsState()
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val monthOccs = remember(activities, ym) { activities.occurrencesBetween(ym.atDay(1), ym.atEndOfMonth()) }
    val selectedOccs = monthOccs.filter { it.date == selected }

    Column(modifier = Modifier.fillMaxSize().background(ScreenBg).padding(16.dp)) {
        ScreenTitle("📆 Template Bulanan")
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp)) {
                MonthGrid(
                    ym = ym, activitiesByDay = monthOccs.groupBy { it.date }, selected = selected,
                    onPrev = { ym = ym.minusMonths(1) }, onNext = { ym = ym.plusMonths(1) },
                    onSelect = { selected = it }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(selected.format(Dates.fullDate), fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
            PastelAddButton("Tambah") { onAddAt(selected.toEpochDay()) }
        }
        LazyColumn {
            if (selectedOccs.isEmpty()) {
                item {
                    SoftCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        IllustratedEmptyState(
                            text = "Belum ada kegiatan di tanggal ini.",
                            actionText = "Isi kegiatan di tanggal ini →"
                        ) { onAddAt(selected.toEpochDay()) }
                    }
                }
            }
            items(selectedOccs.size) { i ->
                val occ = selectedOccs[i]
                ActivityRow(occ, onToggleDone = { vm.toggleDone(occ.activity, occ.date.toEpochDay(), it) }, onClick = { onOpenDetail(occ) })
            }
            item {
                Spacer(Modifier.height(8.dp))
                GradientButton(text = "📄 Export PDF Bulanan", onClick = onExport, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Grid bulan Gregorian (java.time YearMonth) dengan titik warna kategori & penanda hari ini. */
@Composable
fun MonthGrid(
    ym: YearMonth, activitiesByDay: Map<LocalDate, List<Occurrence>>, selected: LocalDate?,
    onPrev: () -> Unit, onNext: () -> Unit, onSelect: (LocalDate) -> Unit
) {
    val firstDow = ym.atDay(1).dayOfWeek.value // Senin=1
    val cells: List<LocalDate?> = List(firstDow - 1) { null } + (1..ym.lengthOfMonth()).map { ym.atDay(it) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, "Bulan lalu") }
            Text(ym.format(Dates.monthTitle), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Ink)
            IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, "Bulan depan") }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach {
                Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = Color(0xFF8A9BA5))
            }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxWidth().height(((cells.size + 6) / 7 * 54).dp)) {
            items(cells.size) { i ->
                val day = cells[i]
                if (day == null) {
                    Box(Modifier.aspectRatio(1f))
                } else {
                    val isToday = day == LocalDate.now()
                    val isSel = day == selected
                    // RONDE 3 (mockup): tanggal terpilih = GRADASI fajar + teks putih;
                    // hari ini (bila tak terpilih) = lingkaran pastel + bingkai biru.
                    Column(
                        modifier = Modifier.aspectRatio(1f).padding(2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                when {
                                    isSel -> Brush.horizontalGradient(listOf(Color(0xFFF2A65A), Color(0xFFF28B82), Color(0xFF6FB3DC)))
                                    isToday -> Brush.horizontalGradient(listOf(Color(0xFFDCEEF9), Color(0xFFDCEEF9)))
                                    else -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                                }
                            )
                            .then(
                                if (isToday && !isSel) Modifier.border(1.5.dp, Color(0xFF6FB3DC), RoundedCornerShape(14.dp))
                                else Modifier
                            )
                            .clickable { onSelect(day) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            day.dayOfMonth.toString(),
                            fontWeight = if (isToday || isSel) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isSel -> Color.White
                                isToday -> Color(0xFF2B6E91)
                                else -> Ink
                            }
                        )
                        Row(horizontalArrangement = Arrangement.Center) {
                            activitiesByDay[day].orEmpty().map { it.activity.category }.distinct().take(2).forEach { cat ->
                                val dot = if (isSel) Color.White else (CategoryColors[cat] ?: Color.Gray)
                                Box(Modifier.size(5.dp).clip(CircleShape).background(dot))
                                Spacer(Modifier.width(2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ================= TAHUNAN: kuartal berkartu + target + meter gradasi ================= */

private val QuarterBands = listOf(
    listOf(Color(0xFF8FD0F0), Color(0xFF6FB3DC)),
    listOf(Color(0xFF8FD0A8), Color(0xFF58A97C)),
    listOf(Color(0xFFFFD98A), Color(0xFFF6A56B)),
    listOf(Color(0xFFB7A8E8), Color(0xFF9B8BD4))
)

@Composable
fun YearlyScreen(
    vm: AppViewModel, onOpenDetail: (Occurrence) -> Unit, onAddAt: (Long) -> Unit,
    onExport: () -> Unit = {}
) {
    val activities by vm.activities.collectAsState()
    val targets by vm.targets.collectAsState()
    var year by remember { mutableStateOf(LocalDate.now().year) }
    var showAddTarget by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<TargetEntity?>(null) }
    val yearOccs = remember(activities, year) {
        activities.occurrencesBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
    }
    val currentQuarter = (LocalDate.now().monthValue - 1) / 3 + 1
    val yearTargets = targets.filter { it.year == year }
    val targetSum = yearTargets.sumOf { it.target }
    val targetDone = yearTargets.sumOf { it.current }

    LazyColumn(modifier = Modifier.fillMaxSize().background(ScreenBg).padding(16.dp)) {
        item {
            ScreenTitle("🌅 Template Tahunan")
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year -= 1 }) { Icon(Icons.Filled.ChevronLeft, "Tahun lalu") }
                Text(year.toString(), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                IconButton(onClick = { year += 1 }) { Icon(Icons.Filled.ChevronRight, "Tahun depan") }
            }
            // Kartu Target Besar (mockup Tahunan): matahari + ringkasan target tahun ini
            SoftCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🎯 Target Besar $year", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                    if (targetSum == 0) {
                        Text("Belum ada target tahun ini.", fontSize = 12.5.sp, color = SubInk, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        Text("$targetDone dari $targetSum tercapai", fontSize = 12.5.sp, color = SubInk, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                        GradientBar(targetDone.toFloat() / targetSum, listOf(Color(0xFFF2A65A), Color(0xFFF28B82)))
                    }
                }
            }
        }
        items(4) { qi ->
            val q = qi + 1
            val qStart = LocalDate.of(year, (q - 1) * 3 + 1, 1)
            val qOccs = yearOccs.filter { (it.date.monthValue - 1) / 3 + 1 == q }
            val qDone = qOccs.count { it.done }
            val qTargets = targets.filter { it.year == year && it.quarter == q }
            SoftCard(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(Brush.horizontalGradient(QuarterBands[qi])))
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Kuartal $q", fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                        if (year == LocalDate.now().year && q == currentQuarter) {
                            PastelPill("▶ Sekarang", Color(0xFFFFE7CF), Color(0xFF8A5E14))
                            Spacer(Modifier.width(6.dp))
                        }
                        PastelAddButton("Tambah kegiatan") { onAddAt(qStart.toEpochDay()) }
                    }
                    Text("${qOccs.size} kegiatan • $qDone selesai", fontSize = 12.5.sp, color = SubInk, modifier = Modifier.padding(top = 4.dp))
                    if (qOccs.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        GradientBar(qDone.toFloat() / qOccs.size, listOf(Color(0xFF8FD0A8), Color(0xFF58A97C)))
                    }
                    qTargets.forEach { t ->
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎯 ${t.title}", modifier = Modifier.weight(1f), color = Ink)
                                Text("${t.current}/${t.target}", fontWeight = FontWeight.Bold, color = Ink)
                                TextButton(onClick = { if (t.current > 0) vm.saveTarget(t.copy(current = t.current - 1)) }) { Text("−") }
                                TextButton(onClick = { if (t.current < t.target) vm.saveTarget(t.copy(current = t.current + 1)) }) { Text("+") }
                                IconButton(onClick = { editTarget = t }) { Text("✏️") }
                                IconButton(onClick = { vm.deleteTarget(t) }) { Icon(Icons.Filled.Delete, "Hapus target") }
                            }
                            GradientBar(
                                if (t.target == 0) 0f else t.current.toFloat() / t.target,
                                listOf(Color(0xFF8FD0F0), Color(0xFFF2A65A))
                            )
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            GradientButton(text = "+ Tambah Target", onClick = { showAddTarget = true }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GradientButton(text = "📄 Export PDF Tahunan", onClick = onExport, modifier = Modifier.fillMaxWidth(), mint = true)
        }
    }

    if (showAddTarget) {
        TargetDialog(
            initial = null, year = year,
            onDismiss = { showAddTarget = false },
            onSave = { title, target, quarter, _ -> vm.saveTarget(TargetEntity(title = title, target = target, quarter = quarter, year = year)); showAddTarget = false }
        )
    }
    editTarget?.let { t ->
        TargetDialog(
            initial = t, year = year,
            onDismiss = { editTarget = null },
            onSave = { title, target, quarter, current -> vm.saveTarget(t.copy(title = title, target = target, quarter = quarter, current = current.coerceIn(0, target))); editTarget = null }
        )
    }
}

@Composable
private fun TargetDialog(initial: TargetEntity?, year: Int, onDismiss: () -> Unit, onSave: (String, Int, Int, Int) -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var targetText by remember { mutableStateOf((initial?.target ?: 10).toString()) }
    var currentText by remember { mutableStateOf((initial?.current ?: 0).toString()) }
    var quarter by remember { mutableStateOf(initial?.quarter ?: 1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Tambah Target $year" else "Edit Target") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Nama target") })
                OutlinedTextField(targetText, { targetText = it.filter { c -> c.isDigit() } }, label = { Text("Target (angka)") })
                if (initial != null) OutlinedTextField(currentText, { currentText = it.filter { c -> c.isDigit() } }, label = { Text("Tercapai saat ini") })
                Row {
                    (1..4).forEach { q ->
                        TextButton(onClick = { quarter = q }) { Text(if (q == quarter) "● Q$q" else "○ Q$q") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val tgt = targetText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val cur = currentText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                if (title.isNotBlank()) onSave(title, tgt, quarter, cur)
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
