package id.rama.todoplanner.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import id.rama.todoplanner.MainActivity
import id.rama.todoplanner.R
import id.rama.todoplanner.TodoApplication
import id.rama.todoplanner.data.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Menerima alarm lalu (1) MEMBUNYIKAN nada + getar DARI KODE dan
 * (2) menampilkan notifikasi heads-up.
 *
 * PERBAIKAN RONDE 2 — bunyi diputar langsung dari kode oleh
 * [ReminderSoundPlayer] (MediaPlayer, AudioAttributes USAGE_ALARM) lewat
 * [ReminderSoundService]; channel hanya untuk tampilan.
 *
 * PERBAIKAN RONDE 6 — CEK ULANG STATUS SELESAI DARI DB SEBELUM BUNYI
 * (lapis kedua perbaikan bug "todo sudah selesai masih bunyi"):
 * ronde <=5 receiver ini membunyikan alarm APA ADANYA dari extras intent,
 * tanpa pernah memeriksa database. Akibatnya alarm yatim/terlambat yang
 * lolos dari pembatalan (skema kode lama, race penjadwalan ulang) PASTI
 * tetap berbunyi walau occurrence-nya sudah ditandai selesai. Kini untuk
 * alarm berkegiatan (bukan tes): muat kegiatan dari DB dulu —
 *  - kegiatan sudah dihapus -> batalkan semua sisa alarmnya, JANGAN bunyi;
 *  - occurrence (EXTRA_OCC_EPOCH_DAY) sudah selesai -> JANGAN bunyi,
 *    lewati dan jadwalkan occurrence berikutnya;
 *  - selain itu -> bunyi seperti biasa; khusus checklist, setelah bunyi
 *    jadwalkan tick berikutnya (alarm berantai).
 * Bila pembacaan DB gagal total (pengecualian), alarm tetap dibunyikan —
 * alarm yang hilang diam-diam lebih buruk daripada bunyi berlebih sesekali.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, 0)
        val notifId = id.toInt()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Todo Planner"
        val text = intent.getStringExtra(EXTRA_TEXT) ?: "Pengingat kegiatanmu"
        val toneUri = intent.getStringExtra(EXTRA_TONE_URI)
        val kind = intent.getStringExtra(EXTRA_KIND)
        // RONDE 4: identitas kegiatan untuk fullScreenIntent -> Detail.
        val activityId = intent.getLongExtra(EXTRA_ACTIVITY_ID, 0L)
        val occEpochDay = intent.getLongExtra(EXTRA_OCC_EPOCH_DAY, 0L)

        // Alarm tes (tanpa kegiatan) langsung bunyi — tidak ada status DB.
        if (activityId <= 0L || kind == "test") {
            ring(context, notifId, title, text, toneUri, activityId, occEpochDay)
            return
        }

        // RONDE 6: verifikasi DB dulu (goAsync agar receiver tidak dibunuh
        // sebelum coroutine selesai).
        val pending = goAsync()
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = appCtx as? TodoApplication
                val act = app?.repository?.getActivity(activityId)
                when {
                    app == null -> ring(context, notifId, title, text, toneUri, activityId, occEpochDay)
                    act == null -> ReminderScheduler.cancelAll(appCtx, activityId)
                    act.isDoneOn(occEpochDay) -> {
                        // SELESAI = JANGAN bunyi. Jadwalkan occurrence
                        // berikutnya dari keadaan terbaru (schedule juga
                        // membatalkan semua sisa alarm occurrence ini).
                        // Khusus checklist: schedule menghitung tick RESET
                        // (selesai + interval) sesuai semantik ronde 6 —
                        // alarm yang baru saja berbunyi ini adalah tick
                        // SEBELUM ditandai selesai, jadi tick reset yang
                        // benar sudah/akan dipasang oleh toggleDone; panggilan
                        // ini hanya memastikan tidak ada sisa alarm yatim.
                        ReminderScheduler.schedule(app, act, app.prefs)
                    }
                    else -> {
                        ring(context, notifId, title, text, toneUri, activityId, occEpochDay)
                        // Alarm BERANTAI checklist: setelah satu tick berbunyi,
                        // jadwalkan tick berikutnya dari keadaan DB terbaru.
                        if (kind == ReminderScheduler.KIND_CHECKLIST) {
                            ReminderScheduler.schedule(app, act, app.prefs)
                        }
                    }
                }
            } catch (e: Exception) {
                // DB tidak terbaca — bunyikan saja (lihat catatan kelas).
                try { ring(context, notifId, title, text, toneUri, activityId, occEpochDay) } catch (e2: Exception) { /* abaikan */ }
            } finally {
                pending.finish()
            }
        }
    }

    /** Bunyikan + tampilkan notifikasi (jalur ronde 2/4, tidak berubah). */
    private fun ring(
        context: Context, notifId: Int, title: String, text: String,
        toneUri: String?, activityId: Long, occEpochDay: Long
    ) {
        val channelId = ReminderChannels.ensureChannel(context, toneUri)
        startSound(context, notifId, title, text, channelId, toneUri, activityId, occEpochDay)
        postNotification(context, notifId, title, text, channelId, activityId, occEpochDay)
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
            // OEM menolak start FGS dari latar -> putar langsung dari receiver.
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
            // RONDE 4: layar MENYALA & alert tampil penuh walau HP terkunci.
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
