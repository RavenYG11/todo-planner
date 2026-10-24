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
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import id.rama.todoplanner.domain.Dates
import id.rama.todoplanner.domain.Occurrence
import id.rama.todoplanner.domain.PdfBlock
import id.rama.todoplanner.domain.buildPdfBlocks
import id.rama.todoplanner.domain.onlyTodoUtama
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Membuat PDF beneran dengan PdfDocument native (tanpa library eksternal, tanpa internet).
 *
 * Desain dasar (ronde 3): A4 portrait, header band gradasi fajar + vektor
 * matahari/awan/bukit, kartu Ringkasan Progress faktual, pill kategori pastel,
 * footer tanggal cetak + "Halaman X dari Y" dari paginasi nyata.
 * Deskripsi kegiatan di bawah judul: 9pt abu-abu italic indent, wrap penuh (ronde 4).
 *
 * RONDE 5 — DESAIN PER PERIODE DIBEDAKAN (struktur dari domain/PdfStructure):
 *  - PDF hanya memuat TODO UTAMA — Daily Reminder dikeluarkan sama sekali.
 *  - Penomoran urut per kelompok 1 hari (mulai ulang tiap hari), semua periode.
 *  - HARIAN : sub-header hari + daftar bernomor; yang selesai berlabel
 *    "Diselesaikan HH:mm" dari timestamp selesai (tanpa timestamp: "Selesai").
 *  - MINGGUAN: sub-header HARI khusus (pita tanggal + ringkasan kecil per hari).
 *  - BULANAN: sub-header MINGGU lalu sub-header hari.
 *  - TAHUNAN: sub-header BERTINGKAT bulan (pita gradasi) -> minggu -> hari.
 * Preview di aplikasi memakai buildPdfBlocks yang sama (ExportScreens).
 */
object PdfExporter {
    private const val PAGE_W = 595 // A4 @72dpi
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN
    private val idLocale = Locale("id", "ID")
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

    fun saveToDownloads(context: Context, userName: String, periodTitle: String, periodLabel: String, occurrences: List<Occurrence>, period: String = "Harian"): Result {
        val fileName = "TodoPlanner-" + periodTitle.replace(" ", "-").replace("/", "-").replace("—", "-") + ".pdf"
        val doc = buildDocument(userName, periodTitle, periodLabel, occurrences, period)
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
                val shareFile = writeCacheCopy(context, userName, periodTitle, periodLabel, occurrences, fileName, period)
                return Result(fileName, "Unduhan/TodoPlanner/$fileName", shareFile)
            }
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "TodoPlanner").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        val shareUri = FileProvider.getUriForFile(context, "id.rama.todoplanner.fileprovider", file).toString()
        return Result(fileName, file.absolutePath, shareUri)
    }

    fun writeCacheCopy(context: Context, userName: String, periodTitle: String, periodLabel: String, occurrences: List<Occurrence>, fileName: String, period: String = "Harian"): String {
        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val file = File(dir, fileName)
        val doc = buildDocument(userName, periodTitle, periodLabel, occurrences, period)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return FileProvider.getUriForFile(context, "id.rama.todoplanner.fileprovider", file).toString()
    }

    /* ---------- Kolom (ronde 5: ada kolom nomor urut per hari) ---------- */
    private const val COL_NUM_X = MARGIN + 4f
    private const val COL_TITLE_X = 66f
    private const val COL_TITLE_W = 172f
    private const val COL_DATE_X = 250f
    private const val COL_CAT_X = 338f
    private const val COL_STATUS_X = 434f

    private const val NOTE_INDENT = 10f
    private const val NOTE_LINE_H = 11.5f

    private val titlePaintMeasure = Paint().apply { textSize = 10.5f }
    private val notePaintMeasure = Paint().apply {
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
    }

    private fun blockHeight(block: PdfBlock): Float = when (block.level) {
        0 -> 32f
        1 -> 26f
        2 -> 24f
        else -> rowHeight(block.occurrence!!)
    }

    private fun rowHeight(occ: Occurrence): Float {
        val lines = wrapText(occ.activity.title, titlePaintMeasure, COL_TITLE_W - 8f, maxLines = 2)
        var h = maxOf(20f, lines.size * 13f + 7f)
        val note = occ.activity.note
        if (note.isNotBlank()) {
            val noteLines = wrapFull(note, notePaintMeasure, COL_TITLE_W - 8f - NOTE_INDENT)
            h += 2f + noteLines.size * NOTE_LINE_H
        }
        return h
    }

    private fun wrapFull(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val lines = ArrayList<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                lines.add(current)
                current = word
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
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

    /** Label status baris — Harian memakai "Diselesaikan HH:mm" persis spek ronde 5. */
    private fun statusText(occ: Occurrence, period: String): String {
        if (!occ.done) return "Mendatang"
        val ts = occ.completedAtMillis
        return if (period == "Harian") {
            if (ts != null) "Diselesaikan ${Dates.formatCompletedTime(ts)}" else "Selesai"
        } else {
            if (ts != null) "✓ Selesai • ${Dates.formatCompletedTime(ts)}" else "✓ Selesai"
        }
    }

    fun buildDocument(userName: String, periodTitle: String, periodLabel: String, occurrencesRaw: List<Occurrence>, period: String = "Harian"): PdfDocument {
        // RONDE 5: Daily Reminder TIDAK masuk PDF sama sekali.
        val occurrences = occurrencesRaw.onlyTodoUtama()
        val blocks = buildPdfBlocks(period, occurrences)
        val doc = PdfDocument()
        val doneCount = occurrences.count { it.done }
        val pct = if (occurrences.isEmpty()) 0 else doneCount * 100 / occurrences.size
        val printedAt = LocalDateTime.now().format(printFmt)

        val byCat = occurrences.groupBy { it.activity.category }
        val topDone = byCat.maxByOrNull { e -> e.value.count { it.done } }
            ?.takeIf { e -> e.value.count { it.done } > 0 }
        val topPending = byCat.maxByOrNull { e -> e.value.count { !it.done } }
            ?.takeIf { e -> e.value.count { !it.done } > 0 }

        // ----- Paginasi nyata atas BLOK (header tidak yatim di kaki halaman) -----
        val headerBlockH = 255f
        val footerTop = PAGE_H - 44f
        val pagesBlocks = ArrayList<List<PdfBlock>>()
        var currentPage = ArrayList<PdfBlock>()
        var yCursor = headerBlockH
        blocks.forEachIndexed { i, block ->
            val h = blockHeight(block)
            var needed = h
            if (block.level < 3 && i + 1 < blocks.size) needed += blockHeight(blocks[i + 1])
            if (yCursor + needed > footerTop && currentPage.isNotEmpty()) {
                pagesBlocks.add(currentPage)
                currentPage = ArrayList()
                yCursor = 86f
            }
            currentPage.add(block)
            yCursor += h
        }
        if (currentPage.isNotEmpty() || pagesBlocks.isEmpty()) pagesBlocks.add(currentPage)
        val totalPages = pagesBlocks.size

        pagesBlocks.forEachIndexed { pageIndex, slice ->
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

            // Kepala kolom (dengan kolom No. penomoran per hari)
            val headBg = Paint().apply { color = Color.rgb(238, 245, 248) }
            c.drawRoundRect(RectF(MARGIN, y - 12f, PAGE_W - MARGIN, y + 6f), 6f, 6f, headBg)
            val headPaint = Paint().apply { textSize = 9.5f; isFakeBoldText = true; color = Color.rgb(90, 105, 115) }
            c.drawText("No.", COL_NUM_X, y, headPaint)
            c.drawText("Kegiatan", COL_TITLE_X, y, headPaint)
            c.drawText("Tanggal", COL_DATE_X, y, headPaint)
            c.drawText("Kategori", COL_CAT_X, y, headPaint)
            c.drawText("Status", COL_STATUS_X, y, headPaint)
            y += 20f

            if (slice.isEmpty()) {
                val normal = Paint().apply { textSize = 10.5f; color = Color.rgb(34, 51, 61) }
                c.drawText("Tidak ada kegiatan pada periode & kategori ini.", MARGIN + 6f, y + 6f, normal)
            }
            slice.forEach { block ->
                y = drawBlock(c, block, y, period)
            }
            drawFooter(c, printedAt, pageIndex + 1, totalPages)
            doc.finishPage(pdfPage)
        }
        return doc
    }

    /** Gambar satu blok (header bertingkat / baris bernomor); mengembalikan y berikutnya. */
    private fun drawBlock(c: Canvas, block: PdfBlock, yTop: Float, period: String): Float {
        when (block.level) {
            0 -> { // BULAN — pita gradasi penuh (tingkat teratas, paling khas)
                val grad = LinearGradient(
                    MARGIN, 0f, PAGE_W - MARGIN, 0f,
                    intArrayOf(Color.rgb(155, 139, 212), Color.rgb(111, 179, 220)),
                    null, Shader.TileMode.CLAMP
                )
                c.drawRoundRect(RectF(MARGIN, yTop, PAGE_W - MARGIN, yTop + 26f), 8f, 8f, Paint().apply { shader = grad })
                val p = Paint().apply { textSize = 12f; isFakeBoldText = true; color = Color.WHITE }
                c.drawText("📅 ${block.label}", MARGIN + 10f, yTop + 18f, p)
                block.sublabel?.let {
                    val s = Paint().apply { textSize = 9f; color = Color.WHITE }
                    c.drawText(it, PAGE_W - MARGIN - 10f - s.measureText(it), yTop + 18f, s)
                }
                return yTop + 32f
            }
            1 -> { // MINGGU — pita pastel + aksen kiri coral
                c.drawRoundRect(RectF(MARGIN, yTop, PAGE_W - MARGIN, yTop + 20f), 6f, 6f,
                    Paint().apply { color = Color.rgb(252, 240, 216) })
                c.drawRect(RectF(MARGIN, yTop, MARGIN + 4f, yTop + 20f),
                    Paint().apply { color = Color.rgb(242, 166, 90) })
                val p = Paint().apply { textSize = 10.5f; isFakeBoldText = true; color = Color.rgb(122, 86, 16) }
                c.drawText("🗓️ ${block.label}", MARGIN + 10f, yTop + 14.5f, p)
                block.sublabel?.let {
                    val s = Paint().apply { textSize = 9f; color = Color.rgb(122, 86, 16) }
                    c.drawText(it, PAGE_W - MARGIN - 10f - s.measureText(it), yTop + 14.5f, s)
                }
                return yTop + 26f
            }
            2 -> { // HARI — pita tanggal tipis biru langit + ringkasan kecil
                c.drawRoundRect(RectF(MARGIN, yTop, PAGE_W - MARGIN, yTop + 18f), 5f, 5f,
                    Paint().apply { color = Color.rgb(225, 242, 251) })
                val p = Paint().apply { textSize = 10.5f; isFakeBoldText = true; color = Color.rgb(27, 84, 117) }
                c.drawText(block.label, MARGIN + 8f, yTop + 13f, p)
                block.sublabel?.let {
                    val s = Paint().apply { textSize = 9f; color = Color.rgb(90, 105, 115) }
                    c.drawText(it, PAGE_W - MARGIN - 8f - s.measureText(it), yTop + 13f, s)
                }
                return yTop + 24f
            }
            else -> {
                val occ = block.occurrence ?: return yTop
                val h = rowHeight(occ)
                val baseline = yTop + 11f
                val normal = Paint().apply { textSize = 10.5f; color = Color.rgb(34, 51, 61) }
                val small = Paint().apply { textSize = 10f; color = Color.rgb(52, 70, 79) }
                val notePaint = Paint().apply {
                    textSize = 9f; color = Color.rgb(108, 120, 128)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                }
                // Nomor urut per hari
                val numPaint = Paint().apply { textSize = 10.5f; isFakeBoldText = true; color = Color.rgb(43, 110, 145) }
                c.drawText("${block.number}.", COL_NUM_X, baseline, numPaint)
                val lines = wrapText(occ.activity.title, normal, COL_TITLE_W - 8f, maxLines = 2)
                lines.forEachIndexed { li, line ->
                    c.drawText(line, COL_TITLE_X, baseline + li * 13f, normal)
                }
                val note = occ.activity.note
                if (note.isNotBlank()) {
                    val noteLines = wrapFull(note, notePaint, COL_TITLE_W - 8f - NOTE_INDENT)
                    noteLines.forEachIndexed { ni, line ->
                        c.drawText(line, COL_TITLE_X + NOTE_INDENT, baseline + lines.size * 13f + 2f + ni * NOTE_LINE_H, notePaint)
                    }
                }
                c.drawText(occ.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", idLocale)), COL_DATE_X, baseline, small)
                val cat = occ.activity.category
                val pillPaint = Paint().apply { color = categoryPastel(cat) }
                val catPaint = Paint().apply { textSize = 9.5f; isFakeBoldText = true; color = categoryTextColor(cat) }
                val pillW = catPaint.measureText(cat) + 18f
                c.drawRoundRect(RectF(COL_CAT_X, baseline - 11f, COL_CAT_X + pillW, baseline + 5f), 8f, 8f, pillPaint)
                c.drawText(cat, COL_CAT_X + 9f, baseline, catPaint)
                val statusPaint = Paint().apply {
                    textSize = 9.5f; isFakeBoldText = true
                    color = if (occ.done) Color.rgb(35, 96, 60) else Color.rgb(90, 105, 115)
                }
                c.drawText(statusText(occ, period), COL_STATUS_X, baseline, statusPaint)
                val y = yTop + h
                val linePaint = Paint().apply { color = Color.rgb(237, 243, 246) }
                c.drawRect(MARGIN, y - 3f, PAGE_W - MARGIN, y - 2.4f, linePaint)
                return y
            }
        }
    }

    private fun drawHeaderBand(c: Canvas, userName: String) {
        val bandH = 104f
        val gradient = LinearGradient(
            0f, 0f, PAGE_W.toFloat(), bandH,
            intArrayOf(Color.rgb(111, 179, 220), Color.rgb(190, 226, 245), Color.rgb(246, 177, 122), Color.rgb(246, 200, 150)),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, PAGE_W.toFloat(), bandH, Paint().apply { shader = gradient })
        val cloudPaint = Paint().apply { color = Color.WHITE; alpha = 215 }
        fun cloud(cx: Float, cy: Float, rx: Float, ry: Float) {
            c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), cloudPaint)
        }
        cloud(120f, 30f, 30f, 9f); cloud(142f, 24f, 19f, 8f)
        cloud(330f, 20f, 23f, 7f); cloud(346f, 15f, 13f, 6f)
        c.drawCircle(500f, 58f, 34f, Paint().apply { color = Color.rgb(246, 165, 107); alpha = 45 })
        val sunGrad = LinearGradient(
            500f, 32f, 500f, 84f,
            intArrayOf(Color.rgb(255, 217, 138), Color.rgb(246, 165, 107)),
            null, Shader.TileMode.CLAMP
        )
        c.drawCircle(500f, 58f, 25f, Paint().apply { shader = sunGrad })
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
