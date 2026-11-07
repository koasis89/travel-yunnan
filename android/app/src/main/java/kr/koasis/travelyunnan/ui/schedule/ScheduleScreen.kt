package kr.koasis.travelyunnan.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.json.Schedule
import kr.koasis.travelyunnan.domain.*
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.common.AppTopBar
import kr.koasis.travelyunnan.ui.common.DayChips
import kr.koasis.travelyunnan.ui.nav.LocalActions
import kr.koasis.travelyunnan.util.External
import java.time.LocalDateTime

@Composable
fun ScheduleScreen(vm: AppViewModel, data: TripData, dayArg: String?) {
    val now by vm.now.collectAsStateWithLifecycle()
    val flags by vm.flags.collectAsStateWithLifecycle()
    ScheduleContent(data, now, dayArg, flags, dayHeader = { kr.koasis.travelyunnan.ui.docs.DayDocsCard(vm, it) }) { vm.toggleFlag(it) }
}

@Composable
fun ScheduleContent(data: TripData, now: LocalDateTime, dayArg: String?, flags: Set<String>, dayHeader: @Composable (String) -> Unit = {}, onToggleDone: (String) -> Unit) {
    val phase = DayResolver.resolve(now, data.days)
    val todayId = (phase as? TripPhase.OnTrip)?.day?.id
    // 출발 전에 아직 할 사전 일정이 있으면 '사전'부터 연다(웹과 같다)
    val preOpen = phase is TripPhase.BeforeTrip && data.pretrip.any { it.date >= now.toLocalDate().toString() }
    var selected by rememberSaveable(dayArg) { mutableStateOf(dayArg ?: todayId ?: if (preOpen) "pre" else data.days.first().id) }
    val day = data.day(selected) ?: data.days.first()
    val rows = data.scheduleOf(day.id)
    val current = if (day.id == todayId) CurrentScheduleFinder.find(rows, now.toLocalTime()).current else null
    val listState = rememberLazyListState()
    LaunchedEffect(selected, current?.id) {
        val i = rows.indexOfFirst { it.id == current?.id }
        if (i >= 0) listState.scrollToItem(i + 1) else listState.scrollToItem(0)
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar("일정")
        DayChips(data.days, selected, { if (it != null) selected = it }, todayId = todayId, preLabel = if (data.pretrip.isNotEmpty()) "사전" else null)
        if (selected == "pre") { PreTripList(data, flags, onToggleDone); return@Column }
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.padding(start = 4.dp, bottom = 6.dp)) {
                    Text("${day.id} ${day.dateLabel} · ${day.route}", style = MaterialTheme.typography.titleLarge)
                    Text("숙박: ${day.lodgingLabel.ifBlank { "-" }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("길게 누르면 완료 표시", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    dayHeader(day.id)
                }
            }
            items(rows, key = { it.id }) { r ->
                ScheduleRow(data, r, isCurrent = r.id == current?.id, done = "done:${r.id}" in flags) { onToggleDone("done:${r.id}") }
            }
        }
    }
}

/** 사전 일정(여행 전 할 일): 날짜·시각·할 일·위챗 문장 바로가기. 완료 표시는 일정과 같은 flags("done:<id>") */
@Composable
fun PreTripList(data: TripData, flags: Set<String>, onToggleDone: (String) -> Unit) {
    val actions = kr.koasis.travelyunnan.ui.nav.LocalActions.current
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("사전 일정 · 출발 전에 할 일", style = MaterialTheme.typography.titleLarge)
            Text("완료하면 '했다'를 누른다", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        items(data.pretrip, key = { it.id }) { x ->
            val ok = "done:${x.id}" in flags
            val tl = data.templateLink(x.templateLinkId)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${x.dateLabel} ${x.time}" + if (ok) "  · 완료" else "", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(x.title, style = MaterialTheme.typography.titleMedium)
                    Text(x.detail, style = MaterialTheme.typography.bodyMedium)
                    x.tips.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        if (tl != null) Button(onClick = { actions.openTemplates(tl.templateId, tl.id) }) { Text("위챗: 가이드 첫 연락 문장") }
                        OutlinedButton(onClick = { onToggleDone("done:${x.id}") }) { Text(if (ok) "완료 취소" else "했다") }
                    }
                    if (x.notificationId != null) Text("캘린더 알림 파일(교통·열차·알림)에 ${x.dateLabel} ${x.time} 알림이 들어 있다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

fun transportIcon(t: String): ImageVector = when {
    t.contains("열차") -> Icons.Filled.Train
    t.contains("셔틀") || t.contains("버스") -> Icons.Filled.DirectionsBus
    t.contains("케이블카") -> Icons.Filled.Landscape
    t.contains("도보") -> Icons.AutoMirrored.Filled.DirectionsWalk
    t.contains("택시") || t.contains("DiDi") || t.contains("DIDI") || t.contains("디디") -> Icons.Filled.LocalTaxi
    t.contains("차") || t.contains("픽업") -> Icons.Filled.DirectionsCar
    else -> Icons.Filled.Schedule
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleRow(data: TripData, r: Schedule, isCurrent: Boolean, done: Boolean, onLongPress: () -> Unit) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val links = remember(r) { ScheduleLinks.find(data, r) }
    val message = remember(r) { ScheduleLinks.messagePhrase(data, r) }
    val templates = remember(r) { data.templatesFor(r) }
    val sights = remember(r) { data.sightsFor(r) }
    val eats = remember(r) { data.restaurantsFor(r) }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // 타임라인 축
        Column(Modifier.width(22.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 18.dp).size(if (isCurrent) 14.dp else 10.dp)
                .background(if (done) MaterialTheme.colorScheme.outlineVariant else if (isCurrent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape))
            Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Spacer(Modifier.width(8.dp))
        ScheduleCard(r, isCurrent, done, onLongPress, links, message, templates, ctx, sights, eats)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleCard(r: Schedule, isCurrent: Boolean, done: Boolean, onLongPress: () -> Unit, links: List<LinkTarget>, message: kr.koasis.travelyunnan.data.json.Phrase?, templates: List<kr.koasis.travelyunnan.data.json.TemplateLink>, ctx: android.content.Context, sights: List<kr.koasis.travelyunnan.data.json.Sight> = emptyList(), eats: List<kr.koasis.travelyunnan.data.json.Restaurant> = emptyList()) {
    val actions = LocalActions.current
    OutlinedCard(
        Modifier.fillMaxWidth().alpha(if (done) 0.45f else 1f).combinedClickable(onClick = {}, onLongClick = onLongPress),
        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else CardDefaults.outlinedCardBorder(),
        colors = CardDefaults.outlinedCardColors(containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(r.timeLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (isCurrent) Text("  지금", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.weight(1f))
                if (done) Icon(Icons.Filled.CheckCircle, "완료", tint = MaterialTheme.colorScheme.primary)
            }
            val title = listOf(r.from, r.to).filter { it.isNotBlank() && it != "-" }.joinToString(" → ")
            if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleMedium)
            if (r.via.isNotBlank() && r.via != "-") Text("경유: ${r.via}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.transport.isNotBlank() && r.transport != "-") Row {
                Icon(transportIcon(r.transport), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(listOf(r.transport, r.duration).filter { it.isNotBlank() && it != "-" }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            if (r.tip.isNotBlank() && r.tip != "-") Text(r.tip, style = MaterialTheme.typography.bodyMedium)
            if (links.isNotEmpty() || message != null || r.amapUrl != null || templates.isNotEmpty() || sights.isNotEmpty() || eats.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                links.forEach { l ->
                    AssistChip(onClick = { actions.openTaxi(l.type, l.id) }, label = { Text(l.label) },
                        leadingIcon = { Icon(Icons.Filled.LocalTaxi, null, Modifier.size(18.dp)) })
                }
                eats.forEach { e ->
                    AssistChip(onClick = { actions.openEat(e.id) }, label = { Text(e.nameKo.substringBefore("(")) },
                        leadingIcon = { Icon(Icons.Filled.Restaurant, null, Modifier.size(18.dp)) })
                }
                sights.forEach { sg ->
                    AssistChip(onClick = { actions.openSight(sg.id) }, label = { Text(sg.nameKo) },
                        leadingIcon = { Icon(Icons.Filled.Landscape, null, Modifier.size(18.dp)) })
                }
                templates.forEach { tl ->
                    AssistChip(onClick = { actions.openTemplates(tl.templateId, tl.id) }, label = { Text("위챗: ${tl.label}") },
                        leadingIcon = { Icon(Icons.Filled.EditNote, null, Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer))
                }
                message?.let { m ->
                    AssistChip(onClick = { actions.openShow(m.id) }, label = { Text("매니저에게 보여주기") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp)) })
                }
                r.amapUrl?.let { u ->
                    AssistChip(onClick = { External.openUrl(ctx, u) }, label = { Text("지도") },
                        leadingIcon = { Icon(Icons.Filled.Place, null, Modifier.size(18.dp)) })
                }
            }
        }
    }
}
