package id.rama.todoplanner.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.rama.todoplanner.TodoApplication
import id.rama.todoplanner.data.ActivityEntity
import id.rama.todoplanner.data.TargetEntity
import id.rama.todoplanner.reminder.ReminderScheduler
import id.rama.todoplanner.ui.components.CropRectFrac
import id.rama.todoplanner.ui.components.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Snapshot profil & pengaturan (sumbernya AppPrefs) supaya Compose recompose saat berubah. */
data class ProfileState(
    val name: String,
    val colorIndex: Int,
    val emoji: String,
    val photoPath: String,
    /** Waktu ubah file foto — memaksa avatar dimuat ulang walau path tidak berubah (putar/crop). */
    val photoStamp: Long,
    val globalToneUri: String?,
    val globalToneName: String,
    val themeMode: String
)

class AppViewModel(private val app: TodoApplication) : AndroidViewModel(app) {
    private val repo = app.repository
    private val prefs = app.prefs

    val activities: StateFlow<List<ActivityEntity>> =
        repo.activities.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val targets: StateFlow<List<TargetEntity>> =
        repo.targets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _profile = MutableStateFlow(readProfile())
    val profile: StateFlow<ProfileState> = _profile.asStateFlow()

    private fun readProfile() = ProfileState(
        name = prefs.userName, colorIndex = prefs.avatarColorIndex, emoji = prefs.avatarEmoji,
        photoPath = prefs.avatarPhotoPath,
        photoStamp = prefs.avatarPhotoPath.takeIf { it.isNotBlank() }
            ?.let { File(it).takeIf { f -> f.exists() }?.lastModified() } ?: 0L,
        globalToneUri = prefs.globalRingtoneUri,
        globalToneName = prefs.globalRingtoneName, themeMode = prefs.themeMode
    )
    private fun refreshProfile() { _profile.value = readProfile() }

    fun saveProfile(name: String, colorIndex: Int, emoji: String) {
        prefs.userName = name.ifBlank { "Rama" }
        prefs.avatarColorIndex = colorIndex
        prefs.avatarEmoji = emoji
        refreshProfile()
    }

    /**
     * Salin foto dari photo picker ke penyimpanan internal (persisten & lokal).
     * PERBAIKAN RONDE 1: orientasi EXIF kini di-bake ke piksel (dulu byte mentah
     * disalin begitu saja → foto tampil miring di avatar).
     */
    fun savePhotoFromUri(uri: Uri) {
        viewModelScope.launch {
            val target = File(app.filesDir, "profile_photo.jpg")
            val ok = withContext(Dispatchers.IO) { PhotoStore.importPhoto(app, uri, target) }
            if (ok) {
                prefs.avatarPhotoPath = target.absolutePath
                refreshProfile()
            }
        }
    }

    /** Simpan hasil layar crop manual (bitmap kerja + jendela crop ter-normalisasi). */
    fun saveCroppedPhoto(bitmap: Bitmap, rect: CropRectFrac) {
        viewModelScope.launch {
            val target = File(app.filesDir, "profile_photo.jpg")
            val ok = withContext(Dispatchers.IO) { PhotoStore.saveCropBitmap(bitmap, rect, target) }
            if (ok) {
                prefs.avatarPhotoPath = target.absolutePath
                refreshProfile()
            }
        }
    }

    /** Putar foto profil tersimpan 90° searah jarum jam (pengaturan manual di Profil). */
    fun rotatePhoto() {
        val path = prefs.avatarPhotoPath
        if (path.isBlank()) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { PhotoStore.rotateClockwise(File(path)) }
            if (ok) refreshProfile()
        }
    }

    fun removePhoto() {
        val path = prefs.avatarPhotoPath
        if (path.isNotBlank()) File(path).delete()
        prefs.avatarPhotoPath = ""
        refreshProfile()
    }

    fun setGlobalTone(uri: String?, name: String) {
        prefs.globalRingtoneUri = uri
        prefs.globalRingtoneName = name
        refreshProfile()
        // Nada global berubah -> jadwal ulang semua reminder agar channel/nadanya ikut berubah.
        viewModelScope.launch {
            repo.getAllActivitiesOnce().forEach { ReminderScheduler.schedule(app, it, prefs) }
        }
    }

    fun setThemeMode(mode: String) { prefs.themeMode = mode; refreshProfile() }

    fun toggleDone(activity: ActivityEntity, epochDay: Long, done: Boolean) {
        viewModelScope.launch { repo.saveActivity(activity.withDoneOn(epochDay, done)) }
    }

    fun saveActivity(entity: ActivityEntity, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.saveActivity(entity)
            val saved = entity.copy(id = id)
            ReminderScheduler.schedule(app, saved, prefs)
            onSaved(id)
        }
    }

    fun deleteActivity(activity: ActivityEntity) {
        viewModelScope.launch {
            ReminderScheduler.cancel(app, activity.id)
            repo.deleteActivity(activity)
        }
    }

    /** Tunda: geser tanggal mulai seri +1 hari dan jadwalkan ulang reminder-nya. */
    fun snoozeActivity(activity: ActivityEntity) {
        saveActivity(activity.copy(dateEpochDay = activity.dateEpochDay + 1))
    }

    fun saveTarget(entity: TargetEntity) { viewModelScope.launch { repo.saveTarget(entity) } }
    fun deleteTarget(entity: TargetEntity) { viewModelScope.launch { repo.deleteTarget(entity) } }

    /** Import backup: ganti seluruh data lalu jadwalkan ulang semua reminder. */
    fun replaceAll(activities: List<ActivityEntity>, targets: List<TargetEntity>, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.getAllActivitiesOnce().forEach { ReminderScheduler.cancel(app, it.id) }
            repo.replaceAll(activities, targets)
            repo.getAllActivitiesOnce().forEach { ReminderScheduler.schedule(app, it, prefs) }
            onDone()
        }
    }

    companion object {
        fun factory(app: TodoApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(app) as T
            }
    }
}
