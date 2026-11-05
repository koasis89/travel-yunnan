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
import kr.koasis.travelyunnan.ui.home.HomeScreen
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
fun AppNav(vm: AppViewModel) {
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
            back = { nav.popBackStack() },
        )
    }

    val d = data
    if (d == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    CompositionLocalProvider(LocalActions provides actions) {
        Scaffold(
            bottomBar = {
                if (showBar) NavigationBar {
                    TABS.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { tab(t.base) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
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
            }
        }
    }
}
