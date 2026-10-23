package id.rama.todoplanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.domain.Dates
import id.rama.todoplanner.domain.Occurrence
import id.rama.todoplanner.domain.occurrencesBetween
import id.rama.todoplanner.ui.AppViewModel
import id.rama.todoplanner.ui.components.PastelChip
import id.rama.todoplanner.ui.theme.SkyBlue
import java.time.LocalDate
import java.time.YearMonth

/**
 * Kalender lokal algoritmik Gregorian (java.time) — tanpa batas tahun praktis,
 * tanpa tabel tahun. Mode Bulan: tap tanggal -> daftar kegiatan. Mode Tahun: 12 kartu bulan.
 */
@Composable
fun CalendarScreen(vm: AppViewModel, onOpenDetail: (Occurrence) -> Unit, onAddAt: (Long) -> Unit) {
    val activities by vm.activities.collectAsState()
    var mode by remember { mutableStateOf("Bulan") }
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("🗓️ Kalender", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            PastelChip(selected = mode == "Bulan", text = "Bulan", accent = SkyBlue) { mode = "Bulan" }
            PastelChip(selected = mode == "Tahun", text = "Tahun", accent = SkyBlue) { mode = "Tahun" }
        }
        Spacer(Modifier.height(8.dp))

        if (mode == "Bulan") {
            val monthOccs = remember(activities, ym) { activities.occurrencesBetween(ym.atDay(1), ym.atEndOfMonth()) }
            val selectedOccs = monthOccs.filter { it.date == selected }
            MonthGrid(
                ym = ym, activitiesByDay = monthOccs.groupBy { it.date }, selected = selected,
                onPrev = { ym = ym.minusMonths(1) }, onNext = { ym = ym.plusMonths(1) },
                onSelect = { selected = it }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(selected.format(Dates.fullDate), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { onAddAt(selected.toEpochDay()) }) { Icon(Icons.Filled.Add, "Tambah") }
            }
            LazyColumn {
                if (selectedOccs.isEmpty()) item { Text("Tidak ada kegiatan di tanggal ini.") }
                items(selectedOccs.size) { i ->
                    val occ = selectedOccs[i]
                    ActivityRow(occ, onToggleDone = { vm.toggleDone(occ.activity, occ.date.toEpochDay(), it) }, onClick = { onOpenDetail(occ) })
                }
            }
        } else {
            val year = ym.year
            val yearOccs = remember(activities, year) {
                activities.occurrencesBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ym = ym.minusYears(1) }) { Text("‹", fontSize = 22.sp) }
                Text(year.toString(), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = { ym = ym.plusYears(1) }) { Text("›", fontSize = 22.sp) }
            }
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize()) {
                items(12) { i ->
                    val m = YearMonth.of(year, i + 1)
                    val count = yearOccs.count { it.date.monthValue == i + 1 }
                    Card(
                        modifier = Modifier.padding(4.dp).clickable { ym = m; mode = "Bulan" }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(m.format(Dates.monthTitle).substringBefore(" "), fontWeight = FontWeight.Bold)
                            Text(if (count == 0) "—" else "$count kegiatan", fontSize = 12.sp, color = Color(0xFF5A6973))
                            Box(Modifier.fillMaxWidth().height(4.dp).background(if (count > 0) Color(0xFF8FD0A8) else Color(0xFFE1EBEF)))
                        }
                    }
                }
            }
        }
    }
}
