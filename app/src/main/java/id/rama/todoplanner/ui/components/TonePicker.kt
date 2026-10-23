package id.rama.todoplanner.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.rama.todoplanner.data.AppPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Nama tampilan sebuah URI nada (judul dari sistem Android). */
fun ringtoneTitle(context: Context, uriString: String?): String = when {
    uriString == AppPrefs.RINGTONE_SILENT -> "Senyap / Getar saja"
    uriString.isNullOrBlank() -> "Bawaan HP"
    else -> try {
        RingtoneManager.getRingtone(context, Uri.parse(uriString))?.getTitle(context) ?: "Nada pilihan"
    } catch (e: Exception) { "Nada pilihan" }
}

/**
 * Field nada dering: nama nada aktif + tombol Putar Contoh (preview, ronde 2)
 * + tombol Ganti yang membuka RINGTONE PICKER BAWAAN ANDROID.
 * Hasil: [onPicked] menerima URI string (atau AppPrefs.RINGTONE_SILENT) + nama tampilannya.
 */
@Composable
fun ToneField(
    currentUri: String?,
    label: String,
    onPicked: (uri: String?, name: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<Ringtone?>(null) }
    var playing by remember { mutableStateOf(false) }

    fun stopPreview() {
        try { preview?.stop() } catch (e: Exception) { /* abaikan */ }
        preview = null
        playing = false
    }
    DisposableEffect(Unit) { onDispose { stopPreview() } }

    fun togglePreview() {
        if (playing) { stopPreview(); return }
        if (currentUri == AppPrefs.RINGTONE_SILENT) return
        val uri = if (currentUri.isNullOrBlank()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            try { Uri.parse(currentUri) } catch (e: Exception) { null }
        } ?: return
        try {
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
            preview = ringtone
            playing = true
            scope.launch {
                delay(6_000)
                if (preview === ringtone) stopPreview()
            }
        } catch (e: Exception) { stopPreview() }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        stopPreview()
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
        if (currentUri != AppPrefs.RINGTONE_SILENT) {
            TextButton(onClick = { togglePreview() }) {
                Text(if (playing) "⏹ Stop" else "▶ Contoh")
            }
        }
        Spacer(Modifier.width(4.dp))
        Button(onClick = {
            stopPreview()
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
