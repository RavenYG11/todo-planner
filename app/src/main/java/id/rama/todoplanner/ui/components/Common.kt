package id.rama.todoplanner.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rama.todoplanner.ui.theme.AmberSoft
import id.rama.todoplanner.ui.theme.Coral
import id.rama.todoplanner.ui.theme.Lavender
import id.rama.todoplanner.ui.theme.Mint
import id.rama.todoplanner.ui.theme.Peach
import id.rama.todoplanner.ui.theme.RoseSoft
import id.rama.todoplanner.ui.theme.SkyBlue
import java.io.File

/** Warna pastel per kategori — satu bahasa di semua layar (dari mockup). */
val CategoryColors: Map<String, Color> = linkedMapOf(
    "Kerja" to Coral,
    "Keluarga" to SkyBlue,
    "Ibadah" to Lavender,
    "Olahraga" to Color(0xFF58A97C),
    "Kesehatan" to RoseSoft,
    "Keuangan" to AmberSoft
)
val Categories: List<String> = CategoryColors.keys.toList()

/** 6 gradasi avatar dari mockup Profil. */
val AvatarGradients: List<List<Color>> = listOf(
    listOf(Color(0xFF7FC4EA), Peach),
    listOf(Peach, Coral),
    listOf(Mint, Color(0xFF58A97C)),
    listOf(Color(0xFFB7A8E8), Lavender),
    listOf(Color(0xFFF2A9C4), RoseSoft),
    listOf(Color(0xFFF2CD7E), AmberSoft)
)

/** Warna tinta utama mockup. */
val InkColor = Color(0xFF22333D)

/**
 * Tombol utama gradasi mockup "Langit Pagi":
 * default coral→peach (CTA), [mint] = biru langit→mint untuk aksi sekunder besar.
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mint: Boolean = false,
    enabled: Boolean = true
) {
    val colors = if (mint) listOf(Color(0xFF5FA9CE), Color(0xFF7FC48F))
    else listOf(Color(0xFFF2A65A), Color(0xFFF28B82))
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    if (enabled) colors else colors.map { it.copy(alpha = 0.4f) }
                )
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Kepala layar gradasi fajar lembut mockup (biru langit → krem peach). */
@Composable
fun DawnHeader(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFFCDEBFF), Color(0xFFEAF7FF), Color(0xFFFFE7CF))
                )
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InkColor)
    }
}

/**
 * Chip pilihan ala mockup: terpilih = pastel sesuai aksen + bingkai aksen + label tinta tebal;
 * tidak terpilih = abu sangat muda + label abu. Lebar selalu mengikuti konten.
 */
@Composable
fun PastelChip(selected: Boolean, text: String, accent: Color, onClick: () -> Unit) {
    val bg = if (selected) accent.copy(alpha = 0.22f) else Color(0xFFF0F4F6)
    val fg = if (selected) InkColor else Color(0xFF5B6E77)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(if (selected) Modifier.border(1.5.dp, accent, RoundedCornerShape(20.dp)) else Modifier)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = fg, fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1
        )
    }
}

/** Avatar lingkaran: foto galeri (crop tengah) bila ada; selain itu emoji / inisial nama. */
@Composable
fun AvatarView(photoPath: String, name: String, emoji: String, colorIndex: Int, size: Dp = 46.dp) {
    // Kunci cache menyertakan waktu ubah file: foto yang diputar/diganti di path yang sama
    // tetap dimuat ulang (path-nya tidak berubah). Dihitung segar tiap komposisi.
    val stamp = if (photoPath.isNotBlank()) {
        File(photoPath).takeIf { it.exists() }?.lastModified() ?: 0L
    } else 0L
    val bitmap = remember(photoPath, stamp) {
        if (photoPath.isNotBlank()) {
            val f = File(photoPath)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        } else null
    }
    val gradient = AvatarGradients[colorIndex.coerceIn(0, AvatarGradients.lastIndex)]
    Box(
        modifier = Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "Foto profil", contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            val label = if (emoji.isNotBlank()) emoji else name.trim().firstOrNull()?.uppercase() ?: "?"
            Text(text = label, color = Color.White, fontSize = (size.value * 0.42).sp)
        }
    }
}

/** Ring progres gradasi pastel (dipinjam dari V2, dilembutkan — sesuai mockup revisi). */
@Composable
fun RingProgress(progress: Float, size: Dp = 92.dp, stroke: Dp = 10.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val sw = stroke.toPx()
        val arcSize = Size(this.size.width - sw, this.size.height - sw)
        val topLeft = Offset(sw / 2, sw / 2)
        drawArc(
            color = Color(0xFFE7EEF2), startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize, style = Stroke(width = sw)
        )
        drawArc(
            brush = Brush.sweepGradient(listOf(SkyBlue, Peach, Mint, SkyBlue)),
            startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
            topLeft = topLeft, size = arcSize, style = Stroke(width = sw)
        )
    }
}
