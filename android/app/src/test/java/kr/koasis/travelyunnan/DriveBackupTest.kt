package kr.koasis.travelyunnan

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.koasis.travelyunnan.data.DriveBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

/** 가짜 Apps Script(POST → 302 → GET 결과)로 드라이브 백업 요청 형식을 확인한다(소켓으로 만든 작은 HTTP 서버) */
class DriveBackupTest {
    private class Fake : AutoCloseable {
        val server = ServerSocket(0)
        val bodies = mutableListOf<String>()
        @Volatile var last = ""
        val port get() = server.localPort
        init {
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val s = runCatching { server.accept() }.getOrNull() ?: break
                    s.use { sock ->
                        val input = sock.getInputStream().buffered()
                        fun line(): String { val sb = StringBuilder(); while (true) { val c = input.read(); if (c < 0 || c == '\n'.code) break; if (c != '\r'.code) sb.append(c.toChar()) }; return sb.toString() }
                        val req = line(); var len = 0
                        while (true) { val h = line(); if (h.isEmpty()) break; if (h.lowercase().startsWith("content-length:")) len = h.substringAfter(':').trim().toInt() }
                        val body = ByteArray(len).also { var n = 0; while (n < len) { val r = input.read(it, n, len - n); if (r < 0) break; n += r } }.toString(Charsets.UTF_8)
                        val (method, path) = req.split(" ").let { it[0] to it[1] }
                        val out = sock.getOutputStream()
                        fun send(code: Int, status: String, text: String, extra: String = "") {
                            val b = text.toByteArray()
                            out.write("HTTP/1.1 $code $status\r\nContent-Type: application/json\r\nContent-Length: ${b.size}\r\nConnection: close\r\n$extra\r\n".toByteArray()); out.write(b); out.flush()
                        }
                        when {
                            method == "POST" -> {
                                bodies += body
                                val tok = Json.parseToJsonElement(body).jsonObject["token"]!!.jsonPrimitive.content
                                last = if (tok == "k") """{"ok":true,"id":"x"}""" else """{"ok":false,"error":"token"}"""
                                send(302, "Found", "", "Location: http://127.0.0.1:$port/echo\r\n")
                            }
                            path.startsWith("/echo") -> send(200, "OK", last)
                            else -> send(200, "OK", if (path.contains("token=k")) """{"ok":true,"folder":"윈난 여행 앱 백업"}""" else """{"ok":false,"error":"token"}""")
                        }
                    }
                }
            }
        }
        override fun close() = server.close()
    }

    @Test fun uploadAndPing() {
        Fake().use { f ->
            val url = "http://127.0.0.1:${f.port}/exec"
            assertEquals("윈난 여행 앱 백업", DriveBackup.ping(DriveBackup.Config(url, "k")))
            assertTrue(DriveBackup.upload(DriveBackup.Config(url, "k"), "문서함", "D3", "D3_바우처_a1.pdf", "application/pdf", byteArrayOf(1, 2, 3), "바우처"))
            val j = Json.parseToJsonElement(f.bodies.last()).jsonObject
            assertEquals("문서함", j["folder"]!!.jsonPrimitive.content); assertEquals("D3", j["sub"]!!.jsonPrimitive.content)
            assertEquals("AQID", j["data"]!!.jsonPrimitive.content)
            val err = runCatching { DriveBackup.upload(DriveBackup.Config(url, "bad"), "문서함", null, "x", "text/plain", null, text = "memo") }
            assertEquals("백업 키가 맞지 않는다", err.exceptionOrNull()?.message)
        }
        assertEquals("D3_옥룡설산 바우처_ab12.pdf", DriveBackup.fileName("D3", "옥룡설산   바우처", "ab12", "voucher.PDF"))
        assertEquals("공통_여권 사본_z.jpg", DriveBackup.fileName(null, "여권 사본", "z", "a.jpg"))
        assertFalse(DriveBackup.ready(DriveBackup.Config("http://example.com", "k")))
        assertTrue(DriveBackup.ready(DriveBackup.Config("https://script.google.com/macros/s/abc/exec", "k")))
    }
}
