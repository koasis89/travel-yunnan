package kr.koasis.travelyunnan.ui.eats

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.json.Restaurant
import kr.koasis.travelyunnan.domain.DayResolver
import kr.koasis.travelyunnan.domain.TargetType
import kr.koasis.travelyunnan.domain.TaxiCards
import kr.koasis.travelyunnan.domain.TripPhase
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.common.*
import kr.koasis.travelyunnan.ui.map.rememberAssetImage
import kr.koasis.travelyunnan.ui.nav.LocalActions
import kr.koasis.travelyunnan.util.External

@Composable
private fun Thumb(path: String?, size: Int, modifier: Modifier = Modifier) {
    val img = path?.let { rememberAssetImage(it) }
    Box(modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Filled.Restaurant, null)
    }
}

@Composable
fun EatsScreen(vm: AppViewModel, data: TripData, dayArg: String?) {
    val actions = LocalActions.current
    val now by vm.now.collectAsStateWithLifecycle()
    val todayId = (DayResolver.resolve(now, data.days) as? TripPhase.OnTrip)?.day?.id
    var day by rememberSaveable(dayArg) { mutableStateOf(dayArg) }
    val list = remember(day) { day?.let { d -> data.restaurants.filter { d in it.dayIds } } ?: data.restaurants }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("맛집 안내", onBack = actions.back)
        DayChips(data.days, day, { day = it }, allLabel = "전체", todayId = todayId)
        val ctx = LocalContext.current
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                val l = data.lodging(data.day(day ?: todayId)?.lodgingId) ?: data.lodgings.firstOrNull { it.phones.isNotEmpty() }
                val ph = l?.phones?.firstOrNull()
                data.restaurantTips.forEach { t ->
                    Card(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(t, style = MaterialTheme.typography.bodyMedium)
                            if (ph != null) SmallAction(Icons.Filled.ContentCopy, "숙소 번호 복사 · $ph") { External.copy(ctx, ph, "숙소 전화번호를 복사했다") }
                        }
                    }
                }
            }
            list.map { it.region }.distinct().forEach { g ->
                item(key = "g-$g") { SectionTitle(g) }
                items(list.filter { it.region == g }, key = { it.id }) { r ->
                    ListItem(
                        leadingContent = { Thumb(r.image, 72) },
                        headlineContent = { Text(r.nameKo) },
                        supportingContent = {
                            val g = r.ratings.firstOrNull()
                            Text((if (g?.score != null) "★%.1f".format(g.score) + (g.reviews?.let { "($it) " } ?: " ") else "") + "${r.nameZh}\n${r.kind} · ${r.perPerson}\n${r.slots.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
                        },
                        modifier = Modifier.clickable { actions.openEat(r.id) },
                    )
                }
            }
            item {
                Text("사진은 대표 음식 참고 사진이며 그 식당 사진이 아니다. 메뉴는 대표 메뉴 예시로 매장마다 다르다. 1인 금액은 참고값이다.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EatScreen(data: TripData, id: String) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val r = data.restaurant(id)
    var board by remember { mutableStateOf(false) }
    val sel = remember(id) { mutableStateMapOf<String, Int>() }
    if (r == null) { Column { AppTopBar("맛집", onBack = actions.back); Text("찾을 수 없다", Modifier.padding(16.dp)) }; return }
    val taxiOpt = r.optionIds.mapNotNull { data.mealOption(it)?.second }.firstOrNull { TaxiCards.isTaxiable(it) }
    val taxiPlace = if (taxiOpt == null) data.place(r.placeId) else null
    if (board) {
        ShowCardScreen(
            lines = listOf(ShowLine(r.nameZh, ShowStyle.Small)) + r.menu.flatMap { listOf(ShowLine(it.zh, ShowStyle.Name), ShowLine(it.ko, ShowStyle.Small)) },
            onClose = { board = false }, header = "종업원에게 보여주세요(가리키며 주문)",
        ) {}
        return
    }
    val pickedList = sel.entries.filter { it.value > 0 }.map { it.key to it.value }
    Column(Modifier.fillMaxSize()) {
        AppTopBar(r.nameKo, onBack = actions.back)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                val img = r.image?.let { rememberAssetImage(it) }
                if (img != null) Image(img, r.nameKo, Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Crop)
                Text(listOfNotNull(r.imageNote, r.credit?.let { listOf(it.title, it.author, it.license).filter { x -> x.isNotBlank() }.joinToString(" · ") }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(r.nameKo, style = MaterialTheme.typography.titleLarge)
                    Text(r.nameZh, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(r.summary, style = MaterialTheme.typography.bodyLarge)
                    Text("${r.kind} · 1인 ${r.perPerson}(참고) · ${r.slots.joinToString(" · ")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        taxiOpt?.let { o -> SmallAction(Icons.Filled.LocalTaxi, "택시 카드") { actions.openTaxi(TargetType.meal, o.id) } }
                        taxiPlace?.let { p -> SmallAction(Icons.Filled.LocalTaxi, "택시 카드") { actions.openTaxi(TargetType.place, p.id) } }
                        r.amapUrl?.let { u -> SmallAction(Icons.Filled.Map, "지도") { External.openUrl(ctx, u) } }
                        SmallAction(Icons.Filled.Fullscreen, "메뉴판 크게") { board = true }
                    }
                }
                if (r.ratings.isNotEmpty()) {
                    SectionTitle("평점·여행자 인기 메뉴 (씨트립)")
                    r.ratings.forEach { g ->
                        Card(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(listOfNotNull(g.score?.let { "★ %.1f".format(it) }, g.reviews?.let { "리뷰 ${it}건" }, "1인 ${g.price}", g.label?.let { l -> kr.koasis.travelyunnan.data.KoGlossDict.ko(l).let { k -> if (k.isNotEmpty()) "$l ($k)" else l } }).joinToString("  ·  "),
                                    style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color(0xFFE08A00))
                                Text("${g.name} · ${g.addr}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                listOf(g.name, g.addr).map { kr.koasis.travelyunnan.data.KoGlossDict.ko(it) }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                                g.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    g.popular.forEach { p ->
                                        val n = sel[p.zh] ?: 0
                                        FilterChip(n > 0, onClick = { sel[p.zh] = (n + 1) % 4 }, label = { Text("${p.zh} ${p.ko}" + if (n > 0) " ×$n" else "") })
                                    }
                                }
                            }
                        }
                    }
                    Text("${data.ratingsNote} 인기 메뉴를 누르면 주문 목록에 담긴다.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                SectionTitle("메뉴판 (누를 때마다 1→2→3→빼기, 여러 개 선택)")
            }
            items(r.menu, key = { it.zh }) { m ->
                val n = sel[m.zh] ?: 0
                Row(Modifier.fillMaxWidth().clickable { sel[m.zh] = (n + 1) % 4 }
                    .background(if (n > 0) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Thumb(m.image, 72)
                    Column(Modifier.weight(1f)) {
                        Text(m.zh, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(m.ko, style = MaterialTheme.typography.bodyMedium)
                        if (m.desc.isNotBlank()) Text(m.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilterChip(n > 0, onClick = { sel[m.zh] = (n + 1) % 4 }, label = { Text(if (n > 0) "×$n" else "담기") })
                }
            }
            if (r.tips.isNotEmpty()) {
                item { SectionTitle("팁·주의") }
                items(r.tips) { t ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Icon(Icons.Filled.Lightbulb, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.width(8.dp))
                        Text(t, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Text("메뉴는 대표 메뉴 예시이며 매장 메뉴판과 다를 수 있다. 실제 메뉴판은 사진을 찍어 문서함에 넣어 두면 다시 볼 수 있다.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(16.dp))
            }
        }
        Button(onClick = { actions.openMenu(if (pickedList.isEmpty()) null else kr.koasis.travelyunnan.ui.meal.encodeFoods(pickedList)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Icon(Icons.Filled.Chat, null); Spacer(Modifier.width(6.dp))
            Text(if (pickedList.isEmpty()) "주문 문장 만들기(음료·가리키기)" else "고른 ${pickedList.sumOf { it.second }}개로 주문 문장 만들기")
        }
    }
}
