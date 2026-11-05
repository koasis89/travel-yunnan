package kr.koasis.travelyunnan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kr.koasis.travelyunnan.data.TripData
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S5-3 완료 기준: 복사 전용(위챗 장문)을 뺀 모든 문장·단어·음식명에 내장 mp3 가 있다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioAssetTest {
    @Test fun everySpeakableItemHasBundledAudio() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val data = TripData.load(ctx)
        val files = ctx.assets.list("audio/phrases").orEmpty().toSet()
        assertTrue(files.none { it.endsWith(".sha") })
        val expected = data.phrases.filter { !it.copyOnly }.map { it.id } + data.words.map { it.id } + data.foodNames.map { it.id }
        val missing = expected.filter { "$it.mp3" !in files }
        assertTrue("음원 없음: $missing", missing.isEmpty())
        assertEquals(165, expected.size)
        val size = ctx.assets.openFd("audio/phrases/ph-001.mp3").use { it.length }
        assertTrue(size > 5000)
    }
}
