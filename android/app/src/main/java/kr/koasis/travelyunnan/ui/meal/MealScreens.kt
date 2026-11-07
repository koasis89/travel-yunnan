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
import androidx.compose.ui.graphics.asImageBitmap
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
import kr.koasis.travelyunnan.domain.koName
import kr.koasis.travelyunnan.domain.zhName
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
                    Text("${"①②③"[i]} ${o.koName()}", style = MaterialTheme.typography.titleMedium)
                    if (o.nameKo.isNotBlank() && o.zhName().isNotBlank()) Text(o.zhName(), style = MaterialTheme.typography.bodyMedium)
                    if (o.addrZh.isNotBlank()) Text(o.addrZh, style = MaterialTheme.typography.bodyMedium)
                    if (o.menu.isNotBlank()) Text("추천: ${o.menu}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.restaurantOfOption(o.id)?.let { r -> SmallAction(Icons.Filled.MenuBook, "식당 정보·메뉴판") { actions.openEat(r.id) } }
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
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // 이 기기에 넣은 음식 사진(워드 사진은 음식 도감과 연결된다)
    val have by produceState(emptyList<String>()) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { kr.koasis.travelyunnan.data.FoodPhotoStore.ids(ctx) } }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("음식 도감", onBack = actions.back) {
            IconButton(onClick = { actions.openFoodPhotos(null) }) { Icon(Icons.Filled.PhotoLibrary, "음식 사진") }
            TextButton(onClick = { actions.openMenu(null) }) { Icon(Icons.Filled.RecordVoiceOver, null); Spacer(Modifier.width(4.dp)); Text("주문 문장") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(regions) { r -> FilterChip(region == r, onClick = { region = r }, label = { Text(r) }) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(data.foods.filter { it.region == region }, key = { it.id }) { f -> FoodCard(data, f, have) }
        }
    }
}

@Composable
private fun FoodCard(data: TripData, f: Food, have: List<String>) {
    val actions = LocalActions.current
    val foodName = remember(f) { data.foodNames.firstOrNull { it.zh == f.nameZh } }
    Card(Modifier.clickable { actions.openMenu(foodName?.id) }) {
        Row(Modifier.padding(12.dp)) {
            val mine = remember(have) { data.photoIdsForFood(f.id, have) }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val local by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, mine.firstOrNull()) {
                value = if (f.image == null) mine.firstOrNull()?.let { id -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { android.graphics.BitmapFactory.decodeFile(kr.koasis.travelyunnan.data.FoodPhotoStore.thumb(ctx, id).path)?.asImageBitmap() }.getOrNull() } } else null
            }
            val img = f.image?.let { rememberAssetImage(it) } ?: local
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
                if (mine.isNotEmpty()) TextButton(onClick = { actions.openFoodPhotos(f.id) }, contentPadding = PaddingValues(0.dp)) { Text("사진 ${mine.size}장 보기") }
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

/** 음료(웹 src/domain/drinks.ts 와 같은 목록) */
data class Drink(val zh: String, val ko: String, val measure: String, val group: String)

val DRINKS = listOf(
    Drink("矿泉水", "생수", "瓶", "물·차"), Drink("热水", "뜨거운 물", "杯", "물·차"), Drink("普洱茶", "보이차", "壶", "물·차"), Drink("滇红茶", "전홍(윈난 홍차)", "壶", "물·차"),
    Drink("冰啤酒", "시원한 맥주", "瓶", "맥주"), Drink("风花雪月啤酒", "풍화설월 맥주", "瓶", "맥주"), Drink("大理啤酒", "다리 맥주", "瓶", "맥주"),
    Drink("鹤庆乾酒", "학경건주(윈난 고량주)", "小瓶", "술"), Drink("杨林肥酒", "양림비주(쿤밍 녹색 술)", "小瓶", "술"), Drink("丽江窨酒", "리장 음주(나시족 쌀술)", "小瓶", "술"), Drink("本地苞谷酒", "옥수수 술(현지 백주)", "小杯", "술"),
    Drink("酸角汁", "산각즙(타마린드 주스)", "杯", "윈난 음료"), Drink("木瓜水", "모과수(젤리 음료)", "碗", "윈난 음료"), Drink("牦牛酸奶", "야크 요거트", "杯", "윈난 음료"),
    Drink("酥油茶", "수유차(버터차)", "壶", "윈난 음료"), Drink("玫瑰花茶", "장미꽃차", "壶", "윈난 음료"),
    Drink("可乐", "콜라", "瓶", "음료수"), Drink("雪碧", "사이다(스프라이트)", "瓶", "음료수"), Drink("橙汁", "오렌지 주스", "杯", "음료수"),
    Drink("云南小粒咖啡", "윈난 원두 커피", "杯", "커피"), Drink("热美式", "따뜻한 아메리카노", "杯", "커피"), Drink("冰美式", "아이스 아메리카노", "杯", "커피"), Drink("热拿铁", "따뜻한 라테", "杯", "커피"),
)
private val DRINK_REQUESTS = listOf("饮料要冰的" to "음료 차갑게", "饮料要常温的" to "음료 상온", "请帮忙开瓶" to "병 따 주세요", "请给我们三个杯子" to "컵 3개 주세요")
private val NUM = listOf("", "一", "两", "三", "四", "五", "六")

/** 여러 음식(각 수량) + 음료 + 요청 → 주문 문장(웹 composeMultiOrder 와 같다). 음식이 없으면 pointUnit 으로 '이거', null 이면 음료만 */
fun composeMultiOrder(foods: List<Pair<String, Int>>, pointUnit: String?, drinks: List<Pair<Drink, Int>>, requests: List<String>): String {
    val parts = mutableListOf<String>()
    val fs = foods.filter { it.second > 0 }
    if (fs.isNotEmpty()) parts += "我要" + fs.joinToString("、") { (z, n) -> "${NUM.getOrElse(n) { n.toString() }}份$z" }
    else if (pointUnit != null) parts += "这个要$pointUnit"
    val ds = drinks.filter { it.second > 0 }.map { (d, n) -> "${NUM.getOrElse(n) { n.toString() }}${d.measure}${d.zh}" }
    if (ds.isNotEmpty()) parts += (if (parts.isNotEmpty()) "再" else "我") + "要" + ds.joinToString("、")
    if (parts.isEmpty()) parts += "你好，我要点菜"
    return (parts + requests).joinToString("，") + "。"
}

fun encodeFoods(l: List<Pair<String, Int>>) = l.filter { it.second > 0 }.joinToString("|") { "${it.first}*${it.second}" }
fun decodeFoods(s: String): List<Pair<String, Int>> = s.split('|').map { it.trim() }.filter { it.isNotEmpty() }
    .map { x -> x.substringBefore('*') to (x.substringAfter('*', "1").toIntOrNull() ?: 1).coerceIn(1, 9) }

/** 음식(빈 문자열 = 이거, null = 음료만) + 음료 + 요청 → 주문 문장. 웹 composeFullOrder 와 같다 */
fun composeFullOrder(food: String?, unit: String, drinks: List<Pair<Drink, Int>>, requests: List<String>): String {
    val parts = mutableListOf<String>()
    if (food != null) parts += if (food.isNotEmpty()) "我要${unit}${food}" else "这个要${unit}"
    val ds = drinks.filter { it.second > 0 }.map { (d, n) -> "${NUM.getOrElse(n) { n.toString() }}${d.measure}${d.zh}" }
    if (ds.isNotEmpty()) parts += (if (parts.isNotEmpty()) "再" else "我") + "要" + ds.joinToString("、")
    if (parts.isEmpty()) parts += "你好，我要点菜"
    return (parts + requests).joinToString("，") + "。"
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
    // 고른 음식(중국어 이름 → 수량). foodArg: 음식 id, 중국어 이름, 또는 "A*1|B*2"(맛집 메뉴판에서 여러 개)
    val picked = remember(foodArg) {
        mutableStateListOf<Pair<String, Int>>().apply {
            when {
                foodArg == null -> {}
                foodArg.contains('*') || foodArg.contains('|') -> addAll(decodeFoods(foodArg))
                else -> (foods.firstOrNull { it.id == foodArg }?.zh ?: foodArg.takeIf { Regex("[\u4e00-\u9fff]").containsMatchIn(it) })?.let { add(it to 1) }
            }
        }
    }
    var unit by rememberSaveable { mutableStateOf("一份") }
    var requests by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showMode by rememberSaveable { mutableStateOf(false) }
    var drinksOnly by rememberSaveable { mutableStateOf(false) }
    val drinkCount = remember { mutableStateMapOf<String, Int>() }
    fun count(zh: String) = picked.firstOrNull { it.first == zh }?.second ?: 0
    fun cycle(zh: String) {
        drinksOnly = false
        val n = (count(zh) + 1) % 4
        val i = picked.indexOfFirst { it.first == zh }
        if (n == 0) { if (i >= 0) picked.removeAt(i) } else if (i >= 0) picked[i] = zh to n else picked.add(zh to n)
    }
    val chosen = DRINKS.mapNotNull { d -> drinkCount[d.zh]?.takeIf { it > 0 }?.let { d to it } }
    val sentence = composeMultiOrder(picked.toList(), if (drinksOnly) null else unit, chosen, requests)
    val koName = { zh: String -> foods.firstOrNull { it.zh == zh }?.ko?.substringBefore("(") ?: zh }
    val koLine = ((if (picked.isNotEmpty()) picked.map { (z, n) -> "${koName(z)} ${n}인분" } else if (drinksOnly) emptyList()
        else listOf("이거(메뉴를 가리키며) · ${UNITS.firstOrNull { u -> u.first == unit }?.second ?: unit}")) +
        chosen.map { (d, n) -> "${d.ko} $n" }).joinToString(" / ")

    if (showMode) {
        ShowCardScreen(
            lines = listOf(ShowLine(sentence, ShowStyle.Phrase), ShowLine(koLine, ShowStyle.Sub)),
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
                Text(koLine, style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallAction(Icons.Filled.Fullscreen, "크게 보여주기") { showMode = true }
                    SmallAction(Icons.AutoMirrored.Filled.VolumeUp, "듣기") { onSpeak(sentence) }
                    SmallAction(Icons.Filled.ContentCopy, "복사") { External.copy(ctx, sentence) }
                }
            }
        }
        if (picked.isEmpty() && !drinksOnly) {
            SectionTitle("가리키기 수량")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UNITS.forEach { (zh, ko) -> FilterChip(unit == zh, onClick = { unit = zh }, label = { Text("$zh $ko") }) }
            }
        }
        SectionTitle("요청")
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            REQUESTS.forEach { (zh, ko) ->
                FilterChip(zh in requests, onClick = { requests = if (zh in requests) requests - zh else requests + zh }, label = { Text("$zh $ko") })
            }
        }
        SectionTitle("음료 (누를 때마다 1→2→3→빼기)")
        DRINKS.map { it.group }.distinct().forEach { g ->
            Text(g, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DRINKS.filter { it.group == g }.forEach { d ->
                    val n = drinkCount[d.zh] ?: 0
                    FilterChip(n > 0, onClick = { drinkCount[d.zh] = (n + 1) % 4 }, label = { Text("${d.zh} ${d.ko}" + if (n > 0) " ×$n" else "") })
                }
            }
        }
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DRINK_REQUESTS.forEach { (zh, ko) ->
                FilterChip(zh in requests, onClick = { requests = if (zh in requests) requests - zh else requests + zh }, label = { Text("$zh $ko") })
            }
        }
        SectionTitle("음식 (여러 개 선택 · 누를 때마다 1→2→3→빼기)")
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(!drinksOnly && picked.isEmpty(), onClick = { picked.clear(); drinksOnly = false }, label = { Text("이거(가리키기)") })
            FilterChip(drinksOnly, onClick = { picked.clear(); drinksOnly = true }, label = { Text("음료만") })
            picked.filter { p -> foods.none { it.zh == p.first } }.forEach { (z, n) -> FilterChip(true, onClick = { cycle(z) }, label = { Text("$z ×$n") }) }
            foods.forEach { f -> val n = count(f.zh); FilterChip(n > 0, onClick = { cycle(f.zh) }, label = { Text("${f.zh} ${f.ko.substringBefore("(")}" + if (n > 0) " ×$n" else "") }) }
        }
        Spacer(Modifier.height(24.dp))
    }
}
