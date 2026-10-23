package id.rama.todoplanner.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import id.rama.todoplanner.MainActivity
import id.rama.todoplanner.R
import id.rama.todoplanner.data.AppPrefs

/**
 * Menerima alarm lalu menampilkan notifikasi lokal.
 * Suara notifikasi berasal dari URI nada pilihan user (ringtone picker Android).
 * Karena suara adalah properti NotificationChannel, id channel diturunkan dari URI nada:
 * nada berbeda = channel berbeda, sehingga suara selalu sesuai pilihan terakhir.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, 0)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Todo Planner"
        val text = intent.getStringExtra(EXTRA_TEXT) ?: "Pengingat kegiatanmu"
        val toneUri = intent.getStringExtra(EXTRA_TONE_URI)
        val channelId = ensureChannel(context, toneUri)

        val openIntent = PendingIntent.getActivity(
            context, id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try { nm.notify(id.toInt(), notif) } catch (e: SecurityException) { /* izin notifikasi belum diberikan */ }
    }

    private fun ensureChannel(context: Context, toneUri: String?): String {
        val channelId = "reminder_" + (toneUri?.hashCode()?.toString() ?: "bawaan")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(channelId, "Reminder Kegiatan", NotificationManager.IMPORTANCE_HIGH)
                when {
                    toneUri == AppPrefs.RINGTONE_SILENT -> {
                        channel.setSound(null, null)
                        channel.enableVibration(true)
                    }
                    toneUri != null -> {
                        val attrs = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                        channel.setSound(Uri.parse(toneUri), attrs)
                    }
                    else -> channel.setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null
                    )
                }
                nm.createNotificationChannel(channel)
            }
        }
        return channelId
    }

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_TONE_URI = "extra_tone_uri"
        const val EXTRA_TONE_NAME = "extra_tone_name"
    }
}
