package id.rama.todoplanner.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fase waktu tunggal (RONDE 3) — SATU sumber kebenaran untuk sapaan, emoji,
 * tagline, gradasi header, dan ilustrasi header Beranda.
 * Batas fase persis keputusan Rama:
 * PAGI 04.00–10.59 • SIANG 11.00–14.59 • SORE 15.00–18.59 • MALAM 19.00–03.59.
 * Jangan hardcode fase/jam di layar — selalu lewat [Dates.phaseOf].
 */
enum class DayPhase { PAGI, SIANG, SORE, MALAM }

/** Semua format tanggal Bahasa Indonesia terpusat di sini. */
object Dates {
    private val idLocale = Locale("id", "ID")
    val fullDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", idLocale)
    val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", idLocale)
    val monthTitle: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", idLocale)
    val dayName: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", idLocale)

    fun formatTime(minutes: Int?): String =
        if (minutes == null) "--:--" else LocalTime.of(minutes / 60, minutes % 60).toString()

    /** RONDE 5: format jam selesai "HH:mm" (24 jam, tanpa detik) dari epoch millis. */
    fun formatCompletedTime(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm"))

    /**
     * RONDE 5: label selesai yang tampil di Harian/Checklist/Detail:
     * "Selesai • HH:mm" bila timestamp ada; "Selesai" saja untuk data lama.
     */
    fun completedLabel(epochMillis: Long?): String =
        if (epochMillis != null) "Selesai • ${formatCompletedTime(epochMillis)}" else "Selesai"

    /** Fase dari jam perangkat — batasnya sama persis dengan tabel sapaan di bawah. */
    fun phaseOf(hour: Int): DayPhase = when (hour) {
        in 4..10 -> DayPhase.PAGI
        in 11..14 -> DayPhase.SIANG
        in 15..18 -> DayPhase.SORE
        else -> DayPhase.MALAM
    }

    /** Sapaan mengikuti jam perangkat: 04-11 pagi, 11-15 siang, 15-19 sore, 19-04 malam. */
    fun greeting(hour: Int): String = when (phaseOf(hour)) {
        DayPhase.PAGI -> "Selamat pagi"
        DayPhase.SIANG -> "Selamat siang"
        DayPhase.SORE -> "Selamat sore"
        DayPhase.MALAM -> "Selamat malam"
    }

    /** Emoji fase hari untuk sapaan Beranda (sesuai mockup: 🌅 pagi, ☀️ siang, 🌇 sore, 🌙 malam). */
    fun phaseEmoji(hour: Int): String = when (phaseOf(hour)) {
        DayPhase.PAGI -> "🌅"
        DayPhase.SIANG -> "☀️"
        DayPhase.SORE -> "🌇"
        DayPhase.MALAM -> "🌙"
    }

    /** Tagline Beranda yang dinamis mengikuti fase hari (versi pagi = kalimat mockup persis). */
    fun phaseTagline(hour: Int): String = when (phaseOf(hour)) {
        DayPhase.PAGI -> "Hari yang cerah untuk menyelesaikan rencana baik."
        DayPhase.SIANG -> "Terus melangkah — selesaikan satu per satu dengan tenang."
        DayPhase.SORE -> "Sore yang teduh untuk menuntaskan rencana hari ini."
        DayPhase.MALAM -> "Malam yang tenang — rapikan sisa hari ini, lalu beristirahat."
    }

    /** Chip aksen fase hari di kartu progres Beranda (pengganti chip cuaca mockup, jujur: bukan data cuaca). */
    fun phaseAccentChip(hour: Int): String = when (phaseOf(hour)) {
        DayPhase.PAGI -> "🌅 Pagi cerah"
        DayPhase.SIANG -> "☀️ Siang terang"
        DayPhase.SORE -> "🌇 Sore teduh"
        DayPhase.MALAM -> "🌙 Malam tenang"
    }

    fun today(): LocalDate = LocalDate.now()
}
