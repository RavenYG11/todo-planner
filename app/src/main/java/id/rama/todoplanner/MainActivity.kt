package id.rama.todoplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import id.rama.todoplanner.ui.AppViewModel
import id.rama.todoplanner.ui.TodoApp
import id.rama.todoplanner.ui.theme.TodoPlannerTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** Activity tunggal. Seluruh navigasi & aturan Back HP diatur di TodoApp (peta induk tetap). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TodoApplication
        val vm = ViewModelProvider(this, AppViewModel.factory(app))[AppViewModel::class.java]
        setContent {
            val profile by vm.profile.collectAsState()
            TodoPlannerTheme(mode = profile.themeMode) {
                TodoApp(vm)
            }
        }
    }
}
