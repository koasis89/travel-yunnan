package kr.koasis.travelyunnan

import kr.koasis.travelyunnan.data.db.TripDoc
import kr.koasis.travelyunnan.domain.DocRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun expenseCsvImport() {
        val csv = "\uFEFF일시,일차,금액,통화,원화환산,구분,결제수단,지불자,메모\n" +
            "2026-11-05 22:48,D3,350000,KRW,350000,교통·투어·입장료,위챗,공동,\"투어, 클룩\"\n" +
            "2026-12-17 12:05,D2,68,CNY,13600,식비,알리페이,아빠,\"점심 \"\"과교미선\"\"\"\n" +
            "깨진 줄,,,\n"
        val E = kr.koasis.travelyunnan.ui.expense.ExpenseCsv
        assertEquals(listOf("a", "b, c", "d \"e\""), E.split("a,\"b, c\",\"d \"\"e\"\"\""))
        val r = E.parse(csv, emptyList())
        assertEquals(2, r.added.size); assertEquals(1, r.bad)
        assertEquals("투어, 클룩", r.added[0].memo); assertEquals("KRW", r.added[0].currency); assertEquals(350000.0, r.added[0].amount, 0.0)
        assertEquals(java.time.Instant.parse("2026-12-17T04:05:00Z").toEpochMilli(), r.added[1].at)
        assertEquals("점심 \"과교미선\"", r.added[1].memo)
        val again = E.parse(csv, r.added)
        assertEquals(0, again.added.size); assertEquals(2, again.skipped)
        // 내보내기 → 가져오기 왕복
        assertEquals(2, E.parse(kr.koasis.travelyunnan.ui.expense.expensesCsv(r.added, 200.0), emptyList()).added.size)
    }

    @Test fun tabOrderSentence() {
        val M = kr.koasis.travelyunnan.ui.meal.DRINKS
        val d = { zh: String -> M.first { it.zh == zh } }
        assertEquals("我要两份过桥米线，这个也要一份，再要三瓶冰啤酒，不要辣。",
            kr.koasis.travelyunnan.ui.meal.composeTabOrder(listOf("过桥米线" to 2), 1, listOf(d("冰啤酒") to 3), listOf("不要辣")))
        assertEquals("这个要两份。", kr.koasis.travelyunnan.ui.meal.composeTabOrder(emptyList(), 2, emptyList(), emptyList()))
        assertEquals("我要一瓶矿泉水、一小瓶鹤庆乾酒。", kr.koasis.travelyunnan.ui.meal.composeTabOrder(emptyList(), 0, listOf(d("矿泉水") to 1, d("鹤庆乾酒") to 1), emptyList()))
        assertEquals(listOf("beer", "liquor", "drink"), listOf(d("冰啤酒"), d("鹤庆乾酒"), d("冰美式")).map { kr.koasis.travelyunnan.ui.meal.tabOf(it) })
        assertEquals(listOf("음식", "요청사항", "음료", "맥주", "술"), kr.koasis.travelyunnan.ui.meal.ORDER_TABS.map { it.second })
    }

    @Test fun pretripAndGuideBooking() {
        val d = TripDataHolder.data
        val p = d.pretrip.single()
        assertEquals("2026-12-15", p.date); assertEquals("옥룡설산 가이드 연락", p.title); assertEquals("tl-08", p.templateLinkId)
        assertTrue(p.date < d.days.first().date)
        // 예약자·예약 번호는 저장소 밖 private.json 에서 들어온다(시험 값으로 확인)
        val T = kr.koasis.travelyunnan.domain.Templates
        T.setGuideBooking("HONG GILDONG", "TEST123")
        val zh = T.guide.render(emptyMap())
        assertTrue(zh.contains("预订人：HONG GILDONG") && zh.contains("订单号（Booking reference）：TEST123"))
        assertEquals("TEST123", T.guide.contact?.bookingRef)
        assertTrue(T.guideConfirm.render(emptyMap()).contains("订单号TEST123"))
        T.setGuideBooking(null, null)
        assertTrue(!T.guide.render(emptyMap()).contains("订单号"))
    }

    @Test fun comeInnRoute() {
        val t = kr.koasis.travelyunnan.domain.Templates.all.first { it.id == "tpl-comeinn-route" }
        assertTrue(t.render(emptyMap()).contains("从中途客栈怎么走到你们客栈"))
        assertEquals("18987966608", t.contact?.wechat)
        val d = TripDataHolder.data
        assertTrue(d.templateLinks.any { it.id == "tl-13" && it.dayId == "D5" && it.templateId == "tpl-comeinn-route" })
        assertTrue(d.lodging("comeinn")!!.amapUrl!!.contains("keyword=%E8%A1%8C%E8%80%85%E6%B0%91%E5%AE%BF"))
    }
}
