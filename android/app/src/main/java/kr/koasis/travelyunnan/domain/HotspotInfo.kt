package kr.koasis.travelyunnan.domain

import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.json.*

/** 지도 핫스팟을 눌렀을 때 보여줄 연관 정보(S8 지도 인터랙션) */
data class HotspotInfo(
    val hotspot: Hotspot,
    val title: String,
    val zh: String,
    val addrZh: String,
    val phones: List<String>,
    val taxi: Pair<TargetType, String>?,
    val lodging: Lodging?,
    val amapUrl: String?,
    val rows: List<Pair<Day, Schedule>>,
    val meals: List<Pair<Meal, MealOption>>,
    val todos: List<LodgingTodo>,
    val note: String,
)

object HotspotResolver {
    fun resolve(data: TripData, h: Hotspot, dayId: String? = null): HotspotInfo {
        var zh = h.zh; var addr = ""; var phones = emptyList<String>(); var taxi: Pair<TargetType, String>? = null
        var lodging: Lodging? = null; var amap: String? = null; var todos = emptyList<LodgingTodo>()
        when (h.type) {
            "lodging" -> data.lodging(h.key)?.let { l ->
                lodging = l; zh = l.nameZh; addr = cleanZhAddress(l.addrZh); phones = l.phones
                taxi = TargetType.lodging to l.id; todos = data.todosOf(l.id)
            }
            "place" -> data.place(h.key)?.let { p -> zh = zh.ifBlank { p.nameZh }; addr = p.addrZh; amap = p.amapUrl; taxi = TargetType.place to p.id }
            "meal" -> h.key?.let { data.findMealOption(it) }?.let { (_, o) ->
                zh = zh.ifBlank { o.nameZh }; addr = cleanZhAddress(o.addrZh); amap = o.amapUrl
                if (TaxiCards.isTaxiable(o)) taxi = TargetType.meal to o.id
            }
        }
        val kws = (h.keywords + listOfNotNull(h.key?.takeIf { h.type == "meal" })).filter { it.isNotBlank() }
        val days = data.days.filter { dayId == null || it.id == dayId }
        val rows = days.flatMap { d -> data.scheduleOf(d.id).filter { r -> kws.any { k -> "${r.from} ${r.via} ${r.to} ${r.tip}".contains(k) } }.map { d to it } }
        val meals = days.flatMap { d -> data.mealsOf(d.id) }.flatMap { m -> m.options.filter { o -> kws.any { k -> o.name.contains(k) || o.nameZh.contains(k) } }.map { m to it } }
        return HotspotInfo(h, h.label, zh, addr, phones, taxi, lodging, amap, rows.take(8), meals.distinctBy { it.second.id }.take(6), todos, h.note)
    }
}
