package kr.koasis.travelyunnan.domain

import kr.koasis.travelyunnan.data.json.Day

/** 영수증·결제 화면 OCR 텍스트 → 금액·가게·날짜·구분 추정 (웹 src/domain/receipt.ts 와 같은 규칙) */
data class ReceiptGuess(
    val amount: Double?,
    val merchant: String,
    val date: String?,
    val time: String?,
    val dayId: String?,
    val category: String,
    val source: String,
)

object ReceiptParser {
    private const val FW = "０１２３４５６７８９．，：－／"
    private const val HW = "0123456789.,:-/"

    fun normalize(t: String): String {
        var s = t.map { c -> FW.indexOf(c).let { if (it >= 0) HW[it] else c } }.joinToString("")
        s = s.replace(Regex("[Y半羊]¥"), "¥")
            .replace(Regex("[￥¥Y半羊](?=[ \\t]*\\d)"), "¥")
            .replace(Regex("(\\d)[ \\t]*[.,][ \\t]*(\\d{2})(?!\\d)"), "$1.$2")
            .replace(Regex("(\\d),(\\d{3})"), "$1$2")
            .replace(Regex("[ \\t]*:[ \\t]*"), ":")
            .replace(Regex("([\\u4e00-\\u9fff])[ \\t]+(?=[\\u4e00-\\u9fff])"), "$1")
        return s
    }

    private val AMOUNT_KEYS = listOf(
        Regex("实付|实收|实际支付|付款金额|支付金额|消费金额|应付金额|应收|订单金额|Paid|PAID") to 100,
        Regex("合计|总计|总额|总金额|共计|Total|TOTAL|金额") to 80,
        Regex("支付成功|交易成功|付款成功|已支付") to 70,
        Regex("小计|Subtotal") to 40,
    )
    private val NOT_AMOUNT = Regex("电话|手机|tel|TEL|单号|订单号|流水号|编号|卡号|税号|No\\.|NO\\.|桌号|人数|找零|找赎|优惠|折扣")
    private val NUM = Regex("(?<!\\d)(?<!\\d[:/.-])-?¥?\\s*(\\d{1,6}(?:\\.\\d{1,2})?)(?![\\d:/])")

    private fun numbersIn(line: String): List<Double> = NUM.findAll(line).mapNotNull { m ->
        val raw = m.groupValues[1]
        val v = raw.toDoubleOrNull() ?: return@mapNotNull null
        if (v <= 0 || v >= 100000) return@mapNotNull null
        if (Regex("^(19|20)\\d\\d$").matches(raw)) return@mapNotNull null
        v
    }.toList()

    private data class Cand(val v: Double, val score: Int, val line: String)

    fun parse(text: String, days: List<Day> = emptyList()): ReceiptGuess {
        val lines = normalize(text).split(Regex("\\r?\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        val cands = mutableListOf<Cand>()
        lines.forEachIndexed { i, line ->
            if (NOT_AMOUNT.containsMatchIn(line)) return@forEachIndexed
            val key = AMOUNT_KEYS.firstOrNull { it.first.containsMatchIn(line) }
            val prevKey = if (i > 0 && numbersIn(lines[i - 1]).isEmpty()) AMOUNT_KEYS.firstOrNull { it.first.containsMatchIn(lines[i - 1]) } else null
            for (v in numbersIn(line)) {
                var score = 0
                if (key != null) score += key.second else if (prevKey != null) score += prevKey.second - 10
                if (Regex("¥|元|RMB|CNY").containsMatchIn(line)) score += 25
                if (Regex("\\.\\d{2}\\b").containsMatchIn(line)) score += 10
                if (Regex("^-?¥?\\s*\\d+(\\.\\d{1,2})?$").matches(line)) score += 20
                if (v == Math.floor(v) && v < 10 && !Regex("¥|元").containsMatchIn(line)) score -= 30
                cands += Cand(v, score, line)
            }
        }
        val best = cands.sortedWith(compareByDescending<Cand> { it.score }.thenByDescending { it.v }).firstOrNull { it.score > 0 }

        val joined = lines.joinToString("\n")
        val dm = Regex("(20\\d\\d)\\s*[-/年.]\\s*(\\d{1,2})\\s*[-/月.]\\s*(\\d{1,2})").find(joined)
        val date = dm?.let { "${it.groupValues[1]}-${it.groupValues[2].padStart(2, '0')}-${it.groupValues[3].padStart(2, '0')}" }
        val tm = Regex("(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?::[0-5]\\d)?(?!\\d)").find(joined)
        val time = tm?.let { "${it.groupValues[1].padStart(2, '0')}:${it.groupValues[2]}" }
        val dayId = date?.let { d -> days.firstOrNull { it.date == d }?.id }

        var merchant = ""
        val mKey = Regex("(?:商户名称|商户名|商家名称|商家|收款方全称|收款方|收款人|店名|门店|对方)[:：\\s]*(.*)$")
        for (i in lines.indices) {
            val m = mKey.find(lines[i]) ?: continue
            merchant = m.groupValues[1].ifBlank { lines.getOrElse(i + 1) { "" } }.trim(); break
        }
        if (merchant.isBlank()) {
            val shop = Regex("店|馆|酒店|餐厅|饭店|超市|客栈|火锅|米线|咖啡|茶|面|小吃|便利|商行|民宿|景区")
            merchant = lines.take(5).firstOrNull { l ->
                Regex("[\\u4e00-\\u9fff]{2,}").containsMatchIn(l) && shop.containsMatchIn(l) && AMOUNT_KEYS.none { it.first.containsMatchIn(l) }
            } ?: ""
        }
        merchant = merchant.replace(Regex("(支付|创建|交易)?时间.*$"), "").replace(Regex("[\\s¥\\d.:\\-]+$"), "")
            .replace(Regex("[ \\t]*([()（）])[ \\t]*"), "$1").trim().take(30)

        return ReceiptGuess(best?.v, merchant, date, time, dayId, guessCategory("$joined $merchant"), best?.line ?: "")
    }

    fun guessCategory(t: String): String = when {
        Regex("酒店|宾馆|客栈|民宿|住宿|房费|Hotel|HOTEL|Inn").containsMatchIn(t) -> "숙박"
        Regex("滴滴|出租|打车|的士|出行|高铁|火车|铁路|12306|车票|门票|景区|索道|缆车|观光车|大巴|客运|机场|地铁|公交|包车|Klook").containsMatchIn(t) -> "교통·투어·입장료"
        Regex("餐|饭|菜|炒|火锅|锅|米线|面|粥|汤|鸡|鱼|肉|烧烤|咖啡|奶|茶饮|甜品|面包|蛋挞|小吃|酒楼|食|啤酒|饮料|美团外卖|外卖").containsMatchIn(t) -> "식비"
        else -> "쇼핑·기념품·예비비"
    }
}
