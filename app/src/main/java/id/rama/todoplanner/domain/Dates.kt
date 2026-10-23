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

    fun today(): LocalDate = LocalDate.now()
}
