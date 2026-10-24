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

    /** RONDE 7: daftar waktu penandaan selesai Daily Reminder hari ini (urut naik). */
    val dailyCompletions: List<Long> get() = activity.dailyCompletionTimes(date.toEpochDay())

    /** RONDE 7: Daily Reminder hari ini sudah pernah ditandai (bukan state selesai). */
    val dailyMarked: Boolean get() = dailyCompletions.isNotEmpty()
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

/* ================= RONDE 5/6: Daily Reminder ber-interval ================= */

/**
 * Jendela hari aktif DEFAULT (06.00–22.00). Sejak RONDE 6 jendela ini bisa
 * diatur user (AppPrefs.dayStartMinutes/dayEndMinutes) dan diteruskan ke
 * fungsi-fungsi di bawah sebagai parameter; konstanta ini hanya nilai bawaan
 * bila pemanggil tidak menyediakan jendela.
 */
val CHECKLIST_WINDOW_START: LocalTime = LocalTime.of(6, 0)
val CHECKLIST_WINDOW_END: LocalTime = LocalTime.of(22, 0)

/**
 * Trigger Daily Reminder BERIKUTNYA setelah [now] di dalam jendela hari aktif
 * ([windowStart]..[windowEnd], default 06.00–22.00):
 *
 *  - BELUM selesai hari itu: tick berjangkar awal jendela (tick pertama tepat
 *    [windowStart], lalu +interval, ... <= [windowEnd]) — perilaku ronde 5.
 *  - SUDAH ditandai selesai hari itu (RONDE 6, MENGGANTIKAN "berhenti hari
 *    itu" ronde 5): timer di-RESET — bunyi berikutnya = waktu ditandai
 *    selesai (timestamp doneTimestamps) + interval, lalu +interval lagi
 *    selama masih <= [windowEnd]; keluar jendela/hari = lanjut ke hari
 *    kemunculan berikutnya sesuai pola ulangan (jangkar awal jendela lagi).
 *    "Batalkan Selesai" menghapus timestamp -> kembali ke jangkar awal jendela.
 *  - Selesai TANPA timestamp (data lama): tidak ada jangkar reset, hari itu
 *    dilewati (perilaku ronde 5) demi keamanan.
 *
 * Mengembalikan (tanggal occurrence, waktu trigger) atau null.
 */
fun ActivityEntity.checklistNextTrigger(
    now: LocalDateTime,
    windowStart: LocalTime = CHECKLIST_WINDOW_START,
    windowEnd: LocalTime = CHECKLIST_WINDOW_END
): Pair<LocalDate, LocalDateTime>? {
    if (!isChecklist) return null
    val intervalMs = intervalMillis() ?: return null
    val interval = java.time.Duration.ofMillis(intervalMs)
    var d = LocalDate.ofEpochDay(dateEpochDay)
    if (d.isBefore(now.toLocalDate())) d = now.toLocalDate()
    repeat(370) {
        if (occursOn(d)) {
            val ws = LocalDateTime.of(d, windowStart)
            val we = LocalDateTime.of(d, windowEnd)
            // RONDE 7 — jangkar RESET kini dari penandaan Daily Reminder
            // TERAKHIR hari itu (daftar dailyDoneLog; fallback timestamp
            // ronde 6). doneDates TIDAK lagi dipakai Daily Reminder.
            val doneTs = lastDailyCompletion(d.toEpochDay())
                ?: completedAtMillis(d.toEpochDay()).takeIf { isDoneOn(d.toEpochDay()) }
            when {
                doneTs != null -> {
                    // RONDE 6/7 — jangkar RESET: penandaan terakhir + interval.
                    var cand: LocalDateTime? = LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(doneTs), java.time.ZoneId.systemDefault()
                    ).plus(interval)
                    if (cand!!.isBefore(ws)) cand = ws
                    if (cand.toLocalDate() == d && !cand.isAfter(we)) {
                        while (!cand!!.isAfter(now)) {
                            cand = cand.plus(interval)
                            if (cand.isAfter(we) || cand.toLocalDate() != d) { cand = null; break }
                        }
                        cand?.let { return d to it }
                    }
                    // Keluar jendela/hari -> lanjut hari kemunculan berikutnya.
                }
                isDoneOn(d.toEpochDay()) -> Unit // selesai tanpa timestamp: lewati hari ini
                else -> {
                    if (now.isBefore(ws)) return d to ws
                    if (now.isBefore(we) || now == we) {
                        val elapsedMs = java.time.Duration.between(ws, now).toMillis()
                        val steps = elapsedMs / intervalMs + 1
                        val candidate = ws.plus(java.time.Duration.ofMillis(steps * intervalMs))
                        if (!candidate.isAfter(we)) return d to candidate
                    }
                }
            }
        }
        d = d.plusDays(1)
    }
    return null
}

/* ================= RONDE 6: pasangan alarm Todo Utama (satu sumber) ================= */

/**
 * Kemunculan Todo Utama berikutnya yang BELUM selesai dan jam mulainya masih
 * di masa depan: (tanggal, waktu pengingat awal?, waktu mulai).
 * Waktu pengingat awal null bila offset 0 / "Tanpa" / sudah lewat — alarm
 * pas-mulai tetap ada. FUNGSI INI satu-satunya implementasi pencarian ini:
 * ReminderScheduler (penjadwalan alarm) dan [nextReminderEntry] (tampilan
 * Mendatang) sama-sama memakainya, supaya yang tampil di daftar PERSIS
 * yang terjadwal sebagai alarm.
 *
 * AUDIT RONDE 6 (bug "Todo Utama tidak muncul di Mendatang" ronde 5):
 * nextReminderEntry lama hanya memakai waktu pengingat AWAL (mulai - offset)
 * sebagai titik acuan — setelah waktu awal lewat tetapi jam mulai BELUM tiba,
 * todo hilang dari daftar padahal alarm pas-mulai masih terjadwal (untuk
 * offset 1 hari, hilang 1 hari penuh); dan todo ber-reminder "Tanpa" tidak
 * pernah bisa muncul sama sekali.
 */
fun ActivityEntity.nextTodoAlarm(now: LocalDateTime): Triple<LocalDate, LocalDateTime?, LocalDateTime>? {
    if (isChecklist) return null
    val startTime = LocalTime.of((startMinutes ?: 9 * 60) / 60, (startMinutes ?: 9 * 60) % 60)
    val offset = reminderMinutesBefore
    var d = LocalDate.ofEpochDay(dateEpochDay)
    if (d.isBefore(now.toLocalDate())) d = now.toLocalDate()
    repeat(370) {
        if (occursOn(d) && !isDoneOn(d.toEpochDay())) {
            val start = LocalDateTime.of(d, startTime)
            if (start.isAfter(now)) {
                val early = offset?.takeIf { it > 0 }?.let { start.minusMinutes(it.toLong()) }
                    ?.takeIf { it.isAfter(now) }
                return Triple(d, early, start)
            }
        }
        d = d.plusDays(1)
    }
    return null
}

/**
 * Trigger "Schedule Warning / Mendatang" BERIKUTNYA untuk kegiatan apa pun:
 *  - Daily Reminder (tanpa jam + interval): trigger interval berikutnya di
 *    jendela hari aktif (semantik RESET ronde 6).
 *  - Todo Utama: alarm berikutnya dari [nextTodoAlarm] — pengingat awal bila
 *    masih di masa depan, selain itu alarm pas-mulai; todo ber-reminder
 *    "Tanpa" tampil dengan waktu mulai. Occurrence SELESAI selalu dilewati.
 */
fun ActivityEntity.nextReminderEntry(
    now: LocalDateTime,
    windowStart: LocalTime = CHECKLIST_WINDOW_START,
    windowEnd: LocalTime = CHECKLIST_WINDOW_END
): Pair<LocalDate, LocalDateTime>? {
    if (isChecklist) return checklistNextTrigger(now, windowStart, windowEnd)
    val next = nextTodoAlarm(now) ?: return null
    return next.first to (next.second ?: next.third)
}

/**
 * RONDE 4 — daftar "Schedule Warning" yang BENAR secara presentasi:
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
    limit: Int,
    windowStart: LocalTime = CHECKLIST_WINDOW_START,
    windowEnd: LocalTime = CHECKLIST_WINDOW_END
): List<Triple<ActivityEntity, LocalDate, LocalDateTime>> =
    mapNotNull { a -> a.nextReminderEntry(now, windowStart, windowEnd)?.let { (d, t) -> Triple(a, d, t) } }
        .sortedWith(compareBy({ it.third }, { it.first.title.lowercase() }))
        .take(limit)
