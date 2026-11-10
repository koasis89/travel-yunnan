package kr.koasis.travelyunnan.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kr.koasis.travelyunnan.data.db.TripDoc
import kr.koasis.travelyunnan.data.json.Day
import kr.koasis.travelyunnan.domain.DocRules
import java.io.File
import java.util.UUID

/**
 * 문서함 파일 보관: 앱 내부 저장소 files/docs/{id}/ 에 원본·PDF 쪽 그림·미리보기를 둔다.
 * 사진 선택기 권한이 사라져도 열리도록 원본을 복사해 둔다(백업 제외, 외부 전송 없음).
 */
object DocStore {
    private const val MAX_PAGES = 12

    fun dir(ctx: Context, id: String) = File(ctx.filesDir, "docs/$id")
    fun original(ctx: Context, d: TripDoc) = File(dir(ctx, d.id), d.fileName.substringAfterLast('/').ifBlank { "original" })
    fun thumb(ctx: Context, id: String) = File(dir(ctx, id), "thumb.jpg")
    /** 화면에 펼쳐 보여 줄 그림들: PDF 는 쪽 그림, 사진은 원본 */
    fun pages(ctx: Context, d: TripDoc): List<File> =
        if (d.pages > 0) (1..d.pages).map { File(dir(ctx, d.id), "page-$it.jpg") }.filter { it.exists() }
        else if (d.mime.startsWith("image/")) listOf(original(ctx, d)) else emptyList()

    fun import(ctx: Context, uri: Uri, days: List<Day>, defaultDay: String?): TripDoc {
        val cr = ctx.contentResolver
        var name = "document"; var size = 0L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) { name = c.getString(0) ?: name; size = c.getLong(1) }
        }
        val mime = cr.getType(uri) ?: if (name.endsWith(".pdf", true)) "application/pdf" else "application/octet-stream"
        return save(ctx, name, mime, size, days, defaultDay) { out -> cr.openInputStream(uri)!!.use { it.copyTo(out) } }
    }

    /** 드라이브에서 내려받은 바이트로 문서를 만든다(드라이브에서 가져오기) */
    fun importBytes(ctx: Context, name: String, mime: String, bytes: ByteArray, days: List<Day>, defaultDay: String?): TripDoc =
        save(ctx, name, mime.ifBlank { if (name.endsWith(".pdf", true)) "application/pdf" else "application/octet-stream" }, bytes.size.toLong(), days, defaultDay) { it.write(bytes) }

    private fun save(ctx: Context, name: String, mime: String, size0: Long, days: List<Day>, defaultDay: String?, write: (java.io.OutputStream) -> Unit): TripDoc {
        var size = size0
        val id = UUID.randomUUID().toString()
        val d = dir(ctx, id).apply { mkdirs() }
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val orig = File(d, safe)
        orig.outputStream().use(write)
        if (size <= 0) size = orig.length()
        var pages = 0
        if (mime == "application/pdf") {
            pages = runCatching { renderPdf(orig, d) }.getOrDefault(0)
            if (pages > 0) writeThumb(BitmapFactory.decodeFile(File(d, "page-1.jpg").path), thumb(ctx, id))
        } else if (mime.startsWith("image/")) {
            val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(orig.path, opt)
            val sample = maxOf(1, maxOf(opt.outWidth, opt.outHeight) / 480)
            BitmapFactory.decodeFile(orig.path, BitmapFactory.Options().apply { inSampleSize = sample })?.let { writeThumb(it, thumb(ctx, id)) }
        }
        return TripDoc(
            id = id, title = DocRules.baseTitle(name), kind = DocRules.guessKind(name),
            dayId = DocRules.guessDay(name, days) ?: defaultDay, note = "", mime = mime, fileName = safe,
            size = size, pages = pages, created = System.currentTimeMillis(),
        )
    }

    /** PDF 각 쪽을 폭 1600px JPEG 로 펼친다(기본 PdfRenderer, 인터넷 불필요) */
    private fun renderPdf(pdf: File, out: File): Int {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                val n = minOf(r.pageCount, MAX_PAGES)
                for (i in 0 until n) r.openPage(i).use { p ->
                    val scale = minOf(3f, 1600f / p.width)
                    val bmp = Bitmap.createBitmap((p.width * scale).toInt().coerceAtLeast(1), (p.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(out, "page-${i + 1}.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    bmp.recycle()
                }
                return n
            }
        }
    }

    private fun writeThumb(src: Bitmap?, file: File) {
        src ?: return
        val s = minOf(1f, 240f / maxOf(src.width, src.height))
        val t = Bitmap.createScaledBitmap(src, (src.width * s).toInt().coerceAtLeast(1), (src.height * s).toInt().coerceAtLeast(1), true)
        file.outputStream().use { t.compress(Bitmap.CompressFormat.JPEG, 70, it) }
    }

    fun delete(ctx: Context, id: String) { dir(ctx, id).deleteRecursively() }

    /** 다른 앱(위챗·PDF 뷰어)으로 보내거나 열 때 쓰는 content:// 주소 */
    fun shareUri(ctx: Context, d: TripDoc): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.docs", original(ctx, d))
}
