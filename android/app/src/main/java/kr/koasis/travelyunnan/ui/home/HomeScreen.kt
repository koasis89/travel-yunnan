package kr.koasis.travelyunnan.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.json.Day
import kr.koasis.travelyunnan.data.json.Schedule
import kr.koasis.travelyunnan.domain.*
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.common.BrandHeader
import kr.koasis.travelyunnan.ui.common.InfoBanner
import kr.koasis.travelyunnan.ui.common.SectionTitle
import kr.koasis.travelyunnan.ui.nav.LocalActions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(vm: AppViewModel, data: TripData) {
    val now by vm.now.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    HomeContent(data, now, overrideActive = settings.overrideDateTime != null)
}

@Composable
fun HomeContent(data: TripData, now: LocalDateTime, overrideActive: Boolean = false) {
    val actions = LocalActions.current
    val phase = remember(now, data) { DayResolver.resolve(now, data.days) }
    val day = remember(phase) { DayResolver.focusDay(phase, data.days) }
    val rows = remember(day) { data.scheduleOf(day.id) }
    val nowState = remember(now, rows, phase) {
        if (phase is TripPhase.OnTrip) CurrentScheduleFinder.find(rows, now.toLocalTime())
        else NowState(null, rows.firstOrNull())
    }
    val nextMove = remember(now) { NextMoveFinder.find(now, data.days) { data.scheduleOf(it) } }
    val suggestCat = PhraseSuggester.suggest(nowState.current ?: nowState.next)
    val catTitle = data.phraseCategories.firstOrNull { it.id == suggestCat }?.title ?: "회화"
    val lodging = data.lodging(day.lodgingId)
    val meal = remember(day, now) { TaxiCards.mealFor(data, day.id, now.hour) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        // 브랜드 헤더
        BrandHeader(
            statusLine = when (phase) {
                is TripPhase.BeforeTrip -> "출발까지 D-${phase.daysLeft} · 12/16(수) 쿤밍 도착"
                is TripPhase.OnTrip -> "${day.id} · ${day.dateLabel} · ${day.route}"
                TripPhase.AfterTrip -> "여행이 끝났다. 수고했다!"
            },
            subLine = "현지 시각 " + now.format(DateTimeFormatter.ofPattern("M/d HH:mm")) + " · 한국보다 1시간 늦다",
        )
        Spacer(Modifier.height(12.dp))
        if (overrideActive) Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            InfoBanner("날짜 지정 모드다. 설정에서 해제하면 실제 시각으로 돌아간다.", Icons.Filled.Schedule)
        }

        DayCard(day, isToday = phase is TripPhase.OnTrip) { actions.openSchedule(day.id) }

        // 지금 일정
        SectionTitle(if (phase is TripPhase.OnTrip) "지금 일정" else "${day.id} 첫 일정")
        NowCard(nowState.current, nowState.next) { actions.openSchedule(day.id) }

        // 다음 이동
        nextMove?.let { m ->
            SectionTitle("다음 이동")
            Card(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable { actions.openSchedule(m.day.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (m.row.transport.contains("열차")) Icons.Filled.Train else Icons.Filled.DirectionsCar, null, Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${m.day.id} ${m.row.timeLabel} · ${m.row.transport}", style = MaterialTheme.typography.titleMedium)
                        Text("${m.row.from} → ${m.row.to}", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatLeft(m.minutesLeft), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }

        kr.koasis.travelyunnan.ui.sights.DaySightsRow(data, day.id)

        // 바로가기
        SectionTitle("바로가기")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Shortcut(
                    Modifier.weight(1f), Icons.Filled.LocalTaxi,
                    if (lodging != null) "숙소 택시 카드" else "공항 택시 카드",
                    lodging?.nameKo?.substringBefore(" (")?.substringBefore("(") ?: "쿤밍 창수이 공항",
                    MaterialTheme.colorScheme.primaryContainer,
                ) {
                    if (lodging != null) actions.openTaxi(TargetType.lodging, lodging.id)
                    else actions.openTaxi(TargetType.place, "kmg_airport")
                }
                Shortcut(
                    Modifier.weight(1f), Icons.Filled.Restaurant, "${TaxiCards.slotFor(now.hour)} 식당 카드",
                    meal?.koName()?.substringBefore("(") ?: "선택지 없음",
                    MaterialTheme.colorScheme.tertiaryContainer,
                ) { if (meal != null) actions.openTaxi(TargetType.meal, meal.id) else actions.openMeals(day.id) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Shortcut(Modifier.weight(1f), Icons.Filled.Translate, "지금 필요한 회화", catTitle,
                    MaterialTheme.colorScheme.secondaryContainer) { actions.openPhrases(suggestCat) }
                Shortcut(Modifier.weight(1f), Icons.Filled.Map, "${day.id} 지도",
                    data.mapsOf(day.id).firstOrNull()?.title ?: "전체 지도",
                    MaterialTheme.colorScheme.surfaceVariant) { actions.openMaps(day.id) }
            }
        }

        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { actions.openMeals(day.id) }) { Text("${day.id} 식사 선택지") }
            TextButton(onClick = { actions.openLodgings(day.lodgingId) }) { Text("숙소 정보") }
            TextButton(onClick = actions.openTransport) { Text("교통·알림") }
        }

        // 오늘의 주의
        val cautions = remember(day) {
            rows.filter { it.tip.contains("★") }.map { "${it.timeLabel}  ${it.tip}" } +
                data.notes.filter { day.id in it.dayIds }.map { "${it.title}: ${it.body}" }
        }
        if (cautions.isNotEmpty()) {
            SectionTitle("${day.id} 주의")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cautions.take(5).forEach { InfoBanner(it, Icons.Filled.Warning, MaterialTheme.colorScheme.tertiaryContainer) }
            }
        }

        // 여행 전: 8일 개요
        if (phase !is TripPhase.OnTrip) {
            SectionTitle("일정 한눈에")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                data.days.forEach { d ->
                    Row(Modifier.fillMaxWidth().clickable { actions.openSchedule(d.id) }.padding(vertical = 6.dp)) {
                        Text(d.id, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(40.dp))
                        Text(d.dateLabel, modifier = Modifier.width(92.dp), style = MaterialTheme.typography.bodyMedium)
                        Text(d.route, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCard(day: Day, isToday: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text((if (isToday) "오늘 " else "") + "${day.id} ${day.dateLabel} · ${day.route}", style = MaterialTheme.typography.titleMedium)
            Text(day.summary, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Row {
                Icon(Icons.Filled.Thermostat, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text("${day.region} ${day.altitude ?: ""} · 낮 ${day.tempDay?.removePrefix("약 ")} / 밤 ${day.tempNight?.removePrefix("약 ")}",
                    style = MaterialTheme.typography.bodyMedium)
            }
            day.outfit?.let {
                Row {
                    Icon(Icons.Filled.Checkroom, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row {
                Icon(Icons.Filled.Hotel, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(day.lodgingLabel.ifBlank { "숙박 없음(귀국)" }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun NowCard(current: Schedule?, next: Schedule?, onClick: () -> Unit) {
    Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (current != null) {
                Text(current.timeLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(listOf(current.from, current.to).filter { it.isNotBlank() && it != "-" }.joinToString(" → "),
                    style = MaterialTheme.typography.titleLarge)
                if (current.transport.isNotBlank() && current.transport != "-")
                    Text("${current.transport} · ${current.duration}", style = MaterialTheme.typography.bodyMedium)
                if (current.tip.isNotBlank()) Text(current.tip, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            } else {
                Text("지금 진행 중인 일정이 없다", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            next?.let {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                val place = listOf(it.to, it.via, it.from).firstOrNull { s -> s.isNotBlank() && s != "-" } ?: it.tip
                Text("다음  ${it.timeLabel}  $place", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Shortcut(modifier: Modifier, icon: ImageVector, title: String, sub: String, color: Color, onClick: () -> Unit) {
    Card(
        modifier.height(118.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
