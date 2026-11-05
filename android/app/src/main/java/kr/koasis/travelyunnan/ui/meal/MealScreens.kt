package kr.koasis.travelyunnan.ui.meal

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import kr.koasis.travelyunnan.data.json.Food
import kr.koasis.travelyunnan.data.json.Meal
import kr.koasis.travelyunnan.data.json.MealOption
import kr.koasis.travelyunnan.domain.DayResolver
import kr.koasis.travelyunnan.domain.TargetType
import kr.koasis.travelyunnan.domain.TaxiCards
import kr.koasis.travelyunnan.domain.TripPhase
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.common.*
import kr.koasis.travelyunnan.ui.map.rememberAssetImage
import kr.koasis.travelyunnan.ui.nav.LocalActions
import kr.koasis.travelyunnan.ui.theme.ShowText
import kr.koasis.travelyunnan.util.External
import java.time.LocalDateTime

// ------------------------------------------------------------ 맛집·식사 (S4-2)

@Composable
fun MealsScreen(vm: AppViewModel, data: TripData, dayArg: String?) {
    val now by vm.now.collectAsStateWithLifecycle()
    MealsContent(data, now, dayArg)
}

@Composable
fun MealsContent(data: TripData, now: LocalDateTime, dayArg: String?) {
    val actions = LocalActions.current
    val todayId = (DayResolver.resolve(now, data.days) as? TripPhase.OnTrip)?.day?.id
    var day by rememberSaveable(dayArg) { mutableStateOf(dayArg ?: todayId ?: "D1") }
    val meals = data.mealsOf(day)
    Column(Modifier.fillMaxSize()) {
        AppTopBar("맛집·식사", onBack = actions.back) {
            IconButton(onClick = actions.openFoods) { Icon(Icons.Filled.MenuBook, "음식 도감") }
        }
        DayChips(data.days, day, { if (it != null) day = it }, todayId = todayId)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(meals, key = { it.id }) { m -> MealCard(data, m) }
            item { data.mealNotes.forEach { Text("※ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MealCard(data: TripData, m: Meal) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.slot, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(m.area, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                m.budgetCny?.let { Text("약 ¥$it", style = MaterialTheme.typography.labelLarge) }
            }
            m.options.forEachIndexed { i, o ->
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${"①②③"[i]} ${o.name}", style = MaterialTheme.typography.titleMedium)
                    if (o.addrZh.isNotBlank()) Text(o.addrZh, style = MaterialTheme.typography.bodyMedium)
                    if (o.menu.isNotBlank()) Text("추천: ${o.menu}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (TaxiCards.isTaxiable(o)) SmallAction(Icons.Filled.LocalTaxi, "택시 카드") { actions.openTaxi(TargetType.meal, o.id) }
                        if (o.addrZh.isNotBlank()) SmallAction(Icons.Filled.ContentCopy, "주소") { External.copy(ctx, o.addrZh, "주소를 복사했다") }
                        o.amapUrl?.let { u -> SmallAction(Icons.Filled.Place, "지도") { External.openUrl(ctx, u) } }
                        val food = data.foodNameFor(o.menu)
                        SmallAction(Icons.Filled.RecordVoiceOver, if (food != null) "주문: ${food.zh}" else "주문 문장") { actions.openMenu(food?.id) }
                    }
                }
            }
            if (m.note.isNotBlank()) InfoBanner(m.note)
            m.extra?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6) }
        }
    }
}

// ------------------------------------------------------------ 음식 도감 (S4-3)

@Composable
fun FoodsScreen(data: TripData) {
    val actions = LocalActions.current
    val regions = remember(data) { data.foods.map { it.region }.distinct() }
    var region by rememberSaveable { mutableStateOf(regions.first()) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("음식 도감", onBack = actions.back) {
            TextButton(onClick = { actions.openMenu(null) }) { Icon(Icons.Filled.RecordVoiceOver, null); Spacer(Modifier.width(4.dp)); Text("주문 문장") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(regions) { r -> FilterChip(region == r, onClick = { region = r }, label = { Text(r) }) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(data.foods.filter { it.region == region }, key = { it.id }) { f -> FoodCard(data, f) }
        }
    }
}

@Composable
private fun FoodCard(data: TripData, f: Food) {
    val actions = LocalActions.current
    val foodName = remember(f) { data.foodNames.firstOrNull { it.zh == f.nameZh } }
    Card(Modifier.clickable { actions.openMenu(foodName?.id) }) {
        Row(Modifier.padding(12.dp)) {
            val img = f.image?.let { rememberAssetImage(it) }
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                if (img != null) Image(img, f.nameKo, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(f.nameZh.take(2), fontSize = 28.sp, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(f.nameZh, style = MaterialTheme.typography.titleLarge)
                Text(f.nameKo + (foodName?.koRead?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(f.description, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                if (f.imageKind == "참고 이미지") Text("참고 이미지(같은 종류의 다른 사진)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ------------------------------------------------------------ 메뉴 포인팅 카드 (S4-3)

private val UNITS = listOf("一份" to "1인분", "一个" to "하나", "一瓶" to "한 병", "一杯" to "한 잔", "两份" to "2인분", "三份" to "3인분")
private val REQUESTS = listOf("不要辣" to "안 맵게", "不要香菜" to "고수 빼고", "少放盐" to "소금 적게", "打包" to "포장")

fun composeOrder(foodZh: String?, unit: String, requests: List<String>): String {
    val main = if (foodZh != null) "我要${unit}${foodZh}" else "这个要${unit}"
    return (listOf(main) + requests).joinToString("，") + "。"
}

@Composable
fun MenuPointScreen(vm: AppViewModel, data: TripData, foodArg: String?) {
    val ctx = LocalContext.current
    MenuPointContent(data, foodArg) { text -> if (!vm.speak("menu-${text.hashCode()}", text)) android.widget.Toast.makeText(ctx, "음성 재생을 할 수 없다", android.widget.Toast.LENGTH_SHORT).show() }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MenuPointContent(data: TripData, foodArg: String?, onSpeak: (String) -> Unit) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val foods = remember(data) { data.foodNames.filter { it.kind == "food" } }
    var foodId by rememberSaveable(foodArg) { mutableStateOf(foodArg) }
    var unit by rememberSaveable { mutableStateOf("一份") }
    var requests by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showMode by rememberSaveable { mutableStateOf(false) }
    val food = foods.firstOrNull { it.id == foodId }
    val sentence = composeOrder(food?.zh, unit, requests)

    if (showMode) {
        ShowCardScreen(
            lines = listOf(ShowLine(sentence, ShowStyle.Phrase), ShowLine(food?.let { "${it.ko} · ${UNITS.firstOrNull { u -> u.first == unit }?.second ?: unit}" } ?: "이거(메뉴를 가리키며)", ShowStyle.Sub)),
            onClose = { showMode = false }, header = "종업원에게 보여주세요",
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(Icons.AutoMirrored.Filled.VolumeUp, "듣기") { onSpeak(sentence) }
                SmallAction(Icons.Filled.ContentCopy, "복사") { External.copy(ctx, sentence) }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("주문 문장 만들기", onBack = actions.back)
        Card(Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sentence, style = ShowText.longPhrase.copy(fontSize = 28.sp, lineHeight = 40.sp), fontWeight = FontWeight.Bold)
                Text(food?.koRead?.let { "워 야오 ${UNITS.firstOrNull { u -> u.first == unit }?.second ?: ""} $it" } ?: "", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallAction(Icons.Filled.Fullscreen, "크게 보여주기") { showMode = true }
                    SmallAction(Icons.AutoMirrored.Filled.VolumeUp, "듣기") { onSpeak(sentence) }
                    SmallAction(Icons.Filled.ContentCopy, "복사") { External.copy(ctx, sentence) }
                }
            }
        }
        SectionTitle("수량")
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UNITS.forEach { (zh, ko) -> FilterChip(unit == zh, onClick = { unit = zh }, label = { Text("$zh $ko") }) }
        }
        SectionTitle("요청")
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            REQUESTS.forEach { (zh, ko) ->
                FilterChip(zh in requests, onClick = { requests = if (zh in requests) requests - zh else requests + zh }, label = { Text("$zh $ko") })
            }
        }
        SectionTitle("음식 (없으면 '이거'로 가리킨다)")
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(foodId == null, onClick = { foodId = null }, label = { Text("이거(가리키기)") })
            foods.forEach { f -> FilterChip(foodId == f.id, onClick = { foodId = f.id }, label = { Text("${f.zh} ${f.ko.substringBefore("(")}") }) }
        }
        Spacer(Modifier.height(24.dp))
    }
}
