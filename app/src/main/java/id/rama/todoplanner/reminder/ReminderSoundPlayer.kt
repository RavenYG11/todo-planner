package id.rama.todoplanner.reminder

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import id.rama.todoplanner.data.AppPrefs

/**
 * Memutar bunyi + getar reminder LANGSUNG DARI KODE — jantung perbaikan ronde 2.
 *
 * Kenapa bukan channel lagi? Di HP Huawei/EMUI Rama (uji ronde 1), notifikasi
 * tampil bagus tetapi SENYAP total padahal channel sudah IMPORTANCE_HIGH +
 * setSound(URI nada) + pola getar. EMUI terbukti bisa membungkam suara yang
 * hanya mengandalkan channel. MediaPlayer dengan `USAGE_ALARM` memakai jalur
 * audio alarm (stream yang sama dengan jam alarm bawaan HP) sehingga tidak
 * bergantung pada konfigurasi channel sama sekali.
 *
 * Perilaku:
 *  - Nada pilihan user diverifikasi dulu (stream dibuka); bila gagal dibaca,
 *    fallback berjenjang: nada ALARM bawaan → nada NOTIFIKASI bawaan →
 *    nada DERING bawaan. Prinsipnya: JANGAN sampai senyap.
 *  - Bunyi di-loop maksimal [PLAY_DURATION_MS] (20 detik), berhenti lebih
 *    awal bila user mengetuk/membuang notifikasi (lihat StopSoundReceiver
 *    & MainActivity) atau menekan aksi "Matikan Bunyi".
 *  - Getar selalu dari kode dengan pola eksplisit, di SEMUA prioritas
 *    kegiatan dan semua mode nada (termasuk mode Senyap = getar saja).
 *  - Partial WakeLock ber-timeout menahan CPU selama pemutaran walau layar mati.
 */
object ReminderSoundPlayer {
    const val PLAY_DURATION_MS = 20_000L

    /** Pola getar eksplisit: jeda, getar, jeda, getar, jeda, getar panjang (di-loop sampai berhenti). */
    val VIBRATION_PATTERN = longArrayOf(0, 300, 200, 300, 200, 600)

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val stopRunnable = Runnable { stop() }

    /** Putar nada [toneUriString] + getar. Aman dipanggil dari receiver maupun service. */
    @Synchronized
    fun play(context: Context, toneUriString: String?) {
        stopInternal()
        val appCtx = context.applicationContext
        acquireWakeLock(appCtx)
        startVibration(appCtx)
        handler.postDelayed(stopRunnable, PLAY_DURATION_MS)
        if (toneUriString == AppPrefs.RINGTONE_SILENT) return // mode senyap: getar saja
        val uri = resolvePlayableUri(appCtx, toneUriString) ?: return // getar tetap jalan
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setWakeMode(appCtx, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(appCtx, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Percobaan terakhir: nada alarm bawaan sistem tanpa verifikasi stream.
            try {
                releasePlayer()
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(
                        appCtx,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e2: Exception) {
                releasePlayer() // getar sudah berjalan — tidak pernah benar-benar senyap total
            }
        }
    }

    /** Getar saja (dipakai juga untuk mode nada Senyap). */
    @Synchronized
    fun vibrateOnly(context: Context) {
        stopInternal()
        val appCtx = context.applicationContext
        acquireWakeLock(appCtx)
        startVibration(appCtx)
        handler.postDelayed(stopRunnable, PLAY_DURATION_MS)
    }

    /** Berhenti total: bunyi, getar, timer, dan wakelock. Idempoten. */
    @Synchronized
    fun stop() {
        handler.removeCallbacks(stopRunnable)
        stopInternal()
    }

    private fun stopInternal() {
        releasePlayer()
        try { vibrator?.cancel() } catch (e: Exception) { /* abaikan */ }
        vibrator = null
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) { /* abaikan */ }
        wakeLock = null
    }

    private fun releasePlayer() {
        try {
            if (player?.isPlaying == true) player?.stop()
        } catch (e: Exception) { /* abaikan */ }
        try { player?.release() } catch (e: Exception) { /* abaikan */ }
        player = null
    }

    private fun acquireWakeLock(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TodoPlanner:ReminderSound")
                .apply { acquire(PLAY_DURATION_MS + 5_000) }
        } catch (e: Exception) { /* tanpa wakelock pun pemutaran tetap dicoba */ }
    }

    private fun startVibration(context: Context) {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // repeat = 0 -> pola diulang dari awal sampai stop()/batal.
                vibrator?.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0), attrs)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(VIBRATION_PATTERN, 0, attrs)
            }
        } catch (e: Exception) { /* HP tanpa vibrator / izin hilang: bunyi tetap jalan */ }
    }

    /**
     * Pilih URI pertama yang BENAR-BENAR bisa dibaca dari rantai fallback:
     * nada pilihan user → alarm bawaan → notifikasi bawaan → dering bawaan.
     */
    private fun resolvePlayableUri(context: Context, toneUriString: String?): Uri? {
        val candidates = ArrayList<Uri>()
        if (!toneUriString.isNullOrBlank() && toneUriString != AppPrefs.RINGTONE_SILENT) {
            try { candidates.add(Uri.parse(toneUriString)) } catch (e: Exception) { /* lewati */ }
        }
        listOf(
            RingtoneManager.TYPE_ALARM,
            RingtoneManager.TYPE_NOTIFICATION,
            RingtoneManager.TYPE_RINGTONE
        ).forEach { type ->
            RingtoneManager.getDefaultUri(type)?.let { candidates.add(it) }
        }
        for (uri in candidates) {
            if (isReadable(context, uri)) return uri
        }
        // Tidak ada yang terverifikasi — kembalikan kandidat pertama agar tetap dicoba diputar.
        return candidates.firstOrNull()
    }

    private fun isReadable(context: Context, uri: Uri): Boolean = try {
        when (uri.scheme) {
            "content", "android.resource" ->
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            "file" -> java.io.File(uri.path ?: "").exists()
            else -> true // skema lain: serahkan ke MediaPlayer
        }
    } catch (e: Exception) {
        false
    }
}
