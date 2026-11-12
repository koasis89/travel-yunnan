package kr.koasis.travelyunnan.ui.meal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.json.MilkTeaDrink
import kr.koasis.travelyunnan.ui.common.*
import kr.koasis.travelyunnan.ui.nav.LocalActions
import kr.koasis.travelyunnan.util.External

/** 주문 문장(웹 milkTeaOrder 와 같다): "你好，我要一杯伯牙绝弦，少糖，去冰。" */
/** 맞춤 옵션(opt)이 있으면 옵션을 그대로 붙이고 당도·얼음 선택은 쓰지 않는다(웹 milkTeaOrder 와 같다) */
fun milkTeaOrder(zh: String, sugar: String, ice: String, opt: String = ""): String =
    if (opt.isNotBlank()) "你好，我要一杯$zh，$opt。"
    else "你好，我要一杯$zh" + (if (sugar.isNotBlank()) "，$sugar" else "") + (if (ice.isNotBlank()) "，$ice" else "") + "。"

private fun parseColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFFC62828))

/** 더보기 → 밀크티 종류(웹 pages/MilkTea.tsx): 브랜드별 추천(맞춤 옵션 포함) + 당도·얼음 골라 주문 문장 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MilkTeaScreen(data: TripData) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val mt = data.milktea
    var brand by rememberSaveable { mutableStateOf(mt.items.firstOrNull()?.id.orEmpty()) }
    var sugar by rememberSaveable { mutableStateOf("") }
    var ice by rememberSaveable { mutableStateOf("") }
    var show by remember { mutableStateOf<MilkTeaDrink?>(null) }
    val b = mt.items.firstOrNull { it.id == brand } ?: mt.items.firstOrNull()
    fun ko(pairs: List<List<String>>, z: String) = pairs.firstOrNull { it.firstOrNull() == z }?.getOrNull(1).orEmpty()

    show?.let { d ->
        val s = milkTeaOrder(d.zh, sugar, ice, d.opt)
        val sub = "${d.koRead} 한 잔" + if (d.opt.isNotBlank()) " · 맞춤 옵션 그대로" else (if (sugar.isNotBlank()) " · ${ko(mt.sugar, sugar)}" else "") + (if (ice.isNotBlank()) " · ${ko(mt.ice, ice)}" else "")
        ShowCardScreen(lines = listOf(ShowLine(s, ShowStyle.Phrase), ShowLine(sub, ShowStyle.Sub)), onClose = { show = null }, header = "점원에게 보여주세요") {
            SmallAction(Icons.Filled.ContentCopy, "복사") { External.copy(ctx, s) }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("밀크티 종류", onBack = actions.back)
        if (b == null) { Text("자료 없음", Modifier.padding(16.dp)); return@Column }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(mt.items, key = { it.id }) { x -> FilterChip(brand == x.id, onClick = { brand = x.id }, label = { Text("${x.nameZh} ${x.nameKo}") }) }
        }
        val col = parseColor(b.color)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(6.dp).fillMaxHeight().background(col))
                        Column(Modifier.padding(14.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                                Text(b.nameZh, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Text(b.nameKo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterVertically))
                                Text(b.nameEn, color = Color.White, style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.align(Alignment.CenterVertically).clip(RoundedCornerShape(8.dp)).background(col).padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                            Text("${b.pinyin} · 추천 ${b.drinks.size}가지", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (b.note.isNotBlank()) Text(b.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            if (b.maps.isNotEmpty()) {
                                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Place, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                                    Text("매장 찾기 (고덕지도)", style = MaterialTheme.typography.titleSmall)
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    b.maps.forEach { x -> AssistChip(onClick = { External.openUrl(ctx, x.url) }, label = { Text("${x.ko} · ${x.days}") }) }
                                }
                                Text("브랜드 이름으로 그 동네를 검색한다. 결과에서 가까운 매장을 고르고, 없으면 다른 브랜드를 찾는다.",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
            items(b.drinks.withIndex().toList(), key = { it.value.id }) { (i, d) ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(col), contentAlignment = Alignment.Center) { Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.zh, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("${d.pinyin} · ${d.koRead}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (d.tag.isNotBlank()) Text(d.tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                            if (d.opt.isNotBlank()) Text(d.opt, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                            Text(d.desc, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                        FilledTonalButton(onClick = { show = d }) { Icon(Icons.Filled.Fullscreen, null); Spacer(Modifier.width(4.dp)); Text("주문") }
                    }
                }
            }
            item { SectionTitle("주문할 때 당도·얼음 (맞춤 주문 메뉴는 옵션 그대로)") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    mt.sugar.forEach { p -> val z = p.getOrElse(0) { "" }; FilterChip(sugar == z, onClick = { sugar = if (sugar == z) "" else z }, label = { Text("$z ${p.getOrElse(1) { "" }}") }) }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    mt.ice.forEach { p -> val z = p.getOrElse(0) { "" }; FilterChip(ice == z, onClick = { ice = if (ice == z) "" else z }, label = { Text("$z ${p.getOrElse(1) { "" }}") }) }
                }
            }
            item {
                Text("브랜드별 추천 메뉴는 인스타그램 chinese_shenjie·hyorin_c·raraclass_chinese 게시물을 정리한 것이다. 메뉴·가격·한정 메뉴는 매장·계절마다 다르다. 12월엔 따뜻하게(热的)가 좋다.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
