package id.rama.todoplanner.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.data.TargetEntity
import id.rama.todoplanner.domain.Dates
import id.rama.todoplanner.domain.Occurrence
import id.rama.todoplanner.domain.occurrencesBetween
import id.rama.todoplanner.ui.AppViewModel
import id.rama.todoplanner.ui.components.CategoryColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val idLocale = Locale("id", "ID")

@Composable
private fun ScreenTitle(title: String) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
}

/* ================= HARIAN: time-block per jam + checklist ================= */

@Composable
fun DailyScreen(vm: AppViewModel, onOpenDetail: (Occurrence) -> Unit, onAddAt: (Long, Int) -> Unit) {
    val activities by vm.activities.collectAsState()
    var date by remember { mutableStateOf(LocalDate.now()) }
    val occs = remember(activities, date) { activities.occurrencesBetween(date, date) }
    val timed = occs.filter { it.activity.startMinutes != null }
    val checklist = occs.filter { it.activity.startMinutes == null }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            ScreenTitle("🌤️ Template Harian")
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { date = date.minusDays(1) }) { Icon(Icons.Filled.ChevronLeft, "Kemarin") }
                Text(date.format(Dates.fullDate), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                IconButton(onClick = { date = date.plusDays(1) }) { Icon(Icons.Filled.ChevronRight, "Besok") }
            }
            if (date == LocalDate.now()) Text("Hari ini", color = Color(0xFF3E7CB1))
            Spacer(Modifier.height(6.dp))
        }
        // Blok jam 04.00-22.00: slot kosong bisa diketuk untuk mengisi kegiatan di jam itu.
        items(19) { idx ->
            val hour = idx + 4
            val inHour = timed.filter { (it.activity.startMinutes ?: 0) / 60 == hour }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("%02d.00".format(hour), fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFE1EBEF)))
                }
                if (inHour.isEmpty()) {
                    Text("+ Isi kegiatan", color = Color(0xFF8A9BA5),
                        modifier = Modifier.fillMaxWidth().clickable { onAddAt(date.toEpochDay(), hour * 60) }.padding(vertical = 6.dp, horizontal = 52.dp))
                } else {
                    inHour.forEach { occ ->
                        Box(modifier = Modifier.padding(start = 52.dp)) {
                            ActivityRow(occ, onToggleDone = { vm.toggleDone(occ.activity, occ.date.toEpochDay(), it) }, onClick = { onOpenDetail(occ) })
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(14.dp))
            Text("Checklist (tanpa jam)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (checklist.isEmpty()) Text("Belum ada kegiatan tanpa jam.")
            checklist.forEach { occ ->
                ActivityRow(occ, onToggleDone = { vm.toggleDone(occ.activity, occ.date.toEpochDay(), it) }, onClick = { onOpenDetail(occ) })
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { onAddAt(date.toEpochDay(), 9 * 60) }, modifier = Modifier.fillMaxWidth()) { Text("📄 / + Kegiatan Hari Ini") }
        }
    }
}

/* ================= MINGGUAN: 7 hari ================= */

@Composable
fun WeeklyScreen(vm: AppViewModel, onOpenDetail: (Occurrence) -> Unit, onAddAt: (Long) -> Unit) {
    val activities by vm.activities.collectAsState()
    var monday by remember { mutableStateOf(LocalDate.now().with(DayOfWeek.MONDAY)) }
    val week = (0..6).map { monday.plusDays(it.toLong()) }
    val occs = remember(activities, monday) { activities.occurrencesBetween(week.first(), week.last()) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            ScreenTitle("🗓️ Template Mingguan")
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monday = monday.minusWeeks(1) }) { Icon(Icons.Filled.ChevronLeft, "Minggu lalu") }
                Text(
                    "${week.first().format(DateTimeFormatter.ofPattern("d MMM", idLocale))} – ${week.last().format(DateTimeFormatter.ofPattern("d MMM yyyy", idLocale))}",
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { monday = monday.plusWeeks(1) }) { Icon(Icons.Filled.ChevronRight, "Minggu depan") }
            }
        }
        items(7) { i ->
            val day = week[i]
            val dayOccs = occs.filter { it.date == day }
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(day.format(Dates.dayName) + ", " + day.dayOfMonth, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (day == LocalDate.now()) Text("Hari ini", color = Color(0xFF3E7CB1))
                        IconButton(onClick = { onAddAt(day.toEpochDay()) }) { Icon(Icons.Filled.Add, "Tambah") }
                    }
                    if (dayOccs.isEmpty()) Text("Kosong", color = Color(0xFF8A9BA5))
                    dayOccs.forEach { occ ->
                        ActivityRow(occ, onToggleDone = { vm.toggleDone(occ.activity, occ.date.toEpochDay(), it) }, onClick = { onOpenDetail(occ) })
                    }
                }
            }
        }
    }
}

/* ================= BULANAN: grid bulan + daftar ================= */

@Composable
fun MonthlyScreen(vm: AppViewModel, onOpenDetail: (Occurrence) -> Unit, onAddAt: (Long) -> Unit) {
    val activities by vm.activities.collectAsState()
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val monthOccs = remember(activities, ym) { activities.occurrencesBetween(ym.atDay(1), ym.atEndOfMonth()) }
    val selectedOccs = monthOccs.filter { it.date == selected }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScreenTitle("📆 Template Bulanan")
        MonthGrid(
            ym = ym, activitiesByDay = monthOccs.groupBy { it.date }, selected = selected,
            onPrev = { ym = ym.minusMonths(1) }, onNext = { ym = ym.plusMonths(1) },
            onSelect = { selected = it }
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(selected.format(Dates.fullDate), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { onAddAt(selected.toEpochDay()) }) { Icon(Icons.Filled.Add, "Tambah") }
        }
        LazyColumn {
            if (selectedOccs.isEmpty()) item { Text("Belum ada kegiatan di tanggal ini.") }
            items(selectedOccs.size) { i ->
                val occ = selectedOccs[i]
                ActivityRow(occ, onToggleDone = { vm.toggleDone(occ.activity, occ.date.toEpochDay(), it) }, onClick = { onOpenDetail(occ) })
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
            Text(ym.format(Dates.monthTitle), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, "Bulan depan") }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach {
                Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = Color(0xFF8A9BA5))
            }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxWidth().height(((cells.size + 6) / 7 * 52).dp)) {
            items(cells.size) { i ->
                val day = cells[i]
                if (day == null) {
                    Box(Modifier.aspectRatio(1f))
                } else {
                    val isToday = day == LocalDate.now()
                    val isSel = day == selected
                    Column(
                        modifier = Modifier.aspectRatio(1f).padding(2.dp)
                            .clip(CircleShape)
                            .background(if (isSel) Color(0xFFDCEEF9) else Color.Transparent)
                            .clickable { onSelect(day) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            day.dayOfMonth.toString(),
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) Color(0xFF3E7CB1) else Color.Unspecified
                        )
                        Row(horizontalArrangement = Arrangement.Center) {
                            activitiesByDay[day].orEmpty().map { it.activity.category }.distinct().take(2).forEach { cat ->
                                Box(Modifier.size(5.dp).clip(CircleShape).background(CategoryColors[cat] ?: Color.Gray))
                                Spacer(Modifier.width(2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ================= TAHUNAN: kuartal + target + meter ================= */

@Composable
fun YearlyScreen(vm: AppViewModel, onOpenDetail: (Occurrence) -> Unit, onAddAt: (Long) -> Unit) {
    val activities by vm.activities.collectAsState()
    val targets by vm.targets.collectAsState()
    var year by remember { mutableStateOf(LocalDate.now().year) }
    var showAddTarget by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<TargetEntity?>(null) }
    val yearOccs = remember(activities, year) {
        activities.occurrencesBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
    }
    val currentQuarter = (LocalDate.now().monthValue - 1) / 3 + 1

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            ScreenTitle("🌅 Template Tahunan")
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year -= 1 }) { Icon(Icons.Filled.ChevronLeft, "Tahun lalu") }
                Text(year.toString(), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = { year += 1 }) { Icon(Icons.Filled.ChevronRight, "Tahun depan") }
            }
        }
        items(4) { qi ->
            val q = qi + 1
            val qStart = LocalDate.of(year, (q - 1) * 3 + 1, 1)
            val qOccs = yearOccs.filter { (it.date.monthValue - 1) / 3 + 1 == q }
            val qDone = qOccs.count { it.done }
            val qTargets = targets.filter { it.year == year && it.quarter == q }
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Kuartal $q", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (year == LocalDate.now().year && q == currentQuarter) Text("▶ Sekarang", color = Color(0xFFF28B82), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { onAddAt(qStart.toEpochDay()) }) { Icon(Icons.Filled.Add, "Tambah kegiatan") }
                    }
                    Text("${qOccs.size} kegiatan • $qDone selesai")
                    if (qOccs.isNotEmpty()) LinearProgressIndicator(progress = { qDone.toFloat() / qOccs.size }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    qTargets.forEach { t ->
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎯 ${t.title}", modifier = Modifier.weight(1f))
                                Text("${t.current}/${t.target}")
                                TextButton(onClick = { if (t.current > 0) vm.saveTarget(t.copy(current = t.current - 1)) }) { Text("−") }
                                TextButton(onClick = { if (t.current < t.target) vm.saveTarget(t.copy(current = t.current + 1)) }) { Text("+") }
                                IconButton(onClick = { editTarget = t }) { Text("✏️") }
                                IconButton(onClick = { vm.deleteTarget(t) }) { Icon(Icons.Filled.Delete, "Hapus target") }
                            }
                            LinearProgressIndicator(
                                progress = { if (t.target == 0) 0f else t.current.toFloat() / t.target },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = { showAddTarget = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Tambah Target") }
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
