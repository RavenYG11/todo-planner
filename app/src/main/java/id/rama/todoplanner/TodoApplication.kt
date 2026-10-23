package id.rama.todoplanner

import android.app.Application
import id.rama.todoplanner.data.AppDatabase
import id.rama.todoplanner.data.AppPrefs
import id.rama.todoplanner.data.Repository

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
    }
}
