package id.rama.todoplanner.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Path
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

/* ================= RONDE 9: warna pilihan user ================= */

/** Parse "#RRGGBB" / "RRGGBB" / "#AARRGGBB" -> Color; null bila tidak valid. */
fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val clean = hex.trim().removePrefix("#")
    return try {
        when (clean.length) {
            6 -> Color(android.graphics.Color.parseColor("#$clean"))
            8 -> Color(android.graphics.Color.parseColor("#$clean"))
            else -> null
        }
    } catch (e: Exception) { null }
}

/** Color -> "#RRGGBB" (alpha dibuang; warna UI di app ini selalu opaque). */
fun colorToHex(color: Color): String =
    "#%02X%02X%02X".format(
        (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()
    )

/** Versi lebih gelap (untuk teks di atas latar pastel) — tiap kanal dikali [factor]. */
fun darkenColor(color: Color, factor: Float = 0.72f): Color =
    Color(color.red * factor, color.green * factor, color.blue * factor, color.alpha)

/** Hex warna kategori lama (dipakai sebagai default bila user tidak memilih warna). */
fun categoryHex(category: String): String =
    (CategoryColors[category] ?: Color.Gray).let { colorToHex(it) }

/**
 * Warna EFEKTIF sebuah kegiatan untuk aksen/dot/pill kategori:
 * warna pilihan user ([ActivityEntity.colorHex]) bila ada & valid,
 * selain itu warna kategori lama. Satu fungsi ini dipakai SEMUA card
 * Todo Utama supaya tidak ada tempat yang lupa mengikuti warna custom.
 * Daily Reminder tidak wajib mengikuti warna ini (pemanggil Ritual
 * memang tidak memakai fungsi ini untuk aksennya).
 */
fun activityColor(activity: id.rama.todoplanner.data.ActivityEntity): Color =
    parseHexColor(activity.colorHex) ?: CategoryColors[activity.category] ?: Color.Gray

/**
 * RONDE 9 (amendemen 5) — warna PRIORITAS, TERPISAH dari warna custom
 * kategori dan TIDAK PERNAH mengikuti colorHex: Rendah = hijau,
 * Sedang = biru, Tinggi = merah. Dipakai chip Prioritas di Form dan
 * teks prioritas di card Beranda (span berwarna, bukan aksen kategori).
 */
val PriorityColors: Map<String, Color> = linkedMapOf(
    "Rendah" to Color(0xFF30A46C),
    "Sedang" to Color(0xFF0091FF),
    "Tinggi" to Color(0xFFE5484D)
)
fun priorityColor(priority: String): Color = PriorityColors[priority] ?: Color.Gray

/** 12 warna preset form "Pilih Warna" (hex, tanpa nama — swatch berbicara sendiri). */
val ColorPresets: List<String> = listOf(
    "#E5484D", "#F76B15", "#FFB224", "#FFE629",
    "#30A46C", "#46A758", "#0091FF", "#0090A3",
    "#8E4EC6", "#D6409F", "#946800", "#697177"
)

/** Warna default label "Daily Reminder" (hijau, sama seperti sebelum ronde 9). */
val DailyReminderDefaultColor: Color = Color(0xFF22714F)

/**
 * RONDE 9 (amendemen 6) — warna pill "Daily Reminder" dari hex setting
 * global: (latar, teks). Hex kosong = default lama persis (latar hijau
 * muda 0xFFDFF5E9 + teks hijau 0xFF22714F). Hex custom = latar warna itu
 * 16% + teks versi gelapnya (terbaca di semua preset, termasuk kuning).
 */
fun dailyReminderLabelColors(storedHex: String): Pair<Color, Color> {
    val parsed = parseHexColor(storedHex)
    if (parsed == null) return Color(0xFFDFF5E9) to DailyReminderDefaultColor
    return parsed.copy(alpha = 0.16f) to darkenColor(parsed, 0.7f)
}

/**
 * RONDE 9 (amendemen 5) — subline card dengan segmen "Prioritas X" yang
 * BERWARNA sesuai pemetaan tetap (bukan warna custom kategori): prefix
 * + "Prioritas {priority}" (tebal, warna prioritas) + suffix opsional.
 * Dipakai ActivityRow & UpcomingItem supaya warna prioritas terlihat di
 * card tanpa mengubah layout subline.
 */
fun annotatedWithPriority(prefix: String, priority: String, suffix: String = ""): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        append(prefix)
        pushStyle(
            androidx.compose.ui.text.SpanStyle(
                color = priorityColor(priority), fontWeight = FontWeight.Bold
            )
        )
        append("Prioritas $priority")
        pop()
        append(suffix)
    }

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
    // RONDE 7: keadaan disabled = abu-abu beneran (bukan gradasi pudar),
    // supaya jelas terlihat tidak bisa diklik.
    val colors = when {
        !enabled -> listOf(Color(0xFFB9C4CA), Color(0xFF9FB0B8))
        mint -> listOf(Color(0xFF5FA9CE), Color(0xFF7FC48F))
        else -> listOf(Color(0xFFF2A65A), Color(0xFFF28B82))
    }
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

/**
 * Ilustrasi pemandangan fajar mockup Beranda, digambar ulang dengan Canvas Compose
 * mengikuti SVG acuan (viewBox 358x92): matahari terbit berpendar di kanan,
 * dua kelompok awan putih, dua lapis bukit hijau di bawah.
 */
@Composable
fun DawnScenery(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Matahari + pendar (SVG: cx 292, cy 58, r 27, halo r 36)
        val sun = Offset(w * (292f / 358f), h * (58f / 92f))
        drawCircle(color = Color(0xFFF6A56B).copy(alpha = 0.16f), radius = h * (36f / 92f), center = sun)
        drawCircle(
            brush = Brush.verticalGradient(listOf(Color(0xFFFFD98A), Color(0xFFF6A56B))),
            radius = h * (27f / 92f), center = sun
        )
        // Awan (elips putih SVG): kelompok kiri (62,34,30,10)+(86,28,20,9); kanan (196,20,24,8)+(214,16,14,6) α .85
        fun cloud(cx: Float, cy: Float, rx: Float, ry: Float, alpha: Float) {
            drawOval(
                color = Color.White.copy(alpha = alpha),
                topLeft = Offset(w * (cx - rx) / 358f, h * (cy - ry) / 92f),
                size = Size(w * (2 * rx) / 358f, h * (2 * ry) / 92f)
            )
        }
        cloud(62f, 34f, 30f, 10f, 1f)
        cloud(86f, 28f, 20f, 9f, 1f)
        cloud(196f, 20f, 24f, 8f, 0.85f)
        cloud(214f, 16f, 14f, 6f, 0.85f)
        // Bukit belakang #BFE3D2: M0 74 Q60 58 120 70 T240 68 T358 62 V92 H0 Z
        val hillBack = Path().apply {
            moveTo(0f, h * (74f / 92f))
            quadraticBezierTo(w * (60f / 358f), h * (58f / 92f), w * (120f / 358f), h * (70f / 92f))
            quadraticBezierTo(w * (180f / 358f), h * (80f / 92f), w * (240f / 358f), h * (68f / 92f))
            quadraticBezierTo(w * (300f / 358f), h * (60f / 92f), w, h * (62f / 92f))
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(hillBack, color = Color(0xFFBFE3D2))
        // Bukit depan #8FD0B4 α .65: M0 82 Q80 70 170 80 T358 76 V92 H0 Z
        val hillFront = Path().apply {
            moveTo(0f, h * (82f / 92f))
            quadraticBezierTo(w * (80f / 358f), h * (70f / 92f), w * (170f / 358f), h * (80f / 92f))
            quadraticBezierTo(w * (265f / 358f), h * (88f / 92f), w, h * (76f / 92f))
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(hillFront, color = Color(0xFF8FD0B4).copy(alpha = 0.65f))
    }
}

/** Pill aksen kecil mockup (badge streak / fase di kartu progres & statistik). */
@Composable
fun AccentPill(text: String, bg: Color, fg: Color = InkColor) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = fg, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
            topLeft = topLeft, size = arcSize,
            style = Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

/* ================= RONDE 2: bahasa visual mockup ================= */

/**
 * Kartu putih lembut ala mockup: permukaan PUTIH (bukan abu surfaceVariant
 * bawaan Material yang membuat kartu Statistik/Beranda tampak abu polos di
 * uji ronde 1), radius besar 18dp, bayangan sangat lembut.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = content
    )
}

/** Pill/badge kecil pastel ala mockup (mis. "🔥 Streak 6 hari"). */
@Composable
fun PastelPill(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Bar progres gradasi dengan rel abu muda, tinggi 9dp seperti mockup (.bar). */
@Composable
fun GradientBar(progress: Float, colors: List<Color>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(9.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFEDF3F6))
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).height(9.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(colors))
            )
        }
    }
}

/**
 * Ilustrasi pemandangan fajar untuk header Beranda — digambar dengan Canvas
 * meniru SVG mockup persis: matahari terbit ber-halo di kanan, awan putih,
 * dua lapis bukit hijau mint. Tanpa aset gambar, full vektor Compose.
 */
@Composable
fun DawnIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().size(78.dp)) {
        val w = size.width
        val h = size.height
        // Halo matahari
        drawCircle(color = Color(0xFFF6A56B).copy(alpha = 0.16f), radius = h * 0.46f, center = Offset(w * 0.815f, h * 0.66f))
        // Matahari terbit (gradasi kuning -> oranye lembut)
        drawCircle(
            brush = Brush.verticalGradient(listOf(Color(0xFFFFD98A), Color(0xFFF6A56B))),
            radius = h * 0.35f, center = Offset(w * 0.815f, h * 0.66f)
        )
        // Awan putih (gugus elips seperti SVG mockup)
        drawOval(color = Color.White, topLeft = Offset(w * 0.09f, h * 0.28f), size = Size(w * 0.17f, h * 0.24f))
        drawOval(color = Color.White, topLeft = Offset(w * 0.155f, h * 0.18f), size = Size(w * 0.115f, h * 0.22f))
        drawOval(color = Color.White.copy(alpha = 0.85f), topLeft = Offset(w * 0.475f, h * 0.12f), size = Size(w * 0.135f, h * 0.20f))
        drawOval(color = Color.White.copy(alpha = 0.85f), topLeft = Offset(w * 0.525f, h * 0.05f), size = Size(w * 0.08f, h * 0.16f))
        // Bukit lapis belakang (#BFE3D2)
        val hillBack = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h * 0.80f)
            quadraticBezierTo(w * 0.17f, h * 0.60f, w * 0.34f, h * 0.76f)
            quadraticBezierTo(w * 0.50f, h * 0.90f, w * 0.67f, h * 0.74f)
            quadraticBezierTo(w * 0.84f, h * 0.62f, w, h * 0.68f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(hillBack, color = Color(0xFFBFE3D2))
        // Bukit lapis depan (#8FD0B4, 65%)
        val hillFront = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h * 0.90f)
            quadraticBezierTo(w * 0.22f, h * 0.74f, w * 0.47f, h * 0.87f)
            quadraticBezierTo(w * 0.72f, h * 0.97f, w, h * 0.83f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(hillFront, color = Color(0xFF8FD0B4).copy(alpha = 0.65f))
    }
}

/** Latar pastel lembut per kategori untuk pill (pasangan [CategoryColors]). */
val CategoryPastelBg: Map<String, Color> = linkedMapOf(
    "Kerja" to Color(0xFFFDE9E4),
    "Keluarga" to Color(0xFFDCEEF7),
    "Ibadah" to Color(0xFFE9E4F7),
    "Olahraga" to Color(0xFFDFF5E9),
    "Kesehatan" to Color(0xFFFBE7F1),
    "Keuangan" to Color(0xFFFCF0D8)
)

/**
 * Pill kategori pastel berwarna + label (bahasa visual mockup: chip kategori di baris jam & tabel PDF).
 * RONDE 9: [color] = warna EFEKTIF (custom user bila ada, lihat [activityColor]);
 * bila null, perilaku lama (warna kategori + latar pastel per kategori).
 * Warna custom: latar = warna itu 18% + teks = versi gelapnya (terbaca di semua preset).
 */
@Composable
fun CategoryPill(category: String, color: Color? = null) {
    val fg = if (color != null) darkenColor(color) else CategoryColors[category] ?: Color.Gray
    val bg = if (color != null) color.copy(alpha = 0.18f)
        else CategoryPastelBg[category] ?: Color(0xFFEDF3F6)
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(category, color = fg, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * Ilustrasi header Beranda per FASE HARI (ronde 3) — vektor Canvas, tanpa aset:
 *  - PAGI : fajar (sama persis DawnIllustration ronde 2).
 *  - SIANG: matahari tinggi kecil + awan lebih banyak, bukit hijau terang.
 *  - SORE : matahari besar rendah oranye-pink dekat cakrawala, awan kemerahan, bukit lebih gelap.
 *  - MALAM: bulan sabit + bintang-bintang, tanpa matahari, bukit siluet gelap.
 */
@Composable
fun PhaseScenery(phase: id.rama.todoplanner.domain.DayPhase, modifier: Modifier = Modifier) {
    if (phase == id.rama.todoplanner.domain.DayPhase.PAGI) {
        DawnIllustration(modifier)
        return
    }
    Canvas(modifier = modifier.fillMaxWidth().size(78.dp)) {
        val w = size.width
        val h = size.height
        fun cloud(cx: Float, cy: Float, sx: Float, sy: Float, color: Color) {
            drawOval(color = color, topLeft = Offset(w * (cx - sx / 2), h * (cy - sy / 2)), size = Size(w * sx, h * sy))
        }
        when (phase) {
            id.rama.todoplanner.domain.DayPhase.SIANG -> {
                // Matahari tinggi (kiri-atas), kecil & terang + halo tipis
                drawCircle(color = Color(0xFFFFD98A).copy(alpha = 0.35f), radius = h * 0.30f, center = Offset(w * 0.16f, h * 0.30f))
                drawCircle(brush = Brush.verticalGradient(listOf(Color(0xFFFFE9A8), Color(0xFFFFC95E))), radius = h * 0.21f, center = Offset(w * 0.16f, h * 0.30f))
                cloud(0.45f, 0.28f, 0.20f, 0.26f, Color.White)
                cloud(0.52f, 0.20f, 0.13f, 0.22f, Color.White)
                cloud(0.72f, 0.38f, 0.17f, 0.22f, Color.White.copy(alpha = 0.9f))
                cloud(0.88f, 0.22f, 0.12f, 0.18f, Color.White.copy(alpha = 0.85f))
            }
            id.rama.todoplanner.domain.DayPhase.SORE -> {
                // Matahari rendah besar di cakrawala + awan kemerahan
                drawCircle(color = Color(0xFFF28B82).copy(alpha = 0.22f), radius = h * 0.62f, center = Offset(w * 0.78f, h * 0.88f))
                drawCircle(brush = Brush.verticalGradient(listOf(Color(0xFFFFB37E), Color(0xFFF2784B))), radius = h * 0.44f, center = Offset(w * 0.78f, h * 0.88f))
                cloud(0.18f, 0.24f, 0.19f, 0.22f, Color(0xFFFFD9C4))
                cloud(0.26f, 0.17f, 0.12f, 0.18f, Color(0xFFFFD9C4))
                cloud(0.52f, 0.14f, 0.14f, 0.18f, Color(0xFFFFC9B8).copy(alpha = 0.9f))
            }
            id.rama.todoplanner.domain.DayPhase.MALAM -> {
                // Bintang-bintang (titik putih berbagai alpha) + bulan sabit (dua lingkaran tumpang tindih)
                val stars = listOf(
                    Triple(0.08f, 0.18f, 2.2f), Triple(0.20f, 0.38f, 1.6f), Triple(0.33f, 0.14f, 2.6f),
                    Triple(0.46f, 0.30f, 1.5f), Triple(0.58f, 0.12f, 2.0f), Triple(0.68f, 0.34f, 1.4f),
                    Triple(0.90f, 0.16f, 2.4f), Triple(0.95f, 0.42f, 1.5f), Triple(0.14f, 0.55f, 1.4f),
                    Triple(0.40f, 0.50f, 1.3f), Triple(0.62f, 0.52f, 1.3f), Triple(0.85f, 0.58f, 1.2f)
                )
                stars.forEach { (x, y, r) ->
                    drawCircle(color = Color.White.copy(alpha = 0.55f + (r - 1.2f) * 0.25f), radius = r, center = Offset(w * x, h * y))
                }
                // Bulan sabit: dua oval dalam satu Path EvenOdd (tanpa lingkaran timpa berwarna latar)
                val moon = Path().apply {
                    fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                    addOval(androidx.compose.ui.geometry.Rect(center = Offset(w * 0.80f, h * 0.34f), radius = h * 0.30f))
                    addOval(androidx.compose.ui.geometry.Rect(center = Offset(w * 0.85f, h * 0.26f), radius = h * 0.26f))
                }
                drawPath(moon, color = Color(0xFFFFF3C4))
            }
            else -> Unit
        }
        // Bukit dua lapis (warna per fase)
        val back = when (phase) {
            id.rama.todoplanner.domain.DayPhase.SORE -> Color(0xFF9A7B8F)
            id.rama.todoplanner.domain.DayPhase.MALAM -> Color(0xFF2E3A5C)
            else -> Color(0xFFBFE3D2)
        }
        val front = when (phase) {
            id.rama.todoplanner.domain.DayPhase.SORE -> Color(0xFF6E5570)
            id.rama.todoplanner.domain.DayPhase.MALAM -> Color(0xFF1D2742)
            else -> Color(0xFF8FD0B4)
        }
        val hillBack = Path().apply {
            moveTo(0f, h * 0.80f)
            quadraticBezierTo(w * 0.17f, h * 0.60f, w * 0.34f, h * 0.76f)
            quadraticBezierTo(w * 0.50f, h * 0.90f, w * 0.67f, h * 0.74f)
            quadraticBezierTo(w * 0.84f, h * 0.62f, w, h * 0.68f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(hillBack, color = back)
        val hillFront = Path().apply {
            moveTo(0f, h * 0.90f)
            quadraticBezierTo(w * 0.22f, h * 0.74f, w * 0.47f, h * 0.87f)
            quadraticBezierTo(w * 0.72f, h * 0.97f, w, h * 0.83f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(hillFront, color = front.copy(alpha = if (phase == id.rama.todoplanner.domain.DayPhase.MALAM) 1f else 0.75f))
    }
}

/** Ilustrasi pegunungan mini untuk kartu puncak Statistik di mockup (segitiga bukit + matahari + bendera). */
@Composable
fun PeakIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(86.dp, 64.dp)) {
        val w = size.width
        val h = size.height
        val back = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.02f, h * 0.88f); lineTo(w * 0.30f, h * 0.47f); lineTo(w * 0.51f, h * 0.66f)
            lineTo(w * 0.77f, h * 0.22f); lineTo(w * 0.98f, h * 0.88f); close()
        }
        drawPath(back, color = Color(0xFFDCEEF7))
        val front = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.02f, h * 0.88f); lineTo(w * 0.30f, h * 0.47f); lineTo(w * 0.44f, h * 0.60f)
            lineTo(w * 0.67f, h * 0.38f); lineTo(w * 0.84f, h * 0.55f); lineTo(w * 0.98f, h * 0.88f); close()
        }
        drawPath(front, color = Color(0xFFBFE3D2).copy(alpha = 0.85f))
        // Matahari di puncak + bendera target
        drawCircle(color = Color(0xFFFFD98A), radius = h * 0.10f, center = Offset(w * 0.77f, h * 0.20f))
        drawLine(color = Color(0xFF8A5E14), start = Offset(w * 0.77f, h * 0.20f), end = Offset(w * 0.77f, h * 0.05f), strokeWidth = 2.5f)
        val flag = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.77f, h * 0.05f); lineTo(w * 0.91f, h * 0.09f); lineTo(w * 0.77f, h * 0.15f); close()
        }
        drawPath(flag, color = Coral)
    }
}
