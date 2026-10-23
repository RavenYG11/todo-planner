package id.rama.todoplanner.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Membuat PDF beneran dengan PdfDocument native (tanpa library eksternal, tanpa internet).
 *
 * RONDE 3 — desain disamakan dengan mockup "Preview PDF" (uji3-mockup-pdf.jpg):
 *  - A4 portrait 595×842.
 *  - Header band GRADASI FAJAR dengan gambar VEKTOR matahari + awan + 2 lapis
 *    bukit (bukan kotak warna polos), judul aplikasi + nama user di atasnya.
 *  - Judul "Laporan {Periode} — {Bulan Tahun}" (diterima sebagai [periodTitle]
 *    dari layar Export, formatnya sudah "Laporan Bulanan — Oktober 2026" dst)
 *    + subjudul nama user + periode/filter yang dipakai.
 *  - Kartu Ringkasan Progress FAKTUAL dari data asli: total, selesai, %,
 *    dan kategori dengan penyelesaian terbanyak (bila ada) — TIDAK ada
 *    catatan naratif karangan; catatan hanya diturunkan dari data
 *    (kategori paling sering selesai / paling banyak belum selesai).
 *  - Tabel Kegiatan | Tanggal | Kategori | Status: kategori digambar sebagai
 *    PILL pastel rounded-rect + teks berwarna gelap kategorinya; status
 *    "✓ Selesai" / "Mendatang" (belum selesai). Judul panjang dibungkus
 *    (wrap) agar tidak terpotong; baris yang tidak muat pindah halaman.
 *  - Footer: kiri "Dicetak {tanggal, jam} • Todo Planner (full lokal & offline)",
 *    kanan "Halaman X dari Y" — paginasi dihitung dari tata letak nyata.
 */
object PdfExporter {
    private const val PAGE_W = 595 // A4 @72dpi
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN
    private val idLocale = Locale("id", "ID")
    private val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", idLocale)
    private val printFmt = DateTimeFormatter.ofPattern("d MMMM yyyy, HH.mm", idLocale)

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

    /** Warna teks gelap di atas pill pastel kategori (kontras terbaca, bahasa mockup). */
    private fun categoryTextColor(category: String): Int = when (category) {
        "Kerja" -> Color.rgb(146, 52, 43)
        "Keluarga" -> Color.rgb(27, 84, 117)
        "Ibadah" -> Color.rgb(74, 58, 140)
        "Olahraga" -> Color.rgb(35, 96, 60)
        "Kesehatan" -> Color.rgb(133, 44, 84)
        "Keuangan" -> Color.rgb(122, 86, 16)
        else -> Color.rgb(70, 80, 88)
    }

    private fun categoryPastel(category: String): Int = when (category) {
        "Kerja" -> Color.rgb(253, 233, 228)
        "Keluarga" -> Color.rgb(220, 238, 247)
        "Ibadah" -> Color.rgb(233, 228, 247)
        "Olahraga" -> Color.rgb(223, 245, 233)
        "Kesehatan" -> Color.rgb(251, 231, 241)
        "Keuangan" -> Color.rgb(252, 240, 216)
        else -> Color.rgb(237, 243, 246)
    }

    /** Tulis PDF ke folder Unduhan (MediaStore) — fallback ke folder aplikasi bila MediaStore gagal. */
    fun saveToDownloads(context: Context, userName: String, periodTitle: String, periodLabel: String, occurrences: List<Occurrence>): Result {
        val fileName = "TodoPlanner-" + periodTitle.replace(" ", "-").replace("/", "-").replace("—", "-") + ".pdf"
        val doc = buildDocument(userName, periodTitle, periodLabel, occurrences)
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
                val shareFile = writeCacheCopy(context, userName, periodTitle, periodLabel, occurrences, fileName)
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
    fun writeCacheCopy(context: Context, userName: String, periodTitle: String, periodLabel: String, occurrences: List<Occurrence>, fileName: String): String {
        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val file = File(dir, fileName)
        val doc = buildDocument(userName, periodTitle, periodLabel, occurrences)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return FileProvider.getUriForFile(context, "id.rama.todoplanner.fileprovider", file).toString()
    }

    /* ---------- Tata letak baris (dipakai untuk paginasi yang benar) ---------- */

    private val titlePaintMeasure = Paint().apply { textSize = 10.5f }
    private const val COL_TITLE_X = MARGIN
    private const val COL_TITLE_W = 238f
    private const val COL_DATE_X = 292f
    private const val COL_CAT_X = 382f
    private const val COL_STATUS_X = 478f

    /** Tinggi satu baris kegiatan: judul dibungkus maks 2 baris, baris min 20pt. */
    private fun rowHeight(occ: Occurrence): Float {
        val lines = wrapText(occ.activity.title, titlePaintMeasure, COL_TITLE_W - 16f, maxLines = 2)
        return maxOf(20f, lines.size * 13f + 7f)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        if (text.isBlank()) return listOf("-")
        val words = text.split(Regex("\\s+"))
        val lines = ArrayList<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                lines.add(current)
                current = word
                if (lines.size == maxLines - 1) break
            }
        }
        // Gabungkan sisa kata ke baris terakhir; elipsis bila masih kelebihan.
        val consumed = lines.joinToString(" ").split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        val rest = words.drop(consumed).joinToString(" ")
        var last = rest
        if (paint.measureText(last) > maxWidth) {
            while (last.isNotEmpty() && paint.measureText("$last…") > maxWidth) last = last.dropLast(1)
            last = last.trimEnd() + "…"
        }
        if (last.isNotEmpty()) lines.add(last)
        return lines.ifEmpty { listOf(text) }
    }

    fun buildDocument(userName: String, periodTitle: String, periodLabel: String, occurrences: List<Occurrence>): PdfDocument {
        val doc = PdfDocument()
        val doneCount = occurrences.count { it.done }
        val pct = if (occurrences.isEmpty()) 0 else doneCount * 100 / occurrences.size
        val printedAt = LocalDateTime.now().format(printFmt)

        // Catatan FAKTUAL turunan data (bukan karangan): kategori dengan
        // penyelesaian terbanyak & kategori dengan sisa terbanyak.
        val byCat = occurrences.groupBy { it.activity.category }
        val topDone = byCat.maxByOrNull { e -> e.value.count { it.done } }
            ?.takeIf { e -> e.value.count { it.done } > 0 }
        val topPending = byCat.maxByOrNull { e -> e.value.count { !it.done } }
            ?.takeIf { e -> e.value.count { !it.done } > 0 }

        // ----- Paginasi nyata: simulasikan tinggi baris per halaman -----
        val headerBlockH = 255f // header band + judul + subjudul + kartu ringkasan + kepala tabel (sesuai alur gambar halaman 1 di bawah)
        val footerTop = PAGE_H - 44f
        val pagesRows = ArrayList<List<Occurrence>>()
        var currentPage = ArrayList<Occurrence>()
        var yCursor = headerBlockH
        for (occ in occurrences) {
            val h = rowHeight(occ)
            if (yCursor + h > footerTop && currentPage.isNotEmpty()) {
                pagesRows.add(currentPage)
                currentPage = ArrayList()
                yCursor = 86f // halaman lanjutan: kepala tabel ulang tanpa blok judul besar
            }
            currentPage.add(occ)
            yCursor += h
        }
        if (currentPage.isNotEmpty() || pagesRows.isEmpty()) pagesRows.add(currentPage)
        val totalPages = pagesRows.size

        pagesRows.forEachIndexed { pageIndex, slice ->
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIndex + 1).create()
            val pdfPage = doc.startPage(info)
            val c = pdfPage.canvas
            c.drawColor(Color.WHITE)
            var y: Float
            if (pageIndex == 0) {
                drawHeaderBand(c, userName)
                y = 138f
                val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true; color = Color.rgb(34, 51, 61) }
                c.drawText(periodTitle, MARGIN, y, titlePaint); y += 17f
                val sub = Paint().apply { textSize = 10f; color = Color.rgb(90, 105, 115) }
                c.drawText("$userName • $periodLabel", MARGIN, y, sub); y += 14f

                // Kartu Ringkasan Progress (mockup: kartu pastel berjudul pin)
                val cardTop = y
                val cardPaint = Paint().apply { color = Color.rgb(240, 247, 250) }
                c.drawRoundRect(RectF(MARGIN, cardTop, PAGE_W - MARGIN, cardTop + 52f), 10f, 10f, cardPaint)
                val cardTitle = Paint().apply { textSize = 11.5f; isFakeBoldText = true; color = Color.rgb(34, 51, 61) }
                val cardText = Paint().apply { textSize = 10.5f; color = Color.rgb(52, 70, 79) }
                c.drawText("📌 Ringkasan Progress", MARGIN + 12f, cardTop + 18f, cardTitle)
                c.drawText(
                    "Total kegiatan: ${occurrences.size} • Selesai: $doneCount ($pct%) • Belum: ${occurrences.size - doneCount}",
                    MARGIN + 12f, cardTop + 36f, cardText
                )
                val fact = when {
                    topDone != null && topPending != null && topDone.key != topPending.key ->
                        "Kategori paling banyak selesai: ${topDone.key} (${topDone.value.count { it.done }}) • Paling banyak belum: ${topPending.key} (${topPending.value.count { !it.done }})"
                    topDone != null -> "Kategori paling banyak selesai: ${topDone.key} (${topDone.value.count { it.done }})"
                    topPending != null -> "Kategori paling banyak belum selesai: ${topPending.key} (${topPending.value.count { !it.done }})"
                    else -> null
                }
                if (fact != null) {
                    val factPaint = Paint().apply { textSize = 9.5f; color = Color.rgb(90, 105, 115) }
                    c.drawText(fact.take(96), MARGIN + 12f, cardTop + 49f, factPaint)
                }
                y = cardTop + 66f
            } else {
                // Halaman lanjutan: pita tipis gradasi agar tetap satu bahasa visual.
                val grad = LinearGradient(
                    0f, 0f, PAGE_W.toFloat(), 0f,
                    intArrayOf(Color.rgb(111, 179, 220), Color.rgb(246, 177, 122), Color.rgb(143, 208, 168)),
                    null, Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, PAGE_W.toFloat(), 26f, Paint().apply { shader = grad })
                val contPaint = Paint().apply { textSize = 10f; isFakeBoldText = true; color = Color.rgb(90, 105, 115) }
                c.drawText("$periodTitle (lanjutan)", MARGIN, 48f, contPaint)
                y = 66f
            }

            // Kepala tabel
            val headBg = Paint().apply { color = Color.rgb(238, 245, 248) }
            c.drawRoundRect(RectF(MARGIN, y - 12f, PAGE_W - MARGIN, y + 6f), 6f, 6f, headBg)
            val headPaint = Paint().apply { textSize = 9.5f; isFakeBoldText = true; color = Color.rgb(90, 105, 115) }
            c.drawText("Kegiatan", COL_TITLE_X + 6f, y, headPaint)
            c.drawText("Tanggal", COL_DATE_X, y, headPaint)
            c.drawText("Kategori", COL_CAT_X, y, headPaint)
            c.drawText("Status", COL_STATUS_X, y, headPaint)
            y += 20f

            if (slice.isEmpty()) {
                val normal = Paint().apply { textSize = 10.5f; color = Color.rgb(34, 51, 61) }
                c.drawText("Tidak ada kegiatan pada periode & kategori ini.", MARGIN + 6f, y + 6f, normal)
            }
            val normal = Paint().apply { textSize = 10.5f; color = Color.rgb(34, 51, 61) }
            val small = Paint().apply { textSize = 10f; color = Color.rgb(52, 70, 79) }
            slice.forEach { occ ->
                val h = rowHeight(occ)
                val baseline = y + 11f
                // Judul (dibungkus, tidak terpotong)
                val lines = wrapText(occ.activity.title, normal, COL_TITLE_W - 16f, maxLines = 2)
                lines.forEachIndexed { li, line ->
                    c.drawText(line, COL_TITLE_X + 6f, baseline + li * 13f, normal)
                }
                // Tanggal
                c.drawText(occ.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", idLocale)), COL_DATE_X, baseline, small)
                // Kategori sebagai PILL pastel rounded-rect + teks berwarna
                val cat = occ.activity.category
                val pillPaint = Paint().apply { color = categoryPastel(cat) }
                val catPaint = Paint().apply { textSize = 9.5f; isFakeBoldText = true; color = categoryTextColor(cat) }
                val pillW = catPaint.measureText(cat) + 18f
                c.drawRoundRect(RectF(COL_CAT_X, baseline - 11f, COL_CAT_X + pillW, baseline + 5f), 8f, 8f, pillPaint)
                c.drawText(cat, COL_CAT_X + 9f, baseline, catPaint)
                // Status
                val statusPaint = Paint().apply {
                    textSize = 10f; isFakeBoldText = true
                    color = if (occ.done) Color.rgb(35, 96, 60) else Color.rgb(90, 105, 115)
                }
                c.drawText(if (occ.done) "✓ Selesai" else "Mendatang", COL_STATUS_X, baseline, statusPaint)
                y += h
                // Garis pemisah lembut
                val linePaint = Paint().apply { color = Color.rgb(237, 243, 246) }
                c.drawRect(MARGIN, y - 3f, PAGE_W - MARGIN, y - 2.4f, linePaint)
            }
            drawFooter(c, printedAt, pageIndex + 1, totalPages)
            doc.finishPage(pdfPage)
        }
        return doc
    }

    /** Header band gradasi fajar + vektor matahari/awan/bukit persis bahasa mockup. */
    private fun drawHeaderBand(c: Canvas, userName: String) {
        val bandH = 104f
        val gradient = LinearGradient(
            0f, 0f, PAGE_W.toFloat(), bandH,
            intArrayOf(Color.rgb(111, 179, 220), Color.rgb(190, 226, 245), Color.rgb(246, 177, 122), Color.rgb(246, 200, 150)),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, PAGE_W.toFloat(), bandH, Paint().apply { shader = gradient })

        // Awan putih (elips bertumpuk, vektor)
        val cloudPaint = Paint().apply { color = Color.WHITE; alpha = 215 }
        fun cloud(cx: Float, cy: Float, rx: Float, ry: Float) {
            c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), cloudPaint)
        }
        cloud(120f, 30f, 30f, 9f); cloud(142f, 24f, 19f, 8f)
        cloud(330f, 20f, 23f, 7f); cloud(346f, 15f, 13f, 6f)

        // Matahari terbit + halo di kanan
        c.drawCircle(500f, 58f, 34f, Paint().apply { color = Color.rgb(246, 165, 107); alpha = 45 })
        val sunGrad = LinearGradient(
            500f, 32f, 500f, 84f,
            intArrayOf(Color.rgb(255, 217, 138), Color.rgb(246, 165, 107)),
            null, Shader.TileMode.CLAMP
        )
        c.drawCircle(500f, 58f, 25f, Paint().apply { shader = sunGrad })

        // Bukit 2 lapis di kaki band (path gelombang)
        val hillBack = Path().apply {
            moveTo(0f, 84f)
            quadTo(70f, 66f, 140f, 80f); quadTo(210f, 92f, 280f, 78f)
            quadTo(350f, 66f, 430f, 76f); quadTo(520f, 84f, PAGE_W.toFloat(), 72f)
            lineTo(PAGE_W.toFloat(), bandH); lineTo(0f, bandH); close()
        }
        c.drawPath(hillBack, Paint().apply { color = Color.rgb(191, 227, 210) })
        val hillFront = Path().apply {
            moveTo(0f, 94f)
            quadTo(90f, 82f, 190f, 92f); quadTo(300f, 100f, 420f, 90f)
            quadTo(510f, 84f, PAGE_W.toFloat(), 88f)
            lineTo(PAGE_W.toFloat(), bandH); lineTo(0f, bandH); close()
        }
        c.drawPath(hillFront, Paint().apply { color = Color.rgb(143, 208, 180); alpha = 175 })

        val appPaint = Paint().apply { textSize = 19f; color = Color.WHITE; isFakeBoldText = true }
        val namePaint = Paint().apply { textSize = 12f; color = Color.WHITE }
        // Bayangan teks tipis agar terbaca di atas gradasi terang
        appPaint.setShadowLayer(3f, 0f, 1f, Color.argb(70, 60, 60, 60))
        c.drawText("Todo Planner", MARGIN, 44f, appPaint)
        c.drawText("Laporan kegiatan — $userName", MARGIN, 64f, namePaint)
    }

    private fun drawFooter(c: Canvas, printedAt: String, page: Int, pages: Int) {
        val small = Paint().apply { textSize = 9f; color = Color.rgb(120, 130, 140) }
        c.drawText("Dicetak $printedAt • Todo Planner (full lokal & offline)", MARGIN, PAGE_H - 28f, small)
        val right = "Halaman $page dari $pages"
        c.drawText(right, PAGE_W - MARGIN - small.measureText(right), PAGE_H - 28f, small)
    }
}
