package kr.koasis.travelyunnan.domain

import java.time.LocalDate

/**
 * 위챗 템플릿의 날짜·시각 값(웹 domain/dateText.ts 와 같다): 숫자로만 받아(월/일, 시:분) 중국어·한국어로 바꾼다.
 * 저장 형식: 날짜 "12/20", 시각 "14:30"(24시간), 날짜·시각 "12/20 11:30". 숫자 형식이 아니면 그대로 쓴다.
 */
object DateText {
    const val TRIP_YEAR = 2026
    private val WD_ZH = listOf("一", "二", "三", "四", "五", "六", "日") // 월~일
    private val WD_KO = listOf("월", "화", "수", "목", "금", "토", "일")
    private val RE = Regex("""^\s*(?:(\d{0,2})\s*/\s*(\d{0,2}))?\s*(?:(\d{0,2})\s*:\s*(\d{0,2}))?\s*$""")

    data class Parts(val mo: String = "", val d: String = "", val h: String = "", val mi: String = "")

    fun parse(raw: String): Parts {
        val m = RE.find(raw) ?: return Parts()
        return Parts(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
    }

    fun join(kind: String, p: Parts): String {
        val date = "${p.mo}/${p.d}"; val time = "${p.h}:${p.mi}"
        return when (kind) { "date" -> date; "time" -> time; else -> "$date $time" }
    }

    private data class V(val mo: Int, val d: Int, val h: Int, val mi: Int)

    private fun valid(raw: String, kind: String): V? {
        val m = RE.find(raw) ?: return null
        val g = m.groupValues
        val needDate = kind != "time"; val needTime = kind != "date"
        if (needDate && (g[1].isEmpty() || g[2].isEmpty())) return null
        if (needTime && (g[3].isEmpty() || g[4].isEmpty())) return null
        val v = V(g[1].toIntOrNull() ?: 0, g[2].toIntOrNull() ?: 0, g[3].toIntOrNull() ?: 0, g[4].toIntOrNull() ?: 0)
        if (needDate && (v.mo !in 1..12 || v.d !in 1..31)) return null
        if (needTime && (v.h > 23 || v.mi > 59)) return null
        if (needDate && runCatching { LocalDate.of(year(v.mo), v.mo, v.d) }.isFailure) return null
        return v
    }

    fun isValid(raw: String, kind: String) = valid(raw, kind) != null

    private fun year(mo: Int) = if (mo < 6) TRIP_YEAR + 1 else TRIP_YEAR
    private fun wd(mo: Int, d: Int) = LocalDate.of(year(mo), mo, d).dayOfWeek.value - 1

    private fun zhTime(h: Int, mi: Int, approx: Boolean): String {
        val period = when { h < 5 -> "凌晨"; h < 12 -> "上午"; h == 12 -> "中午"; h < 18 -> "下午"; else -> "晚上" }
        val mins = when { mi == 0 -> ""; mi == 30 -> "半"; mi < 10 -> "零${mi}分"; else -> "${mi}分" }
        return "$period${if (h % 12 == 0) 12 else h % 12}点$mins" + if (approx) "左右" else ""
    }

    private fun koTime(h: Int, mi: Int): String {
        val period = when { h < 5 -> "새벽"; h < 12 -> "오전"; h == 12 -> "낮"; h < 18 -> "오후"; else -> "저녁" }
        return "$period ${if (h % 12 == 0) 12 else h % 12}시" + when (mi) { 0 -> ""; 30 -> " 반"; else -> " ${mi}분" }
    }

    /** 12/20 11:30 → 12月20日（周日）上午11点半 */
    fun zh(raw: String, kind: String, approx: Boolean = false): String {
        val v = valid(raw, kind) ?: return raw
        val date by lazy { "${v.mo}月${v.d}日（周${WD_ZH[wd(v.mo, v.d)]}）" }
        return when (kind) { "date" -> date; "time" -> zhTime(v.h, v.mi, approx); else -> date + zhTime(v.h, v.mi, approx) }
    }

    /** 12/20 11:30 → 12월 20일(일) 오전 11시 반 */
    fun ko(raw: String, kind: String): String {
        val v = valid(raw, kind) ?: return raw
        val date by lazy { "${v.mo}월 ${v.d}일(${WD_KO[wd(v.mo, v.d)]})" }
        return when (kind) { "date" -> date; "time" -> koTime(v.h, v.mi); else -> "$date ${koTime(v.h, v.mi)}" }
    }
}
