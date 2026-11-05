package kr.koasis.travelyunnan.data.json

import kotlinx.serialization.Serializable

/** data/json 폴더 JSON 파일의 공통 구조: { version, items } */
@Serializable
data class ItemsFile<T>(val version: String = "", val items: List<T> = emptyList())

@Serializable
data class PhrasesFile(
    val version: String = "",
    val categories: List<PhraseCategory> = emptyList(),
    val items: List<Phrase> = emptyList(),
)

@Serializable
data class Day(
    val id: String,
    val date: String,
    val dateLabel: String = "",
    val route: String = "",
    val summary: String = "",
    val lodgingLabel: String = "",
    val lodgingId: String? = null,
    val region: String? = null,
    val altitude: String? = null,
    val tempDay: String? = null,
    val tempNight: String? = null,
    val outfit: String? = null,
    val mapIds: List<String> = emptyList(),
)

@Serializable
data class Schedule(
    val id: String,
    val dayId: String,
    val seq: Int,
    val timeLabel: String = "",
    val timeStart: String? = null,
    val timeEnd: String? = null,
    val from: String = "",
    val via: String = "",
    val to: String = "",
    val transport: String = "",
    val duration: String = "",
    val tip: String = "",
    val amapUrl: String? = null,
    val message: String? = null,
)

@Serializable
data class Lodging(
    val id: String,
    val dates: List<String> = emptyList(),
    val nights: Int? = null,
    val region: String = "",
    val nameKo: String = "",
    val nameZh: String = "",
    val addrZh: String = "",
    val addrEn: String = "",
    val phones: List<String> = emptyList(),
    val room: String = "",
    val bookingNo: String = "",
    val bookedBy: String = "",
    val priceKrw: Long? = null,
    val status: String = "",
    val note: String = "",
    val confirmed: Boolean = false,
)

@Serializable
data class Meal(
    val id: String,
    val dayId: String,
    val seq: Int = 0,
    val area: String = "",
    val slot: String = "",
    val options: List<MealOption> = emptyList(),
    val budgetCny: Int? = null,
    val note: String = "",
    val extra: String? = null,
)

@Serializable
data class MealOption(
    val id: String,
    val name: String,
    val nameZh: String = "",
    val addrZh: String = "",
    val amapUrl: String? = null,
    val menu: String = "",
)

@Serializable
data class Food(
    val id: String,
    val region: String = "",
    val no: Int? = null,
    val nameZh: String = "",
    val nameKo: String = "",
    val description: String = "",
    val imageKind: String = "",
    val image: String? = null,
    val source: String = "",
)

@Serializable
data class PhraseCategory(val id: String, val title: String, val icon: String = "")

@Serializable
data class Phrase(
    val id: String,
    val zh: String,
    val pinyin: String = "",
    val categories: List<String> = emptyList(),
    val repeat: Int = 1,
    val ko: String = "",
    val koRead: String = "",
    val copyOnly: Boolean = false,
    val source: String = "",
)

@Serializable
data class Word(val id: String, val zh: String, val pinyin: String = "", val ko: String = "", val koRead: String = "")

@Serializable
data class FoodName(
    val id: String,
    val zh: String,
    val pinyin: String = "",
    val kind: String = "food",
    val ko: String = "",
    val koRead: String = "",
)

@Serializable
data class Note(
    val id: String,
    val title: String,
    val body: String = "",
    val dayIds: List<String> = emptyList(),
    val phrasesInline: List<String> = emptyList(),
)

@Serializable
data class MapItem(
    val id: String,
    val file: String,
    val title: String = "",
    val caption: String = "",
    val dayIds: List<String> = emptyList(),
    val pins: List<MapPin> = emptyList(),
    val progress: List<String> = emptyList(),
)

@Serializable
data class MapPin(val no: Int, val nameKo: String = "", val nameZh: String = "", val distance: String = "", val kind: String = "")

@Serializable
data class Place(
    val id: String,
    val nameKo: String = "",
    val nameZh: String = "",
    val addrZh: String = "",
    val amapUrl: String? = null,
    val helperIds: List<String> = emptyList(),
)

@Serializable
data class PlaceLink(val match: String, val type: String, val key: String)
