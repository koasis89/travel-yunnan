package kr.koasis.travelyunnan.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.koasis.travelyunnan.BuildConfigInfo
import kr.koasis.travelyunnan.data.AppSettings
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.domain.tripNow
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.common.AppTopBar
import kr.koasis.travelyunnan.ui.common.DayChips
import kr.koasis.travelyunnan.ui.common.SectionTitle
import kr.koasis.travelyunnan.ui.nav.LocalActions
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private data class MoreItem(val icon: ImageVector, val title: String, val sub: String, val enabled: Boolean, val onClick: () -> Unit = {})

@Composable
fun MoreScreen() {
    val actions = LocalActions.current
    val items = listOf(
        MoreItem(Icons.Filled.Hotel, "숙소", "6곳 카드·체크인 문장·할 일", true) { actions.openLodgings(null) },
        MoreItem(Icons.Filled.Restaurant, "맛집·식사", "끼니별 선택지, 택시 카드, 주문 문장", true) { actions.openMeals(null) },
        MoreItem(Icons.Filled.MenuBook, "음식 도감·메뉴 포인팅", "음식 사진·설명, 주문 문장 만들기", true, actions.openFoods),
        MoreItem(Icons.Filled.Train, "교통·열차·알림", "C118·C640 열차, 픽업, 빵차, 일정 알림", true, actions.openTransport),
        MoreItem(Icons.AutoMirrored.Filled.ReceiptLong, "경비", "예산·지출 기록·정산", true, actions.openExpense),
        MoreItem(Icons.Filled.Checklist, "준비물·날씨", "체크리스트와 지역별 옷차림", true, actions.openPacking),
        MoreItem(Icons.Filled.Warning, "유의사항", "고산병·열차·빵차·배달", true, actions.openNotes),
        MoreItem(Icons.Filled.Lock, "긴급·여권", "연락처·고산병·여권(생체인증 잠금)", true, actions.openEmergency),
        MoreItem(Icons.AutoMirrored.Filled.Chat, "위챗 메시지 템플릿", "빵차·픽업·배달 요청문, 변수 치환", true) { actions.openTemplates(null) },
        MoreItem(Icons.Filled.Pin, "숫자 카드", "금액을 크게, 중국어 읽기·흥정 문장", true, actions.openNumber),
        MoreItem(Icons.Filled.PhotoCamera, "사진 일기", "일차별 사진·메모", true) { actions.openDiary(null) },
        MoreItem(Icons.Filled.Settings, "설정", "큰 글씨, 음성, 날짜 지정 모드", true, actions.openSettings),
        MoreItem(Icons.Filled.Info, "데이터·출처", "데이터 버전, 사진 출처", true, actions.openCredits),
    )
    Column(Modifier.fillMaxSize()) {
        AppTopBar("더보기")
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { it ->
                Card(
                    Modifier.fillMaxWidth().alpha(if (it.enabled) 1f else 0.5f).clickable(enabled = it.enabled, onClick = it.onClick),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                            Icon(it.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(it.title, style = MaterialTheme.typography.titleMedium)
                            Text(if (it.enabled) it.sub else "${it.sub} · 다음 업데이트", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, data: TripData) {
    val s by vm.settings.collectAsStateWithLifecycle()
    SettingsContent(data, s, vm::setBigFont, vm::setTtsSlow, vm::setTtsRepeat, vm::setOverride)
}

@Composable
fun SettingsContent(
    data: TripData, s: AppSettings,
    setBigFont: (Boolean) -> Unit, setSlow: (Boolean) -> Unit, setRepeat: (Boolean) -> Unit, setOverride: (String?) -> Unit,
) {
    val actions = LocalActions.current
    val current = tripNow(s.overrideDateTime)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("설정", onBack = actions.back)
        SwitchRow("큰 글씨", "모든 화면 글자를 1.3배로", s.bigFont, setBigFont)
        SwitchRow("느리게 읽기", "중국어 음성 0.7배속", s.ttsSlow, setSlow)
        SwitchRow("3회 반복", "음성을 세 번 이어서 재생", s.ttsRepeat3, setRepeat)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle("날짜 지정 모드")
        Text("여행 전에 특정 날짜·시각의 화면을 미리 본다. 현지 시각 기준이다.",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
        SwitchRow("사용", "현재: " + current.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), s.overrideDateTime != null) { on ->
            setOverride(if (on) "${data.days.first().date}T09:00" else null)
        }
        if (s.overrideDateTime != null) {
            val selectedDay = data.days.firstOrNull { it.date == current.toLocalDate().toString() }?.id
            DayChips(data.days, selectedDay, { id ->
                data.day(id)?.let { setOverride(LocalDate.parse(it.date).atTime(current.toLocalTime()).toString()) }
            })
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("시각", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { setOverride(current.minusHours(1).toString()) }) { Text("-1시간") }
                Text(current.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 12.dp))
                OutlinedButton(onClick = { setOverride(current.plusHours(1).toString()) }) { Text("+1시간") }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 15, 30, 45).forEach { m ->
                    FilterChip(current.minute == m, onClick = { setOverride(current.withMinute(m).toString()) }, label = { Text(":%02d".format(m)) })
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        trailingContent = { Switch(checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
fun CreditsScreen(data: TripData) {
    val actions = LocalActions.current
    Column(Modifier.fillMaxSize()) {
        AppTopBar("데이터·출처", onBack = actions.back)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("데이터 버전 ${data.version} · 앱 ${BuildConfigInfo.VERSION}", style = MaterialTheme.typography.titleMedium)
                Text("일정·숙소·맛집·준비물은 가족 여행 엑셀 일정표, 회화는 중국어·병음 정리 파일에서 변환했다. 지도는 엑셀에 수록된 개략도다.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text("음식 사진 출처 (Wikimedia Commons)", style = MaterialTheme.typography.titleMedium)
            }
            items(data.foods.filter { it.image != null }) { f ->
                Text("${f.nameKo}(${f.nameZh}) — ${f.source} [${f.imageKind}]", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
