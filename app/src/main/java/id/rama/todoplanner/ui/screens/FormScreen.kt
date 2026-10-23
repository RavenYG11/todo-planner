package id.rama.todoplanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.data.ActivityEntity
import id.rama.todoplanner.domain.Dates
import id.rama.todoplanner.ui.AppViewModel
import id.rama.todoplanner.ui.components.Categories
import id.rama.todoplanner.ui.components.CategoryColors
import id.rama.todoplanner.ui.components.DawnHeader
import id.rama.todoplanner.ui.components.GradientButton
import id.rama.todoplanner.ui.components.PastelChip
import id.rama.todoplanner.ui.components.ToneField
import id.rama.todoplanner.ui.theme.AmberSoft
import id.rama.todoplanner.ui.theme.Coral
import id.rama.todoplanner.ui.theme.Lavender
import id.rama.todoplanner.ui.theme.SkyBlue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val REPEAT_OPTIONS = listOf("NONE" to "Tidak", "DAILY" to "Harian", "WEEKLY" to "Mingguan", "MONTHLY" to "Bulanan", "YEARLY" to "Tahunan")
// Opsi "Tepat waktu" DIHAPUS (permintaan Rama, ronde 1): alarm pas jam mulai kini
// otomatis untuk semua kegiatan ber-reminder. Nilai lama 0 di data lama ditangani
// defensif di bawah (tidak ada chip terpilih; user memilih ulang bila membuka form).
private val REMINDER_OPTIONS = listOf<Int?>(null, 15, 60, 1440)
private val REMINDER_LABELS = listOf("Tanpa", "15 mnt sebelum", "1 jam sebelum", "1 hari sebelum")
private val TEMPLATE_OPTIONS = listOf("Harian", "Mingguan", "Bulanan", "Tahunan")
private val PRIORITY_OPTIONS = listOf("Rendah", "Sedang", "Tinggi")

/**
 * Form Tambah/Edit Kegiatan — semua field wajib dari mockup.
 * Perubahan field dilaporkan ke TodoApp (onDirtyChange) untuk konfirmasi Back HP.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormScreen(
    vm: AppViewModel,
    editId: Long?,
    presetDateEpochDay: Long?,
    presetStartMinutes: Int?,
    presetTemplate: String?,
    onDirtyChange: (Boolean) -> Unit,
    onSaved: () -> Unit
) {
    val activities by vm.activities.collectAsState()
    val profile by vm.profile.collectAsState()
    val existing = remember(editId, activities) { editId?.let { id -> activities.firstOrNull { it.id == id } } }

    var title by remember(editId) { mutableStateOf(existing?.title ?: "") }
    var date by remember(editId) {
        mutableStateOf(
            existing?.let { LocalDate.ofEpochDay(it.dateEpochDay) }
                ?: presetDateEpochDay?.let { LocalDate.ofEpochDay(it) }
                ?: LocalDate.now()
        )
    }
    var startMin by remember(editId) { mutableStateOf(existing?.startMinutes ?: presetStartMinutes) }
    var endMin by remember(editId) { mutableStateOf(existing?.endMinutes) }
    var template by remember(editId) { mutableStateOf(existing?.template ?: presetTemplate ?: "Harian") }
    var repeat by remember(editId) { mutableStateOf(existing?.repeatRule ?: "NONE") }
    var reminder by remember(editId) { mutableStateOf<Int?>(existing?.reminderMinutesBefore) }
    var customTone by remember(editId) { mutableStateOf(existing?.ringtoneUri != null) }
    var toneUri by remember(editId) { mutableStateOf(existing?.ringtoneUri) }
    var toneName by remember(editId) { mutableStateOf(existing?.ringtoneName ?: "") }
    var priority by remember(editId) { mutableStateOf(existing?.priority ?: "Sedang") }
    var category by remember(editId) { mutableStateOf(existing?.category ?: "Kerja") }
    var note by remember(editId) { mutableStateOf(existing?.note ?: "") }
    var titleError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf<String?>(null) } // "start" | "end"

    // Deteksi form terisi/diubah untuk konfirmasi tombol Back HP.
    val dirty = remember(title, date, startMin, endMin, template, repeat, reminder, toneUri, priority, category, note, existing) {
        if (existing == null) {
            title.isNotBlank() || note.isNotBlank()
        } else {
            title != existing.title || date.toEpochDay() != existing.dateEpochDay ||
                startMin != existing.startMinutes || endMin != existing.endMinutes ||
                template != existing.template || repeat != existing.repeatRule ||
                reminder != existing.reminderMinutesBefore || toneUri != existing.ringtoneUri ||
                priority != existing.priority || category != existing.category || note != existing.note
        }
    }
    LaunchedEffect(dirty) { onDirtyChange(dirty) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        DawnHeader(if (editId == null) "➕ Tambah Kegiatan" else "✏️ Edit Kegiatan")
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(title, { title = it; titleError = false }, label = { Text("Judul kegiatan") },
            isError = titleError, modifier = Modifier.fillMaxWidth())
        if (titleError) Text("Judul wajib diisi", color = Color.Red)
        Spacer(Modifier.height(8.dp))

        // FlowRow: tombol tanggal/jam lebar mengikuti konten dan membungkus rapi —
        // tidak saling desak seperti Row lama (tombol 'Selesai' sempat jadi lingkaran raksasa).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { showDatePicker = true }) { Text("📅 " + date.format(Dates.shortDate)) }
            OutlinedButton(onClick = { pickingTime = "start" }) { Text("Mulai: " + Dates.formatTime(startMin)) }
            OutlinedButton(onClick = { pickingTime = "end" }) { Text("Selesai: " + Dates.formatTime(endMin)) }
        }
        if (startMin != null) TextButton(onClick = { startMin = null; endMin = null }) { Text("Hapus jam (jadikan checklist)") }
        Spacer(Modifier.height(8.dp))

        Text("Template Tujuan", fontWeight = FontWeight.Bold)
        ChipRow(TEMPLATE_OPTIONS, template, { SkyBlue }) { template = it }
        Text("Ulangi", fontWeight = FontWeight.Bold)
        ChipRow(REPEAT_OPTIONS.map { it.second }, REPEAT_OPTIONS.first { it.first == repeat }.second, { Lavender }) { label ->
            repeat = REPEAT_OPTIONS.first { it.second == label }.first
        }
        Text("Reminder", fontWeight = FontWeight.Bold)
        val reminderIdx = REMINDER_OPTIONS.indexOf(reminder)
        ChipRow(
            REMINDER_LABELS,
            if (reminderIdx >= 0) REMINDER_LABELS[reminderIdx] else "",
            { AmberSoft }
        ) { label ->
            reminder = REMINDER_OPTIONS[REMINDER_LABELS.indexOf(label)]
        }
        if (reminder != null) {
            Text("Nada Dering", fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                PastelChip(selected = !customTone, text = "Ikut Pengaturan", accent = SkyBlue) { customTone = false }
                PastelChip(selected = customTone, text = "Atur Sendiri", accent = SkyBlue) { customTone = true }
            }
            if (customTone) {
                ToneField(currentUri = toneUri ?: profile.globalToneUri, label = "Nada Dering Kegiatan") { uri, name ->
                    toneUri = uri; toneName = name
                }
            } else {
                Text("🎵 ${profile.globalToneName} (global)")
            }
        }
        Text("Prioritas", fontWeight = FontWeight.Bold)
        ChipRow(PRIORITY_OPTIONS, priority, { Coral }) { priority = it }
        Text("Kategori", fontWeight = FontWeight.Bold)
        ChipRow(Categories, category, { opt -> CategoryColors[opt] ?: SkyBlue }) { category = it }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(note, { note = it }, label = { Text("Catatan") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(Modifier.height(14.dp))
        GradientButton(
            text = "💾 Simpan Kegiatan",
            onClick = {
                if (title.isBlank()) { titleError = true; return@GradientButton }
                val entity = (existing ?: ActivityEntity(title = "", dateEpochDay = date.toEpochDay())).copy(
                    title = title.trim(),
                    dateEpochDay = date.toEpochDay(),
                    startMinutes = startMin,
                    endMinutes = endMin,
                    template = template,
                    repeatRule = repeat,
                    reminderMinutesBefore = reminder,
                    ringtoneUri = if (customTone) toneUri else null,
                    ringtoneName = if (customTone) toneName.ifBlank { null } else null,
                    priority = priority,
                    category = category,
                    note = note
                )
                vm.saveActivity(entity)
                onDirtyChange(false)
                onSaved()
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Pilih") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Batal") } }
        ) { DatePicker(state = state) }
    }

    pickingTime?.let { which ->
        val initial = if (which == "start") startMin ?: 9 * 60 else endMin ?: 10 * 60
        val timeState = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = null },
            title = { Text(if (which == "start") "Jam Mulai" else "Jam Selesai") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val value = timeState.hour * 60 + timeState.minute
                    if (which == "start") startMin = value else endMin = value
                    pickingTime = null
                }) { Text("Pilih") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = null }) { Text("Batal") } }
        )
    }
}

/**
 * Baris chip pilihan — PERBAIKAN RONDE 1: dulu Row biasa sehingga chip terakhir
 * terhimpit jadi kolom sempit (teks per huruf). Sekarang FlowRow: tiap chip lebar
 * mengikuti konten dan membungkus rapi ke baris berikutnya.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    options: List<String>,
    selected: String,
    accent: (String) -> Color,
    onSelect: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { opt ->
            PastelChip(selected = opt == selected, text = opt, accent = accent(opt)) { onSelect(opt) }
        }
    }
}
