package kr.koasis.travelyunnan

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kr.koasis.travelyunnan.data.db.AppDatabase
import kr.koasis.travelyunnan.data.db.TripDoc
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** 업그레이드 시 사용자 기록 보존: 1.2.5(DB v2)에서 만든 체크·경비·즐겨찾기가 최신(v4)에서도 남는지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private fun schema(v: Int): JSONObject {
        val dir = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "app/schemas/kr.koasis.travelyunnan.data.db.AppDatabase") }.first { it.exists() }
        return JSONObject(File(dir, "$v.json").readText()).getJSONObject("database")
    }

    @Test fun v2ToLatestKeepsUserData() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("travel_yunnan.db")
        val path = ctx.getDatabasePath("travel_yunnan.db").apply { parentFile?.mkdirs() }
        // 1.2.5 와 같은 v2 데이터베이스를 스키마 파일 그대로 만든다
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val s = schema(2)
            val ents = s.getJSONArray("entities")
            for (i in 0 until ents.length()) {
                val e = ents.getJSONObject(i)
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            val setup = s.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO flags(`key`, at) VALUES('done:D2-01', 1), ('pack:pk-01', 2), ('trek:map05:0', 3)")
            db.execSQL("INSERT INTO phrase_fav(phraseId, at) VALUES('px-001', 1)")
            db.execSQL("INSERT INTO expense(dayId, amount, currency, category, memo, method, payer, at) VALUES('D2', 128.0, 'CNY', '식비', '田村', '위챗', '공동', 10)")
            db.execSQL("INSERT INTO diary(dayId, scheduleId, uri, memo, at) VALUES('D2', 'D2-05', NULL, '고성 산책', 11)")
            db.version = 2
        }
        val room = AppDatabase.create(ctx)
        val dao = room.userDao()
        assertEquals(setOf("done:D2-01", "pack:pk-01", "trek:map05:0"), dao.flagKeys().first().toSet())
        assertEquals(listOf("px-001"), dao.favIds().first())
        val ex = dao.expenses().first().single()
        assertEquals(128.0, ex.amount, 0.0); assertEquals("田村", ex.memo); assertEquals(false, ex.ocr)
        assertEquals("고성 산책", dao.diary().first().single().memo)
        // 새 기능(문서함)도 이어서 쓸 수 있다
        dao.upsertDoc(TripDoc(id = "d1", title = "바우처", kind = "바우처", mime = "application/pdf", fileName = "v.pdf"))
        assertTrue(dao.docs().first().any { it.id == "d1" })
        room.close()
        // 다시 열어도(앱 재실행·다음 업데이트) 그대로
        val again = AppDatabase.create(ctx)
        assertEquals(1, again.userDao().docs().first().size)
        assertEquals(3, again.userDao().flagKeys().first().size)
        again.close()
    }
}
