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
 *
 * RONDE 4 — getar absen total di uji v1.0.1 (suara bunyi, getar tidak):
 *  - Panggilan getar utama kini TANPA AudioAttributes sama sekali. Versi
 *    ronde 2/3 mengikat getar ke attrs USAGE_ALARM; di EMUI getar yang
 *    terikat atribut audio alarm bisa di-gate oleh kebijakan audio OEM
 *    (getar mengikuti mode/atribut alarm) dan gagal TANPA exception —
 *    dan try/catch lama menelannya diam-diam sehingga nol bukti.
 *  - Waveform kini memakai amplitudo penuh 255 di segmen aktif.
 *  - Hasil SETIAP upaya getar (sukses/gagal + pesan + hasVibrator) disimpan
 *    ke AppPrefs sebagai diagnostik dan ditampilkan di layar
 *    Reminder/Pengaturan — tidak ada lagi exception yang ditelan.
 *  - Sebagai lapis cadangan, channel v4 mengaktifkan getar di level channel
 *    (lihat ReminderChannels).
 *  - Selama bunyi, ReminderSoundService (yang menggerakkan pemutar ini)
 *    juga memegang SCREEN_BRIGHT WakeLock + ACQUIRE_CAUSES_WAKEUP supaya
 *    layar ikut menyala di HP terkunci (item 4).
 */
object ReminderSoundPlayer {
    const val PLAY_DURATION_MS = 20_000L

    /** Pola getar eksplisit: jeda, getar, jeda, getar, jeda, getar panjang (di-loop sampai berhenti). */
    val VIBRATION_PATTERN = longArrayOf(0, 300, 200, 300, 200, 600)

    /** RONDE 4: amplitudo PENUH (255) per segmen getar — segmen jeda = 0. */
    val VIBRATION_AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255)

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

    /**
     * RONDE 4 — getar ditulis ulang BERLAPIS (uji ronde 3: suara bunyi, getar
     * NOL, padahal Rama sudah mengizinkan getar di pengaturan notif HP).
     *
     * Tersangka utama dari kode lama (diperbaiki di sini):
     *  1. Getar dipanggil LEWAT AudioAttributes USAGE_ALARM —
     *     `vibrate(effect, attrs)`. Di EMUI/Huawei jalur atribut alarm ini
     *     bisa digerbangi pengaturan haptik alarm HP sehingga panggilan
     *     diterima API tetapi motor tidak berputar. Kini panggilan UTAMA
     *     adalah overload POLOS `vibrate(effect)` TANPA AudioAttributes.
     *  2. Exception DITELAN diam-diam (`catch { /* abaikan */ }`) — kegagalan
     *     apa pun tak terlihat. Kini SETIAP hasil upaya (sukses/gagal +
     *     pesan + hasVibrator) DISIMPAN ke AppPrefs.vibrationDiagnostic dan
     *     ditampilkan di layar Reminder/Pengaturan.
     *  3. Channel v3 menonaktifkan getar (enableVibration(false)) sehingga
     *     tidak ada jalur cadangan OS — kini ada channel v4 bergetar
     *     (lihat ReminderChannels).
     *
     * Lapisan di sini: (a) waveform amplitudo penuh 255 tanpa atribut;
     * (b) fallback waveform DENGAN atribut alarm (perilaku lama); (c) fallback
     * API lama `vibrate(pattern, repeat)`. Hasil lapisan terakhir yang
     * berhasil/gagal dicatat sebagai diagnostik.
     */
    private fun startVibration(context: Context) {
        val v = obtainVibrator(context)
        vibrator = v
        val hasVibrator = try { v?.hasVibrator() == true } catch (e: Exception) { false }
        if (v == null) {
            saveVibrationDiagnostic(context, hasVibrator, "GAGAL: Vibrator service null")
            return
        }
        // (a) UTAMA: tanpa AudioAttributes, amplitudo penuh, loop dari indeks 0.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                v.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, VIBRATION_AMPLITUDES, 0))
                saveVibrationDiagnostic(context, hasVibrator, "OK: waveform amplitudo 255 tanpa AudioAttributes")
                return
            } catch (e: Exception) {
                saveVibrationDiagnostic(context, hasVibrator, "Lapisan A gagal: ${e.message ?: e.javaClass.simpleName}")
            }
            // (b) Cadangan: perilaku lama (dengan atribut alarm).
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                v.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0), attrs)
                saveVibrationDiagnostic(context, hasVibrator, "OK (cadangan B): waveform + AudioAttributes USAGE_ALARM")
                return
            } catch (e: Exception) {
                saveVibrationDiagnostic(context, hasVibrator, "Lapisan B gagal: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        // (c) Cadangan terakhir: API getar pola lama.
        try {
            @Suppress("DEPRECATION")
            v.vibrate(VIBRATION_PATTERN, 0)
            saveVibrationDiagnostic(context, hasVibrator, "OK (cadangan C): vibrate(pattern) API lama")
        } catch (e: Exception) {
            saveVibrationDiagnostic(
                context, hasVibrator,
                "GAGAL TOTAL: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    private fun obtainVibrator(context: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    } catch (e: Exception) {
        null
    }

    /** Apakah HP ini punya motor getar — dipakai Toast tombol Tes Reminder. */
    fun hasVibrator(context: Context): Boolean =
        try { obtainVibrator(context)?.hasVibrator() == true } catch (e: Exception) { false }

    /** Simpan hasil upaya getar terakhir (BUKTI untuk uji berikutnya). Tidak pernah melempar. */
    private fun saveVibrationDiagnostic(context: Context, hasVibrator: Boolean, result: String) {
        try {
            val jam = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM HH.mm"))
            AppPrefs(context).vibrationDiagnostic =
                "$result • hasVibrator=$hasVibrator • $jam"
        } catch (e: Exception) { /* diagnostik tidak boleh menjatuhkan bunyi */ }
    }

    /**
     * Pilih URI pertama yang BENAR-BENAR bisa dibaca dari rantai fallback:
     * nada pilihan user → alarm bawaan → notifikasi bawaan → dering bawaan.
     */
    private fun resolvePlayableUri(context: Context, toneUriString: String?): Uri? {
        val candidates = ArrayList<Uri>()
        // Sentinel "default" (ronde 3) & null & kosong = default sistem: JANGAN
        // pernah di-parse sebagai URI — langsung jatuh ke rantai bawaan di bawah.
        if (!toneUriString.isNullOrBlank() && toneUriString != AppPrefs.RINGTONE_SILENT &&
            toneUriString != AppPrefs.RINGTONE_DEFAULT
        ) {
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
