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
import kotlinx.coroutines.launch

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
        // RONDE 4: identitas kegiatan untuk fullScreenIntent -> Detail.
        val activityId = intent.getLongExtra(EXTRA_ACTIVITY_ID, 0L)
        val occEpochDay = intent.getLongExtra(EXTRA_OCC_EPOCH_DAY, 0L)
        val channelId = ReminderChannels.ensureChannel(context, toneUri)

        startSound(context, notifId, title, text, channelId, toneUri, activityId, occEpochDay)
        postNotification(context, notifId, title, text, channelId, activityId, occEpochDay)

        // RONDE 5 — alarm BERANTAI checklist: begitu satu tick berbunyi,
        // jadwalkan tick berikutnya. Bila checklist sudah dicentang selesai
        // untuk hari itu (atau dihapus), penjadwalan ulang tidak menghasilkan
        // alarm hari itu lagi — rantai berhenti permanen untuk hari itu.
        if (intent.getStringExtra(EXTRA_KIND) == ReminderScheduler.KIND_CHECKLIST && activityId > 0L) {
            val pending = goAsync()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as? id.rama.todoplanner.TodoApplication
                    val act = app?.repository?.getActivity(activityId)
                    if (app != null && act != null) {
                        ReminderScheduler.schedule(app, act, app.prefs)
                    }
                } catch (e: Exception) { /* rantai berhenti; rescheduleAll saat app dibuka memulihkan */ }
                finally { pending.finish() }
            }
        }
    }

    /** Jalur utama: foreground service. Jalur cadangan: putar langsung di sini. */
    private fun startSound(
        context: Context, notifId: Int, title: String, text: String,
        channelId: String, toneUri: String?, activityId: Long, occEpochDay: Long
    ) {
        if (toneUri == AppPrefs.RINGTONE_SILENT) {
            // Mode senyap pilihan user sendiri: tetap getar dari kode.
            try {
                ContextCompat.startForegroundService(
                    context,
                    ReminderSoundService.startIntent(
                        context, notifId, title, text, channelId, toneUri, activityId, occEpochDay
                    )
                )
            } catch (e: Exception) {
                ReminderSoundPlayer.vibrateOnly(context)
            }
            return
        }
        try {
            ContextCompat.startForegroundService(
                context,
                ReminderSoundService.startIntent(
                    context, notifId, title, text, channelId, toneUri, activityId, occEpochDay
                )
            )
        } catch (e: Exception) {
            // EMUI/OEM menolak start FGS dari latar -> putar langsung dari receiver.
            ReminderSoundPlayer.play(context, toneUri)
        }
    }

    private fun postNotification(
        context: Context, notifId: Int, title: String, text: String, channelId: String,
        activityId: Long, occEpochDay: Long
    ) {
        val openIntent = PendingIntent.getActivity(
            context, notifId,
            MainActivity.reminderIntent(context, activityId, occEpochDay),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // RONDE 4: intent WAKE terpisah untuk fullScreenIntent (request code
        // berbeda agar tidak tertukar dengan contentIntent — extras saja tidak
        // membedakan PendingIntent). Peluncuran WAKE menyalakan layar &
        // membuka Detail TANPA menghentikan bunyi; ketukan notifikasi
        // (openIntent) membuka Detail DAN menghentikan bunyi.
        val wakeIntent = PendingIntent.getActivity(
            context, notifId + MainActivity.WAKE_PI_OFFSET,
            MainActivity.reminderIntent(context, activityId, occEpochDay, wake = true),
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
            // RONDE 4: layar MENYALA & alert tampil penuh walau HP terkunci —
            // fullScreenIntent (intent WAKE) membuka MainActivity
            // (showWhenLocked + turnScreenOn diatur di MainActivity untuk
            // intent ini) langsung ke Detail kegiatan. Butuh permission
            // USE_FULL_SCREEN_INTENT.
            .setFullScreenIntent(wakeIntent, true)
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
        const val EXTRA_ACTIVITY_ID = "extra_activity_id"
        const val EXTRA_OCC_EPOCH_DAY = "extra_occ_epoch_day"

        /** Tetap diekspor untuk kompatibilitas baca; pola aktif kini di ReminderSoundPlayer. */
        val VIBRATION_PATTERN: LongArray get() = ReminderSoundPlayer.VIBRATION_PATTERN
    }
}
