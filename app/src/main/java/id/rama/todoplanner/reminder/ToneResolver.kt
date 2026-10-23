package id.rama.todoplanner.reminder

import id.rama.todoplanner.data.AppPrefs

/**
 * Resolver nada reminder — SATU tempat, RONDE 3. Semua jalur (penjadwalan
 * alarm, tombol tes, preview) wajib lewat sini supaya tidak ada jalur yang
 * diam-diam berbeda.
 *
 * Urutan keputusan (dari yang paling spesifik):
 *  1. Nada PER KEGIATAN bila kegiatan mengaturnya sendiri:
 *     - [AppPrefs.RINGTONE_DEFAULT] ("default") → nada DEFAULT sistem.
 *       Sentinel ini EKSPLISIT: ia menang atas global kustom, jadi kegiatan
 *       yang memilih "Bawaan HP (default)" benar-benar berbunyi default
 *       walaupun global sedang memakai nada kustom.
 *     - [AppPrefs.RINGTONE_SILENT] ("silent") → senyap (getar saja).
 *     - URI lain → nada kustom pilihan user untuk kegiatan itu.
 *  2. Nada GLOBAL bila kegiatan tidak mengatur sendiri (ringtoneUri null
 *     = "Ikuti global"):
 *     - null (termasuk setelah user menekan "Bawaan HP (default)" di
 *       pengaturan global, yang menghapus URI kustom) → default sistem.
 *     - "silent" → senyap; URI lain → nada kustom global.
 *  3. Hasil akhir berupa null berarti "default sistem": pemutar
 *     ([ReminderSoundPlayer]) menerjemahkannya ke jalur bawaan berjenjang
 *     yang sudah ada — nada ALARM bawaan → NOTIFIKASI bawaan → DERING bawaan.
 */
object ToneResolver {

    /** Nada efektif untuk sebuah kegiatan. null hasil = default sistem. */
    fun effectiveToneUri(activityToneUri: String?, globalToneUri: String?): String? = when {
        activityToneUri == AppPrefs.RINGTONE_DEFAULT -> null
        activityToneUri != null -> activityToneUri
        globalToneUri == AppPrefs.RINGTONE_DEFAULT -> null
        else -> globalToneUri
    }

    /** Nama tampil nada efektif per kegiatan (untuk Detail & judul alarm tes). */
    fun effectiveToneLabel(activityToneUri: String?, activityToneName: String?, globalToneName: String): String = when {
        activityToneUri == AppPrefs.RINGTONE_DEFAULT -> "Bawaan HP"
        activityToneUri == AppPrefs.RINGTONE_SILENT -> "Senyap / Getar saja"
        activityToneUri != null -> activityToneName?.takeIf { it.isNotBlank() } ?: "Nada pilihan"
        else -> globalToneName
    }
}
