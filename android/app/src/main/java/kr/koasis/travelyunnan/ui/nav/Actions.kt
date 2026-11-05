package kr.koasis.travelyunnan.ui.nav

import androidx.compose.runtime.staticCompositionLocalOf
import kr.koasis.travelyunnan.domain.TargetType

/** 화면 이동 동작. 화면 composable 이 NavController 에 직접 의존하지 않도록 분리한다(테스트 용이). */
data class Actions(
    val openTaxi: (TargetType, String) -> Unit = { _, _ -> },
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
    val openMenu: (String?) -> Unit = {},
    val openTransport: () -> Unit = {},
    val openExpense: () -> Unit = {},
    val openPacking: () -> Unit = {},
    val openNotes: () -> Unit = {},
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
    const val MENU = "menu?food={food}"
    const val TRANSPORT = "transport"
    const val EXPENSE = "expense"
    const val PACKING = "packing"
    const val NOTES = "notes"

    fun schedule(day: String?) = "schedule" + (day?.let { "?day=$it" } ?: "")
    fun phrases(cat: String?) = "phrases" + (cat?.let { "?cat=$it" } ?: "")
    fun maps(day: String?) = "maps" + (day?.let { "?day=$it" } ?: "")
    fun taxi(type: TargetType, id: String) = "taxi/${type.name}/$id"
    fun show(id: String) = "show/$id"
    fun mapView(id: String) = "mapview/$id"
    fun lodgings(id: String?) = "lodgings" + (id?.let { "?id=$it" } ?: "")
    fun meals(day: String?) = "meals" + (day?.let { "?day=$it" } ?: "")
    fun menu(food: String?) = "menu" + (food?.let { "?food=$it" } ?: "")
}
