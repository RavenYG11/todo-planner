package id.rama.todoplanner.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * RONDE 5 — struktur kelompok laporan PDF, dipakai BERSAMA oleh PdfExporter
 * (gambar PDF asli) dan layar Preview di aplikasi, supaya keduanya selalu
 * mengikuti struktur yang sama persis.
 *
 * Aturan (spek ronde 5):
 *  - PDF hanya memuat TODO UTAMA — Daily Reminder (tanpa jam) TIDAK dimasukkan
 *    sama sekali (penyaringan dilakukan pemanggil lewat [onlyTodoUtama]).
 *  - Penomoran urut (1., 2., 3., ...) di dalam tiap kelompok 1 HARI,
 *    mulai ulang per hari, di SEMUA periode.
 *  - HARIAN : daftar 1 hari bernomor.
 *  - MINGGUAN: kelompok per HARI (sub-header hari khusus).
 *  - BULANAN: kelompok per MINGGU lalu per hari.
 *  - TAHUNAN: kelompok per BULAN -> per minggu -> per hari.
 */

private val pdfLocale = Locale("id", "ID")
private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", pdfLocale)
private val shortFmt = DateTimeFormatter.ofPattern("d MMM", pdfLocale)
private val shortYearFmt = DateTimeFormatter.ofPattern("d MMM yyyy", pdfLocale)
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", pdfLocale)

/** Level blok: 0 = bulan, 1 = minggu, 2 = hari, 3 = baris kegiatan. */
data class PdfBlock(
    val level: Int,
    val label: String,
    val sublabel: String? = null,
    val occurrence: Occurrence? = null,
    val number: Int = 0
)

/** Saring khusus Todo Utama (Daily Reminder dikeluarkan dari PDF sama sekali). */
fun List<Occurrence>.onlyTodoUtama(): List<Occurrence> =
    filter { it.activity.startMinutes != null }

private fun mondayOf(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun weekLabel(monday: LocalDate): String {
    val sunday = monday.plusDays(6)
    return "Minggu ${monday.format(shortFmt)} – ${sunday.format(shortYearFmt)}"
}

private fun daySummary(occs: List<Occurrence>): String {
    val done = occs.count { it.done }
    return "${occs.size} kegiatan • $done selesai"
}

/**
 * Susun blok laporan untuk [period] ("Harian"/"Mingguan"/"Bulanan"/"Tahunan").
 * [occurrences] diasumsikan sudah disaring (Todo Utama saja) & akan diurutkan
 * di sini menurut tanggal lalu jam mulai.
 */
fun buildPdfBlocks(period: String, occurrences: List<Occurrence>): List<PdfBlock> {
    val sorted = occurrences.sortedWith(
        compareBy({ it.date }, { it.activity.startMinutes ?: 0 }, { it.activity.title.lowercase() })
    )
    val blocks = ArrayList<PdfBlock>()
    val byDay = sorted.groupBy { it.date }

    fun addDay(day: LocalDate, dayOccs: List<Occurrence>, withHeader: Boolean) {
        if (withHeader) {
            blocks.add(PdfBlock(level = 2, label = day.format(dayFmt), sublabel = daySummary(dayOccs)))
        }
        dayOccs.forEachIndexed { i, occ ->
            blocks.add(PdfBlock(level = 3, label = occ.activity.title, occurrence = occ, number = i + 1))
        }
    }

    when (period) {
        "Mingguan" -> {
            byDay.keys.sorted().forEach { day -> addDay(day, byDay[day].orEmpty(), withHeader = true) }
        }
        "Bulanan" -> {
            val byWeek = sorted.groupBy { mondayOf(it.date) }
            byWeek.keys.sorted().forEach { monday ->
                val weekOccs = byWeek[monday].orEmpty()
                blocks.add(PdfBlock(level = 1, label = weekLabel(monday), sublabel = daySummary(weekOccs)))
                weekOccs.groupBy { it.date }.keys.sorted().forEach { day ->
                    addDay(day, weekOccs.filter { it.date == day }, withHeader = true)
                }
            }
        }
        "Tahunan" -> {
            val byMonth = sorted.groupBy { it.date.withDayOfMonth(1) }
            byMonth.keys.sorted().forEach { monthStart ->
                val monthOccs = byMonth[monthStart].orEmpty()
                blocks.add(
                    PdfBlock(
                        level = 0,
                        label = monthStart.format(monthFmt),
                        sublabel = daySummary(monthOccs)
                    )
                )
                val byWeek = monthOccs.groupBy { mondayOf(it.date) }
                byWeek.keys.sorted().forEach { monday ->
                    val weekOccs = byWeek[monday].orEmpty()
                    blocks.add(PdfBlock(level = 1, label = weekLabel(monday), sublabel = daySummary(weekOccs)))
                    weekOccs.groupBy { it.date }.keys.sorted().forEach { day ->
                        addDay(day, weekOccs.filter { it.date == day }, withHeader = true)
                    }
                }
            }
        }
        else -> { // Harian: 1 hari, tetap diberi sub-header hari + daftar bernomor
            byDay.keys.sorted().forEach { day -> addDay(day, byDay[day].orEmpty(), withHeader = true) }
        }
    }
    return blocks
}
