package kr.koasis.travelyunnan.domain

/** 금액을 중국어 숫자 읽기로 바꾼다(S6-3). 0 ~ 99,999 지원. 예: 150 → 一百五十, 1050 → 一千零五十 */
object ChineseNumber {
    private val DIGITS = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    private val UNITS = listOf("", "十", "百", "千", "万")

    fun read(n: Int): String {
        require(n in 0..99_999)
        if (n == 0) return "零"
        if (n < 20 && n >= 10) return "十" + (if (n % 10 == 0) "" else DIGITS[n % 10])
        val digits = n.toString().map { it - '0' }
        val sb = StringBuilder()
        var pendingZero = false
        for ((i, d) in digits.withIndex()) {
            val pos = digits.size - 1 - i
            if (d == 0) {
                pendingZero = sb.isNotEmpty()
                continue
            }
            if (pendingZero) { sb.append("零"); pendingZero = false }
            // "两百/两千/两万" 구어 표기, 십의 자리·일의 자리는 二
            val digit = if (d == 2 && pos >= 2) "两" else DIGITS[d]
            sb.append(digit).append(UNITS[pos])
        }
        return sb.toString()
    }

    /** 위안 금액 읽기: 150 → 一百五十块, 소수점은 毛 단위까지 */
    fun yuan(amount: Double): String {
        val whole = amount.toInt()
        val jiao = ((amount - whole) * 10 + 0.5).toInt()
        val sb = StringBuilder(read(whole)).append("块")
        if (jiao in 1..9) sb.append(DIGITS[jiao]).append("毛")
        return sb.toString()
    }

    fun pinyinHint(n: Int): String = when {
        n >= 10_000 -> "wàn(万)=1만, qiān(千)=천, bǎi(百)=백, shí(十)=십"
        n >= 1_000 -> "qiān(千)=천, bǎi(百)=백, shí(十)=십"
        n >= 100 -> "bǎi(百)=백, shí(十)=십"
        else -> "shí(十)=십"
    }
}
