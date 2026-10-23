package id.rama.todoplanner.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import id.rama.todoplanner.data.AppPrefs

/**
 * Satu-satunya pengelola notification channel reminder — skema V4 (ronde 4;
 * v3 ronde 2 dipertahankan di bawah sebagai riwayat alasan).
 *
 * PELAJARAN RONDE 1 (terbukti di HP Huawei/EMUI Rama): channel dengan
 * `setSound(...)` + `enableVibration(true)` + IMPORTANCE_HIGH ternyata TIDAK
 * menjamin bunyi/getar — EMUI bisa membungkam channel (mode senyap sistem,
 * klasifikasi channel, atau konfigurasi awal yang terkunci karena channel
 * bersifat immutable). Karena itu di ronde 2:
 *
 *  - Channel v3 SENGAJA dibuat SENYAP & TANPA GETAR. Tugas channel hanya
 *    tampilan: heads-up (IMPORTANCE_HIGH) + ikon + teks. Tidak ada bunyi
 *    ganda, tidak ada sumber bunyi yang berebut.
 *  - SELURUH bunyi + getar diputar DARI KODE oleh [ReminderSoundPlayer]
 *    (MediaPlayer USAGE_ALARM + Vibrator) — jalur yang tidak bisa dibungkam
 *    oleh pengaturan suara channel.
 *  - Channel lama skema `reminder_*` (v1) dan `reminder2_*` (ronde 1)
 *    dihapus supaya tidak menumpuk dan tidak membingungkan di Pengaturan HP.
 */
object ReminderChannels {
    const val PREFIX_V3 = "reminder3_"
    const val PREFIX_V4 = "reminder4_"

    /** ID channel v4 untuk sebuah URI nada (per nada, seperti skema sebelumnya). */
    fun channelIdFor(toneUri: String?): String {
        val key = when {
            toneUri == AppPrefs.RINGTONE_SILENT -> "senyap"
            toneUri.isNullOrBlank() || toneUri == AppPrefs.RINGTONE_DEFAULT -> "bawaan"
            else -> toneUri.hashCode().toString()
        }
        return PREFIX_V4 + key
    }

    /**
     * Pastikan channel v4 untuk [toneUri] ada, dan bersihkan channel skema lama.
     * Mengembalikan ID channel yang aktif.
     *
     * RONDE 4: channel v4 = LAPIS CADANGAN GETAR di level OS. Suara tetap
     * null (bunyi 100% dari kode agar tidak dobel), TAPI getar channel
     * DIAKTIFKAN dengan pola kuat yang sama dengan ReminderSoundPlayer —
     * bila EMUI membungkam getar dari kode, getar channel ini menjadi
     * jalur kedua. Visibilitas lockscreen PUBLIC agar reminder tampil
     * penuh di layar kunci.
     */
    fun ensureChannel(context: Context, toneUri: String?): String {
        val channelId = channelIdFor(toneUri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            deleteLegacyChannels(nm)
            if (nm.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId, "Reminder Kegiatan", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Pengingat Todo Planner — bunyi dari aplikasi, getar ganda (kode + channel)"
                    // Suara tetap null: bunyi diputar dari kode (ReminderSoundPlayer).
                    setSound(null, null)
                    // RONDE 4: getar channel AKTIF sebagai lapis cadangan.
                    enableVibration(true)
                    vibrationPattern = ReminderSoundPlayer.VIBRATION_PATTERN
                    enableLights(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }
        }
        return channelId
    }

    /** Hapus channel v1 ("reminder_..."), ronde 1 ("reminder2_...") & v3 ("reminder3_..."). Aman dipanggil berulang. */
    fun deleteLegacyChannels(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            nm.notificationChannels
                .map { it.id }
                .filter {
                    it.startsWith("reminder_") || it.startsWith("reminder2_") ||
                        it.startsWith(PREFIX_V3)
                }
                .forEach { oldId -> nm.deleteNotificationChannel(oldId) }
        } catch (e: Exception) { /* bukan jalur kritis */ }
    }
}
