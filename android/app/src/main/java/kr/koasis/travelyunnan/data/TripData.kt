package kr.koasis.travelyunnan.data

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kr.koasis.travelyunnan.data.json.*

/**
 * 읽기 전용 여행 데이터. assets/data 폴더의 JSON 파일을 앱 시작 시 한 번 읽어 메모리에 둔다.
 * (데이터가 작아 Room 적재 대신 메모리 보관으로 단순화했다. 사용자 상태만 Room 에 둔다.)
 */
class TripData(
    val version: String,
    val days: List<Day>,
    val schedule: List<Schedule>,
    val lodgings: List<Lodging>,
    val meals: List<Meal>,
    val foods: List<Food>,
    val phraseCategories: List<PhraseCategory>,
    val phrases: List<Phrase>,
    val words: List<Word>,
    val foodNames: List<FoodName>,
    val notes: List<Note>,
    val maps: List<MapItem>,
    val places: List<Place>,
    val placeLinks: List<PlaceLink>,
    val transport: List<TransportItem> = emptyList(),
    val transportNotes: List<String> = emptyList(),
    val budget: List<BudgetItem> = emptyList(),
    val rates: Rates = Rates(),
    val weather: List<Weather> = emptyList(),
    val packing: List<PackingItem> = emptyList(),
    val notifications: List<TripNotification> = emptyList(),
    val lodgingTodos: List<LodgingTodo> = emptyList(),
    val lodgingNotes: List<String> = emptyList(),
    val mealNotes: List<String> = emptyList(),
    val emergency: EmergencyFile = EmergencyFile(),
    val hotspots: List<Hotspot> = emptyList(),
    val templateLinks: List<TemplateLink> = emptyList(),
    val sights: List<Sight> = emptyList(),
    val restaurants: List<Restaurant> = emptyList(),
    val restaurantTips: List<String> = emptyList(),
    val ratingsNote: String = "",
    val blogs: List<kr.koasis.travelyunnan.data.json.Blog> = emptyList(),
) {
    private val dayById = days.associateBy { it.id }
    private val lodgingById = lodgings.associateBy { it.id }
    private val placeById = places.associateBy { it.id }
    private val phraseById = phrases.associateBy { it.id }
    private val mapById = maps.associateBy { it.id }
    private val scheduleByDay = schedule.groupBy { it.dayId }.mapValues { (_, v) -> v.sortedBy { it.seq } }
    private val mealOptionById = meals.flatMap { m -> m.options.map { it.id to (m to it) } }.toMap()

    fun day(id: String?) = id?.let { dayById[it] }
    fun lodging(id: String?) = id?.let { lodgingById[it] }
    fun place(id: String?) = id?.let { placeById[it] }
    fun phrase(id: String?) = id?.let { phraseById[it] }
    fun map(id: String?) = id?.let { mapById[it] }
    fun scheduleOf(dayId: String): List<Schedule> = scheduleByDay[dayId].orEmpty()
    fun mealsOf(dayId: String): List<Meal> = meals.filter { it.dayId == dayId }
    fun mealOption(id: String?): Pair<Meal, MealOption>? = id?.let { mealOptionById[it] }
    fun phrasesIn(category: String): List<Phrase> = phrases.filter { category in it.categories }
    fun templatesFor(row: Schedule) = templateLinks.filter { it.dayId == row.dayId && it.timeStart == row.timeStart }
    fun templateLink(id: String?) = id?.let { k -> templateLinks.firstOrNull { it.id == k } }
    fun hotspotsOf(mapId: String) = hotspots.filter { it.mapId == mapId }
    fun restaurant(id: String?) = id?.let { k -> restaurants.firstOrNull { it.id == k } }
    fun restaurantOfOption(optionId: String) = restaurants.firstOrNull { optionId in it.optionIds }
    fun restaurantsFor(r: Schedule) = restaurants.filter { x -> r.dayId in x.dayIds && x.keywords.any { k -> "${r.from} ${r.via} ${r.to} ${r.tip}".contains(k) } }
    fun sight(id: String?) = id?.let { k -> sights.firstOrNull { it.id == k } }
    fun blogsFor(sightId: String) = blogs.filter { sightId in it.sightIds }
    fun sightsOf(dayId: String) = sights.filter { dayId in it.dayIds }
    /** 일정 행에 관련된 관광지: 그 일차 관광지 중 키워드가 행 글에 들어 있는 것(웹 sightsFor 와 같음) */
    fun sightsFor(r: Schedule) = sightsOf(r.dayId).filter { s -> s.keywords.any { k -> "${r.from} ${r.via} ${r.to} ${r.tip}".contains(k) } }
    fun todosOf(lodgingId: String) = lodgingTodos.filter { it.lodgingId == lodgingId }
    /** 중국어 문자열을 포함하는 회화 문장(유의사항 본문의 인라인 문장 연결용) */
    fun phraseByZh(zh: String): Phrase? {
        val key = zh.replace(Regex("""[\s，,。？?！!]"""), "")
        return phrases.firstOrNull { it.zh.replace(Regex("""[\s，,。？?！!]"""), "") == key }
            ?: phrases.firstOrNull { key.length >= 6 && it.zh.replace(Regex("""[\s，,。？?！!]"""), "").contains(key.take(10)) }
    }
    /** 메뉴 문자열(한국어)로 음식명(중국어) 찾기: "과교미선" → 过桥米线 */
    fun foodNameFor(menuKo: String): FoodName? {
        val menu = menuKo.replace(" ", "")
        return foodNames.filter { it.kind == "food" && it.ko.isNotBlank() }
            .map { it to it.ko.substringBefore("(").replace(" ", "") }
            .filter { (_, key) -> key.length >= 2 && menu.contains(key) }
            .maxByOrNull { (_, key) -> key.length }?.first
    }
    fun mapsOf(dayId: String?): List<MapItem> =
        if (dayId == null) maps else day(dayId)?.mapIds.orEmpty().mapNotNull { map(it) }

    /** 식당 이름(중국어 일부)으로 식사 선택지를 찾는다. */
    fun findMealOption(keyZh: String): Pair<Meal, MealOption>? =
        meals.firstNotNullOfOrNull { m ->
            m.options.firstOrNull { it.name.contains(keyZh) || it.nameZh.contains(keyZh) }?.let { m to it }
        }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun load(context: Context): TripData {
            fun text(name: String) = context.assets.open("data/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
            return fromJson(::text)
        }

        /** 테스트에서도 쓰도록 파일 읽기 함수를 주입받는다. */
        fun fromJson(read: (String) -> String): TripData {
            fun <T> items(name: String, s: KSerializer<T>): ItemsFile<T> =
                json.decodeFromString(ItemsFile.serializer(s), read(name))
            val days = items("days.json", Day.serializer())
            val phrases = json.decodeFromString(PhrasesFile.serializer(), read("phrases.json"))
            val lodgings = json.decodeFromString(LodgingsFile.serializer(), read("lodgings.json"))
            val meals = json.decodeFromString(MealsFile.serializer(), read("meals.json"))
            val transport = json.decodeFromString(TransportFile.serializer(), read("transport.json"))
            return TripData(
                version = days.version,
                days = days.items,
                schedule = items("schedule.json", Schedule.serializer()).items,
                lodgings = lodgings.items,
                meals = meals.items,
                foods = items("foods.json", Food.serializer()).items,
                phraseCategories = phrases.categories,
                phrases = phrases.items,
                words = items("words.json", Word.serializer()).items,
                foodNames = items("foodNames.json", FoodName.serializer()).items,
                notes = items("notes.json", Note.serializer()).items,
                maps = items("maps.json", MapItem.serializer()).items,
                places = items("places.json", Place.serializer()).items,
                placeLinks = items("place_links.json", PlaceLink.serializer()).items,
                transport = transport.items,
                transportNotes = transport.notes,
                budget = items("budget.json", BudgetItem.serializer()).items,
                rates = json.decodeFromString(Rates.serializer(), read("rates.json")),
                weather = items("weather.json", Weather.serializer()).items,
                packing = items("packing.json", PackingItem.serializer()).items,
                notifications = items("notifications.json", TripNotification.serializer()).items,
                lodgingTodos = items("lodging_todos.json", LodgingTodo.serializer()).items,
                lodgingNotes = lodgings.notes,
                mealNotes = meals.notes,
                templateLinks = runCatching { items("template_links.json", TemplateLink.serializer()).items }.getOrDefault(emptyList()),
                sights = runCatching { items("sights.json", Sight.serializer()).items }.getOrDefault(emptyList()),
                restaurants = runCatching { items("restaurants.json", Restaurant.serializer()).items }.getOrDefault(emptyList()),
                restaurantTips = runCatching { json.decodeFromString(RestaurantsFile.serializer(), read("restaurants.json")).tips }.getOrDefault(emptyList()),
                ratingsNote = runCatching { json.decodeFromString(RestaurantsFile.serializer(), read("restaurants.json")).ratingsNote }.getOrDefault(""),
                blogs = runCatching { json.decodeFromString(kr.koasis.travelyunnan.data.json.SightsFile.serializer(), read("sights.json")).blogs }.getOrDefault(emptyList()),
                hotspots = runCatching { items("map_hotspots.json", Hotspot.serializer()).items }.getOrDefault(emptyList()),
                emergency = runCatching { json.decodeFromString(EmergencyFile.serializer(), read("emergency.json")) }.getOrDefault(EmergencyFile()),
            )
        }
    }
}
