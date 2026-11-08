package kr.koasis.travelyunnan.domain

import kr.koasis.travelyunnan.data.KoGlossDict
import kr.koasis.travelyunnan.data.TripData

/** 검색 결과가 열 화면 */
sealed interface SearchTarget {
    data class Schedule(val dayId: String) : SearchTarget
    data class Lodging(val id: String) : SearchTarget
    data class Meals(val dayId: String) : SearchTarget
    data class Eat(val id: String) : SearchTarget
    data class Sight(val id: String) : SearchTarget
    data class Taxi(val placeId: String) : SearchTarget
    data class Menu(val foodZh: String) : SearchTarget
    data class Show(val phraseId: String) : SearchTarget
    data class Template(val id: String) : SearchTarget
    data object MilkTea : SearchTarget
    data object Arrival : SearchTarget
    data object Notes : SearchTarget
    data object Packing : SearchTarget
    data object Emergency : SearchTarget
}

data class SearchHit(val kind: String, val title: String, val sub: String, val target: SearchTarget, val text: String, val name: String)

/** 통합 검색(웹 domain/search.ts 와 같다): 일정·사전 일정·체크리스트·숙소·식사·식당·관광지·장소·음식·회화·템플릿·밀크티·메모·준비물·병원 */
object SearchIndex {
    val KIND_ORDER = listOf("일정", "사전 일정", "체크리스트", "숙소", "식사", "식당", "관광지", "장소", "음식", "회화", "위챗 템플릿", "밀크티", "입국신고", "메모", "준비물", "병원")
    /** 이름이 맞을 때 앞에 둘 순서(대상 자체 → 일정·체크리스트) */
    private val RANK = listOf("식당", "관광지", "숙소", "음식", "장소", "회화", "위챗 템플릿", "입국신고", "밀크티", "병원", "일정", "사전 일정", "식사", "체크리스트", "메모", "준비물")

    private fun clean(s: String?) = if (s.isNullOrBlank() || s == "-") "" else s
    fun norm(s: String) = s.lowercase().filterNot { it.isWhitespace() }

    fun build(d: TripData): List<SearchHit> {
        val out = mutableListOf<SearchHit>()
        fun add(kind: String, title: String, sub: String, t: SearchTarget, vararg extra: String?, zh: String = "") {
            val parts = (listOf(title, sub, zh) + extra).map { clean(it) }.filter { it.isNotEmpty() }
            val ko = parts.map { KoGlossDict.ko(it) }.filter { it.isNotEmpty() }
            val name = if (zh.isNotEmpty()) "$title $zh ${KoGlossDict.ko(zh)}" else title
            out += SearchHit(kind, title, sub, t, norm((parts + ko).joinToString(" ")), norm(name))
        }
        for (r in d.schedule) {
            val title = listOf(clean(r.from), clean(r.to)).filter { it.isNotEmpty() }.joinToString(" → ").ifEmpty { clean(r.tip).take(40) }
            add("일정", title, "${r.dayId} ${d.day(r.dayId)?.dateLabel.orEmpty()} ${r.timeLabel}" + (if (clean(r.via).isNotEmpty()) " · 경유 ${r.via}" else ""),
                SearchTarget.Schedule(r.dayId), r.tip, r.transport, r.duration)
        }
        for (p in d.pretrip) add("사전 일정", p.title, "${p.dateLabel} ${p.time}", SearchTarget.Schedule("pre"), p.detail, *p.tips.toTypedArray(), *p.checklist.map { it.text }.toTypedArray())
        for (nd in d.checklists.nextDay) for (x in nd.items) add("체크리스트", x.text, "${nd.dayId} 맨 아래 · ${nd.title}", SearchTarget.Schedule(nd.dayId))
        for (x in d.checklists.departure.items) add("체크리스트", x.text, d.checklists.departure.title, SearchTarget.Schedule("pre"))
        for (l in d.lodgings) add("숙소", l.nameKo, "${l.dates.joinToString("~") { it.drop(5) }} · ${l.region}", SearchTarget.Lodging(l.id), l.addrZh, l.addrEn, l.room, l.note, zh = l.nameZh)
        for (m in d.meals) for (o in m.options) add("식사", o.nameKo.ifBlank { o.name }, "${m.dayId} ${m.slot} · ${m.area}", SearchTarget.Meals(m.dayId), o.name, o.nameZh, o.addrZh, o.menu, m.note)
        for (r in d.restaurants) add("식당", r.nameKo, "${r.region} · ${r.kind}", SearchTarget.Eat(r.id), r.summary, *r.slots.toTypedArray(), *r.tips.toTypedArray(), *r.keywords.toTypedArray(),
            *r.menu.flatMap { listOf(it.zh, it.ko, it.desc) }.toTypedArray(), *r.ratings.flatMap { g -> listOf(g.name, g.addr) + g.popular.flatMap { listOf(it.zh, it.ko) } }.toTypedArray(), zh = r.nameZh)
        for (s in d.sights) add("관광지", s.nameKo, "${s.region} · ${s.dayIds.joinToString("·")}", SearchTarget.Sight(s.id), s.summary, *s.highlights.toTypedArray(), *s.tips.toTypedArray(), *s.keywords.toTypedArray(), s.hours, s.fee, zh = s.nameZh)
        for (p in d.places) add("장소", p.nameKo, p.nameZh, SearchTarget.Taxi(p.id), p.addrZh, zh = p.nameZh)
        for (f in d.foods) add("음식", f.nameKo, f.nameZh, SearchTarget.Menu(f.nameZh), f.description, f.region, zh = f.nameZh)
        for (p in d.phrases) add("회화", p.ko, p.zh, SearchTarget.Show(p.id), p.pinyin, p.koRead, zh = p.zh)
        for (t in Templates.all) add("위챗 템플릿", t.title, t.contact?.name.orEmpty(), SearchTarget.Template(t.id), t.render(emptyMap()), t.renderKo(emptyMap()), *t.lines.flatMap { listOf(it.zh, it.ko) }.toTypedArray())
        for (b in d.milktea.items) for (x in b.drinks) add("밀크티", "${x.zh} ${x.koRead}", "${b.nameKo}(${b.nameZh})", SearchTarget.MilkTea, x.desc, x.pinyin, b.nameEn)
        val ac = d.checklists.arrivalCard
        if (ac.title.isNotBlank()) add("입국신고", ac.title, "알리페이 이민국 12367", SearchTarget.Arrival, ac.summary, *ac.steps.flatMap { listOf(it.title, it.desc) }.toTypedArray(), *ac.fields.map { "${it.label} ${it.value}" }.toTypedArray())
        for (n in d.notes) add("메모", n.title, n.dayIds.joinToString("·"), SearchTarget.Notes, n.body, *n.phrasesInline.toTypedArray())
        for (p in d.packing) add("준비물", p.name, p.group + if (p.qty.isNotBlank()) " · ${p.qty}" else "", SearchTarget.Packing, p.note)
        for (h in d.emergency.hospitals) add("병원", h.nameKo, h.region, SearchTarget.Emergency, h.nameZh, h.addrZh, h.note)
        return out
    }

    /** 띄어쓰기로 나눈 낱말이 모두 들어간 것만. 이름에 들어가면 앞으로 */
    fun search(index: List<SearchHit>, q: String, limit: Int = 200): List<SearchHit> {
        val terms = q.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        return index.filter { h -> terms.all { it in h.text } }
            .sortedBy { h -> (if (terms.all { it in h.name }) 0 else 100) + RANK.indexOf(h.kind) }
            .take(limit)
    }
}
