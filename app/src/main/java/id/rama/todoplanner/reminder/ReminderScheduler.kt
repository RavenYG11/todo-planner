package id.rama.todoplanner.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import id.rama.todoplanner.data.ActivityEntity
import id.rama.todoplanner.data.AppPrefs
import id.rama.todoplanner.domain.nextReminder
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Menjadwalkan alarm reminder lokal dengan AlarmManager.
 * Nada efektif = nada per kegiatan, atau global bila kegiatan tidak mengatur sendiri.
 */
object ReminderScheduler {

    fun schedule(context: Context, activity: ActivityEntity, prefs: AppPrefs) {
        cancel(context, activity.id)
        val next = activity.nextReminder(LocalDateTime.now()) ?: return
        val triggerMillis = next.second.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val toneUri = activity.ringtoneUri ?: prefs.globalRingtoneUri
        val toneName = activity.ringtoneName ?: prefs.globalRingtoneName
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ID, activity.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, activity.title)
            putExtra(ReminderReceiver.EXTRA_TONE_URI, toneUri)
            putExtra(ReminderReceiver.EXTRA_TONE_NAME, toneName)
        }
        intent.putExtra(ReminderReceiver.EXTRA_TEXT, buildText(activity))
        val pending = PendingIntent.getBroadcast(
            context, activity.id.toInt(), intent,
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

    private fun buildText(activity: ActivityEntity): String {
        val jam = activity.startMinutes?.let { "%02d.%02d".format(it / 60, it % 60) } ?: ""
        return if (jam.isEmpty()) "Pengingat kegiatanmu" else "Mulai pukul $jam"
    }

    fun cancel(context: Context, activityId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, activityId.toInt(), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending)
        pending.cancel()
    }
}
