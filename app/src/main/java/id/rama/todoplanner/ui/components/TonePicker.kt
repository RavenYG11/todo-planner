package id.rama.todoplanner.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.rama.todoplanner.data.AppPrefs

/** Nama tampilan sebuah URI nada (judul dari sistem Android). */
fun ringtoneTitle(context: Context, uriString: String?): String = when {
    uriString == AppPrefs.RINGTONE_SILENT -> "Senyap / Getar saja"
    uriString.isNullOrBlank() -> "Bawaan HP"
    else -> try {
        RingtoneManager.getRingtone(context, Uri.parse(uriString))?.getTitle(context) ?: "Nada pilihan"
    } catch (e: Exception) { "Nada pilihan" }
}

/**
 * Field nada dering: nama nada aktif + tombol Ganti yang membuka
 * RINGTONE PICKER BAWAAN ANDROID (bukan daftar buatan aplikasi).
 * Hasil: [onPicked] menerima URI string (atau AppPrefs.RINGTONE_SILENT) + nama tampilannya.
 */
@Composable
fun ToneField(
    currentUri: String?,
    label: String,
    onPicked: (uri: String?, name: String) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val picked: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (picked == null) {
                // Di ringtone picker, memilih "Senyap" menghasilkan URI null dengan result OK.
                onPicked(AppPrefs.RINGTONE_SILENT, "Senyap / Getar saja")
            } else {
                onPicked(picked.toString(), ringtoneTitle(context, picked.toString()))
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "🎵 " + ringtoneTitle(context, currentUri), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, label)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                if (!currentUri.isNullOrBlank() && currentUri != AppPrefs.RINGTONE_SILENT) {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentUri))
                }
            }
            launcher.launch(intent)
        }) { Text("Ganti") }
    }
}
