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
        assertEquals("05:45", s.current?.timeStart)
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
        assertEquals(12, data.notifications.size)
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
        assertEquals(4, Templates.all.size)
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
    }

    @Test fun hotspotsResolve() {
        val d = TripDataHolder.data
        assertTrue(d.hotspots.size >= 50)
        val come = d.hotspots.first { it.id == "map05-06" }
        val info = HotspotResolver.resolve(d, come)
        assertEquals("comeinn", info.lodging?.id)
        assertTrue(info.rows.any { it.second.dayId == "D5" })
        val tian = HotspotResolver.resolve(d, d.hotspots.first { it.id == "map06-15" })
        assertTrue(tian.meals.any { it.second.name.contains("田村") })
        assertEquals(TargetType.meal, tian.taxi?.first)
        val station = HotspotResolver.resolve(d, d.hotspots.first { it.id == "map03-04" }, "D7")
        assertTrue(station.rows.all { it.first.id == "D7" } && station.rows.isNotEmpty())
    }
}

object TripDataHolder {
    val data: TripData by lazy {
        val dir = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "data/json") }.first { File(it, "days.json").exists() }
        TripData.fromJson { File(dir, it).readText() }
    }
}
