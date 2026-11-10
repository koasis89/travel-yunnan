package kr.koasis.travelyunnan.ui.sights

import kr.koasis.travelyunnan.ui.common.KoGloss
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
                Text("요금·운영 시간은 참고용이며 바뀔 수 있다. 사진은 대부분 Flickr 의 CC 라이선스 사진이고 일부는 여행자가 직접 찍은 사진이다. 각 관광지 화면에 촬영자와 라이선스를 적었다.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
fun SightScreen(vm: AppViewModel, data: TripData, id: String) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val docs by vm.docs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var blogBusy by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<kr.koasis.travelyunnan.data.json.Blog?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val b = pending ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        blogBusy = b.id
        scope.launch {
            val d = vm.addDocs(listOf(uri), null).firstOrNull()
            blogBusy = null
            if (d == null) { android.widget.Toast.makeText(ctx, "PDF 저장 실패", android.widget.Toast.LENGTH_SHORT).show(); return@launch }
            vm.updateDoc(d.copy(title = b.title, kind = "블로그", note = "${b.blog} · ${b.date}\n${b.url}"))
            actions.openDoc(d.id, false)
        }
    }
    val s = data.sight(id)
    if (s == null) { Column { AppTopBar("관광지", onBack = actions.back); Text("찾을 수 없습니다", Modifier.padding(16.dp)) }; return }
    val place = data.place(s.placeId)
    val rows = remember(s) { data.schedule.filter { r -> r.dayId in s.dayIds && s.keywords.any { k -> "${r.from} ${r.via} ${r.to} ${r.tip}".contains(k) } } }
    Column(Modifier.fillMaxSize()) {
        AppTopBar(s.nameKo, onBack = actions.back)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                SightImage(s, 240)
                s.credit?.let { c ->
                    Text("사진: ${c.title} · ${c.author} · ${c.license}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).clickable(enabled = c.url.isNotBlank()) { External.openUrl(ctx, c.url) })
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
            val blogs = data.blogsFor(s.id)
            if (blogs.isNotEmpty()) {
                item { SectionTitle("블로그 후기 (${blogs.size})") }
                items(blogs, key = { "blog-" + it.id }) { b ->
                    val d = kr.koasis.travelyunnan.domain.DocRules.docForBlog(b, docs)
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(b.title, style = MaterialTheme.typography.titleSmall)
                            Text("${b.blog} · ${b.date}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(b.summary, style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (d != null) Button(onClick = { actions.openDoc(d.id, false) }) { Icon(Icons.Filled.Description, null); Spacer(Modifier.width(6.dp)); Text("PDF 보기") }
                                else OutlinedButton(onClick = { pending = b; picker.launch(arrayOf("application/pdf", "image/*")) }, enabled = blogBusy == null) {
                                    Icon(Icons.Filled.NoteAdd, null); Spacer(Modifier.width(6.dp)); Text(if (blogBusy == b.id) "넣는 중…" else "PDF 넣기")
                                }
                                OutlinedButton(onClick = { External.openUrl(ctx, b.url) }) { Icon(Icons.Filled.OpenInNew, null); Spacer(Modifier.width(6.dp)); Text(if (b.search) "블로그 검색" else "원문 열기") }
                            }
                        }
                    }
                }
                item {
                    Text("PDF 는 'PDF 넣기'로 한 번 고르면 이 휴대폰의 문서함(분류: 블로그)에 저장되어 인터넷 없이 볼 수 있다. 문서함에 이미 넣은 PDF 는 제목으로 찾아 바로 연다. 글의 저작권은 블로그 글쓴이에게 있다.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(16.dp))
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
    val all = remember { kr.koasis.travelyunnan.domain.AmapLinks.rows(data) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    val rows = all.filter { group == null || it.group == group }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("지도 링크 모음", onBack = actions.back) {
            IconButton(onClick = { External.copy(ctx, rows.joinToString("\n\n") { kr.koasis.travelyunnan.domain.AmapLinks.text(it) }, "${rows.size}곳 복사 완료") }) {
                Icon(Icons.Filled.ContentCopy, "전체 복사")
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text("고덕지도(Amap) 앱을 깔고 누른다. '정확'은 고덕지도에서 공유받은 위치, 나머지는 이름·주소 검색 링크다. 복사 버튼은 이름·주소·링크를 붙여넣기용 글로 복사한다.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            item(key = "groups") {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = group == null, onClick = { group = null }, label = { Text("전체 ${all.size}") })
                    all.map { it.group }.distinct().forEach { g ->
                        FilterChip(selected = group == g, onClick = { group = g }, label = { Text("$g ${all.count { it.group == g }}") })
                    }
                }
            }
            rows.map { it.group }.distinct().forEach { g ->
                item(key = "g-$g") { SectionTitle("$g (${rows.count { it.group == g }})") }
                items(rows.filter { it.group == g }, key = { it.url }) { r ->
                    ListItem(
                        headlineContent = { Text(r.nameKo + if (r.exact) "  · 정확" else "") },
                        supportingContent = { Column { Text(listOf(r.nameZh, r.addrZh).filter { it.isNotBlank() }.joinToString("\n")); KoGloss(r.addrZh) } },
                        trailingContent = { IconButton(onClick = { External.copy(ctx, kr.koasis.travelyunnan.domain.AmapLinks.text(r), "이름·주소·링크 복사 완료") }) { Icon(Icons.Filled.ContentCopy, "복사") } },
                        modifier = Modifier.clickable { External.openUrl(ctx, r.url) },
                    )
                }
            }
        }
    }
}
