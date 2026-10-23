package id.rama.todoplanner

import android.app.Application
import id.rama.todoplanner.data.AppDatabase
import id.rama.todoplanner.data.AppPrefs
import id.rama.todoplanner.data.Repository
import id.rama.todoplanner.reminder.ReminderChannels
import id.rama.todoplanner.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Kelas Application: satu-satunya tempat membuat database & repository (singleton sederhana). */
class TodoApplication : Application() {
    lateinit var repository: Repository
        private set
    lateinit var prefs: AppPrefs
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        prefs = AppPrefs(this)
        repository = Repository(db.activityDao(), db.targetDao())

        // RONDE 2 — jalur kegiatan LAMA: setiap aplikasi dibuka, pastikan
        // channel v3 siap (channel lama v1/ronde-1 dibersihkan) dan jadwalkan
        // ulang semua alarm, supaya kegiatan yang dibuat sebelum update ini
        // ikut berbunyi lewat jalur bunyi baru dari kode. Pekerjaan ringan
        // (jumlah kegiatan personal kecil) dan berjalan di IO dispatcher.
        val appCtx = this
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderChannels.ensureChannel(appCtx, prefs.globalRingtoneUri)
                ReminderScheduler.rescheduleAll(appCtx, repository.getAllActivitiesOnce(), prefs)
            } catch (e: Exception) { /* penjadwalan ulang juga terjadi di BootReceiver & saat simpan */ }
        }
    }
}
