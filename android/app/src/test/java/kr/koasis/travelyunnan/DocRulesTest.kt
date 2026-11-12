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
        assertEquals(5, d.pretrip.size); assertTrue(d.pretrip.all { it.date < d.days.first().date })
        val p = d.pretrip.single { it.id == "p-01" }
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
        assertTrue(t.render(emptyMap()).let { it.contains("可以提前入住吗") && it.contains("从茶马客栈接我们回客栈") })
        assertEquals("18987966608", t.contact?.wechat)
        val d = TripDataHolder.data
        assertTrue(d.templateLinks.any { it.id == "tl-14" && it.dayId == "D5" && it.timeStart == "10:30" && it.templateId == "tpl-comeinn-route" })
        assertEquals("https://surl.amap.com/4zvUETfO416", d.lodging("comeinn")!!.amapUrl)
    }

    @Test fun milkTea() {
        val m = TripDataHolder.data.milktea
        assertEquals(listOf("霸王茶姬", "喜茶", "茶百道", "瑞幸咖啡", "蜜雪冰城", "奈雪的茶", "爷爷不泡茶", "KOI 可宜"), m.items.map { it.nameZh })
        assertTrue(m.items.all { b -> b.drinks.isNotEmpty() && b.drinks.all { it.zh.isNotBlank() && it.koRead.isNotBlank() && it.desc.isNotBlank() } })
        assertEquals("你好，我要一杯伯牙绝弦，少糖，热的。", kr.koasis.travelyunnan.ui.meal.milkTeaOrder("伯牙绝弦", "少糖", "热的"))
        assertEquals("你好，我要一杯生椰拿铁。", kr.koasis.travelyunnan.ui.meal.milkTeaOrder("生椰拿铁", "", ""))
        assertEquals("你好，我要一杯小黄油拿铁，全冰去水，深烘，不另外加糖，黄油减半。", kr.koasis.travelyunnan.ui.meal.milkTeaOrder("小黄油拿铁", "少糖", "热的", "全冰去水，深烘，不另外加糖，黄油减半"))
        assertEquals("加马蹄丸子", m.items.first { it.id == "yeye" }.drinks.first { it.zh == "栀子龙眼椰" }.opt)
        assertTrue(m.items.all { b -> b.maps.size == 7 && b.maps.all { it.url.startsWith("https://uri.amap.com/search?keyword=") } })
        assertEquals("내 주변", m.items[0].maps[0].ko)
    }

    @Test fun koGloss() {
        val d = TripDataHolder.data
        val ko = kr.koasis.travelyunnan.data.KoGlossDict::ko
        val zhOnly = { x: String -> x.any { it in '\u4e00'..'\u9fff' } && x.none { it in '가'..'힣' } }
        val need = (d.lodgings.flatMap { listOf(it.addrZh, it.nameZh) } + d.emergency.hospitals.map { it.addrZh } +
            d.schedule.flatMap { listOf(it.from, it.via, it.to) }).filter(zhOnly)
        assertTrue(need.size >= 10)
        assertEquals(emptyList<String>(), need.filter { ko(it).isEmpty() })
        assertEquals("리장역", ko("丽江站"))
        assertEquals("", ko(""))
        assertEquals("12월 20일 체크인 3명, 얼리체크인 희망", kr.koasis.travelyunnan.domain.Templates.COMEINN.friendNoteKo)
    }

    @Test fun templateNumericDates() {
        val dt = kr.koasis.travelyunnan.domain.DateText
        assertEquals("12月20日（周日）上午11点半", dt.zh("12/20 11:30", "datetime"))
        assertEquals("12월 20일(일) 오전 11시 반", dt.ko("12/20 11:30", "datetime"))
        assertEquals("12月17日（周四）中午12点27分", dt.zh("12/17 12:27", "datetime"))
        assertEquals("下午2点左右", dt.zh("14:00", "time", true))
        assertEquals("下午4点零5分", dt.zh("16:05", "time"))
        assertEquals("12月18日（周五）", dt.zh("12/18", "date"))
        assertEquals("明天上午10点", dt.zh("明天上午10点", "datetime"))
        assertEquals("21", dt.parse("/21 10:30").d)
        assertEquals(false, dt.isValid("/21 10:30", "datetime"))
        val T = kr.koasis.travelyunnan.domain.Templates
        assertTrue(T.shangrila.render(mapOf("date" to "12/21 10:30")).contains("我想预订12月21日（周一）上午10点半，从"))
        assertTrue(T.shangrila.renderKo(mapOf("date" to "12/21 10:30")).contains("12월 21일(월) 오전 10시 반"))
        assertTrue(T.comeinnRoute.render(emptyMap()).contains("大概下午2点左右到客栈"))
        val n = T.all.first { it.id == "tpl-delivery-note" }
        assertEquals(listOf("我们没有国内手机号，请别打电话，直接让机器人送上来就行。", "我们没有国内手机号，请别打电话，直接放在前台就行。"), n.lines.map { it.zh })
        // 웹과 같은 값(일정 연결 값은 숫자)
        assertEquals("12/21 10:30", TripDataHolder.data.templateLinks.first { it.id == "tl-06" }.values["date"])
    }

    @Test fun checklists() {
        val d = TripDataHolder.data
        assertTrue(d.days.all { d.nextDayOf(it.id) != null })
        assertTrue(d.nextDayOf("D2")!!.items.any { "산소캔" in it.text })
        assertTrue(d.nextDayOf("D1")!!.items.any { "기차 반입 금지" in it.text })
        val ids = d.checklists.nextDay.flatMap { it.items } + d.checklists.departure.items + d.pretrip.flatMap { it.checklist }
        assertEquals(ids.size, ids.map { it.id }.toSet().size)
        assertEquals("/arrival-card", d.pretrip.first { it.id == "p-04" }.link)
        val f = d.checklists.arrivalCard.fields
        assertEquals(listOf("입국 정보", "머무를 호텔", "출국 정보"), f.map { it.group }.distinct())
        assertTrue(f.first { it.label == "상세 주소(중국어)" }.value.contains(d.lodging("kunming1")!!.addrZh))
        assertEquals(false, f.first { it.label == "입국 사유" }.copy)
        assertEquals("KE161", f.first { it.label == "입국 항공편" }.value); assertEquals("KE162", f.first { it.label == "출국 항공편" }.value)
        assertEquals(listOf("KE161", "20261216", "昆明长水国际机场", "KE162", "20261223"), TripDataHolder.data.pretrip.first { it.id == "p-04" }.copy.map { it.value })
    }

    @Test fun longhuaDimSum() {
        val d = TripDataHolder.data
        val r = d.restaurants.first { it.id == "longhua" }
        assertEquals("龙华茶楼(顺城店)", r.nameZh)
        assertEquals(2, r.routes.size)
        assertTrue(r.routes[0].url.startsWith("https://uri.amap.com/navigation?from=102.722334,25.018203"))
        assertTrue(d.schedule.any { it.dayId == "D7" && "龙华" in it.to })
    }

    @Test fun search() {
        val idx = kr.koasis.travelyunnan.domain.SearchIndex.build(TripDataHolder.data)
        val S = kr.koasis.travelyunnan.domain.SearchIndex
        assertTrue(S.search(idx, "산소캔").any { it.kind == "체크리스트" })
        assertEquals(kr.koasis.travelyunnan.domain.SearchTarget.Eat("longhua"), S.search(idx, "龙华").first().target)
        assertEquals("관광지", S.search(idx, "사방가").first().kind)
        assertTrue(S.search(idx, "로봇").any { it.target == kr.koasis.travelyunnan.domain.SearchTarget.Template("tpl-delivery-note") })
        assertTrue(S.search(idx, "여권 기차").all { "여권" in it.text && "기차" in it.text })
        assertTrue(S.search(idx, "  ").isEmpty())
    }

    @Test fun wechatQrPhrases() {
        val d = TripDataHolder.data
        val basic = d.phrasesIn("basic").map { it.zh }
        assertTrue(listOf("可以加个微信吗？", "请扫我的二维码。", "我扫你，还是你扫我？", "你的微信号是多少？").all { it in basic })
        assertTrue(d.phrases.first { it.id == "msg-003" }.let { it.copyOnly && "message" in it.categories })
        assertEquals("QR코드", d.words.first { it.zh == "二维码" }.ko)
        val T = kr.koasis.travelyunnan.domain.Templates
        val qr = T.all.first { it.id == "tpl-wechat-qr" }
        assertEquals(8, qr.lines.size); assertTrue(qr.lines.all { it.label.isNotBlank() })
        assertTrue(T.all.first { it.id == "tpl-wechat-hello" }.render(mapOf("date" to "12/17", "about" to "入住")).contains("12月17日（周四）入住的客人"))
        assertTrue(d.templateLinks.count { it.templateId == "tpl-wechat-qr" } == 3)
    }

    @Test fun featureSearch() {
        val S = kr.koasis.travelyunnan.domain.SearchIndex
        val idx = S.build(TripDataHolder.data)
        val F = kr.koasis.travelyunnan.domain.SearchTarget.Feature::class
        assertEquals(kr.koasis.travelyunnan.domain.SearchTarget.Feature("number"), S.search(idx, "숫자카드").first().target)
        assertEquals(kr.koasis.travelyunnan.domain.SearchTarget.Feature("diary"), S.search(idx, "사진일기").first().target)
        assertEquals(kr.koasis.travelyunnan.domain.SearchTarget.Feature("number"), S.search(idx, "흥정").first().target)
        assertEquals(kr.koasis.travelyunnan.domain.SearchTarget.Feature("expense"), S.search(idx, "지출").first().target)
        assertTrue(S.FEATURES.map { it.key }.toSet().size == S.FEATURES.size)
        assertTrue(F.isInstance(S.search(idx, "설정").first().target))
    }
}
