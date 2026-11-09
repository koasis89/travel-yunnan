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
    /** 기능(화면). key 는 ui/search/SearchScreen 의 open() 이 화면으로 바꾼다 */
    data class Feature(val key: String) : SearchTarget
}

data class SearchHit(val kind: String, val title: String, val sub: String, val target: SearchTarget, val text: String, val name: String)

/** 통합 검색(웹 domain/search.ts 와 같다): 일정·사전 일정·체크리스트·숙소·식사·식당·관광지·장소·음식·회화·템플릿·밀크티·메모·준비물·병원 */
object SearchIndex {
    val KIND_ORDER = listOf("기능", "일정", "사전 일정", "체크리스트", "숙소", "식사", "식당", "관광지", "장소", "음식", "회화", "위챗 템플릿", "밀크티", "입국신고", "메모", "준비물", "병원")
    /** 이름이 맞을 때 앞에 둘 순서(대상 자체 → 일정·체크리스트) */
    private val RANK = listOf("기능", "식당", "관광지", "숙소", "음식", "장소", "회화", "위챗 템플릿", "입국신고", "밀크티", "병원", "일정", "사전 일정", "식사", "체크리스트", "메모", "준비물")

    /** 기능(화면) 검색: 키·이름·설명·흔히 쓰는 말 */
    data class FeatureDef(val key: String, val title: String, val sub: String, val words: String)
    val FEATURES = listOf(
        FeatureDef("home", "오늘", "오늘 일정·지금 할 일", "홈 현재 다음 일정"),
        FeatureDef("schedule", "일정", "D1~D8 시간표·다음날 준비", "스케줄 시간표 동선 다음날 준비 체크리스트"),
        FeatureDef("pretrip", "사전 일정", "출발 전 할 일·체크리스트", "출국 준비 이심 esim 입국 전"),
        FeatureDef("phrases", "회화", "기본 회화·단어장·음성", "중국어 말 문장 단어 단어장 발음"),
        FeatureDef("maps", "지도", "일차별 동선 지도", "지도 동선 위치"),
        FeatureDef("lodgings", "숙소", "숙소 카드·체크인 문장·할 일", "호텔 객잔 체크인 예약"),
        FeatureDef("meals", "맛집·식사", "끼니별 식당 선택지·택시 카드", "점심 저녁 아침 끼니 맛집"),
        FeatureDef("foods", "음식 도감·메뉴 포인팅", "음식 사진·설명, 주문 문장", "요리 메뉴 음식 이름 주문"),
        FeatureDef("foodphotos", "음식 사진", "리장 음식 사진 모음", "사진 갤러리"),
        FeatureDef("diary", "사진 일기", "일차별 사진·메모, 드라이브 백업", "사진일기 일기 다이어리 기록 사진 메모"),
        FeatureDef("number", "숫자 카드", "금액을 크게, 중국어 읽기·흥정 문장", "숫자 금액 가격 돈 위안 흥정 깎아 얼마"),
        FeatureDef("expense", "경비", "예산·지출 기록·정산", "지출 돈 예산 가계부 정산 영수증 환율"),
        FeatureDef("docs", "문서함", "바우처·기차표·숙소 예약·영수증 보관, 드라이브 백업", "서류 바우처 기차표 티켓 예약 확인서 영수증 pdf 드라이브 백업 구글"),
        FeatureDef("transport", "교통·열차·알림", "열차·픽업·빵차, 일정 알림", "기차 열차 교통 알림 캘린더 공항 픽업"),
        FeatureDef("sights", "관광지 안내", "관광지 사진과 설명", "관광 명소 볼거리"),
        FeatureDef("eats", "맛집 안내", "식당 메뉴판·평점", "식당 맛집 메뉴판 평점"),
        FeatureDef("amap", "지도 링크 모음", "고덕지도 링크 복사·공유", "지도 고덕 amap 위치 주소"),
        FeatureDef("packing", "준비물·날씨", "준비물 체크리스트·지역별 옷차림", "짐 옷 날씨 기온 준비물"),
        FeatureDef("notes", "유의사항", "고산병·열차·빵차·배달", "주의 메모 팁 고산병"),
        FeatureDef("emergency", "긴급·여권", "연락처·고산병·여권(생체인증 잠금)", "긴급 응급 병원 대사관 경찰 110 120 여권 분실"),
        FeatureDef("templates", "위챗 메시지 템플릿", "빵차·픽업·배달·QR 주고받기 문장", "위챗 메시지 문장 템플릿 예약 요청"),
        FeatureDef("arrival", "입국신고 QR(알리페이)", "중국 입국신고서 작성 순서·값 복사", "입국 신고 qr 알리페이 입국카드 arrival"),
        FeatureDef("milktea", "밀크티 종류", "브랜드별 추천·당도·얼음 주문", "밀크티 음료 차 나이차 카페"),
        FeatureDef("settings", "설정", "큰 글씨·음성·날짜 지정·드라이브 백업", "설정 글씨 크기 음성 날짜 드라이브 백업"),
    )

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
        for (f in FEATURES) add("기능", f.title, f.sub, SearchTarget.Feature(f.key), f.words)
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
