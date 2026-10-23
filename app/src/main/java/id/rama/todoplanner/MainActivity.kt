package id.rama.todoplanner

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import id.rama.todoplanner.reminder.StopSoundReceiver
import id.rama.todoplanner.ui.AppViewModel
import id.rama.todoplanner.ui.TodoApp
import id.rama.todoplanner.ui.theme.TodoPlannerTheme

/** Activity tunggal. Seluruh navigasi & aturan Back HP diatur di TodoApp (peta induk tetap). */
class MainActivity : ComponentActivity() {

    private lateinit var vm: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TodoApplication
        vm = ViewModelProvider(this, AppViewModel.factory(app))[AppViewModel::class.java]
        // Ronde 2: membuka aplikasi (termasuk dari ketukan notifikasi reminder)
        // menghentikan bunyi + getar yang sedang diputar dari kode.
        // RONDE 4: peluncuran WAKE (fullScreenIntent yang menyalakan layar)
        // TIDAK menghentikan bunyi — alarm harus tetap berdering sampai user
        // bertindak (aksi "Matikan Bunyi" / ketuk notifikasi) atau 20 detik
        // habis. Tanpa ini layar menyala tetapi bunyi mati seketika.
        if (intent?.getBooleanExtra(EXTRA_WAKE_REMINDER, false) != true) {
            StopSoundReceiver.requestStop(this)
        }
        handleReminderIntent(intent)
        setContent {
            val profile by vm.profile.collectAsState()
            TodoPlannerTheme(mode = profile.themeMode) {
                TodoApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTop: notifikasi diketuk saat aplikasi sudah terbuka di atas.
        // Peluncuran WAKE (fullScreenIntent) tidak menghentikan bunyi (lihat onCreate).
        if (intent.getBooleanExtra(EXTRA_WAKE_REMINDER, false) != true) {
            StopSoundReceiver.requestStop(this)
        }
        if (::vm.isInitialized) handleReminderIntent(intent)
    }

    /**
     * RONDE 4 — intent dari reminder (fullScreenIntent / ketukan notifikasi):
     *  - Khusus peluncuran WAKE (fullScreenIntent): bangunkan layar ala
     *    aplikasi alarm — tampil di atas lockscreen + nyalakan layar
     *    (API 27+: setShowWhenLocked/setTurnScreenOn; di bawah itu flag
     *    window ekuivalen).
     *  - Teruskan permintaan buka Detail kegiatan ke TodoApp lewat ViewModel
     *    (berlaku untuk WAKE maupun ketukan notifikasi).
     */
    private fun handleReminderIntent(intent: Intent?) {
        if (intent == null) return
        val wake = intent.getBooleanExtra(EXTRA_WAKE_REMINDER, false)
        val activityId = intent.getLongExtra(EXTRA_OPEN_DETAIL_ID, 0L)
        val epochDay = intent.getLongExtra(EXTRA_OPEN_DETAIL_EPOCH_DAY, 0L)
        if (!wake && activityId <= 0L) return
        if (wake) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                )
            }
        }
        if (activityId > 0L) vm.requestOpenDetail(activityId, epochDay)
    }

    companion object {
        const val EXTRA_WAKE_REMINDER = "extra_wake_reminder"
        const val EXTRA_OPEN_DETAIL_ID = "extra_open_detail_id"
        const val EXTRA_OPEN_DETAIL_EPOCH_DAY = "extra_open_detail_epoch_day"

        /** Selisih request code PendingIntent fullScreenIntent vs contentIntent (extras tidak membedakan PendingIntent). */
        const val WAKE_PI_OFFSET = 500_000

        /**
         * Intent membuka aplikasi DARI reminder (dipakai receiver & service).
         * [wake] = true KHUSUS untuk fullScreenIntent (menyalakan layar, bunyi
         * TIDAK dihentikan); ketukan notifikasi memakai wake = false.
         */
        fun reminderIntent(context: Context, activityId: Long, occEpochDay: Long, wake: Boolean = false): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (wake) putExtra(EXTRA_WAKE_REMINDER, true)
                if (activityId > 0L) {
                    putExtra(EXTRA_OPEN_DETAIL_ID, activityId)
                    putExtra(EXTRA_OPEN_DETAIL_EPOCH_DAY, occEpochDay)
                }
            }
    }
}
