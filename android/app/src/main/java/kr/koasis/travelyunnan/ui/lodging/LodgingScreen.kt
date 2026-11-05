package kr.koasis.travelyunnan.ui.lodging

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.json.Lodging
import kr.koasis.travelyunnan.domain.TargetType
import kr.koasis.travelyunnan.ui.common.*
import kr.koasis.travelyunnan.ui.nav.LocalActions
import kr.koasis.travelyunnan.util.External

@Composable
fun LodgingScreen(vm: AppViewModelAlias, data: TripData, focusId: String?) {
    val flags by vm.flags.collectAsStateWithLifecycle()
    LodgingContent(data, focusId, flags) { vm.toggleFlag(it) }
}

typealias AppViewModelAlias = kr.koasis.travelyunnan.ui.AppViewModel

@Composable
fun LodgingContent(data: TripData, focusId: String?, flags: Set<String>, onToggle: (String) -> Unit) {
    val actions = LocalActions.current
    val state = rememberLazyListState()
    LaunchedEffect(focusId) {
        val i = data.lodgings.indexOfFirst { it.id == focusId }
        if (i >= 0) state.scrollToItem(i)
    }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("숙소", onBack = actions.back)
        LazyColumn(state = state, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(data.lodgings, key = { it.id }) { l -> LodgingCard(data, l, flags, onToggle) }
            item {
                Spacer(Modifier.height(8.dp))
                data.lodgingNotes.forEach { Text("※ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LodgingCard(data: TripData, l: Lodging, flags: Set<String>, onToggle: (String) -> Unit) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val todos = data.todosOf(l.id)
    val dateLabel = l.dates.joinToString("~") { it.substring(5).replace("-", "/") }
    Card(colors = CardDefaults.cardColors(containerColor = if (l.confirmed) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$dateLabel · ${l.nights}박 · ${l.region}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(l.status) }, enabled = false)
            }
            Text(l.nameKo, style = MaterialTheme.typography.titleLarge)
            if (l.nameZh.isNotBlank()) Text(l.nameZh, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
            Text(l.addrZh, style = MaterialTheme.typography.bodyLarge)
            if (l.addrEn.isNotBlank()) Text(l.addrEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(l.room, style = MaterialTheme.typography.bodyMedium)
            if (l.bookingNo.isNotBlank()) Text("예약번호 ${l.bookingNo} · ${l.bookedBy}", style = MaterialTheme.typography.bodyMedium)
            l.priceKrw?.let { Text("%,d원".format(it), style = MaterialTheme.typography.bodyMedium) }
            if (l.note.isNotBlank()) Text(l.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                SmallAction(Icons.Filled.LocalTaxi, "택시 카드") { actions.openTaxi(TargetType.lodging, l.id) }
                if (l.addrZh.isNotBlank()) SmallAction(Icons.Filled.ContentCopy, "주소 복사") { External.copy(ctx, l.nameZh.ifBlank { "" } + " " + l.addrZh, "주소를 복사했다") }
                SmallAction(Icons.Filled.Login, "체크인 문장") { actions.openPhrases("hotel") }
                data.phrase("px-006")?.let { p -> SmallAction(Icons.Filled.People, "3인 확인") { actions.openShow(p.id) } }
                PhoneButtons(l.phones)
            }
            if (todos.isNotEmpty()) {
                Text("할 일", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                todos.forEach { t ->
                    val key = "todo:${t.id}"
                    Row(Modifier.fillMaxWidth().clickable { onToggle(key) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(key in flags, onCheckedChange = { onToggle(key) })
                        Text(t.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        t.phraseId?.let { pid ->
                            IconButton(onClick = { actions.openShow(pid) }) { Icon(Icons.AutoMirrored.Filled.Chat, "문장 보기", tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
        }
    }
}
