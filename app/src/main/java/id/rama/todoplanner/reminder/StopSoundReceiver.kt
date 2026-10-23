package id.rama.todoplanner.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Menghentikan bunyi + getar reminder: dipicu saat notifikasi dibuang
 * (deleteIntent), tombol aksi "Matikan Bunyi" diketuk, atau aplikasi dibuka.
 * Broadcast dipilih (bukan service PendingIntent) karena broadcast selalu
 * boleh dikirim dari interaksi notifikasi, termasuk saat aplikasi di latar.
 */
class StopSoundReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderSoundPlayer.stop()
        try {
            context.stopService(ReminderSoundService.stopIntent(context))
        } catch (e: Exception) { /* service memang belum tentu sedang jalan */ }
    }

    companion object {
        /** Minta berhenti dari kode aplikasi (mis. MainActivity saat dibuka dari notifikasi). */
        fun requestStop(context: Context) {
            ReminderSoundPlayer.stop()
            try {
                context.stopService(ReminderSoundService.stopIntent(context))
            } catch (e: Exception) { /* abaikan */ }
        }
    }
}
