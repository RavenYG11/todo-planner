package id.rama.todoplanner.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import id.rama.todoplanner.TodoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Setelah HP reboot, semua alarm hilang — jadwalkan ulang seluruh reminder di sini.
 * Ronde 2: sekalian pastikan channel v3 tersedia & channel lama dibersihkan,
 * supaya kegiatan LAMA (dibuat sebelum update ronde 2) ikut berbunyi dengan
 * jalur bunyi baru dari kode.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as TodoApplication
        val appCtx = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderChannels.ensureChannel(appCtx, app.prefs.globalRingtoneUri)
                ReminderScheduler.rescheduleAll(
                    appCtx, app.repository.getAllActivitiesOnce(), app.prefs
                )
            } finally { pending.finish() }
        }
    }
}
