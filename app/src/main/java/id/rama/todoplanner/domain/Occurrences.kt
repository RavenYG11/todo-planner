package id.rama.todoplanner.domain

import id.rama.todoplanner.data.ActivityEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Satu kemunculan nyata sebuah kegiatan pada satu tanggal. */
data class Occurrence(val activity: ActivityEntity, val date: LocalDate) {
    val done: Boolean get() = activity.isDoneOn(date.toEpochDay())

    /** RONDE 5: epoch millis waktu occurrence ini diselesaikan; null bila belum / data lama. */
    val completedAtMillis: Long? get() = activity.completedAtMillis(date.toEpochDay())
}

/** Apakah [activity] muncul pada [date] menurut aturan ulanginya? (kalender Gregorian via java.time) */
fun ActivityEntity.occursOn(date: LocalDate): Boolean {
    val start = LocalDate.ofEpochDay(dateEpochDay)
    if (date.isBefore(start)) return false
    return when (repeatRule) {
        "DAILY" -> true
        "WEEKLY" -> date.dayOfWeek == start.dayOfWeek
        "MONTHLY" -> date.dayOfMonth == start.dayOfMonth &&
            ChronoUnit.MONTHS.between(start.withDayOfMonth(1), date.withDayOfMonth(1)) >= 0
        "YEARLY" -> date.month == start.month && date.dayOfMonth == start.dayOfMonth
        else -> date == start // NONE
    }
}

fun ActivityEntity.occurrencesBetween(from: LocalDate, to: LocalDate): List<Occurrence> {
    val out = ArrayList<Occurrence>()
    var d = from
    while (!d.isAfter(to)) {
        if (occursOn(d)) out.add(Occurrence(this, d))
        d = d.plusDays(1)
    }
    return out
}

fun List<ActivityEntity>.occurrencesBetween(from: LocalDate, to: LocalDate): List<Occurrence> =
    flatMap { it.occurrencesBetween(from, to) }
        .sortedWith(compareBy({ it.date }, { it.activity.startMinutes ?: 9 * 60 }))

/** Waktu bunyi reminder untuk satu kemunculan; null bila kegiatan tanpa reminder/jam. */
fun ActivityEntity.reminderDateTime(date: LocalDate): LocalDateTime? {
    val before = reminderMinutesBefore ?: return null
    val time = LocalTime.of((startMinutes ?: 9 * 60) / 60, (startMinutes ?: 9 * 60) % 60)
    return LocalDateTime.of(date, time).minusMinutes(before.toLong())
}

/** Kemunculan berikutnya (mulai hari ini) yang reminder-nya masih di masa depan. */
fun ActivityEntity.nextReminder(now: LocalDateTime): Pair<LocalDate, LocalDateTime>? {
    var d = LocalDate.ofEpochDay(dateEpochDay)
    if (d.isBefore(now.toLocalDate())) d = now.toLocalDate()
    repeat(370) {
        if (occursOn(d)) {
            val t = reminderDateTime(d)
            if (t != null && t.isAfter(now)) return d to t
        }
        d = d.plusDays(1)
    }
    return null
}

/* ================= RONDE 5: checklist ber-interval ================= */

/** Jendela bunyi checklist dalam 1 hari: 06.00–22.00 (keputusan manager, spek ronde 5). */
val CHECKLIST_WINDOW_START: LocalTime = LocalTime.of(6, 0)
val CHECKLIST_WINDOW_END: LocalTime = LocalTime.of(22, 0)

/**
 * Trigger checklist BERIKUTNYA setelah [now]:
 * pada hari kemunculannya, bunyi berulang tiap interval di dalam jendela
 * 06.00–22.00 (tick pertama tepat 06.00, lalu +interval, ... <= 22.00).
 * Occurrence yang sudah dicentang selesai untuk hari itu DILEWATI total
 * (berhenti permanen hari itu) — pencarian lanjut ke hari kemunculan berikut.
 * Mengembalikan (tanggal occurrence, waktu trigger) atau null.
 */
fun ActivityEntity.checklistNextTrigger(now: LocalDateTime): Pair<LocalDate, LocalDateTime>? {
    if (!isChecklist) return null
    val intervalMs = intervalMillis() ?: return null
    var d = LocalDate.ofEpochDay(dateEpochDay)
    if (d.isBefore(now.toLocalDate())) d = now.toLocalDate()
    repeat(370) {
        if (occursOn(d) && !isDoneOn(d.toEpochDay())) {
            val windowStart = LocalDateTime.of(d, CHECKLIST_WINDOW_START)
            val windowEnd = LocalDateTime.of(d, CHECKLIST_WINDOW_END)
            if (now.isBefore(windowStart)) return d to windowStart
            if (now.isBefore(windowEnd) || now == windowEnd) {
                val elapsedMs = java.time.Duration.between(windowStart, now).toMillis()
                val steps = elapsedMs / intervalMs + 1
                val candidate = windowStart.plus(java.time.Duration.ofMillis(steps * intervalMs))
                if (!candidate.isAfter(windowEnd)) return d to candidate
            }
        }
        d = d.plusDays(1)
    }
    return null
}

/**
 * Trigger "Reminder Terdekat" BERIKUTNYA untuk kegiatan apa pun (ronde 5):
 *  - Checklist (tanpa jam + interval): trigger interval berikutnya.
 *  - Todo Utama: reminder kemunculan berikutnya — occurrence yang SUDAH
 *    SELESAI dilewati (tidak boleh tampil lagi, kasus uji Rama ronde 4).
 */
fun ActivityEntity.nextReminderEntry(now: LocalDateTime): Pair<LocalDate, LocalDateTime>? {
    if (isChecklist) return checklistNextTrigger(now)
    var d = LocalDate.ofEpochDay(dateEpochDay)
    if (d.isBefore(now.toLocalDate())) d = now.toLocalDate()
    repeat(370) {
        if (occursOn(d) && !isDoneOn(d.toEpochDay())) {
            val t = reminderDateTime(d)
            if (t != null && t.isAfter(now)) return d to t
        }
        d = d.plusDays(1)
    }
    return null
}

/**
 * RONDE 4 — daftar "Reminder Terdekat" yang BENAR secara presentasi:
 * SATU entri per kegiatan = kemunculan berikutnya SAJA.
 *
 * Dulu layar Beranda & Reminder mengekspansi 14 hari ke depan per kegiatan,
 * jadi dua kegiatan HARIAN ber-reminder sama (mis. 08.45) memenuhi daftar
 * berselang-seling per hari ("gonta-ganti Doa/tes 1") — datanya benar,
 * presentasinya buruk. Seri lengkap tetap terlihat di Detail kegiatan.
 *
 * Urutan: waktu trigger naik; seri (tie) dipecah stabil menurut judul.
 */
fun List<ActivityEntity>.nextReminders(
    now: LocalDateTime,
    limit: Int
): List<Triple<ActivityEntity, LocalDate, LocalDateTime>> =
    mapNotNull { a -> a.nextReminderEntry(now)?.let { (d, t) -> Triple(a, d, t) } }
        .sortedWith(compareBy({ it.third }, { it.first.title.lowercase() }))
        .take(limit)
