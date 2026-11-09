package kr.koasis.travelyunnan.ui.nav

import androidx.compose.runtime.staticCompositionLocalOf
import kr.koasis.travelyunnan.domain.TargetType

/** 화면 이동 동작. 화면 composable 이 NavController 에 직접 의존하지 않도록 분리한다(테스트 용이). */
data class Actions(
    val openTaxi: (TargetType, String) -> Unit = { _, _ -> },
    val openHome: () -> Unit = {},
    val openSchedule: (String?) -> Unit = {},
    val openPhrases: (String?) -> Unit = {},
    val openShow: (String) -> Unit = {},
    val openMaps: (String?) -> Unit = {},
    val openMapView: (String) -> Unit = {},
    val openSettings: () -> Unit = {},
    val openCredits: () -> Unit = {},
    val openLodgings: (String?) -> Unit = {},
    val openMeals: (String?) -> Unit = {},
    val openFoods: () -> Unit = {},
    val openFoodPhotos: (String?) -> Unit = {},
    val openMilkTea: () -> Unit = {},
    val openArrivalCard: () -> Unit = {},
    val openMenu: (String?) -> Unit = {},
    val openTransport: () -> Unit = {},
    val openExpense: () -> Unit = {},
    val openPacking: () -> Unit = {},
    val openNotes: () -> Unit = {},
    val openEmergency: () -> Unit = {},
    val openTemplates: (String?, String?) -> Unit = { _, _ -> },
    val openNumber: () -> Unit = {},
    val openDiary: (String?) -> Unit = {},
    val openDocs: (String?) -> Unit = {},
    val openSights: (String?) -> Unit = {},
    val openAmapLinks: () -> Unit = {},
    val openEats: (String?) -> Unit = {},
    val openEat: (String) -> Unit = {},
    val openSight: (String) -> Unit = {},
    /** 문서 보기. edit=true 면 정보 편집을 펼친 채로 연다(추가 직후) */
    val openDoc: (String, Boolean) -> Unit = { _, _ -> },
    val back: () -> Unit = {},
)

val LocalActions = staticCompositionLocalOf { Actions() }

object Routes {
    const val HOME = "home"
    const val SCHEDULE = "schedule?day={day}"
    const val PHRASES = "phrases?cat={cat}"
    const val MAPS = "maps?day={day}"
    const val MORE = "more"
    const val TAXI = "taxi/{type}/{id}"
    const val SHOW = "show/{id}"
    const val MAPVIEW = "mapview/{id}"
    const val SETTINGS = "settings"
    const val CREDITS = "credits"
    const val LODGINGS = "lodgings?id={id}"
    const val MEALS = "meals?day={day}"
    const val FOODS = "foods"
    const val MILKTEA = "milktea"
    const val ARRIVAL = "arrival-card"
    const val SEARCH = "search"
    const val FOOD_PHOTOS = "foodphotos?food={food}"
    fun foodPhotos(food: String?) = "foodphotos" + (food?.let { "?food=$it" } ?: "")
    const val MENU = "menu?food={food}"
    const val TRANSPORT = "transport"
    const val EXPENSE = "expense"
    const val PACKING = "packing"
    const val NOTES = "notes"
    const val EMERGENCY = "emergency"
    const val TEMPLATES = "templates?id={id}&link={link}"
    const val NUMBER = "number"
    const val DIARY = "diary?day={day}"
    const val DOCS = "docs?day={day}"
    const val SIGHTS = "sights?day={day}"
    const val AMAP = "amap"
    const val EATS = "eats?day={day}"
    const val EAT = "eat/{id}"
    const val SIGHT = "sight/{id}"
    const val DOC = "doc/{id}?edit={edit}"

    fun schedule(day: String?) = "schedule" + (day?.let { "?day=$it" } ?: "")
    fun phrases(cat: String?) = "phrases" + (cat?.let { "?cat=$it" } ?: "")
    fun maps(day: String?) = "maps" + (day?.let { "?day=$it" } ?: "")
    fun taxi(type: TargetType, id: String) = "taxi/${type.name}/$id"
    fun show(id: String) = "show/$id"
    fun mapView(id: String) = "mapview/$id"
    fun lodgings(id: String?) = "lodgings" + (id?.let { "?id=$it" } ?: "")
    fun meals(day: String?) = "meals" + (day?.let { "?day=$it" } ?: "")
    fun menu(food: String?) = "menu" + (food?.let { "?food=" + android.net.Uri.encode(it) } ?: "")
    fun templates(id: String?, link: String? = null) =
        "templates" + listOfNotNull(id?.let { "id=$it" }, link?.let { "link=$it" }).joinToString("&").let { if (it.isEmpty()) "" else "?$it" }
    fun diary(day: String?) = "diary" + (day?.let { "?day=$it" } ?: "")
    fun docs(day: String?) = "docs" + (day?.let { "?day=$it" } ?: "")
    fun sights(day: String?) = "sights" + (day?.let { "?day=$it" } ?: "")
    fun sight(id: String) = "sight/$id"
    fun eats(day: String?) = "eats" + (day?.let { "?day=$it" } ?: "")
    fun eat(id: String) = "eat/$id"
    fun doc(id: String, edit: Boolean) = "doc/$id?edit=$edit"
}
