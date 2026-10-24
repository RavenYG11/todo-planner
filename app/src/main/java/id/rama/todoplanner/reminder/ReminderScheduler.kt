package id.rama.todoplanner.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import id.rama.todoplanner.MainActivity
import id.rama.todoplanner.data.ActivityEntity
import id.rama.todoplanner.data.AppPrefs
import id.rama.todoplanner.domain.checklistNextTrigger
import id.rama.todoplanner.domain.nextTodoAlarm
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Menjadwalkan alarm reminder lokal dengan AlarmManager.
 * Nada efektif = nada per kegiatan, atau global bila kegiatan tidak mengatur sendiri.
 *
 * PERILAKU (ronde 1, dipertahankan): opsi "Tepat waktu" dihapus sebagai
 * pilihan tersendiri — SETIAP kegiatan ber-reminder mendapat 2 alarm:
 *  1. alarm pengingat awal pada (jam mulai − offset), dan
 *  2. alarm pas jam mulai kegiatan itu sendiri.
 * Reminder "Tanpa" (null) = tidak ada alarm sama sekali.
 * Suara + getar berlaku untuk semua prioritas kegiatan.
 *
 * RONDE 6 — SKEMA REQUEST CODE ditulis ulang & PEMBATALAN dibuat SIMETRIS:
 *  - Skema ronde 5 (early=id*2, start=id*2+1, checklist=700000+id, legacy=id)
 *    BERTABRAKAN antar-kegiatan (legacy id N == early id N/2 untuk N genap)
 *    dan [cancel] lama TIDAK PERNAH membatalkan varian WAKE/full-screen
 *    (PendingIntent getActivity berkode alarm + 500.000 di ReminderReceiver
 *    & ReminderSoundService). Kombinasi itu salah satu akar bug "todo sudah
 *    selesai masih bunyi": pembatalan tidak mencakup semua yang dijadwalkan.
 *  - Skema baru: basis 2.000.000 + id*10 + jenis (0=awal, 1=mulai,
 *    2=checklist) — tidak bertabrakan dengan kode lama, kode tes (990.002),
 *    maupun kode WAKE-nya sendiri (+500.000).
 *  - [cancelAll] membatalkan SEMUA varian untuk satu kegiatan: alarm awal,
 *    alarm pas-mulai, rantai checklist, varian WAKE (+500.000) dari ketiganya,
 *    kode skema LAMA (agar alarm yatim versi sebelumnya ikut mati), notifikasi
 *    yang telanjur tampil, dan tes tidak disentuh (bukan milik kegiatan).
 *    Daftar kode dihitung dari fungsi yang SAMA untuk jalur schedule & cancel.
 */
object ReminderScheduler {

    private const val KIND_EARLY = "early"
    private const val KIND_START = "start"
    private const val KIND_TEST = "test"
    const val KIND_CHECKLIST = "checklist"

    /** Request code & ID notifikasi khusus alarm tes (jauh dari kode kegiatan). */
    const val TEST_REQUEST_CODE = 990_002

    /* ---------- skema request code (ronde 6) ---------- */
    private const val CODE_BASE = 2_000_000
    private fun earlyCode(id: Long): Int = CODE_BASE + (id * 10).toInt()
    private fun startCode(id: Long): Int = CODE_BASE + (id * 10).toInt() + 1
    private fun checklistCode(id: Long): Int = CODE_BASE + (id * 10).toInt() + 2

    /* Kode skema LAMA (ronde <=5) — hanya untuk membersihkan alarm yatim. */
    private fun oldEarlyCode(id: Long): Int = (id * 2).toInt()
    private fun oldStartCode(id: Long): Int = (id * 2 + 1).toInt()
    private fun oldChecklistCode(id: Long): Int = (700_000 + id).toInt()

    /** SEMUA kode alarm broadcast milik satu kegiatan (baru + lama + legacy). */
    private fun allAlarmCodes(id: Long): List<Int> = listOf(
        earlyCode(id), startCode(id), checklistCode(id),
        oldEarlyCode(id), oldStartCode(id), oldChecklistCode(id),
        id.toInt() // alarm tunggal skema paling awal
    ).distinct()

    /** Kode varian WAKE/full-screen (getActivity) untuk kode alarm apa pun. */
    private fun wakeCode(alarmCode: Int): Int = alarmCode + MainActivity.WAKE_PI_OFFSET

    fun schedule(context: Context, activity: ActivityEntity, prefs: AppPrefs) {
        cancelAll(context, activity.id)
        // Checklist (tanpa jam) memakai alarm berantai interval, BUKAN
        // pasangan alarm early/start todo utama.
        if (activity.isChecklist) {
            scheduleChecklist(context, activity, prefs)
            return
        }
        if (activity.reminderMinutesBefore == null) return
        val offset = activity.reminderMinutesBefore ?: return
        val now = java.time.LocalDateTime.now()
        // RONDE 6: pencarian pasangan alarm memakai domain nextTodoAlarm —
        // implementasi yang sama dengan daftar Mendatang (tidak bisa meleset).
        val next = activity.nextTodoAlarm(now) ?: return
        val (occDate, earlyAt, startAt) = next
        // RONDE 3: resolver tunggal — sentinel per-kegiatan "default" menang
        // atas global kustom; null per-kegiatan = ikuti global. Lihat ToneResolver.
        val toneUri = ToneResolver.effectiveToneUri(activity.ringtoneUri, prefs.globalRingtoneUri)
        val toneName = ToneResolver.effectiveToneLabel(activity.ringtoneUri, activity.ringtoneName, prefs.globalRingtoneName)
        val jam = startTimeText(activity)

        if (earlyAt != null) {
            setAlarm(
                context, earlyCode(activity.id), KIND_EARLY,
                title = "⏰ ${offsetLabel(offset)} lagi: ${activity.displayTitle}",
                text = "Mulai pukul $jam",
                triggerMillis = earlyAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                toneUri = toneUri, toneName = toneName,
                activityId = activity.id, occEpochDay = occDate.toEpochDay()
            )
        }
        setAlarm(
            context, startCode(activity.id), KIND_START,
            title = "Mulai sekarang: ${activity.displayTitle}",
            text = "Pukul $jam — kegiatan dimulai sekarang",
            triggerMillis = startAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            toneUri = toneUri, toneName = toneName,
            activityId = activity.id, occEpochDay = occDate.toEpochDay()
        )
    }

    /**
     * Jadwalkan SATU alarm checklist berikutnya (alarm berantai): hanya satu
     * alarm aktif tiap saat; setelah berbunyi, ReminderReceiver memanggil
     * [schedule] lagi untuk tick berikutnya. Trigger dihitung domain
     * (checklistNextTrigger) mengikuti JENDELA HARI AKTIF user (ronde 6,
     * dari AppPrefs — bukan lagi konstanta 06.00–22.00) dan semantik RESET
     * ronde 6 (selesai + interval).
     */
    private fun scheduleChecklist(context: Context, activity: ActivityEntity, prefs: AppPrefs) {
        val windowStart = LocalTime.of(prefs.dayStartMinutes / 60, prefs.dayStartMinutes % 60)
        val windowEnd = LocalTime.of(prefs.dayEndMinutes / 60, prefs.dayEndMinutes % 60)
        val next = activity.checklistNextTrigger(java.time.LocalDateTime.now(), windowStart, windowEnd) ?: return
        val (occDate, trigger) = next
        val toneUri = ToneResolver.effectiveToneUri(activity.ringtoneUri, prefs.globalRingtoneUri)
        val toneName = ToneResolver.effectiveToneLabel(activity.ringtoneUri, activity.ringtoneName, prefs.globalRingtoneName)
        setAlarm(
            context, checklistCode(activity.id), KIND_CHECKLIST,
            title = "✓ Checklist: ${activity.displayTitle}",
            text = "Pengingat checklist — tiap ${activity.intervalLabel()}",
            triggerMillis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            toneUri = toneUri, toneName = toneName,
            activityId = activity.id, occEpochDay = occDate.toEpochDay()
        )
    }

    /** Alarm tes: berbunyi 10 detik dari sekarang dengan nada global terpilih. */
    fun scheduleTest(context: Context, prefs: AppPrefs) {
        val trigger = System.currentTimeMillis() + 10_000L
        setAlarm(
            context, TEST_REQUEST_CODE, KIND_TEST,
            title = "🔔 Tes Reminder — Todo Planner",
            text = "Bunyi + getar tes. Nada: ${prefs.globalRingtoneName}",
            triggerMillis = trigger,
            toneUri = ToneResolver.effectiveToneUri(null, prefs.globalRingtoneUri),
            toneName = prefs.globalRingtoneName,
            activityId = 0L, occEpochDay = LocalDate.now().toEpochDay()
        )
    }

    /** Jadwalkan ulang SEMUA kegiatan (aplikasi dibuka / boot / nada global berubah). */
    fun rescheduleAll(context: Context, activities: List<ActivityEntity>, prefs: AppPrefs) {
        activities.forEach { schedule(context, it, prefs) }
    }

    private fun setAlarm(
        context: Context,
        requestCode: Int,
        kind: String,
        title: String,
        text: String,
        triggerMillis: Long,
        toneUri: String?,
        toneName: String?,
        activityId: Long,
        occEpochDay: Long
    ) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ID, requestCode.toLong())
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_TEXT, text)
            putExtra(ReminderReceiver.EXTRA_KIND, kind)
            putExtra(ReminderReceiver.EXTRA_TONE_URI, toneUri)
            putExtra(ReminderReceiver.EXTRA_TONE_NAME, toneName)
            // RONDE 4: identitas kegiatan untuk fullScreenIntent -> Detail.
            putExtra(ReminderReceiver.EXTRA_ACTIVITY_ID, activityId)
            putExtra(ReminderReceiver.EXTRA_OCC_EPOCH_DAY, occEpochDay)
        }
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        }
    }

    private fun startTimeText(activity: ActivityEntity): String {
        val m = activity.startMinutes ?: 9 * 60
        return "%02d.%02d".format(m / 60, m % 60)
    }

    private fun offsetLabel(min: Int): String = when (min) {
        15 -> "15 menit"
        60 -> "1 jam"
        1440 -> "1 hari"
        else -> "$min menit"
    }

    /**
     * RONDE 6 — batalkan SEMUA PendingIntent milik satu kegiatan (dipakai
     * toggleDone lewat [schedule], hapus kegiatan, dan receiver):
     * alarm awal + pas-mulai + rantai checklist (skema baru DAN lama),
     * varian WAKE/full-screen (+500.000) dari semua kode itu, serta
     * notifikasi yang mungkin telanjur tampil dengan ID tersebut.
     */
    fun cancelAll(context: Context, activityId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val codes = allAlarmCodes(activityId)
        codes.forEach { code ->
            val intent = Intent(context, ReminderReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return@forEach
            am.cancel(pending)
            pending.cancel()
        }
        // Varian WAKE/full-screen: PendingIntent getActivity (bukan alarm,
        // jadi dibatalkan lewat pending.cancel(), bukan AlarmManager).
        codes.forEach { code ->
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, wakeCode(code), intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return@forEach
            pending.cancel()
        }
        // Notifikasi occurrence yang telanjur tampil ikut dibersihkan.
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            codes.forEach { code -> nm.cancel(code) }
        } catch (e: Exception) { /* bukan jalur kritis */ }
    }

    /** Nama lama dipertahankan untuk pemanggil yang sudah ada. */
    fun cancel(context: Context, activityId: Long) = cancelAll(context, activityId)
}
