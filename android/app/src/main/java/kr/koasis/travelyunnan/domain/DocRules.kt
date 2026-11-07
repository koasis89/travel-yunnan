package kr.koasis.travelyunnan.domain

import kr.koasis.travelyunnan.data.db.TripDoc
import kr.koasis.travelyunnan.data.json.Day

/** 문서함: 파일 이름으로 분류·일차를 추정하고 정렬·거르기(웹 src/domain/docs.ts 와 같은 규칙) */
object DocRules {
    val KINDS = listOf("바우처", "기차표", "항공권", "숙소 예약", "영수증", "보험·신분", "블로그", "기타")

    private val rules = listOf(
        "블로그" to Regex("blog|블로그|naver|네이버|후기|여행기", RegexOption.IGNORE_CASE),
        "기차표" to Regex("c446|c644|train|rail|火车|铁路|12306|动车|高铁|기차|열차|승차권", RegexOption.IGNORE_CASE),
        "항공권" to Regex("flight|boarding|airline|航班|机票|登机|항공|탑승권|e-?ticket", RegexOption.IGNORE_CASE),
        "바우처" to Regex("voucher|klook|kkday|凭证|tour|투어|바우처|입장권|门票", RegexOption.IGNORE_CASE),
        "숙소 예약" to Regex("hotel|booking|agoda|airbnb|酒店|客栈|民宿|숙소|호텔|예약 ?확인", RegexOption.IGNORE_CASE),
        "영수증" to Regex("receipt|小票|发票|收据|支付|wechat ?pay|alipay|영수증|결제", RegexOption.IGNORE_CASE),
        "보험·신분" to Regex("passport|insurance|visa|여권|보험|비자|신분", RegexOption.IGNORE_CASE),
    )

    /** 블로그 글에 맞는 문서함 PDF: 메모의 원문 주소 → 같은 제목 → 제목·파일 이름의 표시 낱말(웹 docForBlog) */
    fun docForBlog(b: kr.koasis.travelyunnan.data.json.Blog, docs: List<TripDoc>): TripDoc? =
        docs.firstOrNull { it.note.contains(b.url) } ?: docs.firstOrNull { it.title == b.title }
            ?: docs.firstOrNull { d -> b.match.any { k -> "${d.title} ${d.fileName}".contains(k) } }

    fun guessKind(name: String): String = rules.firstOrNull { it.second.containsMatchIn(name) }?.first ?: "기타"

    fun guessDay(name: String, days: List<Day>): String? {
        if (Regex("c446", RegexOption.IGNORE_CASE).containsMatchIn(name)) return days.firstOrNull { it.id == "D2" }?.id
        if (Regex("c644", RegexOption.IGNORE_CASE).containsMatchIn(name)) return days.firstOrNull { it.id == "D7" }?.id
        val m = Regex("(?:^|[^\\d])(1[0-2]|0?[1-9])\\s*(?:[-./_]|月)\\s*([0-3]?\\d)(?:日)?(?!\\d)").find(name)
            ?: Regex("(?:^|[^\\d])(12)([0-3]\\d)(?!\\d)").find(name) ?: return null
        val md = m.groupValues[1].padStart(2, '0') + "-" + m.groupValues[2].padStart(2, '0')
        return days.firstOrNull { it.date.endsWith(md) }?.id
    }

    fun baseTitle(fileName: String): String =
        fileName.replace(Regex("\\.[A-Za-z0-9]{2,5}$"), "").replace(Regex("_+"), " ").trim().ifBlank { "문서" }

    /** 오늘 일차 문서 → 일차 순 → 일차 없음, 같은 일차는 최근 순 */
    fun sort(list: List<TripDoc>, todayId: String?): List<TripDoc> =
        list.sortedWith(compareBy<TripDoc> { d -> if (d.dayId != null && d.dayId == todayId) -1 else d.dayId?.drop(1)?.toIntOrNull() ?: 99 }
            .thenByDescending { it.created })

    fun filter(list: List<TripDoc>, kind: String?, dayId: String?, q: String = ""): List<TripDoc> {
        val s = q.trim().lowercase()
        return list.filter { d -> (kind == null || d.kind == kind) && (dayId == null || d.dayId == dayId) &&
            (s.isEmpty() || "${d.title} ${d.note} ${d.fileName}".lowercase().contains(s)) }
    }
}
