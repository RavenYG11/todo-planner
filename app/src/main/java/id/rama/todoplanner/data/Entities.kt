package id.rama.todoplanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Satu baris = satu kegiatan (atau satu SERI kegiatan bila [repeatRule] != NONE).
 * Penyelesaian per-tanggal disimpan di [doneDates] (daftar epochDay dipisah koma),
 * supaya menandai selesai pada satu tanggal tidak menyelesaikan seluruh serinya.
 *
 * Konvensi kolom:
 * - [dateEpochDay] : tanggal mulai, java.time.LocalDate.toEpochDay()
 * - [startMinutes]/[endMinutes] : menit sejak 00:00 (null = tanpa jam)
 * - [reminderMinutesBefore] : null = tanpa reminder; 0 = tepat waktu; 15/60/1440 = sebelum mulai
 * - [ringtoneUri] : null = ikut global; "silent" = senyap/getar; selain itu URI nada pilihan user
 */
@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dateEpochDay: Long,
    val startMinutes: Int? = null,
    val endMinutes: Int? = null,
    val template: String = "Harian",
    val repeatRule: String = "NONE",
    val reminderMinutesBefore: Int? = null,
    val ringtoneUri: String? = null,
    val ringtoneName: String? = null,
    val priority: String = "Sedang",
    val category: String = "Kerja",
    val note: String = "",
    val doneDates: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun isDoneOn(epochDay: Long): Boolean =
        doneDates.split(",").any { it.trim() == epochDay.toString() }

    fun withDoneOn(epochDay: Long, done: Boolean): ActivityEntity {
        val set = doneDates.split(",").filter { it.isNotBlank() }.toMutableSet()
        if (done) set.add(epochDay.toString()) else set.remove(epochDay.toString())
        return copy(doneDates = set.joinToString(","))
    }
}

/** Target tahunan per kuartal (meter progres di layar Tahunan). */
@Entity(tableName = "targets")
data class TargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val target: Int,
    val current: Int = 0,
    val quarter: Int,
    val year: Int
)
