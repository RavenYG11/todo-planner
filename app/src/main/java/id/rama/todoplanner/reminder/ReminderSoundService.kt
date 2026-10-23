package id.rama.todoplanner.reminder

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import id.rama.todoplanner.MainActivity
import id.rama.todoplanner.R

/**
 * Layanan latar depan RINGAN yang membunyikan reminder (ronde 2).
 *
 * Kenapa service? BroadcastReceiver hanya hidup sesaat — di banyak HP
 * (terutama EMUI) proses aplikasi bisa dibekukan/dimatikan begitu onReceive
 * selesai sehingga MediaPlayer ikut mati. Foreground service menjaga proses
 * tetap hidup selama bunyi diputar (maks. 20 detik, lalu berhenti sendiri).
 *
 * Jalur mulai: ReminderReceiver memanggil `startForegroundService` tepat
 * setelah alarm eksak berbunyi — konteks alarm adalah salah satu konteks
 * yang diizinkan Android 12+ untuk memulai layanan latar depan. Bila OEM
 * tetap menolak (ForegroundServiceStartNotAllowedException dll), receiver
 * menangkapnya dan jatuh ke pemutaran langsung di receiver (lihat
 * ReminderReceiver) — bunyi tidak pernah bergantung pada satu jalur saja.
 */
class ReminderSoundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var stopSelfRunnable: Runnable? = null

    /** RONDE 4: kunci layar TERANG selama bunyi (cadangan fullScreenIntent). */
    private var screenWakeLock: android.os.PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        val notifId = intent?.getIntExtra(EXTRA_NOTIF_ID, DEFAULT_NOTIF_ID) ?: DEFAULT_NOTIF_ID
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Todo Planner"
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Pengingat kegiatanmu"
        val channelId = intent?.getStringExtra(EXTRA_CHANNEL_ID)
            ?: ReminderChannels.ensureChannel(this, null)
        val toneUri = intent?.getStringExtra(EXTRA_TONE_URI)
        val activityId = intent?.getLongExtra(EXTRA_ACTIVITY_ID, 0L) ?: 0L
        val occEpochDay = intent?.getLongExtra(EXTRA_OCC_EPOCH_DAY, 0L) ?: 0L

        // RONDE 4 (b): bangunkan LAYAR selama bunyi — SCREEN_BRIGHT +
        // ACQUIRE_CAUSES_WAKEUP, timeout seumur bunyi (+5 dtk penyangga).
        acquireScreenWakeLock()

        try {
            val notif = buildNotification(notifId, title, text, channelId, activityId, occEpochDay)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(notifId, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(notifId, notif)
            }
        } catch (e: Exception) {
            // Gagal jadi foreground — tetap putar dari pemutar langsung lalu berhenti.
            ReminderSoundPlayer.play(this, toneUri)
            scheduleStopSelf()
            return START_NOT_STICKY
        }

        ReminderSoundPlayer.play(this, toneUri)
        scheduleStopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopSelfRunnable?.let { handler.removeCallbacks(it) }
        releaseScreenWakeLock()
        ReminderSoundPlayer.stop()
        super.onDestroy()
    }

    @android.annotation.SuppressLint("WakelockTimeout")
    private fun acquireScreenWakeLock() {
        try {
            if (screenWakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            @Suppress("DEPRECATION")
            screenWakeLock = pm.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "TodoPlanner:ReminderScreen"
            ).apply { acquire(ReminderSoundPlayer.PLAY_DURATION_MS + 5_000) }
        } catch (e: Exception) { /* tanpa kunci layar pun bunyi tetap jalan */ }
    }

    private fun releaseScreenWakeLock() {
        try {
            if (screenWakeLock?.isHeld == true) screenWakeLock?.release()
        } catch (e: Exception) { /* abaikan */ }
        screenWakeLock = null
    }

    private fun scheduleStopSelf() {
        stopSelfRunnable?.let { handler.removeCallbacks(it) }
        stopSelfRunnable = Runnable { shutdown() }
        handler.postDelayed(stopSelfRunnable!!, ReminderSoundPlayer.PLAY_DURATION_MS + 1_000)
    }

    private fun shutdown() {
        ReminderSoundPlayer.stop()
        releaseScreenWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    private fun buildNotification(
        notifId: Int, title: String, text: String, channelId: String,
        activityId: Long, occEpochDay: Long
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this, notifId,
            MainActivity.reminderIntent(this, activityId, occEpochDay),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Intent WAKE terpisah untuk fullScreenIntent (lihat ReminderReceiver).
        val wakeIntent = PendingIntent.getActivity(
            this, notifId + MainActivity.WAKE_PI_OFFSET,
            MainActivity.reminderIntent(this, activityId, occEpochDay, wake = true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getBroadcast(
            this, notifId,
            Intent(this, StopSoundReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // RONDE 4: notifikasi foreground pun membawa fullScreenIntent —
            // siapa pun yang tampil (notif receiver / notif service, ID sama)
            // sama-sama bisa menyalakan layar & membuka Detail.
            .setFullScreenIntent(wakeIntent, true)
            .setContentIntent(openIntent)
            .setDeleteIntent(stopIntent)
            .addAction(0, "Matikan Bunyi", stopIntent)
            .setAutoCancel(true)
            .build()
    }

    companion object {
        const val ACTION_START = "id.rama.todoplanner.reminder.START_SOUND"
        const val ACTION_STOP = "id.rama.todoplanner.reminder.STOP_SOUND"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_TONE_URI = "extra_tone_uri"
        const val EXTRA_ACTIVITY_ID = "extra_activity_id"
        const val EXTRA_OCC_EPOCH_DAY = "extra_occ_epoch_day"
        const val DEFAULT_NOTIF_ID = 990_001

        fun startIntent(
            context: Context, notifId: Int, title: String, text: String,
            channelId: String, toneUri: String?,
            activityId: Long = 0L, occEpochDay: Long = 0L
        ): Intent = Intent(context, ReminderSoundService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_NOTIF_ID, notifId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_TONE_URI, toneUri)
            putExtra(EXTRA_ACTIVITY_ID, activityId)
            putExtra(EXTRA_OCC_EPOCH_DAY, occEpochDay)
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, ReminderSoundService::class.java).apply { action = ACTION_STOP }
    }
}
