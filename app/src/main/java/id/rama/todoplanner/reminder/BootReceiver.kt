package id.rama.todoplanner.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import id.rama.todoplanner.TodoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Setelah HP reboot, semua alarm hilang — jadwalkan ulang seluruh reminder di sini. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as TodoApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.getAllActivitiesOnce().forEach {
                    ReminderScheduler.schedule(context.applicationContext, it, app.prefs)
                }
            } finally { pending.finish() }
        }
    }
}
