package id.rama.todoplanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palet "Langit Pagi" dari mockup final.
val SkyBlue = Color(0xFF6FB3DC)
val SkyBlueDark = Color(0xFF3E7CB1)
val Peach = Color(0xFFF6B17A)
val Coral = Color(0xFFF28B82)
val Mint = Color(0xFF8FD0A8)
val GreenDeep = Color(0xFF58A97C)
val Lavender = Color(0xFF9B8BD4)
val AmberSoft = Color(0xFFE8B84B)
val RoseSoft = Color(0xFFE88BB0)
val Ink = Color(0xFF22333D)
val PaperBg = Color(0xFFFBFCFD)

private val LightColors = lightColorScheme(
    primary = SkyBlueDark,
    secondary = Coral,
    tertiary = GreenDeep,
    background = PaperBg,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    // Container pastel "Langit Pagi" (ronde 1): chip terpilih & elemen sekunder
    // tidak lagi biru Material pekat, melainkan pastel sesuai mockup.
    primaryContainer = Color(0xFFE1F2FB),
    onPrimaryContainer = Ink,
    secondaryContainer = Color(0xFFFDE9E4),
    onSecondaryContainer = Ink,
    surfaceVariant = Color(0xFFF0F4F6),
    onSurfaceVariant = Color(0xFF58707C),
    outline = Color(0xFFD8E2E8)
)

private val DarkColors = darkColorScheme(
    primary = SkyBlue,
    secondary = Peach,
    tertiary = Mint
)

/** Tema aplikasi; [mode] = "Terang" | "Gelap" | "Sistem" dari Pengaturan. */
@Composable
fun TodoPlannerTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "Gelap" -> true
        "Sistem" -> isSystemInDarkTheme()
        else -> false
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
