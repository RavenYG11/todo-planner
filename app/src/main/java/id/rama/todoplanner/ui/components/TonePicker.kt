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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.data.AppPrefs
import id.rama.todoplanner.ui.theme.SkyBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Nama tampilan sebuah URI nada (judul dari sistem Android). */
fun ringtoneTitle(context: Context, uriString: String?): String = when {
    uriString == AppPrefs.RINGTONE_SILENT -> "Senyap / Getar saja"
    uriString == AppPrefs.RINGTONE_DEFAULT -> "Bawaan HP"
    uriString.isNullOrBlank() -> "Bawaan HP"
    else -> try {
        RingtoneManager.getRingtone(context, Uri.parse(uriString))?.getTitle(context) ?: "Nada pilihan"
    } catch (e: Exception) { "Nada pilihan" }
}

/** URI nada default sistem untuk preview: alarm bawaan → notifikasi → dering. */
private fun systemDefaultToneUri(): Uri? =
    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

/** Apakah nilai ini berarti "nada default sistem"? (null, kosong, atau sentinel ronde 3) */
fun isDefaultTone(uriString: String?): Boolean =
    uriString.isNullOrBlank() || uriString == AppPrefs.RINGTONE_DEFAULT

/**
 * Field nada dering — RONDE 3.
 *
 * Struktur (atas ke bawah):
 *  1. Bila [showDefaultOption] = true (pemakaian GLOBAL di layar Reminder),
 *     opsi eksplisit PALING ATAS: "Bawaan HP (default)". Memilihnya memanggil
 *     [onPicked] dengan (null, "Bawaan HP") — untuk global itu berarti URI
 *     kustom DIHAPUS, jadi nada kembali ke bawaan HP. Opsi tampil terpilih
 *     (pastel + bingkai) bila nada aktif memang default.
 *  2. Baris nada aktif: nama nada + ▶ Contoh (preview; untuk default, preview
 *     memutar nada default sistem jalur alarm→notifikasi→dering) + tombol
 *     "Ganti" bergaya gradasi yang membuka RINGTONE PICKER BAWAAN ANDROID.
 *
 * Hasil [onPicked]: URI string kustom, [AppPrefs.RINGTONE_SILENT], atau
 * null untuk default (konteks global). Konteks per-kegiatan memakai sentinel
 * [AppPrefs.RINGTONE_DEFAULT] lewat chip di Form — lihat FormScreen.
 */
@Composable
fun ToneField(
    currentUri: String?,
    label: String,
    showDefaultOption: Boolean = false,
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
        val uri = if (isDefaultTone(currentUri)) {
            systemDefaultToneUri()
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

    Column(modifier = Modifier.fillMaxWidth()) {
        if (showDefaultOption) {
            val defaultSelected = isDefaultTone(currentUri)
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (defaultSelected) SkyBlue.copy(alpha = 0.20f) else Color(0xFFF0F4F6))
                    .then(
                        if (defaultSelected) Modifier.border(1.5.dp, SkyBlue, RoundedCornerShape(14.dp))
                        else Modifier
                    )
                    .clickable {
                        stopPreview()
                        // Reset ke bawaan HP: null = hapus URI kustom (global).
                        onPicked(null, "Bawaan HP")
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "🎵 Bawaan HP (default)", fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp, color = Color(0xFF22333D), modifier = Modifier.weight(1f)
                    )
                    if (defaultSelected) Text("✓ Terpilih", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B6E91))
                }
            }
            Spacer(Modifier.padding(vertical = 4.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "🎵 " + ringtoneTitle(context, currentUri), modifier = Modifier.weight(1f))
            if (currentUri != AppPrefs.RINGTONE_SILENT) {
                TextButton(onClick = { togglePreview() }) {
                    Text(if (playing) "⏹ Stop" else "▶ Contoh")
                }
            }
            Spacer(Modifier.width(4.dp))
            GradientButton(
                text = "Ganti",
                onClick = {
                    stopPreview()
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, label)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        if (!isDefaultTone(currentUri) && currentUri != AppPrefs.RINGTONE_SILENT) {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentUri))
                        }
                    }
                    launcher.launch(intent)
                }
            )
        }
    }
}
