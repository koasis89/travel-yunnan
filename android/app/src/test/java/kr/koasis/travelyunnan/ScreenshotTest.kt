package kr.koasis.travelyunnan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import kr.koasis.travelyunnan.data.AppSettings
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.domain.TargetType
import kr.koasis.travelyunnan.data.db.Expense
import kr.koasis.travelyunnan.ui.expense.ExpenseContent
import kr.koasis.travelyunnan.ui.home.HomeContent
import kr.koasis.travelyunnan.ui.lodging.LodgingContent
import kr.koasis.travelyunnan.ui.meal.FoodsScreen
import kr.koasis.travelyunnan.ui.meal.MealsContent
import kr.koasis.travelyunnan.ui.meal.MenuPointContent
import kr.koasis.travelyunnan.ui.notes.NotesScreen
import kr.koasis.travelyunnan.ui.packing.PackingContent
import kr.koasis.travelyunnan.ui.transport.TransportContent
import kr.koasis.travelyunnan.ui.map.MapsContent
import kr.koasis.travelyunnan.ui.nav.Actions
import kr.koasis.travelyunnan.ui.nav.LocalActions
import kr.koasis.travelyunnan.ui.phrase.PhraseCallbacks
import kr.koasis.travelyunnan.ui.phrase.PhrasesContent
import kr.koasis.travelyunnan.ui.phrase.ShowPhraseContent
import kr.koasis.travelyunnan.ui.schedule.ScheduleContent
import kr.koasis.travelyunnan.ui.taxi.TaxiScreen
import kr.koasis.travelyunnan.ui.theme.TravelTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/**
 * 에뮬레이터 없이 화면을 PNG 로 렌더링해 확인한다(Robolectric + Roborazzi).
 * 결과: app/build/outputs/roborazzi 폴더의 PNG
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val data by lazy { TripData.load(ApplicationProvider.getApplicationContext()) }
    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent {
            TravelTheme(dark = false) { CompositionLocalProvider(LocalActions provides Actions()) { content() } }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun home_beforeTrip() = shot("01_home_before_trip") { HomeContent(data, LocalDateTime.parse("2026-11-05T21:00")) }

    @Test fun home_d5() = shot("02_home_d5") { HomeContent(data, LocalDateTime.parse("2026-12-20T12:30"), overrideActive = true) }

    @Test fun schedule_d4() = shot("03_schedule_d4") {
        ScheduleContent(data, LocalDateTime.parse("2026-12-19T17:10"), "D4", setOf("done:D4-01", "done:D4-02")) {}
    }

    @Test fun taxi_mulanyu() = shot("04_taxi_mulanyu") { TaxiScreen(data, TargetType.lodging, "mulanyu") { true } }

    @Test fun taxi_meal() = shot("05_taxi_meal_d2_dinner") { TaxiScreen(data, TargetType.meal, "D2-05-1") { false } }

    @Test fun phrases_car() = shot("06_phrases_car") {
        PhrasesContent(data, "car", AppSettings(ttsSlow = true), listOf("ph-001"), emptyList(), ttsMissing = false, PhraseCallbacks())
    }

    @Test fun show_phrase() = shot("07_show_phrase") { ShowPhraseContent(data, "ph-027") }

    @Test fun show_message() = shot("08_show_wechat_message") { ShowPhraseContent(data, "msg-002") }

    @Test fun maps_d5() {
        rule.setContent {
            TravelTheme(dark = false) {
                CompositionLocalProvider(LocalActions provides Actions()) {
                    MapsContent(data, LocalDateTime.parse("2026-12-20T12:30"), null, setOf("trek:map05:0")) {}
                }
            }
        }
        rule.waitForIdle()
        rule.onNode(hasContentDescription("호도협 경로·고도 단면")).assertExists()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/09_maps_d5.png")
    }

    @Test fun home_dark() {
        rule.setContent {
            TravelTheme(dark = true) { CompositionLocalProvider(LocalActions provides Actions()) { HomeContent(data, LocalDateTime.parse("2026-12-17T07:40")) } }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/10_home_d2_dark.png")
    }

    // ---- S4
    @Test fun lodging() = shot("11_lodging") { LodgingContent(data, "mulanyu", setOf("todo:lt-03")) {} }

    @Test fun meals_d2() = shot("12_meals_d2") { MealsContent(data, LocalDateTime.parse("2026-12-17T18:00"), "D2") }

    @Test fun foods() = shot("13_foods") { FoodsScreen(data) }

    @Test fun menu_point() = shot("14_menu_point") { MenuPointContent(data, "fn-030") {} }

    @Test fun transport() = shot("15_transport") {
        TransportContent(data, LocalDateTime.parse("2026-12-16T20:00"), setOf("n-03"), hasPermission = true, canExact = true, {}, {}, {})
    }

    @Test fun expense() = shot("16_expense") {
        val list = listOf(
            Expense(1, "D2", 240.0, "CNY", "식비", "田村 야크훠궈", "위챗", "공동", 1766000000000),
            Expense(2, "D2", 190.0 * 3, "CNY", "교통·투어·입장료", "C118 열차 3인", "알리페이", "아빠", 1765990000000),
            Expense(3, "D2", 27.0, "CNY", "쇼핑·기념품·예비비", "야크 요거트", "현금", "첫째", 1766010000000),
        )
        ExpenseContent(data, list, AppSettings(cashCny = 1500.0), LocalDateTime.parse("2026-12-17T20:00"), {}, {}, {}, {})
    }

    @Test fun packing() = shot("17_packing") { PackingContent(data, setOf("pack:pk-01", "pack:pk-02", "pack:pk-13")) {} }

    @Test fun notes() = shot("18_notes") { NotesScreen(data) }
}
