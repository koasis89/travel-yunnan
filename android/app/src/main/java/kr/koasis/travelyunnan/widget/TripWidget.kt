package kr.koasis.travelyunnan.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import kr.koasis.travelyunnan.MainActivity
import kr.koasis.travelyunnan.TravelApp
import kr.koasis.travelyunnan.domain.*
import kr.koasis.travelyunnan.ui.nav.Routes

/** 홈 화면 위젯(S6-4): 다음 이동 + 오늘 숙소 중국어 주소. 누르면 택시 카드로 간다. */
class TripWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as TravelApp
        val data = app.container.tripData
        val settings = app.container.settings.settings.first()
        val now = tripNow(settings.overrideDateTime)
        val phase = DayResolver.resolve(now, data.days)
        val day = DayResolver.focusDay(phase, data.days)
        val lodging = data.lodging(day.lodgingId)
        val move = NextMoveFinder.find(now, data.days) { data.scheduleOf(it) }
        val title = when (phase) {
            is TripPhase.BeforeTrip -> "출발까지 D-${phase.daysLeft}"
            is TripPhase.OnTrip -> "${day.id} ${day.route}"
            TripPhase.AfterTrip -> "여행 끝"
        }
        val route = if (lodging != null) Routes.taxi(TargetType.lodging, lodging.id) else Routes.taxi(TargetType.place, "kmg_airport")
        provideContent { WidgetContent(title, move?.let { "${it.day.id} ${it.row.timeLabel} ${it.row.transport} · ${formatLeft(it.minutesLeft)}" }, lodging?.nameZh, lodging?.addrZh?.let { cleanZhAddress(it) }, route) }
    }
}

@Composable
private fun WidgetContent(title: String, move: String?, nameZh: String?, addrZh: String?, route: String) {
    val intent = Intent(Intent.ACTION_MAIN).setClassName("kr.koasis.travelyunnan", "kr.koasis.travelyunnan.MainActivity")
        .putExtra(MainActivity.EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    GlanceTheme {
        Column(
            GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF1F6F6B))).cornerRadius(16.dp).padding(12.dp).clickable(actionStartActivity(intent)),
        ) {
            Text(title, style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp, fontWeight = FontWeight.Bold))
            move?.let { Text(it, style = TextStyle(color = ColorProvider(Color(0xFFFCE6C2)), fontSize = 12.sp), maxLines = 2) }
            Spacer(GlanceModifier.height(6.dp))
            Text(nameZh ?: "쿤밍 长水国际机场", style = TextStyle(color = ColorProvider(Color.White), fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            addrZh?.let { Text(it, style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp), maxLines = 2) }
            Spacer(GlanceModifier.defaultWeight())
            Text("눌러서 택시 카드 열기", style = TextStyle(color = ColorProvider(Color(0xFFB7E0DC)), fontSize = 11.sp))
        }
    }
}

class TripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TripWidget()
}
