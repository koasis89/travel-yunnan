package kr.koasis.travelyunnan

import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/** S3-1 완료 기준: 일차 판별·현재 일정·다음 이동 단위 테스트 (리포지토리 data/json 실데이터 사용) */
class DomainTest {
    private val data: TripData by lazy {
        val dir = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "data/json") }.first { File(it, "days.json").exists() }
        TripData.fromJson { File(dir, it).readText() }
    }

    private fun at(s: String) = LocalDateTime.parse(s)
    private fun phase(s: String) = DayResolver.resolve(at(s), data.days)
    private fun nowState(s: String): NowState {
        val p = phase(s) as TripPhase.OnTrip
        return CurrentScheduleFinder.find(data.scheduleOf(p.day.id), at(s).toLocalTime())
    }

    @Test fun beforeTrip_countsDaysLeft() {
        val p = phase("2026-11-05T10:00")
        assertTrue(p is TripPhase.BeforeTrip)
        assertEquals(41L, (p as TripPhase.BeforeTrip).daysLeft)
        assertEquals("D1", DayResolver.focusDay(p, data.days).id)
    }

    @Test fun d1_night_airportArrival() {
        val s = nowState("2026-12-16T22:30")
        assertEquals("D1-01", s.current?.id)
        assertEquals("D1-02", s.next?.id)
    }

    @Test fun d3_dawn_tourPickup() {
        val s = nowState("2026-12-18T05:50")
        assertEquals("D3", (phase("2026-12-18T05:50") as TripPhase.OnTrip).day.id)
        assertEquals("05:00", s.current?.timeStart)
        assertTrue(s.next!!.transport.contains("투어 차량"))
    }

    @Test fun d5_trekking() {
        val s = nowState("2026-12-20T12:30")
        assertEquals("차마객잔", s.current?.from)
        assertTrue(s.current!!.to.contains("중도객잔"))
    }

    @Test fun d8_evening_airportMove() {
        val s = nowState("2026-12-23T19:45")
        assertEquals("19:30", s.current?.timeStart)
        val m = NextMoveFinder.find(at("2026-12-23T19:00"), data.days) { data.scheduleOf(it) }
        assertEquals("19:30", m?.row?.timeStart)
        assertEquals(30L, m?.minutesLeft)
    }

    @Test fun afterTrip() {
        val p = phase("2026-12-24T09:00")
        assertEquals(TripPhase.AfterTrip, p)
        assertEquals("D8", DayResolver.focusDay(p, data.days).id)
    }

    @Test fun nextMove_isTrainC446() {
        val m = NextMoveFinder.find(at("2026-12-17T06:00"), data.days) { data.scheduleOf(it) }!!
        assertEquals("D2", m.day.id)
        assertTrue(m.row.transport.contains("C446"))
        assertEquals(149L, m.minutesLeft)
        val d7 = NextMoveFinder.find(at("2026-12-22T05:00"), data.days) { data.scheduleOf(it) }!!
        assertTrue(d7.row.transport.contains("C644"))
        assertEquals("07:22", d7.row.timeStart)
    }

    @Test fun openEndedRow_lastsUntilNextRow() {
        // D1 23:40~ 행은 종료 시각이 없어도 이후 진행 중으로 본다
        val s = nowState("2026-12-16T23:55")
        assertEquals("23:40", s.current?.timeStart)
    }

    @Test fun cleanAddress_removesKoreanNotes() {
        assertEquals("云雪丽(大水车店)", cleanZhAddress("云雪丽(大水车店) 검색"))
        assertEquals("昆明市官渡区塘子巷 (铁朋小锅米线)", cleanZhAddress("昆明市官渡区塘子巷 (铁朋小锅米线 검색)"))
        assertEquals("昆明站 附近 (全季酒店 昆明火车站店)", cleanZhAddress("昆明站 附近 (예: 全季酒店 昆明火车站店 검색)"))
    }

    @Test fun scheduleLinks_lodgingAndMessages() {
        val arrival = data.scheduleOf("D2").first { it.to.contains("물라뉴") }
        assertTrue(ScheduleLinks.find(data, arrival).any { it.type == TargetType.lodging && it.id == "mulanyu" })
        val d4 = data.schedule.first { it.message?.contains("虎跳峡徒步") == true }
        assertEquals("msg-001", ScheduleLinks.messagePhrase(data, d4)?.id)
        val d6 = data.schedule.first { it.message?.contains("香格里拉的包车") == true }
        assertEquals("msg-002", ScheduleLinks.messagePhrase(data, d6)?.id)
    }

    @Test fun taxiCards_lodgingAndMeal() {
        val c = TaxiCards.build(data, TargetType.lodging, "mulanyu")!!
        assertEquals("丽江沐蘭遇の汤宿", c.nameZh)
        assertTrue(c.helpers.any { it.id == "px-003" })
        val lunch = TaxiCards.mealFor(data, "D2", 13)!!
        assertTrue(lunch.name.contains("田村"))
        val card = TaxiCards.build(data, TargetType.meal, lunch.id)!!
        assertTrue(card.addrZh.startsWith("丽江市古城区"))
    }

    @Test fun phraseSuggestion() {
        val station = data.scheduleOf("D2").first { it.transport.contains("C446") }
        assertEquals("station", PhraseSuggester.suggest(station))
    }

    @Test fun orderSentence() {
        assertEquals("我要一份过桥米线，不要辣，不要香菜。", kr.koasis.travelyunnan.ui.meal.composeOrder("过桥米线", "一份", listOf("不要辣", "不要香菜")))
        assertEquals("这个要一个。", kr.koasis.travelyunnan.ui.meal.composeOrder(null, "一个", emptyList()))
        assertEquals("过桥米线", data.foodNameFor("과교미선")?.zh)
        assertEquals("牦牛肉火锅", data.foodNameFor("야크훠궈(토마토·마라탕)")?.zh)
    }

    @Test fun expenseCsvAndBudget() {
        val list = listOf(kr.koasis.travelyunnan.data.db.Expense(1, "D2", 100.0, "CNY", "식비", "a,b", "위챗", "공동", 1766000000000))
        val csv = kr.koasis.travelyunnan.ui.expense.expensesCsv(list, 200.0)
        assertTrue(csv.lines().size == 2 && csv.contains("20000") && csv.contains("\"a,b\""))
        assertEquals("식비", kr.koasis.travelyunnan.ui.expense.budgetKey("식비(맛집·식사 계획)"))
        assertEquals(15, data.notifications.size)
        assertTrue(data.notifications.all { kr.koasis.travelyunnan.notify.TripNotifier.epochMillis(it.at) > 0 })
    }

    @Test fun noteInlinePhrasesResolve() {
        val n = data.notes.first { it.title == "메이투안 요청사항" }
        val ids = n.phrasesInline.mapNotNull { data.phraseByZh(it)?.id }
        assertEquals(listOf("px-008", "px-009"), ids)
    }
}

class S6Test {
    @Test fun chineseNumbers() {
        val r = ChineseNumber::read
        assertEquals("零", r(0)); assertEquals("八", r(8)); assertEquals("十", r(10)); assertEquals("十五", r(15))
        assertEquals("二十", r(20)); assertEquals("四十五", r(45)); assertEquals("一百", r(100)); assertEquals("一百零五", r(105))
        assertEquals("一百五十", r(150)); assertEquals("两百", r(200)); assertEquals("一千零五十", r(1050)); assertEquals("两千三百", r(2300))
        assertEquals("一万两千", r(12000)); assertEquals("九万九千九百九十九", r(99999))
        assertEquals("一百五十块五毛", ChineseNumber.yuan(150.5)); assertEquals("八十块", ChineseNumber.yuan(80.0))
    }

    @Test fun templatesRender() {
        val t = Templates.shangrila
        val zh = t.render(mapOf("date" to "12月21日上午10点"))
        assertTrue(zh.startsWith("你好，我想预订12月21日上午10点，从靠山十九度到"))
        assertTrue(Templates.delivery.render(mapOf("room" to "305")).contains("房间号305"))
        assertFalse(Templates.delivery.render(emptyMap()).contains("房间号"))
        assertEquals(9, Templates.all.size)
    }

    @Test fun templateLinksAttachToRows() {
        val d = TripDataHolder.data
        val d4 = d.scheduleOf("D4").first { it.timeStart == "17:30" }
        assertEquals(listOf("tpl-tiger"), d.templatesFor(d4).map { it.templateId })
        val d5 = d.scheduleOf("D5").first { it.timeStart == "15:15" }
        val sh = d.templatesFor(d5).single()
        assertTrue(Templates.shangrila.render(sh.values).contains("上午10点半"))
        val d1 = d.scheduleOf("D1").first { it.timeStart == "23:40" }
        assertEquals(setOf("tpl-pickup", "tpl-delivery"), d.templatesFor(d1).map { it.templateId }.toSet())
        val d3 = d.scheduleOf("D3").first { it.timeStart == "05:00" }
        assertEquals(setOf("tpl-guide", "tpl-guide-arrive", "tpl-guide-late"), d.templatesFor(d3).map { it.templateId }.toSet())
        assertEquals("tpl-guide-confirm", d.templatesFor(d.scheduleOf("D2").first { it.timeStart == "20:00" }).single().templateId)
        assertEquals("tpl-guide-dropoff", d.templatesFor(d.scheduleOf("D3").first { it.timeStart == "15:00" }).single().templateId)
        assertTrue(Templates.all.filter { it.id.startsWith("tpl-guide") }.all { it.contact?.wechat == "18088479095" })
        assertTrue(Templates.all.first { it.id == "tpl-guide" }.render(emptyMap()).contains("四方街"))
    }

    @Test fun sightsLinked() {
        val d = TripDataHolder.data
        assertTrue(d.sights.size >= 20)
        val r = d.scheduleOf("D6").first { it.to.contains("송찬림사") }
        assertTrue(d.sightsFor(r).any { it.id == "songzanlin" })
        assertTrue(d.sights.filter { it.image != null }.all { !it.credit?.license.isNullOrBlank() && java.io.File(dataDir().parentFile.parentFile, "assets/${it.image}").exists() })
    }

    @Test fun amapLinks() {
        val d = TripDataHolder.data
        assertTrue(d.lodgings.filter { it.nameZh.isNotBlank() }.all { it.amapUrl?.startsWith("https://") == true })
        val rows = kr.koasis.travelyunnan.domain.AmapLinks.rows(d)
        assertEquals("https://surl.amap.com/1ZGvMhI4fh", rows.first { it.nameZh == "一瓦堂咖啡店" }.url)
        assertTrue(kr.koasis.travelyunnan.domain.AmapLinks.text(rows.first()).startsWith("📍"))
        // 식당은 모두 한국어 이름으로 보이고 중국어 이름은 따로 둔다
        val food = rows.filter { it.group == "식당" }
        assertTrue(food.all { Regex("[가-힣]").containsMatchIn(it.nameKo.substringAfter(" · ")) })
        assertEquals("D2 점심 · 톈춘 산탕 야크고기 훠궈(고성 대수차점)", food.first { it.nameZh == "田村·酸汤牦牛肉火锅(古城大水车店)" }.nameKo)
        assertEquals("丽江粑粑", food.first { it.nameKo.contains("리장 바바") }.nameZh)
        assertTrue(!kr.koasis.travelyunnan.domain.AmapLinks.text(food.first { it.nameZh.isBlank() }).contains("중국어 이름"))
        assertEquals(TargetType.place, ScheduleLinks.find(d, d.scheduleOf("D4").first { it.via.contains("기와카페") }).firstOrNull { it.id == "yiwatang" }?.type)
    }

    @Test fun orderWithDrinks() {
        val d = { zh: String -> kr.koasis.travelyunnan.ui.meal.DRINKS.first { it.zh == zh } }
        assertEquals("我要一份过桥米线，再要两瓶冰啤酒、一瓶矿泉水，不要辣。",
            kr.koasis.travelyunnan.ui.meal.composeFullOrder("过桥米线", "一份", listOf(d("冰啤酒") to 2, d("矿泉水") to 1), listOf("不要辣")))
        assertEquals("我要三杯云南小粒咖啡。", kr.koasis.travelyunnan.ui.meal.composeFullOrder(null, "一份", listOf(d("云南小粒咖啡") to 3), emptyList()))
        assertEquals("这个要一份。", kr.koasis.travelyunnan.ui.meal.composeFullOrder("", "一份", emptyList(), emptyList()))
    }

    @Test fun orderMultiFoods() {
        val beer = kr.koasis.travelyunnan.ui.meal.DRINKS.first { it.zh == "冰啤酒" }
        assertEquals("我要一份汽锅鸡、两份三七汽锅鸡，再要三瓶冰啤酒，不要辣。",
            kr.koasis.travelyunnan.ui.meal.composeMultiOrder(listOf("汽锅鸡" to 1, "三七汽锅鸡" to 2), "一份", listOf(beer to 3), listOf("不要辣")))
        assertEquals("这个要两份。", kr.koasis.travelyunnan.ui.meal.composeMultiOrder(emptyList(), "两份", emptyList(), emptyList()))
        val enc = kr.koasis.travelyunnan.ui.meal.encodeFoods(listOf("汽锅鸡" to 1, "野生菌" to 2))
        assertEquals(listOf("汽锅鸡" to 1, "野生菌" to 2), kr.koasis.travelyunnan.ui.meal.decodeFoods(enc))
    }

    @Test fun ctripRatings() {
        val d = TripDataHolder.data
        val j = d.restaurant("jianxinyuan")!!.ratings.single()
        assertEquals(4.5, j.score!!, 0.0); assertEquals(189, j.reviews)
        assertTrue(j.popular.any { it.zh == "标准过桥米线" })
        assertTrue(d.restaurants.count { it.ratings.isNotEmpty() } >= 18)
        assertTrue(d.ratingsNote.contains("大众点评"))
    }

    @Test fun hospitalAndNewShops() {
        val d = TripDataHolder.data
        val h = d.emergency.hospitals.single()
        assertEquals("丽江市人民医院", h.nameZh); assertEquals("https://surl.amap.com/jNlnaOmd1fJ", h.amapUrl)
        assertTrue(d.place(h.placeId) != null && h.phrases.size >= 2)
        assertEquals("sanerjiusi", d.restaurant("sanerjiusi")?.placeId)
        assertTrue(d.restaurantTips.any { it.contains("大众点评") })
    }

    @Test fun restaurantsLinked() {
        val d = TripDataHolder.data
        assertTrue(d.restaurants.size >= 20)
        assertEquals("jianxinyuan", d.restaurantOfOption("D7-03-1")?.id)
        val r = d.scheduleOf("D3").first { it.to.contains("천천선") }
        assertTrue(d.restaurantsFor(r).map { it.id }.containsAll(listOf("tiantianxian", "apo")))
        val root = dataDir().parentFile.parentFile
        assertTrue(d.restaurants.all { x -> x.amapUrl != null && x.menu.isNotEmpty() && (listOfNotNull(x.image) + x.menu.mapNotNull { it.image }).all { File(root, "assets/$it").exists() } })
    }

    @Test fun mapsOnePerDay() {
        val d = TripDataHolder.data
        assertEquals(listOf("map_d3", "map06"), d.day("D3")!!.mapIds)
        assertEquals(listOf("map_d8"), d.day("D8")!!.mapIds)
        assertTrue(d.maps.none { it.id == "map02" || it.id == "map04" || it.id == "ref_cablecar" })
        val pins = d.map("map06")!!.pins
        assertEquals(15, pins.size)
        assertTrue(pins.all { it.hotelDistance.isNotBlank() && it.color.startsWith("#") })
        assertTrue(d.hotspotsOf("map_d3").any { it.key == "sifang" })
    }

    @Test fun hotspotsResolve() {
        val d = TripDataHolder.data
        assertTrue(d.hotspots.size >= 50)
        val come = d.hotspots.first { it.id == "map05-06" }
        val info = HotspotResolver.resolve(d, come)
        assertEquals("comeinn", info.lodging?.id)
        assertTrue(info.rows.any { it.second.dayId == "D5" })
        val tian = HotspotResolver.resolve(d, d.hotspots.first { it.id == "map06-17" })
        assertTrue(tian.meals.any { it.second.name.contains("田村") })
        assertEquals(TargetType.meal, tian.taxi?.first)
        val station = HotspotResolver.resolve(d, d.hotspots.first { it.id == "map03-04" }, "D7")
        assertTrue(station.rows.all { it.first.id == "D7" } && station.rows.isNotEmpty())
    }
}

private fun dataDir() = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
    .map { File(it, "data/json") }.first { File(it, "days.json").exists() }

object TripDataHolder {
    val data: TripData by lazy {
        val dir = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "data/json") }.first { File(it, "days.json").exists() }
        TripData.fromJson { File(dir, it).readText() }
    }
}

/** 영수증 해석: 웹 tests/receipt.test.ts 와 같은 경우 */
class ReceiptParserTest {
    private val days = listOf(
        kr.koasis.travelyunnan.data.json.Day(id = "D2", date = "2026-12-17"),
        kr.koasis.travelyunnan.data.json.Day(id = "D4", date = "2026-12-19"),
    )

    @Test fun wechatPay() {
        val r = ReceiptParser.parse("支付成功\n¥128.00\n收款方 田村酸汤牦牛肉火锅\n支付时间 2026年12月17日 14:32:05\n交易单号 4200001234567890", days)
        assertEquals(128.0, r.amount!!, 0.001); assertTrue(r.merchant.contains("田村")); assertEquals("2026-12-17", r.date)
        assertEquals("14:32", r.time); assertEquals("D2", r.dayId); assertEquals("식비", r.category)
    }

    @Test fun alipayNegative() {
        val r = ReceiptParser.parse("交易成功\n-45.00\n商品说明 丁丁当当酸奶\n收款方全称 丽江古城丁丁当当\n创建时间 2026-12-19 09:41", days)
        assertEquals(45.0, r.amount!!, 0.001); assertEquals("D4", r.dayId)
    }

    @Test fun paperReceipt() {
        val r = ReceiptParser.parse("云雪丽现炒云南菜(大水车店)\n电话 0888-5123456\n松露粥 1 38.00\n炸茄盒 2 56.00\n小计 236.00\n优惠 -10.00\n实收 226.00\n2026/12/17 19:20", days)
        assertEquals(226.0, r.amount!!, 0.001); assertTrue(r.merchant.contains("云雪丽")); assertEquals("19:20", r.time)
    }

    @Test fun ocrNoise() {
        assertEquals("合计:128.00", ReceiptParser.normalize("合 计 ：１２８，００"))
        assertEquals(128.0, ReceiptParser.parse("合 计 ：１２８，００").amount!!, 0.001)
        assertEquals(32.5, ReceiptParser.parse("行程 费用\n半 32.50").amount!!, 0.001)
    }

    @Test fun didiAndMisc() {
        val r = ReceiptParser.parse("滴滴出行\n行程费用\n¥ 32.50\n2026-12-17 12:58")
        assertEquals(32.5, r.amount!!, 0.001); assertEquals("교통·투어·입장료", r.category)
        assertEquals(88.0, ReceiptParser.parse("2026\n3\n合计 88.00").amount!!, 0.001)
        assertNull(ReceiptParser.parse("谢谢光临\n欢迎再来").amount)
        assertEquals("숙박", ReceiptParser.guessCategory("丽江沐蘭遇の汤宿 房费"))
        assertEquals("쇼핑·기념품·예비비", ReceiptParser.guessCategory("鲜花饼 礼盒"))
    }
}
