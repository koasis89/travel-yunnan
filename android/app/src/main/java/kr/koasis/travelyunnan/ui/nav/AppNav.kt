package kr.koasis.travelyunnan.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kr.koasis.travelyunnan.domain.TargetType
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.diary.DiaryScreen
import kr.koasis.travelyunnan.ui.emergency.EmergencyScreen
import kr.koasis.travelyunnan.ui.expense.ExpenseScreen
import kr.koasis.travelyunnan.ui.number.NumberCardScreen
import kr.koasis.travelyunnan.ui.template.TemplateScreen
import kr.koasis.travelyunnan.ui.home.HomeScreen
import kr.koasis.travelyunnan.ui.lodging.LodgingScreen
import kr.koasis.travelyunnan.ui.meal.FoodsScreen
import kr.koasis.travelyunnan.ui.meal.MealsScreen
import kr.koasis.travelyunnan.ui.meal.MenuPointScreen
import kr.koasis.travelyunnan.ui.notes.NotesScreen
import kr.koasis.travelyunnan.ui.packing.PackingScreen
import kr.koasis.travelyunnan.ui.transport.TransportScreen
import kr.koasis.travelyunnan.ui.map.MapViewScreen
import kr.koasis.travelyunnan.ui.map.MapsScreen
import kr.koasis.travelyunnan.ui.more.CreditsScreen
import kr.koasis.travelyunnan.ui.more.MoreScreen
import kr.koasis.travelyunnan.ui.more.SettingsScreen
import kr.koasis.travelyunnan.ui.phrase.PhrasesScreen
import kr.koasis.travelyunnan.ui.phrase.ShowPhraseScreen
import kr.koasis.travelyunnan.ui.schedule.ScheduleScreen
import kr.koasis.travelyunnan.ui.taxi.TaxiScreen

private data class Tab(val route: String, val base: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "home", "오늘", Icons.Filled.Home),
    Tab(Routes.SCHEDULE, "schedule", "일정", Icons.Filled.CalendarMonth),
    Tab(Routes.PHRASES, "phrases", "회화", Icons.Filled.Translate),
    Tab(Routes.MAPS, "maps", "지도", Icons.Filled.Map),
    Tab(Routes.MORE, "more", "더보기", Icons.Filled.Menu),
)

@Composable
fun AppNav(vm: AppViewModel, initialRoute: String? = null) {
    val nav = rememberNavController()
    val data by vm.data.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = TABS.any { it.route == route }

    fun go(r: String) = nav.navigate(r) { launchSingleTop = true }
    fun tab(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = false }
        launchSingleTop = true
    }

    val actions = remember(nav) {
        Actions(
            openTaxi = { t, id -> go(Routes.taxi(t, id)) },
            openSchedule = { tab(Routes.schedule(it)) },
            openPhrases = { tab(Routes.phrases(it)) },
            openShow = { go(Routes.show(it)) },
            openMaps = { tab(Routes.maps(it)) },
            openMapView = { go(Routes.mapView(it)) },
            openSettings = { go(Routes.SETTINGS) },
            openCredits = { go(Routes.CREDITS) },
            openLodgings = { go(Routes.lodgings(it)) },
            openMeals = { go(Routes.meals(it)) },
            openFoods = { go(Routes.FOODS) },
            openFoodPhotos = { go(Routes.foodPhotos(it)) },
            openMilkTea = { go(Routes.MILKTEA) },
            openArrivalCard = { go(Routes.ARRIVAL) },
            openMenu = { go(Routes.menu(it)) },
            openTransport = { go(Routes.TRANSPORT) },
            openExpense = { go(Routes.EXPENSE) },
            openPacking = { go(Routes.PACKING) },
            openNotes = { go(Routes.NOTES) },
            openEmergency = { go(Routes.EMERGENCY) },
            openTemplates = { id, link -> go(Routes.templates(id, link)) },
            openNumber = { go(Routes.NUMBER) },
            openDiary = { go(Routes.diary(it)) },
            openDocs = { go(Routes.docs(it)) },
            openSights = { go(Routes.sights(it)) },
            openAmapLinks = { go(Routes.AMAP) },
            openEats = { go(Routes.eats(it)) },
            openEat = { go(Routes.eat(it)) },
            openSight = { go(Routes.sight(it)) },
            openDoc = { id, edit -> go(Routes.doc(id, edit)) },
            back = { nav.popBackStack() },
        )
    }

    val d = data
    if (d == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LaunchedEffect(initialRoute) { initialRoute?.let { r -> runCatching { nav.navigate(r) { launchSingleTop = true } } } }

    CompositionLocalProvider(LocalActions provides actions) {
        Scaffold(
            bottomBar = {
                if (showBar) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                    TABS.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { tab(t.base) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onBackground,
                            ),
                        )
                    }
                }
            },
            contentWindowInsets = WindowInsets(0),
        ) { pad ->
            NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(pad)) {
                composable(Routes.HOME) { HomeScreen(vm, d) }
                composable(Routes.SCHEDULE, arguments = listOf(navArgument("day") { type = NavType.StringType; nullable = true })) {
                    ScheduleScreen(vm, d, it.arguments?.getString("day"))
                }
                composable(Routes.PHRASES, arguments = listOf(navArgument("cat") { type = NavType.StringType; nullable = true })) {
                    PhrasesScreen(vm, d, it.arguments?.getString("cat"))
                }
                composable(Routes.MAPS, arguments = listOf(navArgument("day") { type = NavType.StringType; nullable = true })) {
                    MapsScreen(vm, d, it.arguments?.getString("day"))
                }
                composable(Routes.MORE) { MoreScreen() }
                composable(Routes.TAXI) {
                    val type = runCatching { TargetType.valueOf(it.arguments?.getString("type") ?: "") }.getOrNull()
                    TaxiScreen(d, type, it.arguments?.getString("id").orEmpty())
                }
                composable(Routes.SHOW) { ShowPhraseScreen(vm, d, it.arguments?.getString("id").orEmpty()) }
                composable(Routes.MAPVIEW) { MapViewScreen(d, it.arguments?.getString("id").orEmpty()) }
                composable(Routes.SETTINGS) { SettingsScreen(vm, d) }
                composable(Routes.CREDITS) { CreditsScreen(d) }
                composable(Routes.LODGINGS, arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true })) {
                    LodgingScreen(vm, d, it.arguments?.getString("id"))
                }
                composable(Routes.MEALS, arguments = listOf(navArgument("day") { type = NavType.StringType; nullable = true })) {
                    MealsScreen(vm, d, it.arguments?.getString("day"))
                }
                composable(Routes.FOODS) { FoodsScreen(d) }
                composable(Routes.MILKTEA) { kr.koasis.travelyunnan.ui.meal.MilkTeaScreen(d) }
                composable(Routes.ARRIVAL) { kr.koasis.travelyunnan.ui.schedule.ArrivalCardScreen(d) }
                composable(Routes.FOOD_PHOTOS, arguments = listOf(navArgument("food") { type = NavType.StringType; nullable = true })) {
                    kr.koasis.travelyunnan.ui.meal.FoodPhotosScreen(d, it.arguments?.getString("food"))
                }
                composable(Routes.MENU, arguments = listOf(navArgument("food") { type = NavType.StringType; nullable = true })) {
                    MenuPointScreen(vm, d, it.arguments?.getString("food"))
                }
                composable(Routes.TRANSPORT) { TransportScreen(vm, d) }
                composable(Routes.EXPENSE) { ExpenseScreen(vm, d) }
                composable(Routes.PACKING) { PackingScreen(vm, d) }
                composable(Routes.NOTES) { NotesScreen(d) }
                composable(Routes.EMERGENCY) { EmergencyScreen(vm, d) }
                composable(Routes.TEMPLATES, arguments = listOf(
                    navArgument("id") { type = NavType.StringType; nullable = true },
                    navArgument("link") { type = NavType.StringType; nullable = true },
                )) {
                    TemplateScreen(d, it.arguments?.getString("id"), it.arguments?.getString("link"))
                }
                composable(Routes.NUMBER) { NumberCardScreen(vm) }
                composable(Routes.DIARY, arguments = listOf(navArgument("day") { type = NavType.StringType; nullable = true })) {
                    DiaryScreen(vm, d, it.arguments?.getString("day"))
                }
                composable(Routes.SIGHTS, arguments = listOf(navArgument("day") { type = NavType.StringType; nullable = true })) {
                    kr.koasis.travelyunnan.ui.sights.SightsScreen(vm, d, it.arguments?.getString("day"))
                }
                composable(Routes.EATS, arguments = listOf(navArgument("day") { type = NavType.StringType; nullable = true })) {
                    kr.koasis.travelyunnan.ui.eats.EatsScreen(vm, d, it.arguments?.getString("day"))
                }
                composable(Routes.EAT) { kr.koasis.travelyunnan.ui.eats.EatScreen(d, it.arguments?.getString("id").orEmpty()) }
                composable(Routes.AMAP) { kr.koasis.travelyunnan.ui.sights.AmapLinksScreen(d) }
                composable(Routes.SIGHT) { kr.koasis.travelyunnan.ui.sights.SightScreen(vm, d, it.arguments?.getString("id").orEmpty()) }
                composable(Routes.DOCS, arguments = listOf(navArgument("day") { type = NavType.StringType; nullable = true })) {
                    kr.koasis.travelyunnan.ui.docs.DocsScreen(vm, d, it.arguments?.getString("day"))
                }
                composable(Routes.DOC, arguments = listOf(navArgument("edit") { type = NavType.BoolType; defaultValue = false })) {
                    kr.koasis.travelyunnan.ui.docs.DocViewScreen(vm, d, it.arguments?.getString("id").orEmpty(), it.arguments?.getBoolean("edit") ?: false)
                }
            }
        }
    }
}
