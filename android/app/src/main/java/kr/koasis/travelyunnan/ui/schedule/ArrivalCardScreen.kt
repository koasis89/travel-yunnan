package kr.koasis.travelyunnan.ui.schedule

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.ui.common.AppTopBar
import kr.koasis.travelyunnan.ui.common.InfoBanner
import kr.koasis.travelyunnan.ui.common.SectionTitle
import kr.koasis.travelyunnan.ui.common.SmallAction
import kr.koasis.travelyunnan.ui.nav.LocalActions
import kr.koasis.travelyunnan.util.External

/** 알리페이로 중국 입국신고서(Arrival Card) QR 만들기(웹 pages/ArrivalCard.tsx): 순서 + 입국·호텔·출국 값 복사 */
@Composable
fun ArrivalCardScreen(data: TripData) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val ac = data.checklists.arrivalCard
    // 항공편 편명은 이 기기에만 저장한다
    val prefs = remember { ctx.getSharedPreferences("arrival-card", Context.MODE_PRIVATE) }
    val inputs = remember { mutableStateMapOf<String, String>().apply { ac.fields.mapNotNull { it.input }.forEach { k -> put(k, prefs.getString(k, "") ?: "") } } }
    val groups = ac.fields.map { it.group }.distinct()
    Column(Modifier.fillMaxSize()) {
        AppTopBar("입국신고 QR (알리페이)", onBack = actions.back)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { InfoBanner(ac.summary, Icons.Filled.QrCode2) }
            item { SectionTitle("만드는 순서", Modifier.padding(start = 0.dp)) }
            itemsIndexed(ac.steps, key = { _, s -> "s" + s.title }) { i, s ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.titleSmall)
                        Text(s.desc, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            groups.forEach { g ->
                item(key = "g-$g") { SectionTitle(g, Modifier.padding(start = 0.dp)) }
                ac.fields.filter { it.group == g }.forEach { f ->
                    item(key = "f-${f.group}-${f.label}") {
                        val v = f.input?.let { inputs[it] ?: "" } ?: f.value
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(f.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (f.input != null) OutlinedTextField(v, { x ->
                                        val t = x.uppercase().filterNot { it.isWhitespace() }
                                        inputs[f.input] = t; prefs.edit().putString(f.input, t).apply()
                                    }, placeholder = { Text("편명(영문+숫자)") }, singleLine = true, modifier = Modifier.widthIn(max = 220.dp))
                                    else Text(v, style = MaterialTheme.typography.titleMedium)
                                    if (f.note.isNotBlank()) Text(f.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                if (f.copy && v.isNotBlank()) SmallAction(Icons.Filled.ContentCopy, "복사") { External.copy(ctx, v, "${f.label} 복사 완료") }
                            }
                        }
                    }
                }
            }
            item { SectionTitle("알아 둘 것", Modifier.padding(start = 0.dp)) }
            ac.notes.forEach { n -> item(key = "n-$n") { Text("· $n", style = MaterialTheme.typography.bodyMedium) } }
            item {
                Text("영문 이름·여권 번호·생년월일은 여권 사진으로 자동 입력되니 확인만 한다. 개인 정보라 이 앱에는 넣지 않는다.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
