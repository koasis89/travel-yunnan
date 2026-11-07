package kr.koasis.travelyunnan

import kr.koasis.travelyunnan.data.db.TripDoc
import kr.koasis.travelyunnan.domain.DocRules
import org.junit.Assert.assertEquals
import org.junit.Test

/** 문서함 분류·일차 추정과 정렬(웹 tests/docs.test.ts 와 같은 사례) */
class DocRulesTest {
    private val days = TripDataHolder.data.days
    private fun doc(id: String, dayId: String? = null, kind: String = "기타", title: String = "t", note: String = "", created: Long = 0) =
        TripDoc(id = id, title = title, kind = kind, dayId = dayId, note = note, mime = "image/png", fileName = "a.png", created = created)

    @Test fun guessKind() {
        assertEquals("기차표", DocRules.guessKind("C446_trip.com.pdf"))
        assertEquals("바우처", DocRules.guessKind("Klook_voucher_yulong.pdf"))
        assertEquals("바우처", DocRules.guessKind("옥룡설산 투어 바우처.png"))
        assertEquals("숙소 예약", DocRules.guessKind("agoda_booking_confirmation.pdf"))
        assertEquals("영수증", DocRules.guessKind("微信支付 小票.jpg"))
        assertEquals("보험·신분", DocRules.guessKind("여권 사본.jpg"))
        assertEquals("기타", DocRules.guessKind("Screenshot_20261105.png"))
        assertEquals("블로그", DocRules.guessKind("리장 옥룡설산 케이블카 후기 _ 네이버 블로그.pdf"))
    }

    @Test fun blogsAndDocForBlog() {
        val d = TripDataHolder.data
        assertEquals(5, d.blogs.size)
        assertEquals(1, d.blogsFor("yulong").size)
        assertEquals(2, d.blogsFor("lijiang_oldtown").size)
        val b = d.blogsFor("yulong").first()
        assertEquals("a", DocRules.docForBlog(b, listOf(doc("a", note = "x\n${b.url}")))?.id)
        assertEquals("c", DocRules.docForBlog(b, listOf(TripDoc(id = "c", title = "t", kind = "블로그", mime = "application/pdf", fileName = "리장 옥룡설산 케이블카 후기.pdf")))?.id)
        assertEquals(null, DocRules.docForBlog(b, listOf(doc("e"))))
    }

    @Test fun guessDay() {
        assertEquals("D2", DocRules.guessDay("C446.pdf", days))
        assertEquals("D7", DocRules.guessDay("ticket_C644", days))
        assertEquals("D3", DocRules.guessDay("tour 12-18.png", days))
        assertEquals("D5", DocRules.guessDay("12月20日 车票", days))
        assertEquals("D4", DocRules.guessDay("voucher_1219.pdf", days))
        assertEquals(null, DocRules.guessDay("Screenshot_20261105_1230.png", days))
        assertEquals(null, DocRules.guessDay("photo.jpg", days))
        assertEquals("Klook voucher", DocRules.baseTitle("Klook_voucher.pdf"))
    }

    @Test fun sortAndFilter() {
        val l = listOf(doc("x"), doc("d5", "D5"), doc("d2", "D2"), doc("d3", "D3"))
        assertEquals(listOf("d3", "d2", "d5", "x"), DocRules.sort(l, "D3").map { it.id })
        assertEquals(listOf("d2", "d3", "d5", "x"), DocRules.sort(l, null).map { it.id })
        val m = listOf(doc("a", "D3", "바우처", "옥룡설산"), doc("b", "D2", "기차표", note = "C446 7호차"))
        assertEquals(listOf("a"), DocRules.filter(m, "바우처", null).map { it.id })
        assertEquals(listOf("b"), DocRules.filter(m, null, "D2").map { it.id })
        assertEquals(listOf("b"), DocRules.filter(m, null, null, "7호차").map { it.id })
    }

    @Test fun foodPhotoNames() {
        val d = TripDataHolder.data
        assertEquals(93, d.foodPhotos.items.size)
        assertEquals("铜锅饭", d.foodPhotoName("photo_005")?.nameZh)
        assertEquals(null, d.foodPhotoName("photo_013"))
        assertEquals("photo_005", kr.koasis.travelyunnan.data.FoodPhotoStore.photoId("리장음식사진/photo_005.webp"))
    }

    @Test fun docxFoodPhotos() {
        val f = java.io.File("../../web/tests/fixtures/food_photos_sample.docx").takeIf { it.exists() } ?: java.io.File("../web/tests/fixtures/food_photos_sample.docx")
        java.util.zip.ZipFile(f).use { z ->
            fun t(p: String) = z.getInputStream(z.getEntry(p)).use { it.readBytes().decodeToString() }
            assertEquals(listOf("docx-01-01" to "word/media/image1.png", "docx-01-02" to "word/media/image2.png", "docx-02-01" to "word/media/image3.png"),
                kr.koasis.travelyunnan.data.FoodPhotoStore.docxImages(t("word/document.xml"), t("word/_rels/document.xml.rels")))
        }
        val d = TripDataHolder.data
        assertEquals(listOf("docx-01-01"), d.photoIdsForFood("food-05", listOf("photo_005", "docx-01-01", "docx-02-01")))
        assertEquals(listOf("docx-02-01"), d.photoIdsForFood("food-23", listOf("photo_005", "docx-01-01", "docx-02-01")))
        assertEquals("腊排骨火锅", d.foodPhotoName("docx-01-01")?.nameZh)
    }
}
