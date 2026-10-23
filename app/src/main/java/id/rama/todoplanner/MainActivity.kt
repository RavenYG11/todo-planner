package id.rama.todoplanner

import android.content.Intent
import android.os.Bundle
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ronde 2: membuka aplikasi (termasuk dari ketukan notifikasi reminder)
        // menghentikan bunyi + getar yang sedang diputar dari kode.
        StopSoundReceiver.requestStop(this)
        val app = application as TodoApplication
        val vm = ViewModelProvider(this, AppViewModel.factory(app))[AppViewModel::class.java]
        setContent {
            val profile by vm.profile.collectAsState()
            TodoPlannerTheme(mode = profile.themeMode) {
                TodoApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTop: notifikasi diketuk saat aplikasi sudah terbuka di atas.
        StopSoundReceiver.requestStop(this)
    }
}
