package id.rama.todoplanner.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import id.rama.todoplanner.data.AppPrefs

/**
 * Satu-satunya pengelola notification channel reminder — skema V3 (ronde 2).
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

    /** ID channel v3 untuk sebuah URI nada (per nada, seperti skema sebelumnya). */
    fun channelIdFor(toneUri: String?): String {
        val key = when {
            toneUri == AppPrefs.RINGTONE_SILENT -> "senyap"
            toneUri.isNullOrBlank() || toneUri == AppPrefs.RINGTONE_DEFAULT -> "bawaan"
            else -> toneUri.hashCode().toString()
        }
        return PREFIX_V3 + key
    }

    /**
     * Pastikan channel v3 untuk [toneUri] ada, dan bersihkan channel skema lama.
     * Mengembalikan ID channel yang aktif.
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
                    description = "Pengingat Todo Planner — bunyi & getar diputar dari aplikasi"
                    // SENGAJA senyap & tanpa getar di level channel: bunyi + getar
                    // 100% dari kode (ReminderSoundPlayer). Lihat doc di atas.
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(true)
                }
                nm.createNotificationChannel(channel)
            }
        }
        return channelId
    }

    /** Hapus channel v1 ("reminder_...") dan ronde 1 ("reminder2_..."). Aman dipanggil berulang. */
    fun deleteLegacyChannels(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            nm.notificationChannels
                .map { it.id }
                .filter { it.startsWith("reminder_") || it.startsWith("reminder2_") }
                .forEach { oldId -> nm.deleteNotificationChannel(oldId) }
        } catch (e: Exception) { /* bukan jalur kritis */ }
    }
}
