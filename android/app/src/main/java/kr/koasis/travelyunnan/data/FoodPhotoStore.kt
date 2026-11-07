package kr.koasis.travelyunnan.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 음식 사진: 사용자가 가진 사진 묶음(zip·사진 파일)을 이 기기 안(filesDir/foodphotos)에만 넣는다(웹 store/foodPhotos.ts 와 같은 방식).
 * 사진마다 목록용 <id>_t.jpg(360px)와 크게 볼 <id>.jpg(1280px)를 만든다. id 는 파일 이름에서 확장자를 뺀 것(예: photo_002).
 */
object FoodPhotoStore {
    private val IMG = Regex("\\.(webp|jpe?g|png|gif)$", RegexOption.IGNORE_CASE)
    fun dir(ctx: Context) = File(ctx.filesDir, "foodphotos").apply { mkdirs() }
    fun thumb(ctx: Context, id: String) = File(dir(ctx), "${id}_t.jpg")
    fun full(ctx: Context, id: String) = File(dir(ctx), "$id.jpg")
    fun ids(ctx: Context): List<String> =
        dir(ctx).listFiles { f -> f.name.endsWith("_t.jpg") }?.map { it.name.removeSuffix("_t.jpg") }?.sorted().orEmpty()
    fun clear(ctx: Context) { dir(ctx).deleteRecursively() }
    fun photoId(name: String) = name.substringAfterLast('/').replace(Regex("\\.[A-Za-z0-9]{2,5}$"), "")

    private fun displayName(ctx: Context, uri: Uri): String =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()

    /**
     * 워드(docx) 안 그림을 문서 순서대로: '1. 납배골 훠궈 …'처럼 번호로 시작하는 문단이 묶음, 그 아래 그림이 묶음 안 순서
     * → id docx-<묶음 2자리>-<순서 2자리> 와 docx 안 경로(웹 docxImages 와 같은 규칙)
     */
    fun docxImages(documentXml: String, relsXml: String): List<Pair<String, String>> {
        val rid = Regex("Id=\"(rId\\d+)\"[^>]*Target=\"([^\"]+)\"").findAll(relsXml).associate { it.groupValues[1] to it.groupValues[2] }
        val out = mutableListOf<Pair<String, String>>(); var g = 0; var k = 0
        for (p in Regex("<w:p[ >][\\s\\S]*?</w:p>").findAll(documentXml)) {
            val t = Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(p.value).joinToString("") { it.groupValues[1] }.trim()
            Regex("^(\\d+)\\.\\s").find(t)?.let { g = it.groupValues[1].toInt(); k = 0 }
            for (m in Regex("r:embed=\"(rId\\d+)\"").findAll(p.value)) {
                k++; val target = rid[m.groupValues[1]] ?: continue
                out += "docx-%02d-%02d".format(g, k) to "word/" + target.removePrefix("/").removePrefix("word/")
            }
        }
        return out
    }

    /** zip·워드(docx)·사진들을 넣는다. 같은 이름은 덮어쓴다. docxAllow: 워드에서 넣을 사진 id. 반환: 넣은 장 수 */
    fun import(ctx: Context, uris: List<Uri>, docxAllow: Set<String> = emptySet(), onProgress: (Int) -> Unit = {}): Int {
        var n = 0
        for (u in uris) {
            val name = displayName(ctx, u)
            val mime = ctx.contentResolver.getType(u).orEmpty()
            if (name.endsWith(".docx", true) || mime.contains("wordprocessingml")) {
                val tmp = File.createTempFile("food", ".docx", ctx.cacheDir)
                try {
                    ctx.contentResolver.openInputStream(u)?.use { ins -> tmp.outputStream().use { ins.copyTo(it) } }
                    java.util.zip.ZipFile(tmp).use { z ->
                        fun text(path: String) = z.getEntry(path)?.let { e -> z.getInputStream(e).use { it.readBytes().decodeToString() } }.orEmpty()
                        for ((id, media) in docxImages(text("word/document.xml"), text("word/_rels/document.xml.rels"))) {
                            if (docxAllow.isNotEmpty() && id !in docxAllow) continue
                            val e = z.getEntry(media) ?: continue
                            if (save(ctx, id, z.getInputStream(e).use { it.readBytes() })) { n++; onProgress(n) }
                        }
                    }
                } finally { tmp.delete() }
            } else if (name.endsWith(".zip", true) || mime.contains("zip")) {
                ctx.contentResolver.openInputStream(u)?.use { ins ->
                    ZipInputStream(ins.buffered()).use { z ->
                        while (true) {
                            val e = z.nextEntry ?: break
                            if (!e.isDirectory && IMG.containsMatchIn(e.name) && !e.name.contains("__MACOSX")) {
                                if (save(ctx, photoId(e.name), z.readBytes())) { n++; onProgress(n) }
                            }
                        }
                    }
                }
            } else if (mime.startsWith("image/") || IMG.containsMatchIn(name)) {
                val bytes = ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: continue
                if (save(ctx, photoId(name), bytes)) { n++; onProgress(n) }
            }
        }
        return n
    }

    private fun save(ctx: Context, id: String, bytes: ByteArray): Boolean = runCatching {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
        var s = 1; while (maxOf(b.outWidth, b.outHeight) / (s * 2) >= 1280) s *= 2
        val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return false
        write(bm, 1280, 82, full(ctx, id)); write(bm, 360, 72, thumb(ctx, id)); bm.recycle(); true
    }.getOrDefault(false)

    private fun write(src: Bitmap, max: Int, q: Int, out: File) {
        val sc = minOf(1f, max.toFloat() / maxOf(src.width, src.height))
        val bm = if (sc < 1f) Bitmap.createScaledBitmap(src, (src.width * sc).toInt().coerceAtLeast(1), (src.height * sc).toInt().coerceAtLeast(1), true) else src
        out.outputStream().use { bm.compress(Bitmap.CompressFormat.JPEG, q, it) }
        if (bm !== src) bm.recycle()
    }
}
