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
import androidx.compose.ui.Alignment
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
    // 한국어 설명 줄: 날짜·시각은 그대로(템플릿이 한국어로 바꾼다), 나머지 중국어 값은 번역 사전으로
    val koLine = t.renderKo(t.fields.associate { f -> val x = (values[f.key] ?: "").ifBlank { f.default }; f.key to if (f.kind != null) x else kr.koasis.travelyunnan.data.KoGlossDict.ko(x).ifEmpty { x } })
    var showLine by remember { mutableStateOf<kr.koasis.travelyunnan.domain.TemplateLine?>(null) }
    showLine?.let { l ->
        ShowCardScreen(listOf(ShowLine(l.zh, ShowStyle.Long), ShowLine(l.ko, ShowStyle.Small)), onClose = { showLine = null }, header = "보여 주세요") {
            SmallAction(Icons.Filled.ContentCopy, "복사") { External.copy(ctx, l.zh, "문장을 복사했다") }
        }
        return
    }
    if (showMode) {
        ShowCardScreen(listOf(ShowLine(zh, ShowStyle.Long), ShowLine(koLine, ShowStyle.Small)), onClose = { showMode = false }, header = "매니저에게 보여주세요") {
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
            if (t.lines.isNotEmpty()) {
                if (t.linesNote.isNotBlank()) Text(t.linesNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                t.lines.forEachIndexed { i, l ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(l.label.ifBlank { "${i + 1}" }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(l.zh, fontSize = 19.sp, lineHeight = 28.sp)
                            Text(l.ko, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallAction(Icons.Filled.ContentCopy, "복사") { External.copy(ctx, l.zh, "복사했다") }
                                SmallAction(Icons.Filled.Fullscreen, "크게") { showLine = l }
                            }
                        }
                    }
                }
            } else Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(zh, fontSize = 19.sp, lineHeight = 28.sp)
                    Text(koLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallAction(Icons.Filled.ContentCopy, "복사") { External.copy(ctx, zh, "문장을 복사했다") }
                        SmallAction(Icons.AutoMirrored.Filled.Chat, "위챗으로") { External.openWechat(ctx, zh) }
                        SmallAction(Icons.Filled.Fullscreen, "크게") { showMode = true }
                    }
                }
            }
            t.contact?.let { c ->
                Card {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(c.name, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text("위챗 ID  ${c.wechat}", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallAction(Icons.Filled.ContentCopy, "위챗 ID 복사") { External.copy(ctx, c.wechat, "위챗 ID를 복사했다. 친구 추가 검색창에 붙여넣는다") }
                            SmallAction(Icons.AutoMirrored.Filled.Chat, "위챗 열기") { External.openWechat(ctx, c.wechat) }
                        }
                        if (c.bookingRef.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("대표 예약자 ${c.booker} · 예약 번호 ${c.bookingRef}", style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            SmallAction(Icons.Filled.ContentCopy, "예약 번호") { External.copy(ctx, c.bookingRef, "예약 번호를 복사했다") }
                        }
                        Text("친구 추가: 위챗 → 오른쪽 위 ⊕ → 친구 추가(添加朋友) → 검색창에 ID 붙여넣기 → 요청 메시지에 아래 문장", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(c.friendNote, style = MaterialTheme.typography.bodyMedium)
                                if (c.friendNoteKo.isNotBlank()) Text(c.friendNoteKo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SmallAction(Icons.Filled.ContentCopy, "복사") { External.copy(ctx, c.friendNote, "친구 요청 문장을 복사했다") }
                        }
                        OutlinedButton(onClick = { sel = "tpl-wechat-qr" }) { Text("QR·아이디 주고받기 회화") }
                        if (c.note.isNotBlank()) Text(c.note, style = MaterialTheme.typography.bodySmall)
                        if (c.email.isNotBlank()) Text("이메일(참고): ${c.email} · 연락은 위챗으로 한다. 바우처의 '+86 3180884…' 긴 번호는 깨진 값이다.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            if (t.fields.isNotEmpty()) SectionTitle("바꿀 값")
            t.fields.forEach { f ->
                if (f.kind != null) { DateTimeFields(f, values[f.key] ?: "") { values = values + (f.key to it) }; return@forEach }
                OutlinedTextField(
                    value = values[f.key] ?: "", onValueChange = { values = values + (f.key to it) },
                    label = { Text(f.label) }, placeholder = { Text(f.default) }, supportingText = (listOf(kr.koasis.travelyunnan.data.KoGlossDict.ko((values[f.key] ?: "").ifBlank { f.default }), f.hint).filter { it.isNotBlank() }.joinToString("\n")).takeIf { it.isNotBlank() }?.let { s -> { Text(s) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (t.fields.isNotEmpty()) Text("날짜·시각은 숫자만 넣는다(시각은 24시간, 예: 14시 30분). 비워 두면 기본값이 들어간다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** 날짜·시각을 숫자 칸(월·일·시·분)으로 받는다(웹 DateTimeInput). 아래에 중국어·한국어로 어떻게 들어가는지 보여 준다 */
@Composable
private fun DateTimeFields(f: kr.koasis.travelyunnan.domain.TemplateField, value: String, onChange: (String) -> Unit) {
    val dt = kr.koasis.travelyunnan.domain.DateText
    val kind = f.kind ?: return
    val cur = dt.parse(value); val def = dt.parse(f.default)
    val p = if (value.isBlank()) def else cur
    val boxes = buildList {
        if (kind != "time") { add(Triple("mo", "월", p.mo)); add(Triple("d", "일", p.d)) }
        if (kind != "date") { add(Triple("h", "시", p.h)); add(Triple("mi", "분", p.mi)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(f.label + if (f.hint.isNotBlank()) " · ${f.hint}" else "", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            boxes.forEach { (k, label, v) ->
                OutlinedTextField(v, { x ->
                    val n = x.filter { it.isDigit() }.take(2)
                    val np = when (k) { "mo" -> p.copy(mo = n); "d" -> p.copy(d = n); "h" -> p.copy(h = n); else -> p.copy(mi = n) }
                    onChange(dt.join(kind, np))
                }, modifier = Modifier.width(64.dp), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                Text(label)
            }
        }
        val eff = if (dt.isValid(value, kind)) value else f.default
        Text("${dt.zh(eff, kind, f.approx)} · ${dt.ko(eff, kind)}" + if (value.isNotBlank() && !dt.isValid(value, kind)) " (칸을 다 채우면 바뀐다)" else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
