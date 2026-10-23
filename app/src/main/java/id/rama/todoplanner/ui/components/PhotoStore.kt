package id.rama.todoplanner.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Hasil crop ter-normalisasi terhadap dimensi bitmap sumber (0f..1f):
 * [left]/[top] = sudut kiri-atas jendela crop, [sideX]/[sideY] = sisi persegi
 * terhadap lebar/tinggi bitmap.
 */
data class CropRectFrac(val left: Float, val top: Float, val sideX: Float, val sideY: Float)

/**
 * Pengolahan foto profil — framework Android murni (TANPA dependensi baru; build offline).
 *
 * PERBAIKAN RONDE 1 (foto miring): versi lama menyalin byte JPEG mentah dari galeri,
 * dan penampil bitmap mentah mengabaikan tag orientasi EXIF → foto tampil berputar.
 * Sekarang orientasi EXIF di-"bake" ke piksel bitmap sebelum disimpan.
 */
object PhotoStore {
    private const val MAX_SIZE = 1024

    /** Muat bitmap dari [uri] dengan orientasi EXIF sudah diterapkan ke piksel. */
    fun loadOrientedBitmap(context: Context, uri: Uri): Bitmap? {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) { null } ?: return null
        return decodeOriented(bytes)
    }

    /** Muat bitmap dari file internal dengan orientasi EXIF diterapkan (untuk file lama). */
    fun loadOrientedBitmap(file: File): Bitmap? {
        if (!file.exists()) return null
        val bytes = try { file.readBytes() } catch (e: Exception) { null } ?: return null
        return decodeOriented(bytes)
    }

    private fun decodeOriented(bytes: ByteArray): Bitmap? {
        val orientation = try {
            ByteArrayInputStream(bytes).use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }
        } catch (e: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return applyOrientation(raw, orientation)
    }

    private fun applyOrientation(bmp: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bmp
        }
        return try {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        } catch (e: Exception) { bmp }
    }

    /** Impor foto galeri: baking EXIF + crop persegi tengah + perkecil, simpan ke [target]. */
    fun importPhoto(context: Context, uri: Uri, target: File): Boolean {
        val bmp = loadOrientedBitmap(context, uri) ?: return false
        val side = minOf(bmp.width, bmp.height)
        val cropped = Bitmap.createBitmap(
            bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side
        )
        return saveJpeg(scaleDown(cropped, MAX_SIZE), target)
    }

    /** Simpan hasil crop manual user ([rect] ter-normalisasi dari layar crop) ke [target]. */
    fun saveCrop(context: Context, uri: Uri, rect: CropRectFrac, target: File): Boolean {
        val bmp = loadOrientedBitmap(context, uri) ?: return false
        return saveCropBitmap(bmp, rect, target)
    }

    /** Crop bitmap yang sudah termuat (dipakai layar crop agar konsisten pikselnya). */
    fun saveCropBitmap(bmp: Bitmap, rect: CropRectFrac, target: File): Boolean {
        val sidePx = (rect.sideX * bmp.width).roundToInt()
            .coerceIn(1, minOf(bmp.width, bmp.height))
        val x = (rect.left * bmp.width).roundToInt().coerceIn(0, bmp.width - sidePx)
        val y = (rect.top * bmp.height).roundToInt().coerceIn(0, bmp.height - sidePx)
        val cropped = try {
            Bitmap.createBitmap(bmp, x, y, sidePx, sidePx)
        } catch (e: Exception) { return false }
        return saveJpeg(scaleDown(cropped, MAX_SIZE), target)
    }

    /** Putar foto tersimpan 90° searah jarum jam, simpan ulang di file yang sama. */
    fun rotateClockwise(file: File): Boolean {
        val bmp = loadOrientedBitmap(file) ?: return false
        val m = Matrix().apply { postRotate(90f) }
        val rotated = try {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        } catch (e: Exception) { return false }
        return saveJpeg(rotated, file)
    }

    private fun scaleDown(bmp: Bitmap, maxSize: Int): Bitmap {
        val longest = max(bmp.width, bmp.height)
        if (longest <= maxSize) return bmp
        val ratio = maxSize.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * ratio).roundToInt().coerceAtLeast(1),
            (bmp.height * ratio).roundToInt().coerceAtLeast(1),
            true
        )
    }

    private fun saveJpeg(bmp: Bitmap, target: File): Boolean = try {
        FileOutputStream(target).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        true
    } catch (e: Exception) { false }
}
