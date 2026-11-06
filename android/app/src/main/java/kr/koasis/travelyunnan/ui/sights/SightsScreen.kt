package kr.koasis.travelyunnan.ui.sights

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.json.Sight
import kr.koasis.travelyunnan.domain.DayResolver
import kr.koasis.travelyunnan.domain.TargetType
import kr.koasis.travelyunnan.domain.TripPhase
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.common.AppTopBar
import kr.koasis.travelyunnan.ui.common.DayChips
import kr.koasis.travelyunnan.ui.common.SectionTitle
import kr.koasis.travelyunnan.ui.map.rememberAssetImage
import kr.koasis.travelyunnan.ui.nav.LocalActions
import kr.koasis.travelyunnan.util.External

@Composable
private fun SightImage(s: Sight, height: Int, modifier: Modifier = Modifier) {
    val img = s.image?.let { rememberAssetImage(it) }
    Box(modifier.fillMaxWidth().height(height.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, s.nameKo, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.Landscape, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun SightCard(s: Sight, compact: Boolean = false, onClick: () -> Unit) {
    Card(Modifier.then(if (compact) Modifier.width(220.dp) else Modifier.fillMaxWidth()).clickable(onClick = onClick)) {
        SightImage(s, if (compact) 120 else 170)
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("${s.nameKo}  ${s.nameZh}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!compact) Text(s.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${s.dayIds.joinToString("·")} · ${s.duration}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun SightsScreen(vm: AppViewModel, data: TripData, dayArg: String?) {
    val actions = LocalActions.current
    val now by vm.now.collectAsStateWithLifecycle()
    val todayId = (DayResolver.resolve(now, data.days) as? TripPhase.OnTrip)?.day?.id
    var day by rememberSaveable(dayArg) { mutableStateOf(dayArg) }
    val list = remember(day) { day?.let { data.sightsOf(it) } ?: data.sights }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("관광지 안내", onBack = actions.back)
        DayChips(data.days, day, { day = it }, allLabel = "전체", todayId = todayId)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            list.map { it.region }.distinct().forEach { region ->
                item(key = "r-$region") { SectionTitle(region) }
                items(list.filter { it.region == region }, key = { it.id }) { s ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { SightCard(s) { actions.openSight(s.id) } }
                }
            }
            item {
                Text("요금·운영 시간은 참고용이며 바뀔 수 있다. 사진은 Flickr 의 CC 라이선스 사진이며, 각 관광지 화면에 촬영자와 라이선스를 적었다.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
fun SightScreen(data: TripData, id: String) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val s = data.sight(id)
    if (s == null) { Column { AppTopBar("관광지", onBack = actions.back); Text("찾을 수 없다", Modifier.padding(16.dp)) }; return }
    val place = data.place(s.placeId)
    val rows = remember(s) { data.schedule.filter { r -> r.dayId in s.dayIds && s.keywords.any { k -> "${r.from} ${r.via} ${r.to} ${r.tip}".contains(k) } } }
    Column(Modifier.fillMaxSize()) {
        AppTopBar(s.nameKo, onBack = actions.back)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                SightImage(s, 240)
                s.credit?.let { c ->
                    Text("사진: ${c.title} · ${c.author} · ${c.license}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).clickable { External.openUrl(ctx, c.url) })
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.nameKo, style = MaterialTheme.typography.titleLarge)
                    Text(s.nameZh, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(s.summary, style = MaterialTheme.typography.bodyLarge)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            InfoLine(Icons.Filled.Schedule, s.hours)
                            InfoLine(Icons.Filled.ConfirmationNumber, s.fee)
                            InfoLine(Icons.Filled.Timer, "머무는 시간 ${s.duration}")
                        }
                    }
                    if (place != null) Button(onClick = { actions.openTaxi(TargetType.place, place.id) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.LocalTaxi, null); Spacer(Modifier.width(6.dp)); Text("택시 카드 · ${place.nameZh}")
                    }
                    (s.amapUrl ?: place?.amapUrl)?.let { u ->
                        OutlinedButton(onClick = { External.openUrl(ctx, u) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Map, null); Spacer(Modifier.width(6.dp)); Text("지도에서 보기(고덕지도)")
                        }
                    }
                }
            }
            if (s.highlights.isNotEmpty()) {
                item { SectionTitle("볼거리") }
                items(s.highlights) { h -> Bullet(Icons.Filled.AutoAwesome, h) }
            }
            if (s.tips.isNotEmpty()) {
                item { SectionTitle("팁·주의") }
                items(s.tips) { h -> Bullet(Icons.Filled.Lightbulb, h) }
            }
            if (rows.isNotEmpty()) {
                item { SectionTitle("관련 일정") }
                items(rows, key = { it.id }) { r ->
                    Row(Modifier.fillMaxWidth().clickable { actions.openSchedule(r.dayId) }.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text("${r.dayId} ${r.timeLabel}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(118.dp))
                        Text(listOf(r.from, r.to).filter { it.isNotBlank() && it != "-" }.joinToString(" → "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(text, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun Bullet(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Icon(icon, null, Modifier.size(18.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 홈 화면용: 그날 관광지 가로 목록 */
@Composable
fun DaySightsRow(data: TripData, dayId: String) {
    val actions = LocalActions.current
    val list = remember(dayId) { data.sightsOf(dayId) }
    if (list.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle("$dayId 관광지", Modifier.weight(1f))
        TextButton(onClick = { actions.openSights(dayId) }) { Text("모두 보기") }
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list, key = { it.id }) { s -> SightCard(s, compact = true) { actions.openSight(s.id) } }
    }
}


/** 지도 링크 모음: 숙소·관광지·식당 고덕지도 링크(웹 #/amap 과 같은 목록). 누르면 열고, 길게 누르면 붙여넣기용 글을 복사 */
@Composable
fun AmapLinksScreen(data: TripData) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val rows = remember { kr.koasis.travelyunnan.domain.AmapLinks.rows(data) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("지도 링크 모음", onBack = actions.back) {
            IconButton(onClick = { External.copy(ctx, rows.joinToString("\n\n") { kr.koasis.travelyunnan.domain.AmapLinks.text(it) }, "${rows.size}곳을 복사했다") }) {
                Icon(Icons.Filled.ContentCopy, "전체 복사")
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text("고덕지도(Amap) 앱을 깔고 누른다. '정확'은 고덕지도에서 공유받은 위치, 나머지는 이름·주소 검색 링크다. 복사 버튼은 이름·주소·링크를 붙여넣기용 글로 복사한다.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            rows.map { it.group }.distinct().forEach { g ->
                item(key = "g-$g") { SectionTitle("$g (${rows.count { it.group == g }})") }
                items(rows.filter { it.group == g }, key = { it.url }) { r ->
                    ListItem(
                        headlineContent = { Text(r.nameKo + if (r.exact) "  · 정확" else "") },
                        supportingContent = { Text(listOf(r.nameZh, r.addrZh).filter { it.isNotBlank() }.joinToString("\n")) },
                        trailingContent = { IconButton(onClick = { External.copy(ctx, kr.koasis.travelyunnan.domain.AmapLinks.text(r), "이름·주소·링크를 복사했다") }) { Icon(Icons.Filled.ContentCopy, "복사") } },
                        modifier = Modifier.clickable { External.openUrl(ctx, r.url) },
                    )
                }
            }
        }
    }
}
