package id.rama.todoplanner.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Semua format tanggal Bahasa Indonesia terpusat di sini. */
object Dates {
    private val idLocale = Locale("id", "ID")
    val fullDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", idLocale)
    val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", idLocale)
    val monthTitle: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", idLocale)
    val dayName: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", idLocale)

    fun formatTime(minutes: Int?): String =
        if (minutes == null) "--:--" else LocalTime.of(minutes / 60, minutes % 60).toString()

    /** Sapaan mengikuti jam perangkat: 04-11 pagi, 11-15 siang, 15-19 sore, 19-04 malam. */
    fun greeting(hour: Int): String = when (hour) {
        in 4..10 -> "Selamat pagi"
        in 11..14 -> "Selamat siang"
        in 15..18 -> "Selamat sore"
        else -> "Selamat malam"
    }

    /** Emoji fase hari untuk sapaan Beranda (sesuai mockup: 🌅 pagi, ☀️ siang, 🌇 sore, 🌙 malam). */
    fun phaseEmoji(hour: Int): String = when (hour) {
        in 4..10 -> "🌅"
        in 11..14 -> "☀️"
        in 15..18 -> "🌇"
        else -> "🌙"
    }

    /** Tagline Beranda yang dinamis mengikuti fase hari (versi pagi = kalimat mockup persis). */
    fun phaseTagline(hour: Int): String = when (hour) {
        in 4..10 -> "Hari yang cerah untuk menyelesaikan rencana baik."
        in 11..14 -> "Terus melangkah — selesaikan satu per satu dengan tenang."
        in 15..18 -> "Sore yang teduh untuk menuntaskan rencana hari ini."
        else -> "Malam yang tenang — rapikan sisa hari ini, lalu beristirahat."
    }

    /** Chip aksen fase hari di kartu progres Beranda (pengganti chip cuaca mockup, jujur: bukan data cuaca). */
    fun phaseAccentChip(hour: Int): String = when (hour) {
        in 4..10 -> "🌅 Pagi cerah"
        in 11..14 -> "☀️ Siang terang"
        in 15..18 -> "🌇 Sore teduh"
        else -> "🌙 Malam tenang"
    }

    fun today(): LocalDate = LocalDate.now()
}
