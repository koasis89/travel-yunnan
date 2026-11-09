package kr.koasis.travelyunnan.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.domain.SearchIndex
import kr.koasis.travelyunnan.domain.SearchTarget
import kr.koasis.travelyunnan.domain.TargetType
import kr.koasis.travelyunnan.ui.common.AppTopBar
import kr.koasis.travelyunnan.ui.nav.Actions
import kr.koasis.travelyunnan.ui.nav.LocalActions

private val EXAMPLES = listOf("숫자 카드", "사진 일기", "여권", "산소캔", "기차", "과교미선", "빵차", "사방가", "택시", "딤섬", "알리페이", "고산병")

/** 통합 검색(아래 탭 '검색', 웹 pages/Search.tsx): 앱 안의 일정·숙소·식당·관광지·회화·템플릿 등을 한 번에 찾는다 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(data: TripData, initial: String = "") {
    val actions = LocalActions.current
    var q by rememberSaveable { mutableStateOf(initial) }
    var kind by rememberSaveable { mutableStateOf("") }
    val index = remember(data) { SearchIndex.build(data) }
    val all = remember(index, q) { SearchIndex.search(index, q) }
    val kinds = SearchIndex.KIND_ORDER.filter { k -> all.any { it.kind == k } }
    val shown = if (kind.isEmpty()) all else all.filter { it.kind == kind }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("검색")
        OutlinedTextField(q, { q = it; kind = "" }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("일정·숙소·식당·관광지·회화·체크리스트…") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = ""; kind = "" }) { Icon(Icons.Filled.Clear, "지우기") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
        if (q.isBlank()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("한국어·중국어 모두 된다. 띄어 쓰면 낱말이 모두 들어간 것만 찾는다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { EXAMPLES.forEach { x -> AssistChip(onClick = { q = x }, label = { Text(x) }) } }
            }
            return@Column
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(kind.isEmpty(), onClick = { kind = "" }, label = { Text("전체 ${all.size}") }) }
            items(kinds) { k -> FilterChip(kind == k, onClick = { kind = k }, label = { Text("$k ${all.count { it.kind == k }}") }) }
        }
        if (shown.isEmpty()) Text("찾는 내용이 없다. 낱말을 줄이거나 다르게 써 본다.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(shown) { h ->
                Row(Modifier.fillMaxWidth().clickable { open(actions, h.target) }.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    SuggestionChip(onClick = { open(actions, h.target) }, label = { Text(h.kind, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (h.sub.isNotBlank()) Text(h.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

private fun open(a: Actions, t: SearchTarget) = when (t) {
    is SearchTarget.Schedule -> a.openSchedule(t.dayId)
    is SearchTarget.Lodging -> a.openLodgings(t.id)
    is SearchTarget.Meals -> a.openMeals(t.dayId)
    is SearchTarget.Eat -> a.openEat(t.id)
    is SearchTarget.Sight -> a.openSight(t.id)
    is SearchTarget.Taxi -> a.openTaxi(TargetType.place, t.placeId)
    is SearchTarget.Menu -> a.openMenu(t.foodZh)
    is SearchTarget.Show -> a.openShow(t.phraseId)
    is SearchTarget.Template -> a.openTemplates(t.id, null)
    SearchTarget.MilkTea -> a.openMilkTea()
    SearchTarget.Arrival -> a.openArrivalCard()
    SearchTarget.Notes -> a.openNotes()
    SearchTarget.Packing -> a.openPacking()
    SearchTarget.Emergency -> a.openEmergency()
    is SearchTarget.Feature -> when (t.key) {
        "home" -> a.openHome()
        "schedule" -> a.openSchedule(null)
        "pretrip" -> a.openSchedule("pre")
        "phrases" -> a.openPhrases(null)
        "maps" -> a.openMaps(null)
        "lodgings" -> a.openLodgings(null)
        "meals" -> a.openMeals(null)
        "foods" -> a.openFoods()
        "foodphotos" -> a.openFoodPhotos(null)
        "diary" -> a.openDiary(null)
        "number" -> a.openNumber()
        "expense" -> a.openExpense()
        "docs" -> a.openDocs(null)
        "transport" -> a.openTransport()
        "sights" -> a.openSights(null)
        "eats" -> a.openEats(null)
        "amap" -> a.openAmapLinks()
        "packing" -> a.openPacking()
        "notes" -> a.openNotes()
        "emergency" -> a.openEmergency()
        "templates" -> a.openTemplates(null, null)
        "arrival" -> a.openArrivalCard()
        "milktea" -> a.openMilkTea()
        "settings" -> a.openSettings()
        else -> Unit
    }
}
