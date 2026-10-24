package id.rama.todoplanner.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import id.rama.todoplanner.data.ActivityEntity
import id.rama.todoplanner.data.AppPrefs
import id.rama.todoplanner.domain.checklistNextTrigger
import id.rama.todoplanner.domain.occursOn
import java.time.LocalDate
import java.time.LocalDateTime
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
 * RONDE 2: ditambah [scheduleTest] (tombol "Tes Reminder 10 Detik" di
 * Pengaturan/Reminder) dan [rescheduleAll] (dipakai saat aplikasi dibuka &
 * setelah boot, supaya kegiatan LAMA dari versi sebelumnya ikut berbunyi
 * dengan jalur bunyi baru).
 */
object ReminderScheduler {

    private const val KIND_EARLY = "early"
    private const val KIND_START = "start"
    private const val KIND_TEST = "test"
    const val KIND_CHECKLIST = "checklist"

    /** Request code & ID notifikasi khusus alarm tes (jauh dari kode kegiatan: id*2 / id*2+1). */
    const val TEST_REQUEST_CODE = 990_002

    fun schedule(context: Context, activity: ActivityEntity, prefs: AppPrefs) {
        cancel(context, activity.id)
        // RONDE 5: checklist (tanpa jam) memakai alarm berantai interval,
        // BUKAN pasangan alarm early/start todo utama.
        if (activity.isChecklist) {
            scheduleChecklist(context, activity, prefs)
            return
        }
        val offset = activity.reminderMinutesBefore ?: return
        val now = LocalDateTime.now()
        val pair = nextAlarmPair(activity, offset, now) ?: return
        val (earlyAt, startAt) = pair
        // RONDE 3: resolver tunggal — sentinel per-kegiatan "default" menang
        // atas global kustom; null per-kegiatan = ikuti global. Lihat ToneResolver.
        val toneUri = ToneResolver.effectiveToneUri(activity.ringtoneUri, prefs.globalRingtoneUri)
        val toneName = ToneResolver.effectiveToneLabel(activity.ringtoneUri, activity.ringtoneName, prefs.globalRingtoneName)
        val jam = startTimeText(activity)

        // RONDE 4: tanggal kemunculan = tanggal jam mulai (dipakai fullScreenIntent
        // untuk membuka Detail tanggal yang tepat).
        val occEpochDay = startAt.toLocalDate().toEpochDay()
        if (earlyAt != null) {
            setAlarm(
                context, earlyCode(activity.id), KIND_EARLY,
                title = "⏰ ${offsetLabel(offset)} lagi: ${activity.title}",
                text = "Mulai pukul $jam",
                triggerMillis = earlyAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                toneUri = toneUri, toneName = toneName,
                activityId = activity.id, occEpochDay = occEpochDay
            )
        }
        setAlarm(
            context, startCode(activity.id), KIND_START,
            title = "Mulai sekarang: ${activity.title}",
            text = "Pukul $jam — kegiatan dimulai sekarang",
            triggerMillis = startAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            toneUri = toneUri, toneName = toneName,
            activityId = activity.id, occEpochDay = occEpochDay
        )
    }

    /**
     * RONDE 5 — jadwalkan SATU alarm checklist berikutnya (alarm berantai):
     * hanya satu alarm aktif tiap saat; setelah berbunyi, ReminderReceiver
     * memanggil [schedule] lagi untuk tick berikutnya. Trigger dihitung oleh
     * domain (checklistNextTrigger): jendela 06.00–22.00, berhenti untuk hari
     * itu begitu occurrence dicentang selesai (trigger berikutnya = hari
     * kemunculan berikutnya, atau tidak ada sama sekali).
     */
    private fun scheduleChecklist(context: Context, activity: ActivityEntity, prefs: AppPrefs) {
        val next = activity.checklistNextTrigger(LocalDateTime.now()) ?: return
        val (occDate, trigger) = next
        val toneUri = ToneResolver.effectiveToneUri(activity.ringtoneUri, prefs.globalRingtoneUri)
        val toneName = ToneResolver.effectiveToneLabel(activity.ringtoneUri, activity.ringtoneName, prefs.globalRingtoneName)
        setAlarm(
            context, checklistCode(activity.id), KIND_CHECKLIST,
            title = "✓ Checklist: ${activity.title}",
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

    /**
     * Pasangan alarm untuk kemunculan terdekat yang jam mulainya masih di masa depan.
     * first = waktu pengingat awal (null bila offset 0 / sudah lewat), second = jam mulai.
     */
    private fun nextAlarmPair(
        activity: ActivityEntity,
        offset: Int,
        now: LocalDateTime
    ): Pair<LocalDateTime?, LocalDateTime>? {
        val startTime = LocalTime.of(
            (activity.startMinutes ?: 9 * 60) / 60,
            (activity.startMinutes ?: 9 * 60) % 60
        )
        var d = LocalDate.ofEpochDay(activity.dateEpochDay)
        if (d.isBefore(now.toLocalDate())) d = now.toLocalDate()
        repeat(370) {
            // RONDE 5: occurrence yang sudah SELESAI dilewati — alarmnya tidak
            // boleh dijadwalkan lagi (kasus uji: kegiatan selesai masih tampil/berbunyi).
            if (activity.occursOn(d) && !activity.isDoneOn(d.toEpochDay())) {
                val start = LocalDateTime.of(d, startTime)
                if (start.isAfter(now)) {
                    val early = start.minusMinutes(offset.toLong())
                    val earlyValid = if (offset > 0 && early.isAfter(now)) early else null
                    return earlyValid to start
                }
            }
            d = d.plusDays(1)
        }
        return null
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

    private fun earlyCode(id: Long): Int = (id * 2).toInt()
    private fun startCode(id: Long): Int = (id * 2 + 1).toInt()

    /** RONDE 5: kode alarm berantai checklist — jauh dari kode early/start & tes. */
    private fun checklistCode(id: Long): Int = (700_000 + id).toInt()

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

    fun cancel(context: Context, activityId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Batalkan kedua alarm skema baru + alarm tunggal skema lama (kode = id)
        // supaya tidak ada alarm yatim dari versi aplikasi sebelumnya.
        listOf(earlyCode(activityId), startCode(activityId), checklistCode(activityId), activityId.toInt()).forEach { code ->
            val intent = Intent(context, ReminderReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return@forEach
            am.cancel(pending)
            pending.cancel()
        }
    }
}
