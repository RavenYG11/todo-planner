package id.rama.todoplanner.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Preferensi ringan (profil & pengaturan) di SharedPreferences — lokal, tanpa akun/server.
 * Kegiatan & target yang terstruktur disimpan di Room (lihat AppDatabase).
 */
class AppPrefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("todo_prefs", Context.MODE_PRIVATE)

    var userName: String
        get() = sp.getString("user_name", "Rama") ?: "Rama"
        set(v) = sp.edit().putString("user_name", v).apply()

    /** Indeks warna gradasi avatar 0..5 (lihat daftar warna di ui/components). */
    var avatarColorIndex: Int
        get() = sp.getInt("avatar_color", 0)
        set(v) = sp.edit().putInt("avatar_color", v).apply()

    /** Kosong = pakai inisial nama; selain itu emoji pilihan user. */
    var avatarEmoji: String
        get() = sp.getString("avatar_emoji", "") ?: ""
        set(v) = sp.edit().putString("avatar_emoji", v).apply()

    /** Path file foto profil di penyimpanan internal aplikasi; kosong = tanpa foto. */
    var avatarPhotoPath: String
        get() = sp.getString("avatar_photo", "") ?: ""
        set(v) = sp.edit().putString("avatar_photo", v).apply()

    /** URI nada dering global; null = nada notifikasi bawaan; "silent" = senyap/getar. */
    var globalRingtoneUri: String?
        get() = sp.getString("global_ringtone_uri", null)
        set(v) = sp.edit().putString("global_ringtone_uri", v).apply()

    var globalRingtoneName: String
        get() = sp.getString("global_ringtone_name", "Bawaan HP") ?: "Bawaan HP"
        set(v) = sp.edit().putString("global_ringtone_name", v).apply()

    /** "Terang" | "Gelap" | "Sistem" */
    var themeMode: String
        get() = sp.getString("theme_mode", "Terang") ?: "Terang"
        set(v) = sp.edit().putString("theme_mode", v).apply()

    companion object {
        const val RINGTONE_SILENT = "silent"

        /**
         * Sentinel eksplisit "Bawaan HP (default)" — RONDE 3.
         * Dipakai terutama PER KEGIATAN: nilai ini berarti "paksa nada default
         * sistem", BUKAN "ikuti global" (ikuti global = ringtoneUri null).
         * Untuk nada GLOBAL, memilih Bawaan HP = menghapus URI kustom
         * (globalRingtoneUri disimpan null) — hasil akhirnya sama: default sistem.
         * Resolver tunggalnya: reminder/ToneResolver.kt.
         */
        const val RINGTONE_DEFAULT = "default"
    }
}
