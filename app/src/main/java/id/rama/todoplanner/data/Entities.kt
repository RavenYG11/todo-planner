package id.rama.todoplanner.data

import androidx.room.ColumnInfo
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
 *
 * RONDE 5:
 * - DAILY REMINDER = kegiatan TANPA jam mulai ([startMinutes] == null, lihat [isChecklist]).
 *   Todo Utama = kegiatan DENGAN jam mulai.
 * - Daily Reminder tidak memakai [reminderMinutesBefore]; ia memakai INTERVAL
 *   pengulangan dalam 1 hari: [intervalValue] + [intervalUnit]
 *   ("DETIK" | "MENIT" | "JAM"). Pola hari pengulangan tetap [repeatRule].
 * - [doneTimestamps] : waktu selesai per occurrence, format
 *   "epochDay:epochMillis,epochDay:epochMillis" — pasangan untuk [doneDates].
 *   Bila occurrence selesai tanpa timestamp (data lama), [doneDates] tetap
 *   sumber kebenaran selesai/tidak; timestamp hanya menambah jam selesainya.
 *
 * RONDE 6:
 * - [iconEmoji] : SATU emoji logo kegiatan (grapheme pertama dari input user,
 *   "" = tanpa logo). Kolom baru migrasi Room 2->3.
 * - Semantik Daily Reminder BERUBAH (menggantikan ronde 5): menandai Daily Reminder
 *   SELESAI bukan lagi membunuh rantai hari itu — timer di-RESET: bunyi
 *   berikutnya = waktu ditandai selesai + interval, tetap di dalam jendela
 *   hari aktif (lihat domain checklistNextTrigger). Jangkar reset-nya adalah
 *   timestamp di [doneTimestamps] ini.
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
    val createdAt: Long = System.currentTimeMillis(),
    // RONDE 5 — interval pengulangan Daily Reminder (null = bukan Daily Reminder ber-interval)
    val intervalValue: Int? = null,
    val intervalUnit: String? = null,
    // RONDE 5 — "epochDay:epochMillis,..." waktu selesai per occurrence.
    // defaultValue diselaraskan PERSIS dengan SQL migrasi 1->2
    // (ADD COLUMN ... TEXT NOT NULL DEFAULT '') agar validasi skema Room lolos.
    @ColumnInfo(defaultValue = "") val doneTimestamps: String = "",
    // RONDE 6 — logo emoji kegiatan ("" = tanpa logo). defaultValue PERSIS
    // sama dengan SQL migrasi 2->3 agar validasi skema Room lolos.
    @ColumnInfo(defaultValue = "") val iconEmoji: String = "",
    // RONDE 7 — DAFTAR waktu penandaan selesai Daily Reminder per hari.
    // Format: entri per hari dipisah ";", tiap entri "epochDay:millis,millis,..."
    // (banyak timestamp dalam satu hari — SATU per penandaan). Berbeda dari
    // [doneTimestamps] yang hanya SATU timestamp per hari untuk Todo Utama.
    // Daily Reminder TIDAK memakai doneDates: menandainya BUKAN state selesai.
    // defaultValue PERSIS sama dengan SQL migrasi 3->4.
    @ColumnInfo(defaultValue = "") val dailyDoneLog: String = "",
    // RONDE 9 — warna pilihan user per kegiatan, format hex "#RRGGBB"
    // (null = TIDAK dipilih -> ikut warna kategori lama, lihat
    // ui/components.Common.activityColor). Kolom nullable baru migrasi
    // Room 4->5, jadi data lama & backup lama (tanpa field ini) aman:
    // null = perilaku persis sebelum ronde 9. Daily Reminder TIDAK wajib
    // mengikuti warna ini (hanya Todo Utama yang diwarnai custom).
    val colorHex: String? = null
) {
    /** RONDE 5: Daily Reminder = TANPA jam mulai. Todo Utama = ada jam mulai. */
    val isChecklist: Boolean get() = startMinutes == null

    /** RONDE 6: judul siap tampil — logo emoji di depan judul bila ada. */
    val displayTitle: String get() = if (iconEmoji.isBlank()) title else "$iconEmoji $title"

    fun isDoneOn(epochDay: Long): Boolean =
        doneDates.split(",").any { it.trim() == epochDay.toString() }

    /** Peta epochDay -> epochMillis waktu selesai (hanya occurrence yang punya timestamp). */
    fun doneTimestampMap(): Map<Long, Long> =
        doneTimestamps.split(",").mapNotNull { part ->
            val bits = part.trim().split(":")
            if (bits.size != 2) null
            else bits[0].toLongOrNull()?.let { d -> bits[1].toLongOrNull()?.let { t -> d to t } }
        }.toMap()

    /** Waktu selesai (epoch millis) occurrence ini; null bila belum selesai / data lama tanpa timestamp. */
    fun completedAtMillis(epochDay: Long): Long? = doneTimestampMap()[epochDay]

    /**
     * Tandai selesai / batalkan selesai untuk SATU occurrence.
     * Selesai = simpan timestamp [atMillis] (default: sekarang).
     * Batalkan selesai = timestamp occurrence itu DIHAPUS total (restart penuh).
     */
    fun withDoneOn(epochDay: Long, done: Boolean, atMillis: Long = System.currentTimeMillis()): ActivityEntity {
        val set = doneDates.split(",").filter { it.isNotBlank() }.toMutableSet()
        val stamps = doneTimestampMap().toMutableMap()
        if (done) {
            set.add(epochDay.toString())
            stamps[epochDay] = atMillis
        } else {
            set.remove(epochDay.toString())
            stamps.remove(epochDay)
        }
        return copy(
            doneDates = set.joinToString(","),
            doneTimestamps = stamps.entries.sortedBy { it.key }
                .joinToString(",") { "${it.key}:${it.value}" }
        )
    }

    /**
     * RONDE 7 — semua waktu penandaan selesai Daily Reminder pada [epochDay],
     * urut naik (kosong = belum pernah ditandai hari itu). Fallback data lama:
     * timestamp ronde 6 di [doneTimestamps] untuk hari itu dihitung sebagai
     * satu penandaan, supaya riwayat & jangkar reset tidak hilang saat update.
     */
    fun dailyCompletionTimes(epochDay: Long): List<Long> {
        val fromLog = dailyDoneLog.split(";").mapNotNull { entry ->
            val bits = entry.trim().split(":")
            if (bits.size != 2 || bits[0].toLongOrNull() != epochDay) null
            else bits[1].split(",").mapNotNull { it.trim().toLongOrNull() }
        }.flatten()
        if (fromLog.isNotEmpty()) return fromLog.sorted()
        val legacy = completedAtMillis(epochDay)
        return if (isChecklist && legacy != null) listOf(legacy) else emptyList()
    }

    /** RONDE 7 — waktu penandaan Daily Reminder TERAKHIR pada [epochDay]; null bila belum ada. */
    fun lastDailyCompletion(epochDay: Long): Long? = dailyCompletionTimes(epochDay).lastOrNull()

    /**
     * RONDE 7 — catat SATU penandaan selesai Daily Reminder pada [epochDay]
     * pukul [atMillis]: DITAMBAHKAN ke daftar hari itu (tidak menimpa, tidak
     * ter-reset oleh bunyi berikutnya), dan doneDates TIDAK disentuh.
     */
    fun withDailyCompletion(epochDay: Long, atMillis: Long = System.currentTimeMillis()): ActivityEntity {
        val byDay = dailyDoneLog.split(";").mapNotNull { entry ->
            val bits = entry.trim().split(":")
            if (bits.size != 2) null
            else bits[0].toLongOrNull()?.let { d ->
                d to bits[1].split(",").mapNotNull { it.trim().toLongOrNull() }.toMutableList()
            }
        }.toMap().toMutableMap()
        val list = byDay.getOrPut(epochDay) { mutableListOf() }
        list.add(atMillis); list.sort()
        val encoded = byDay.entries.sortedBy { it.key }
            .joinToString(";") { (d, times) -> "$d:${times.joinToString(",")}" }
        return copy(dailyDoneLog = encoded)
    }

    /** Panjang interval Daily Reminder dalam milidetik; null bila interval belum diatur. */
    fun intervalMillis(): Long? {
        val v = intervalValue ?: return null
        if (v <= 0) return null
        return when (intervalUnit) {
            "DETIK" -> v * 1_000L
            "JAM" -> v * 3_600_000L
            else -> v * 60_000L // "MENIT" (default)
        }
    }

    /** Label interval yang dibaca manusia, mis. "30 Detik", "5 Menit", "1 Jam". */
    fun intervalLabel(): String {
        val v = intervalValue ?: return "-"
        val unit = when (intervalUnit) {
            "DETIK" -> "Detik"
            "JAM" -> "Jam"
            else -> "Menit"
        }
        return "$v $unit"
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
