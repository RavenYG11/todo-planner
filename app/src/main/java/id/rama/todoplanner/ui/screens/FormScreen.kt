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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.data.ActivityEntity
import id.rama.todoplanner.data.AppPrefs
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * RONDE 6 — ambil grapheme (klaster karakter) PERTAMA dari input user:
 * satu emoji utuh (termasuk yang ber-codepoint ganda) untuk logo kegiatan.
 */
private fun firstGrapheme(text: String): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ""
    val it = java.text.BreakIterator.getCharacterInstance()
    it.setText(trimmed)
    val end = it.next()
    return if (end == java.text.BreakIterator.DONE) "" else trimmed.substring(0, end)
}

private val REPEAT_OPTIONS = listOf("NONE" to "Tidak", "DAILY" to "Harian", "WEEKLY" to "Mingguan", "MONTHLY" to "Bulanan", "YEARLY" to "Tahunan")
// Opsi "Tepat waktu" DIHAPUS (permintaan Rama, ronde 1): alarm pas jam mulai kini
// otomatis untuk semua kegiatan ber-reminder. Nilai lama 0 di data lama ditangani
// defensif di bawah (tidak ada chip terpilih; user memilih ulang bila membuka form).
private val REMINDER_OPTIONS = listOf<Int?>(null, 15, 60, 1440)
private val REMINDER_LABELS = listOf("Tanpa", "15 mnt sebelum", "1 jam sebelum", "1 hari sebelum")
// RONDE 5: preset interval Daily Reminder (nilai, kode satuan, label chip) + input angka bebas.
private val INTERVAL_PRESETS = listOf(
    Triple(30, "DETIK", "30 Detik"),
    Triple(1, "MENIT", "1 Menit"),
    Triple(5, "MENIT", "5 Menit"),
    Triple(15, "MENIT", "15 Menit"),
    Triple(30, "MENIT", "30 Menit"),
    Triple(1, "JAM", "1 Jam"),
    Triple(2, "JAM", "2 Jam")
)
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
    // RONDE 5 — interval pengulangan Daily Reminder (hanya dipakai bila tanpa jam).
    // Satuan disimpan sebagai kode entitas: "DETIK" | "MENIT" | "JAM".
    var intervalValueText by remember(editId) { mutableStateOf(existing?.intervalValue?.toString() ?: "5") }
    var intervalUnit by remember(editId) { mutableStateOf(existing?.intervalUnit ?: "MENIT") }
    // Mode nada per kegiatan (RONDE 3) — 3 keadaan yang TIDAK ambigu:
    //  "global"  = Ikuti global (ringtoneUri disimpan null)
    //  "default" = Bawaan HP (default) — sentinel AppPrefs.RINGTONE_DEFAULT,
    //              hasil akhirnya benar-benar default sistem walau global kustom
    //  "custom"  = Atur Sendiri (URI nada pilihan kegiatan ini)
    var toneMode by remember(editId) {
        mutableStateOf(
            when {
                existing?.ringtoneUri == id.rama.todoplanner.data.AppPrefs.RINGTONE_DEFAULT -> "default"
                existing?.ringtoneUri != null -> "custom"
                else -> "global"
            }
        )
    }
    var toneUri by remember(editId) {
        mutableStateOf(existing?.ringtoneUri?.takeIf { it != id.rama.todoplanner.data.AppPrefs.RINGTONE_DEFAULT })
    }
    var toneName by remember(editId) { mutableStateOf(existing?.ringtoneName ?: "") }
    var priority by remember(editId) { mutableStateOf(existing?.priority ?: "Sedang") }
    var category by remember(editId) { mutableStateOf(existing?.category ?: "Kerja") }
    var note by remember(editId) { mutableStateOf(existing?.note ?: "") }
    // RONDE 6 — logo emoji kegiatan ("" = tanpa logo).
    var iconEmoji by remember(editId) { mutableStateOf(existing?.iconEmoji ?: "") }
    var titleError by remember { mutableStateOf(false) }
    // RONDE 6 — jendela hari aktif user (untuk teks bantuan Daily Reminder).
    val formContext = LocalContext.current
    val prefs = remember { AppPrefs(formContext) }
    val windowLabel = "%02d.%02d–%02d.%02d".format(
        prefs.dayStartMinutes / 60, prefs.dayStartMinutes % 60,
        prefs.dayEndMinutes / 60, prefs.dayEndMinutes % 60
    )
    var showDatePicker by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf<String?>(null) } // "start" | "end"

    // Deteksi form terisi/diubah untuk konfirmasi tombol Back HP.
    // Nilai nada yang AKAN disimpan menurut mode (untuk deteksi dirty & simpan).
    val savedToneUri: String? = when (toneMode) {
        "default" -> id.rama.todoplanner.data.AppPrefs.RINGTONE_DEFAULT
        "custom" -> toneUri
        else -> null
    }
    val dirty = remember(title, date, startMin, endMin, template, repeat, reminder, intervalValueText, intervalUnit, savedToneUri, priority, category, note, iconEmoji, existing) {
        if (existing == null) {
            title.isNotBlank() || note.isNotBlank() || iconEmoji.isNotBlank()
        } else {
            title != existing.title || date.toEpochDay() != existing.dateEpochDay ||
                startMin != existing.startMinutes || endMin != existing.endMinutes ||
                template != existing.template || repeat != existing.repeatRule ||
                reminder != existing.reminderMinutesBefore || savedToneUri != existing.ringtoneUri ||
                intervalValueText != (existing.intervalValue?.toString() ?: "5") ||
                intervalUnit != (existing.intervalUnit ?: "MENIT") ||
                iconEmoji != (existing.iconEmoji) ||
                priority != existing.priority || category != existing.category || note != existing.note
        }
    }

    // RONDE 6 — VALIDASI JAM TODO UTAMA: Ulangi = "Tidak" (sekali jalan) ->
    // tanggal+jam mulai HARUS setelah waktu realtime HP. Bila ulangan lain,
    // waktu lampau boleh (occurrence berikutnya yang berlaku). Daily Reminder
    // (tanpa jam) tidak divalidasi di sini.
    val pastOnceError: String = if (repeat == "NONE" && startMin != null) {
        val chosen = LocalDateTime.of(date, LocalTime.of(startMin!! / 60, startMin!! % 60))
        if (!chosen.isAfter(LocalDateTime.now())) {
            "Waktu kegiatan sudah lewat. Untuk Ulangi = Tidak, pilih tanggal & jam di masa depan."
        } else ""
    } else ""
    // RONDE 7 — VALIDASI LIVE dari state TERKINI pada SETIAP perubahan:
    // tidak ada lagi pesan basi yang disimpan. Mode tanpa jam (Daily
    // Reminder) TIDAK kena aturan waktu lampau (pastOnceError sudah ""
    // karena startMin == null); warning hilang begitu kondisi valid lagi.
    // Interval Daily Reminder wajib angka >= 1.
    val intervalValid: Boolean = startMin != null ||
        (intervalValueText.toIntOrNull()?.let { it >= 1 } == true)
    val isFormValid: Boolean = title.isNotBlank() && pastOnceError.isBlank() && intervalValid
    LaunchedEffect(dirty) { onDirtyChange(dirty) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        DawnHeader(if (editId == null) "➕ Tambah Kegiatan" else "✏️ Edit Kegiatan")
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(title, { title = it; titleError = false }, label = { Text("Judul kegiatan") },
            isError = titleError, modifier = Modifier.fillMaxWidth())
        if (titleError) Text("Judul wajib diisi", color = Color.Red)
        Spacer(Modifier.height(8.dp))

        // RONDE 6 — logo emoji: satu emoji dari keyboard emoji HP (field
        // tunggal; yang disimpan hanya grapheme pertama). Bisa dihapus.
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = iconEmoji,
                onValueChange = { iconEmoji = firstGrapheme(it) },
                label = { Text("Logo emoji (opsional)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            if (iconEmoji.isNotBlank()) {
                TextButton(onClick = { iconEmoji = "" }) { Text("Hapus") }
            }
        }
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
        if (startMin != null) TextButton(onClick = { startMin = null; endMin = null }) { Text("Hapus jam (jadikan Daily Reminder)") }
        // RONDE 7: pesan validasi SELALU dari hitungan live di atas —
        // hilang seketika saat mode/tanggal/jam/ulangan berubah jadi valid.
        if (pastOnceError.isNotBlank()) {
            Text(pastOnceError, color = Color.Red, fontSize = 12.5.sp)
        }
        if (startMin == null && !intervalValid) {
            Text("Interval Daily Reminder wajib diisi angka minimal 1.", color = Color.Red, fontSize = 12.5.sp)
        }
        Spacer(Modifier.height(8.dp))

        Text("Template Tujuan", fontWeight = FontWeight.Bold)
        ChipRow(TEMPLATE_OPTIONS, template, { SkyBlue }) { template = it }
        Text("Ulangi", fontWeight = FontWeight.Bold)
        ChipRow(REPEAT_OPTIONS.map { it.second }, REPEAT_OPTIONS.first { it.first == repeat }.second, { Lavender }) { label ->
            repeat = REPEAT_OPTIONS.first { it.second == label }.first
        }
        // RONDE 5 — DAILY REMINDER (tanpa jam) dibedakan dari TODO UTAMA:
        // opsi reminder "sebelum mulai" DIGANTI interval pengulangan 1 hari.
        // Pola hari pengulangan (chip "Ulangi" di atas) tetap dipertahankan.
        val isChecklistForm = startMin == null
        if (isChecklistForm) {
            Text("😉 Daily Reminder — Interval Pengulangan", fontWeight = FontWeight.Bold)
            Text(
                "Berbunyi berulang tiap interval pukul $windowLabel. Ditandai selesai 😉 = catatan jam tersimpan + timer reset (bunyi lagi setelah +interval).",
                fontSize = 12.sp, color = Color(0xFF70858E)
            )
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = intervalValueText,
                    onValueChange = { intervalValueText = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text("Nilai") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.padding(4.dp))
                ChipRow(
                    listOf("Detik", "Menit", "Jam"),
                    when (intervalUnit) { "DETIK" -> "Detik"; "JAM" -> "Jam"; else -> "Menit" },
                    { AmberSoft }
                ) { label ->
                    intervalUnit = when (label) { "Detik" -> "DETIK"; "Jam" -> "JAM"; else -> "MENIT" }
                }
            }
            Text("Preset interval", fontSize = 12.sp, color = Color(0xFF70858E))
            ChipRow(
                INTERVAL_PRESETS.map { it.third },
                INTERVAL_PRESETS.firstOrNull {
                    it.first.toString() == intervalValueText && it.second == intervalUnit
                }?.third ?: "",
                { AmberSoft }
            ) { label ->
                val preset = INTERVAL_PRESETS.first { it.third == label }
                intervalValueText = preset.first.toString()
                intervalUnit = preset.second
            }
        } else {
            Text("Reminder", fontWeight = FontWeight.Bold)
            val reminderIdx = REMINDER_OPTIONS.indexOf(reminder)
            ChipRow(
                REMINDER_LABELS,
                if (reminderIdx >= 0) REMINDER_LABELS[reminderIdx] else "",
                { AmberSoft }
            ) { label ->
                reminder = REMINDER_OPTIONS[REMINDER_LABELS.indexOf(label)]
            }
        }
        if (reminder != null || isChecklistForm) {
            Text("Nada Dering", fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                PastelChip(selected = toneMode == "global", text = "Ikuti global", accent = SkyBlue) { toneMode = "global" }
                PastelChip(selected = toneMode == "default", text = "Bawaan HP (default)", accent = SkyBlue) { toneMode = "default" }
                PastelChip(selected = toneMode == "custom", text = "Atur Sendiri", accent = SkyBlue) { toneMode = "custom" }
            }
            when (toneMode) {
                "custom" -> ToneField(currentUri = toneUri, label = "Nada Dering Kegiatan") { uri, name ->
                    toneUri = uri; toneName = name
                }
                "default" -> Text("🎵 Bawaan HP")
                else -> Text("🎵 ${profile.globalToneName} (global)")
            }
        }
        Text("Prioritas", fontWeight = FontWeight.Bold)
        ChipRow(PRIORITY_OPTIONS, priority, { Coral }) { priority = it }
        Text("Kategori", fontWeight = FontWeight.Bold)
        ChipRow(Categories, category, { opt -> CategoryColors[opt] ?: SkyBlue }) { category = it }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(note, { note = it }, label = { Text("Catatan") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(Modifier.height(14.dp))
        // RONDE 7: tombol DISABLED beneran (abu-abu + tidak bisa diklik)
        // selama form tidak valid — bukan lagi klik-tapi-diam.
        GradientButton(
            text = "💾 Simpan Kegiatan",
            enabled = isFormValid,
            onClick = {
                if (!isFormValid) return@GradientButton // pengaman; tombol disabled pun tidak memanggil ini
                if (title.isBlank()) { titleError = true; return@GradientButton }
                // RONDE 5: Daily Reminder menyimpan interval (reminder "sebelum
                // mulai" dikosongkan); todo utama menyimpan reminder seperti
                // biasa dan interval dikosongkan — dua mode tidak tercampur.
                val entity = (existing ?: ActivityEntity(title = "", dateEpochDay = date.toEpochDay())).copy(
                    title = title.trim(),
                    dateEpochDay = date.toEpochDay(),
                    startMinutes = startMin,
                    endMinutes = endMin,
                    template = template,
                    repeatRule = repeat,
                    reminderMinutesBefore = if (startMin == null) null else reminder,
                    intervalValue = if (startMin == null) {
                        intervalValueText.toIntOrNull()?.coerceAtLeast(1) ?: 5
                    } else null,
                    intervalUnit = if (startMin == null) intervalUnit else null,
                    ringtoneUri = savedToneUri,
                    ringtoneName = when (toneMode) {
                        "custom" -> toneName.ifBlank { null }
                        "default" -> "Bawaan HP"
                        else -> null
                    },
                    priority = priority,
                    category = category,
                    note = note,
                    // RONDE 6: logo emoji (untuk kegiatan baru, existing==null
                    // memakai entitas dasar yang iconEmoji-nya "" lalu ditimpa di sini).
                    iconEmoji = iconEmoji
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
        // RONDE 5: dial 360° Material DIGANTI picker slide vertikal
        // (ui/components/SlideTimePicker.kt) — geser ↕ dengan snap, ketuk
        // angka aktif untuk mengetik numerik. Batal/Pilih tetap.
        val initial = if (which == "start") startMin ?: 9 * 60 else endMin ?: 10 * 60
        id.rama.todoplanner.ui.components.SlideTimePickerDialog(
            title = if (which == "start") "Jam Mulai" else "Jam Selesai",
            initialMinutes = initial,
            onDismiss = { pickingTime = null },
            onConfirm = { value ->
                if (which == "start") startMin = value else endMin = value
                pickingTime = null
            }
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
