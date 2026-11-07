package kr.koasis.travelyunnan.domain

import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.json.Meal
import kr.koasis.travelyunnan.data.json.MealOption
import kr.koasis.travelyunnan.data.json.Phrase
import kr.koasis.travelyunnan.data.json.Schedule

/** 택시 카드 대상 */
enum class TargetType { lodging, place, meal }

data class TaxiCard(
    val type: TargetType,
    val id: String,
    val nameZh: String,
    val addrZh: String,
    val nameKo: String,
    val phones: List<String> = emptyList(),
    val bookingInfo: String = "",
    val amapUrl: String? = null,
    val helpers: List<Phrase> = emptyList(),
    val warning: String? = null,
    /** 같은 끼니의 다른 선택지(식당 카드일 때) */
    val siblings: List<MealOption> = emptyList(),
)

private val HANGUL = Regex("""[가-힣]+:?""")
private val CJK = Regex("""[一-鿿]""")

/** "云雪丽(大水车店) 검색" → "云雪丽(大水车店)". 한글 메모와 빈 괄호를 지운다. */
fun cleanZhAddress(s: String): String =
    s.replace(HANGUL, "")
        .replace(Regex("""\(\s*\)|（\s*）"""), "")
        .replace(Regex("""\(\s+"""), "(")
        .replace(Regex("""\s+\)"""), ")")
        .replace(Regex("""\s{2,}"""), " ")
        .trim(' ', '/', ',')

fun hasChinese(s: String) = CJK.containsMatchIn(s)

/** 식사 선택지의 한국어 이름(웹 optKo 와 같다) */
fun MealOption.koName(): String = nameKo.ifBlank { name }

/** 식사 선택지의 중국어 이름. 한국어 이름이 따로 있으면 원래 이름에서 한글을 뺀 것, 중국어가 없으면 빈칸(웹 optZh) */
fun MealOption.zhName(): String {
    val z = if (nameKo.isNotBlank()) cleanZhAddress(name) else nameZh.ifBlank { name }
    return if (hasChinese(z)) z else ""
}

object TaxiCards {
    private val LODGING_HELPERS = mapOf(
        "kunming1" to listOf("px-002"),
        "mulanyu" to listOf("px-003"),
        "comeinn" to listOf("px-004"),
    )

    fun build(data: TripData, type: TargetType, id: String): TaxiCard? = when (type) {
        TargetType.lodging -> data.lodging(id)?.let { l ->
            TaxiCard(
                type, id,
                nameZh = l.nameZh.ifBlank { "昆明 酒店(待定)" },
                addrZh = cleanZhAddress(l.addrZh),
                nameKo = l.nameKo,
                phones = l.phones,
                bookingInfo = listOf(l.bookingNo, l.bookedBy).filter { it.isNotBlank() }.joinToString(" · "),
                amapUrl = l.amapUrl,
                helpers = LODGING_HELPERS[id].orEmpty().mapNotNull { data.phrase(it) },
                warning = if (!l.confirmed) "숙소 미정: ${l.note}" else null,
            )
        }
        TargetType.place -> data.place(id)?.let { p ->
            TaxiCard(type, id, p.nameZh, p.addrZh, p.nameKo, amapUrl = p.amapUrl,
                helpers = p.helperIds.mapNotNull { data.phrase(it) })
        }
        TargetType.meal -> data.mealOption(id)?.let { (meal, o) -> mealCard(meal, o) }
    }

    private fun mealCard(meal: Meal, o: MealOption): TaxiCard {
        val addr = cleanZhAddress(o.addrZh)
        return TaxiCard(
            TargetType.meal, o.id,
            nameZh = o.nameZh.ifBlank { o.name },
            addrZh = if (hasChinese(addr)) addr else "",
            nameKo = "${meal.dayId} ${meal.slot} · ${o.koName()}",
            amapUrl = o.amapUrl,
            siblings = meal.options.filter { it.id != o.id && isTaxiable(it) },
            warning = if (!hasChinese(o.name + addr)) "중국어 주소가 없는 선택지다. 지도앱 링크를 이용한다." else null,
        )
    }

    fun isTaxiable(o: MealOption) = hasChinese(o.name) || hasChinese(o.addrZh) || o.amapUrl != null

    /** 시간대별 끼니: 아침(~10시) · 점심(~15시) · 저녁(~21시) · 야식 */
    fun slotFor(hour: Int) = when {
        hour < 10 -> "아침"
        hour < 15 -> "점심"
        hour < 21 -> "저녁"
        else -> "야식"
    }

    /** 오늘·현재 시간대에 맞는 식당 선택지(택시 카드 가능한 것) */
    fun mealFor(data: TripData, dayId: String, hour: Int): MealOption? {
        val meals = data.mealsOf(dayId)
        val want = slotFor(hour)
        val ordered = meals.filter { it.slot == want } + meals.filter { it.slot != want }
        return ordered.firstNotNullOfOrNull { m -> m.options.firstOrNull { isTaxiable(it) } }
    }
}

data class LinkTarget(val label: String, val type: TargetType, val id: String)

object ScheduleLinks {
    /** 일정 행의 출발지·도착지 문구에서 숙소·장소·식당 링크를 찾는다. */
    fun find(data: TripData, row: Schedule): List<LinkTarget> {
        val text = "${row.to} / ${row.via}"
        val out = LinkedHashMap<String, LinkTarget>()
        for (l in data.placeLinks) {
            if (!text.contains(l.match)) continue
            val t = when (l.type) {
                "lodging" -> data.lodging(l.key)?.let { base ->
                    // 같은 호텔을 두 번 묵는 경우(쿤밍) 해당 일자의 예약으로 연결한다
                    val date = data.day(row.dayId)?.date
                    val l2 = data.lodgings.firstOrNull { it.nameZh == base.nameZh && date != null && date in it.dates } ?: base
                    LinkTarget(l2.nameKo.substringBefore(" (").substringBefore("("), TargetType.lodging, l2.id)
                }
                "place" -> data.place(l.key)?.let { LinkTarget(it.nameKo, TargetType.place, it.id) }
                "meal" -> data.findMealOption(l.key)?.let { (_, o) -> LinkTarget(o.koName().substringBefore("("), TargetType.meal, o.id) }
                else -> null
            } ?: continue
            out.putIfAbsent("${t.type}:${t.id}", t)
        }
        return out.values.toList()
    }

    /** 일정 행에 붙은 위챗 장문이 회화 데이터의 어느 메시지인지 찾는다. */
    fun messagePhrase(data: TripData, row: Schedule): Phrase? {
        val msg = row.message?.replace(Regex("""\s"""), "") ?: return null
        return data.phrasesIn("message").firstOrNull { p ->
            msg.contains(p.zh.replace(Regex("""\s"""), "").take(15))
        }
    }
}
