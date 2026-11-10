package kr.koasis.travelyunnan.ui.transport

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.json.Schedule
import kr.koasis.travelyunnan.data.json.TransportItem
import kr.koasis.travelyunnan.data.json.TripNotification
import kr.koasis.travelyunnan.domain.TargetType
import kr.koasis.travelyunnan.notify.TripNotifier
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.common.*
import kr.koasis.travelyunnan.ui.nav.LocalActions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun TransportScreen(vm: AppViewModel, data: TripData) {
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var hasPermission by remember { mutableStateOf(TripNotifier.hasPostPermission(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it; if (it) vm.registerNotifications() }
    TransportContent(
        data, now, settings.disabledNotifications, hasPermission, TripNotifier.canExact(ctx),
        onToggle = { vm.toggleNotification(it) },
        onRequestPermission = { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onOpenExactSettings = {
            if (Build.VERSION.SDK_INT >= 31) runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
    )
}

private data class TrainInfo(val row: Schedule, val dayId: String, val stationPlace: String)

@Composable
fun TransportContent(
    data: TripData, now: LocalDateTime, disabled: Set<String>, hasPermission: Boolean, canExact: Boolean,
    onToggle: (String) -> Unit, onRequestPermission: () -> Unit, onOpenExactSettings: () -> Unit,
) {
    val actions = LocalActions.current
    val trains = remember(data) {
        data.schedule.filter { it.transport.contains("열차") && it.transport.contains("C") }
            .map { TrainInfo(it, it.dayId, if (it.from.contains("쿤밍")) "kunming_station" else "shangrila_station") }
    }
    val groups = remember(data) { data.transport.groupBy { it.group } }
    val altNote = data.notes.firstOrNull { it.title == "빵차 대안" }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("교통·열차·알림", onBack = actions.back)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionTitle("열차") }
            items(trains, key = { it.row.id }) { t -> TrainCard(data, t) }

            item { SectionTitle("일정 알림") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!hasPermission) Column {
                        InfoBanner("알림 권한 꺼짐 · 열차·픽업 알림을 받으려면 허용하세요.", Icons.Filled.NotificationsOff, MaterialTheme.colorScheme.tertiaryContainer)
                        TextButton(onClick = onRequestPermission) { Text("알림 권한 허용") }
                    }
                    if (!canExact) Column {
                        InfoBanner("정확한 시각 알림이 꺼져 있어 몇 분 늦을 수 있다. 05:45 픽업처럼 분 단위가 중요하면 켠다.", Icons.Filled.Alarm)
                        TextButton(onClick = onOpenExactSettings) { Text("정확한 알림 허용") }
                    }
                    Text("현지 시각 기준 · 스위치를 끄면 그 알림만 울리지 않습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(data.notifications, key = { it.id }) { n -> NotificationRow(n, enabled = n.id !in disabled, past = LocalDateTime.parse(n.at).isBefore(now)) { onToggle(n.id) } }

            item { SectionTitle("역에서 쓰는 문장") }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction(Icons.Filled.Train, "역·보안검색 회화") { actions.openPhrases("station") }
                    SmallAction(Icons.Filled.ConfirmationNumber, "매표 회화") { actions.openPhrases("ticket") }
                }
            }

            groups.forEach { (g, list) ->
                item { SectionTitle(g) }
                items(list, key = { it.id }) { t -> TransportRow(t) }
            }
            altNote?.let {
                item { SectionTitle("빵차 대안") }
                item { Box(Modifier.padding(horizontal = 16.dp)) { InfoBanner(it.body, Icons.Filled.DirectionsBus) } }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    data.transportNotes.forEach { Text("※ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("환율: ¥1 = ${data.rates.CNY.toInt()}원, \$1 = ${data.rates.USD.toInt()}원 (${data.rates.persons}인)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TrainCard(data: TripData, t: TrainInfo) {
    val actions = LocalActions.current
    val day = data.day(t.dayId)
    val code = Regex("C\\d+").find(t.row.transport)?.value ?: t.row.transport
    val alt = Regex("대안 (C\\d+ [\\d:→]+)").find(t.row.tip)?.groupValues?.get(1)
    Card(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Train, null); Spacer(Modifier.width(8.dp))
                Text("$code · ${t.dayId} ${day?.dateLabel ?: ""}", style = MaterialTheme.typography.titleLarge)
            }
            Text("${t.row.timeLabel}  ${t.row.from} → ${t.row.to}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
            Text("${t.row.duration} · ${t.row.tip}", style = MaterialTheme.typography.bodyMedium)
            if (alt != null) Text("대안 $alt", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            Text("출발 30분 전 역 도착, 출발 20분 전 개찰 마감, 여권으로 개찰", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallAction(Icons.Filled.LocalTaxi, "역 택시 카드") { actions.openTaxi(TargetType.place, t.stationPlace) }
                SmallAction(Icons.Filled.CalendarMonth, "일정 보기") { actions.openSchedule(t.dayId) }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: TripNotification, enabled: Boolean, past: Boolean, onToggle: () -> Unit) {
    val at = LocalDateTime.parse(n.at)
    ListItem(
        headlineContent = { Text(n.title) },
        overlineContent = { Text("${n.dayId} · " + at.format(DateTimeFormatter.ofPattern("M/d HH:mm")) + if (past) " · 지남" else "") },
        supportingContent = { Text(n.body, maxLines = 3) },
        leadingContent = {
            Icon(when (n.kind) { "train" -> Icons.Filled.Train; "flight" -> Icons.Filled.Flight; "hotel" -> Icons.Filled.Hotel; else -> Icons.Filled.DirectionsCar }, null)
        },
        trailingContent = { Switch(enabled && !past, onCheckedChange = { onToggle() }, enabled = !past) },
        modifier = Modifier.clickable(enabled = !past) { onToggle() },
    )
}

@Composable
private fun TransportRow(t: TransportItem) {
    val price = t.unitPrice?.let { p -> (if (p == p.toLong().toDouble()) "%,d".format(p.toLong()) else p.toString()) + " ${t.currency}" } ?: ""
    ListItem(
        headlineContent = { Text(t.title) },
        supportingContent = { Text(listOf("$price/${t.unit} × ${t.qty.toInt()}", t.note).filter { it.isNotBlank() }.joinToString(" · ")) },
        trailingContent = { Text("%,d원".format(t.totalKrw.toLong()), style = MaterialTheme.typography.labelLarge) },
    )
}
