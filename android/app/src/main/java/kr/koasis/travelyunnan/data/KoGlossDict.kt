package kr.koasis.travelyunnan.data

/** 중국어만 있는 화면 글자의 한국어 번역 사전(data/json/ko_gloss.json). TripData.fromJson 이 채운다 */
object KoGlossDict {
    @Volatile var items: Map<String, String> = emptyMap()
    fun ko(zh: String?): String = zh?.trim()?.takeIf { it.isNotEmpty() }?.let { items[it] }.orEmpty()
}
