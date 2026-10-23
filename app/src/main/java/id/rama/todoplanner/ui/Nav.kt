package id.rama.todoplanner.ui

/**
 * Daftar layar aplikasi (14 layar sesuai mockup final) + PETA INDUK TETAP untuk tombol Back HP.
 * Back BUKAN undo riwayat sesi: setiap layar punya induk tetap (keputusan Rama, mockup #81).
 */
sealed interface Screen {
    data object Home : Screen
    data object Daily : Screen
    data object Weekly : Screen
    data object Monthly : Screen
    data object Yearly : Screen
    data object Calendar : Screen
    data object Menu : Screen
    data object Reminder : Screen
    data object Stats : Screen
    data object Settings : Screen
    data object Export : Screen
    data class PdfPreview(val period: String, val anchorEpochDay: Long, val categories: Set<String>) : Screen
    data class Detail(val activityId: Long, val dateEpochDay: Long) : Screen
    data class Form(
        val editId: Long? = null,
        val presetDateEpochDay: Long? = null,
        val presetStartMinutes: Int? = null,
        val presetTemplate: String? = null
    ) : Screen
    data object Profile : Screen
}

/** Induk tetap tiap layar. Home tidak punya induk (Back di Home = tekan 2x keluar). */
fun parentOf(screen: Screen): Screen = when (screen) {
    is Screen.PdfPreview -> Screen.Export
    is Screen.Profile -> Screen.Settings
    is Screen.Detail -> Screen.Daily
    is Screen.Form -> Screen.Home
    else -> Screen.Home
}
