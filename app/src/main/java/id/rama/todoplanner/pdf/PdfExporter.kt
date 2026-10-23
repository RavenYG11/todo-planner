package id.rama.todoplanner.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import id.rama.todoplanner.domain.Occurrence
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Membuat PDF beneran dengan PdfDocument native (tanpa library eksternal, tanpa internet).
 * Desain mengikuti mockup Preview A4: header gradasi Langit Pagi, judul periode besar,
 * ringkasan progress, tabel kegiatan berwarna kategori lembut, footer tanggal cetak + nomor halaman.
 */
object PdfExporter {
    private const val PAGE_W = 595 // A4 @72dpi
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private val idLocale = Locale("id", "ID")
    private val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", idLocale)

    data class Result(val displayName: String, val location: String, val shareUri: String?)

    fun categoryColor(category: String): Int = when (category) {
        "Kerja" -> Color.rgb(242, 139, 130)
        "Keluarga" -> Color.rgb(111, 179, 220)
        "Ibadah" -> Color.rgb(155, 139, 212)
        "Olahraga" -> Color.rgb(88, 169, 124)
        "Kesehatan" -> Color.rgb(232, 139, 176)
        "Keuangan" -> Color.rgb(232, 184, 75)
        else -> Color.rgb(150, 160, 170)
    }

    /** Tulis PDF ke folder Unduhan (MediaStore) — fallback ke folder aplikasi bila MediaStore gagal. */
    fun saveToDownloads(context: Context, userName: String, periodTitle: String, occurrences: List<Occurrence>): Result {
        val fileName = "TodoPlanner-" + periodTitle.replace(" ", "-").replace("/", "-") + ".pdf"
        val doc = buildDocument(userName, periodTitle, occurrences)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TodoPlanner")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
                doc.close()
                val shareFile = writeCacheCopy(context, userName, periodTitle, occurrences, fileName)
                return Result(fileName, "Unduhan/TodoPlanner/$fileName", shareFile)
            }
        }
        // API 26-28 atau MediaStore gagal: folder eksternal aplikasi + tetap bisa dibagikan.
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "TodoPlanner").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        val shareUri = FileProvider.getUriForFile(context, "id.rama.todoplanner.fileprovider", file).toString()
        return Result(fileName, file.absolutePath, shareUri)
    }

    /** Salinan di cache untuk dibagikan lewat share intent. */
    fun writeCacheCopy(context: Context, userName: String, periodTitle: String, occurrences: List<Occurrence>, fileName: String): String {
        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val file = File(dir, fileName)
        val doc = buildDocument(userName, periodTitle, occurrences)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return FileProvider.getUriForFile(context, "id.rama.todoplanner.fileprovider", file).toString()
    }

    fun buildDocument(userName: String, periodTitle: String, occurrences: List<Occurrence>): PdfDocument {
        val doc = PdfDocument()
        val rowsPerPage = 22
        val pages = maxOf(1, (occurrences.size + rowsPerPage - 1) / rowsPerPage)
        val doneCount = occurrences.count { it.done }
        val pct = if (occurrences.isEmpty()) 0 else doneCount * 100 / occurrences.size
        val todayText = LocalDate.now().format(dateFmt)

        for (page in 0 until pages) {
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, page + 1).create()
            val pdfPage = doc.startPage(info)
            val c = pdfPage.canvas
            drawHeader(c, userName)
            var y = 150f
            val titlePaint = Paint().apply { textSize = 22f; isFakeBoldText = true; color = Color.rgb(34, 51, 61) }
            val normal = Paint().apply { textSize = 11f; color = Color.rgb(34, 51, 61) }
            val small = Paint().apply { textSize = 9.5f; color = Color.rgb(90, 105, 115) }
            c.drawText(periodTitle, MARGIN, y, titlePaint); y += 24f
            c.drawText("Ringkasan: ${occurrences.size} kegiatan • $doneCount selesai • progres $pct%", MARGIN, y, normal); y += 26f

            // Kepala tabel
            val head = Paint().apply { color = Color.rgb(225, 235, 239) }
            c.drawRect(MARGIN, y - 13f, PAGE_W - MARGIN, y + 5f, head)
            c.drawText("Kegiatan", MARGIN + 6, y, small)
            c.drawText("Tanggal", 300f, y, small)
            c.drawText("Kategori", 390f, y, small)
            c.drawText("Status", 480f, y, small)
            y += 22f

            val slice = occurrences.drop(page * rowsPerPage).take(rowsPerPage)
            slice.forEach { occ ->
                val dot = Paint().apply { color = categoryColor(occ.activity.category) }
                c.drawCircle(MARGIN + 6, y - 3.5f, 4f, dot)
                c.drawText(occ.activity.title.take(38), MARGIN + 16, y, normal)
                c.drawText(occ.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", idLocale)), 300f, y, normal)
                c.drawText(occ.activity.category, 390f, y, normal)
                c.drawText(if (occ.done) "Selesai" else "Belum", 480f, y, normal)
                y += 20f
            }
            if (occurrences.isEmpty()) {
                c.drawText("Tidak ada kegiatan pada periode & kategori ini.", MARGIN, y, normal)
            }
            drawFooter(c, todayText, page + 1, pages)
            doc.finishPage(pdfPage)
        }
        return doc
    }

    private fun drawHeader(c: Canvas, userName: String) {
        val gradient = LinearGradient(
            0f, 0f, PAGE_W.toFloat(), 110f,
            intArrayOf(Color.rgb(111, 179, 220), Color.rgb(246, 177, 122), Color.rgb(143, 208, 168)),
            null, Shader.TileMode.CLAMP
        )
        val bg = Paint().apply { shader = gradient }
        c.drawRect(0f, 0f, PAGE_W.toFloat(), 110f, bg)
        val sun = Paint().apply { color = Color.rgb(255, 236, 190) }
        c.drawCircle(505f, 42f, 20f, sun)
        val hill = Paint().apply { color = Color.rgb(88, 169, 124) }
        c.drawRect(0f, 96f, PAGE_W.toFloat(), 110f, hill)
        val namePaint = Paint().apply { textSize = 13f; color = Color.WHITE; isFakeBoldText = true }
        val appPaint = Paint().apply { textSize = 20f; color = Color.WHITE; isFakeBoldText = true }
        c.drawText("Todo Planner", MARGIN, 48f, appPaint)
        c.drawText("Laporan kegiatan — $userName", MARGIN, 72f, namePaint)
    }

    private fun drawFooter(c: Canvas, todayText: String, page: Int, pages: Int) {
        val small = Paint().apply { textSize = 9f; color = Color.rgb(120, 130, 140) }
        c.drawText("Dicetak: $todayText", MARGIN, PAGE_H - 28f, small)
        val right = "Halaman $page dari $pages"
        c.drawText(right, PAGE_W - MARGIN - small.measureText(right), PAGE_H - 28f, small)
    }
}
