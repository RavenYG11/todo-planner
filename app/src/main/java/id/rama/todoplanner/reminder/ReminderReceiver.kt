package id.rama.todoplanner.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import id.rama.todoplanner.MainActivity
import id.rama.todoplanner.R
import id.rama.todoplanner.data.AppPrefs

/**
 * Menerima alarm lalu (1) MEMBUNYIKAN nada + getar DARI KODE dan
 * (2) menampilkan notifikasi heads-up.
 *
 * PERBAIKAN RONDE 2 — akar masalah ronde 1: bunyi hanya digantungkan ke
 * notification channel (setSound di channel v2 "reminder2_*"). Di HP
 * Huawei/EMUI Rama pendekatan channel-only TERBUKTI tidak cukup: notifikasi
 * tampil bagus tetapi senyap & tidak getar (EMUI dapat membungkam channel —
 * klasifikasi/pengaturan channel per aplikasi berada di luar kendali kode).
 *
 * Pendekatan tuntas ronde 2:
 *  1. Bunyi diputar langsung dari kode oleh [ReminderSoundPlayer]
 *     (MediaPlayer, AudioAttributes USAGE_ALARM — jalur audio jam alarm)
 *     lewat [ReminderSoundService] (foreground, proses dijamin hidup);
 *     bila OEM menolak start service dari latar, jatuh ke pemutar langsung
 *     di receiver ini. Nada pilihan diverifikasi bisa dibaca; gagal →
 *     fallback nada alarm/notifikasi bawaan. TIDAK ada jalur senyap,
 *     kecuali user sendiri memilih nada "Senyap" (tetap getar).
 *  2. Getar juga dari kode ([ReminderSoundPlayer] + Vibrator, pola eksplisit)
 *     untuk SEMUA prioritas kegiatan.
 *  3. Channel v3 ([ReminderChannels]) SENGAJA senyap — hanya untuk tampilan
 *     heads-up; channel lama v1/ronde-1 dibersihkan.
 *  4. Notifikasi membawa deleteIntent + aksi "Matikan Bunyi" →
 *     [StopSoundReceiver]; mengetuk notifikasi membuka aplikasi dan
 *     MainActivity menghentikan bunyi.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, 0)
        val notifId = id.toInt()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Todo Planner"
        val text = intent.getStringExtra(EXTRA_TEXT) ?: "Pengingat kegiatanmu"
        val toneUri = intent.getStringExtra(EXTRA_TONE_URI)
        val channelId = ReminderChannels.ensureChannel(context, toneUri)

        startSound(context, notifId, title, text, channelId, toneUri)
        postNotification(context, notifId, title, text, channelId)
    }

    /** Jalur utama: foreground service. Jalur cadangan: putar langsung di sini. */
    private fun startSound(
        context: Context, notifId: Int, title: String, text: String,
        channelId: String, toneUri: String?
    ) {
        if (toneUri == AppPrefs.RINGTONE_SILENT) {
            // Mode senyap pilihan user sendiri: tetap getar dari kode.
            try {
                ContextCompat.startForegroundService(
                    context,
                    ReminderSoundService.startIntent(context, notifId, title, text, channelId, toneUri)
                )
            } catch (e: Exception) {
                ReminderSoundPlayer.vibrateOnly(context)
            }
            return
        }
        try {
            ContextCompat.startForegroundService(
                context,
                ReminderSoundService.startIntent(context, notifId, title, text, channelId, toneUri)
            )
        } catch (e: Exception) {
            // EMUI/OEM menolak start FGS dari latar -> putar langsung dari receiver.
            ReminderSoundPlayer.play(context, toneUri)
        }
    }

    private fun postNotification(
        context: Context, notifId: Int, title: String, text: String, channelId: String
    ) {
        val openIntent = PendingIntent.getActivity(
            context, notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getBroadcast(
            context, notifId,
            Intent(context, StopSoundReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent)
            .setDeleteIntent(stopIntent)
            .addAction(0, "Matikan Bunyi", stopIntent)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(notifId, notif)
        } catch (e: SecurityException) { /* izin notifikasi belum diberikan — bunyi sudah diputar dari kode */ }
    }

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_KIND = "extra_kind"
        const val EXTRA_TONE_URI = "extra_tone_uri"
        const val EXTRA_TONE_NAME = "extra_tone_name"

        /** Tetap diekspor untuk kompatibilitas baca; pola aktif kini di ReminderSoundPlayer. */
        val VIBRATION_PATTERN: LongArray get() = ReminderSoundPlayer.VIBRATION_PATTERN
    }
}
