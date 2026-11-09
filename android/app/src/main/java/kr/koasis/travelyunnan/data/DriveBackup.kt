package kr.koasis.travelyunnan.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 구글 드라이브 백업(웹 store/drive.ts 와 같다): 사용자가 배포한 Apps Script 웹 앱(tools/drive_backup.gs)으로 파일을 보낸다.
 * 주소·키는 이 기기 설정(SharedPreferences) 또는 assets 의 private.json(driveUrl·driveToken) 값을 쓴다. 저장소(공개)에는 넣지 않는다.
 * 올린 것은 flags 'drive:doc:<id>'·'drive:diary:<id>' 로 표시한다(AppViewModel).
 */
object DriveBackup {
    data class Config(val url: String, val token: String)

    @Volatile private var fallback = Config("", "")
    fun setDefault(url: String?, token: String?) { fallback = Config(url.orEmpty().trim(), token.orEmpty().trim()) }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("drive-backup", Context.MODE_PRIVATE)
    fun config(ctx: Context): Config {
        val p = prefs(ctx)
        val u = p.getString("url", "").orEmpty(); val t = p.getString("token", "").orEmpty()
        return if (u.isNotBlank() && t.isNotBlank()) Config(u, t) else fallback
    }
    fun save(ctx: Context, c: Config?) {
        prefs(ctx).edit().apply { if (c == null) clear() else putString("url", c.url.trim()).putString("token", c.token.trim()) }.apply()
    }
    fun ready(c: Config) = Regex("^https://script\\.google(usercontent)?\\.com/").containsMatchIn(c.url) && c.token.isNotBlank()

    private fun read(conn: HttpURLConnection): JsonObject {
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        return runCatching { Json.parseToJsonElement(text).jsonObject }.getOrElse { throw IllegalStateException("응답을 읽지 못했다($code)") }
    }
    private fun fail(o: JsonObject): Nothing =
        throw IllegalStateException(if (o["error"]?.jsonPrimitive?.content == "token") "백업 키가 맞지 않는다" else o["error"]?.jsonPrimitive?.content ?: "실패")

    /** 연결 확인: 폴더 이름. 네트워크라 IO 스레드에서 부른다 */
    fun ping(c: Config): String {
        val conn = URL("${c.url}?token=${URLEncoder.encode(c.token, "UTF-8")}").openConnection() as HttpURLConnection
        conn.connectTimeout = 15000; conn.readTimeout = 30000; conn.instanceFollowRedirects = true
        val o = read(conn)
        if (o["ok"]?.jsonPrimitive?.booleanOrNull != true) fail(o)
        return o["folder"]?.jsonPrimitive?.content.orEmpty()
    }

    /** 파일 하나 올리기. 드라이브에 같은 이름이 있으면 건너뛴다. 네트워크라 IO 스레드에서 부른다 */
    fun upload(c: Config, folder: String, sub: String?, name: String, mime: String, bytes: ByteArray?, memo: String = "", text: String? = null): Boolean {
        val body = JsonObject(buildMap {
            put("token", JsonPrimitive(c.token)); put("folder", JsonPrimitive(folder)); put("sub", JsonPrimitive(sub.orEmpty()))
            put("name", JsonPrimitive(name)); put("mime", JsonPrimitive(mime)); put("memo", JsonPrimitive(memo))
            if (bytes != null) put("data", JsonPrimitive(java.util.Base64.getEncoder().encodeToString(bytes)))
            if (text != null) put("text", JsonPrimitive(text))
        }).toString()
        val conn = URL(c.url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"; conn.doOutput = true; conn.connectTimeout = 15000; conn.readTimeout = 120000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Content-Type", "text/plain;charset=utf-8")
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        // Apps Script 는 302 로 결과 주소를 돌려준다. POST 는 자동으로 따라가지 않을 수 있어 직접 GET 한다
        val o = if (conn.responseCode in 301..303) {
            val next = URL(conn.getHeaderField("Location")).openConnection() as HttpURLConnection
            next.connectTimeout = 15000; next.readTimeout = 60000; read(next)
        } else read(conn)
        if (o["ok"]?.jsonPrimitive?.booleanOrNull != true) fail(o)
        return o["skipped"]?.jsonPrimitive?.booleanOrNull != true
    }

    /** 드라이브에 올릴 파일 이름: D3_제목_id.확장자 */
    fun fileName(dayId: String?, title: String, id: String, original: String): String {
        val ext = Regex("\\.[A-Za-z0-9]{1,5}$").find(original)?.value?.lowercase().orEmpty()
        return "${dayId ?: "공통"}_${title.replace(Regex("\\s+"), " ").trim().take(60)}_$id$ext"
    }
}
