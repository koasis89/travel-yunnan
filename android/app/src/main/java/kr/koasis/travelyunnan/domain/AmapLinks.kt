package kr.koasis.travelyunnan.domain

import kr.koasis.travelyunnan.data.TripData

/** 지도 링크 모음(웹 src/domain/cards.ts amapRows·amapText 와 같은 규칙) */
object AmapLinks {
    data class Row(val group: String, val nameKo: String, val nameZh: String, val addrZh: String, val url: String) {
        val exact get() = url.contains("surl.amap")
    }

    fun rows(data: TripData): List<Row> {
        val out = mutableListOf<Row>(); val seen = mutableSetOf<String>()
        fun push(r: Row) { if (r.url.isNotBlank() && seen.add(r.url)) out += r }
        data.lodgings.forEach { l -> l.amapUrl?.let { push(Row("숙소", l.nameKo, l.nameZh, cleanZhAddress(l.addrZh), it)) } }
        data.sights.forEach { s ->
            val p = data.place(s.placeId)
            (s.amapUrl ?: p?.amapUrl)?.let { push(Row("관광지·장소", s.nameKo, p?.nameZh ?: s.nameZh, p?.addrZh.orEmpty(), it)) }
        }
        data.places.forEach { p -> p.amapUrl?.let { push(Row("관광지·장소", p.nameKo, p.nameZh, p.addrZh, it)) } }
        data.meals.sortedWith(compareBy({ it.dayId }, { it.seq })).forEach { m ->
            m.options.forEach { o -> o.amapUrl?.let { push(Row("식당", "${m.dayId} ${m.slot} · ${o.name}", o.nameZh.ifBlank { o.name }, cleanZhAddress(o.addrZh), it)) } }
        }
        return out
    }

    fun text(r: Row): String = buildList {
        add("📍${r.nameKo}"); add("중국어 이름 : ${r.nameZh}")
        if (Regex("[\\u4e00-\\u9fff]").containsMatchIn(r.addrZh)) add("고덕지도 주소 : ${r.addrZh}")
        add("고덕지도 주소 링크 : ${r.url}")
    }.joinToString("\n")
}
