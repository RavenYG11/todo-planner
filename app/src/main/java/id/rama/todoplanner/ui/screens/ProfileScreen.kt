package id.rama.todoplanner.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.domain.Dates
import id.rama.todoplanner.ui.AppViewModel
import id.rama.todoplanner.ui.components.AvatarGradients
import id.rama.todoplanner.ui.components.AvatarView
import id.rama.todoplanner.ui.components.DawnHeader
import id.rama.todoplanner.ui.components.GradientButton
import id.rama.todoplanner.ui.components.PhotoCropper
import id.rama.todoplanner.ui.components.SoftCard
import java.time.LocalTime

/**
 * Profil & Personalisasi — UI BERSIH (aturan Rama #83): label singkat saja,
 * TANPA paragraf penjelasan/meta. Penjelasan teknis ada di README, bukan di UI.
 */
@Composable
fun ProfileScreen(vm: AppViewModel, onSaved: () -> Unit) {
    val profile by vm.profile.collectAsState()
    var name by remember { mutableStateOf(profile.name) }
    var colorIndex by remember { mutableStateOf(profile.colorIndex) }
    var emoji by remember { mutableStateOf(profile.emoji) }

    // Setelah memilih foto → layar crop manual (geser/zoom/putar) dulu, baru disimpan.
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cropUri = uri
    }
    cropUri?.let { uri ->
        BackHandler { cropUri = null }
        PhotoCropper(
            uri = uri,
            onCancel = { cropUri = null },
            onConfirm = { bmp, rect ->
                vm.saveCroppedPhoto(bmp, rect)
                cropUri = null
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        DawnHeader("👤 Profil & Personalisasi")
        Spacer(Modifier.height(12.dp))

        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AvatarView(profile.photoPath, name, emoji, colorIndex, 76.dp)
                Spacer(Modifier.height(8.dp))
                Text("Halo, ${name.ifBlank { "…" }}!", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(Dates.greeting(LocalTime.now().hour) + ", " + name)
            }
        }
        Spacer(Modifier.height(12.dp))

        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Nama", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = name, onValueChange = { if (it.length <= 30) name = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(12.dp))

                Text("Foto Profil", fontWeight = FontWeight.Bold)
                Text(if (profile.photoPath.isBlank()) "Belum ada foto" else "Foto aktif ✓")
                GradientButton(
                    text = "📷 Pilih Foto dari Galeri",
                    onClick = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                    mint = true
                )
                if (profile.photoPath.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.rotatePhoto() }, modifier = Modifier.weight(1f)) { Text("🔄 Putar 90°") }
                        OutlinedButton(onClick = { vm.removePhoto() }, modifier = Modifier.weight(1f)) { Text("🗑️ Hapus Foto") }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Warna Avatar", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    AvatarGradients.forEachIndexed { i, colors ->
                        Box(
                            modifier = Modifier.size(38.dp).clip(CircleShape)
                                .background(Brush.linearGradient(colors))
                                .then(if (i == colorIndex) Modifier.border(3.dp, Color(0xFF22333D), CircleShape) else Modifier)
                                .clickable { colorIndex = i }
                        )
                    }
                }

                Text("Avatar", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    FilterChip(selected = emoji.isEmpty(), onClick = { emoji = "" }, label = { Text("Inisial") })
                    listOf("😊", "🌅", "⭐", "🚀").forEach { e ->
                        FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(e) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                GradientButton(
                    text = "💾 Simpan Profil",
                    onClick = { vm.saveProfile(name.trim(), colorIndex, emoji); onSaved() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
