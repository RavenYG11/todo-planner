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
 * Menerima alarm lalu menampilkan notifikasi lokal BERBUNYI + GETAR.
 * Nada notifikasi berasal dari URI nada pilihan user (ringtone picker Android)
 * dan dipakai di kedua alarm (pengingat awal & pas jam mulai).
 *
 * PERBAIKAN RONDE 1 (notifikasi muncul tapi senyap & tidak getar di HP Rama):
 *  1. ID channel BARU ber-prefix "reminder2_" — channel skema lama "reminder_*"
 *     bersifat immutable: bila pertama kali tercipta dengan konfigurasi salah,
 *     suaranya terkunci selamanya. Channel lama juga dibersihkan di sini.
 *  2. Getar kini diaktifkan + pola eksplisit untuk SEMUA cabang nada
 *     (dulu enableVibration hanya dipanggil di cabang senyap saja).
 *  3. Suara dipasang dari URI nada kegiatan (fallback: nada notifikasi bawaan HP).
 * Suara/getar tidak pernah digerbangi prioritas kegiatan.
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Untuk Android < 26 (minSdk aplikasi 26, jadi kanal yang berlaku);
            // tetap dipasang sebagai lapis pengaman.
            .setVibrate(VIBRATION_PATTERN)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try { nm.notify(id.toInt(), notif) } catch (e: SecurityException) { /* izin notifikasi belum diberikan */ }
    }

    private fun ensureChannel(context: Context, toneUri: String?): String {
        val toneKey = when {
            toneUri == AppPrefs.RINGTONE_SILENT -> "senyap"
            toneUri.isNullOrBlank() -> "bawaan"
            else -> toneUri.hashCode().toString()
        }
        val channelId = "reminder2_$toneKey"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Bersihkan channel skema lama ("reminder_...") yang berpotensi terkunci senyap.
            try {
                nm.notificationChannels
                    .map { it.id }
                    .filter { it.startsWith("reminder_") }
                    .forEach { oldId -> nm.deleteNotificationChannel(oldId) }
            } catch (e: Exception) { /* abaikan — bukan jalur kritis */ }

            if (nm.getNotificationChannel(channelId) == null) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val channel = NotificationChannel(
                    channelId, "Reminder Kegiatan", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Pengingat kegiatan Todo Planner (bunyi + getar)"
                    enableVibration(true)
                    vibrationPattern = VIBRATION_PATTERN
                    when {
                        toneUri == AppPrefs.RINGTONE_SILENT -> setSound(null, null)
                        !toneUri.isNullOrBlank() -> setSound(Uri.parse(toneUri), attrs)
                        else -> setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), attrs
                        )
                    }
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
        const val EXTRA_KIND = "extra_kind"
        const val EXTRA_TONE_URI = "extra_tone_uri"
        const val EXTRA_TONE_NAME = "extra_tone_name"

        /** Pola getar eksplisit: jeda, getar, jeda, getar. */
        val VIBRATION_PATTERN = longArrayOf(0, 250, 150, 250)
    }
}
