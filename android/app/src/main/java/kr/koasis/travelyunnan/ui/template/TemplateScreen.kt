package kr.koasis.travelyunnan.ui.template

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.domain.Templates
import kr.koasis.travelyunnan.ui.common.*
import kr.koasis.travelyunnan.ui.nav.LocalActions
import kr.koasis.travelyunnan.util.External

/** 위챗 메시지 템플릿(S6-2): 변수 입력 → 중국어 본문 → 복사·위챗·크게 보여주기 */
@Composable
fun TemplateScreen(data: TripData, idArg: String?, linkArg: String? = null) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val link = remember(linkArg) { data.templateLink(linkArg) }
    var sel by rememberSaveable(idArg, linkArg) { mutableStateOf(link?.templateId ?: idArg ?: Templates.all.first().id) }
    val t = Templates.all.firstOrNull { it.id == sel } ?: Templates.all.first()
    // 일정에서 열었으면 그 일정에 맞는 값을 미리 채운다
    var values by rememberSaveable(sel, linkArg) { mutableStateOf(if (link != null && link.templateId == sel) link.values else mapOf()) }
    var showMode by rememberSaveable { mutableStateOf(false) }
    val zh = t.render(values)
    if (showMode) {
        ShowCardScreen(listOf(ShowLine(zh, ShowStyle.Long), ShowLine(t.renderKo(values), ShowStyle.Small)), onClose = { showMode = false }, header = "매니저에게 보여주세요") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(Icons.Filled.ContentCopy, "복사") { External.copy(ctx, zh, "문장을 복사했다") }
                SmallAction(Icons.AutoMirrored.Filled.Chat, "위챗으로") { External.openWechat(ctx, zh) }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("위챗 메시지 템플릿", onBack = actions.back)
        link?.let { l ->
            val day = data.day(l.dayId)
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                InfoBanner("${l.dayId} ${day?.dateLabel ?: ""} ${l.timeStart} 일정에 맞춰 값을 채웠다: ${l.label}", Icons.AutoMirrored.Filled.Chat)
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Templates.all, key = { it.id }) { x -> FilterChip(sel == x.id, onClick = { sel = x.id }, label = { Text(x.title) }) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(zh, fontSize = 19.sp, lineHeight = 28.sp)
                    Text(t.renderKo(values), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallAction(Icons.Filled.ContentCopy, "복사") { External.copy(ctx, zh, "문장을 복사했다") }
                        SmallAction(Icons.AutoMirrored.Filled.Chat, "위챗으로") { External.openWechat(ctx, zh) }
                        SmallAction(Icons.Filled.Fullscreen, "크게") { showMode = true }
                    }
                }
            }
            SectionTitle("바꿀 값")
            t.fields.forEach { f ->
                OutlinedTextField(
                    value = values[f.key] ?: "", onValueChange = { values = values + (f.key to it) },
                    label = { Text(f.label) }, placeholder = { Text(f.default) }, supportingText = if (f.hint.isNotBlank()) ({ Text(f.hint) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("비워 두면 기본값이 들어간다. 중국어 그대로 입력해도 되고, 숫자·시각만 바꿔도 된다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
