package kr.koasis.travelyunnan.ui.packing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.common.AppTopBar
import kr.koasis.travelyunnan.ui.common.SectionTitle
import kr.koasis.travelyunnan.ui.nav.LocalActions

@Composable
fun PackingScreen(vm: AppViewModel, data: TripData) {
    val flags by vm.flags.collectAsStateWithLifecycle()
    PackingContent(data, flags) { vm.toggleFlag(it) }
}

@Composable
fun PackingContent(data: TripData, flags: Set<String>, onToggle: (String) -> Unit) {
    val actions = LocalActions.current
    val groups = remember(data) { data.packing.groupBy { it.group } }
    val checked = data.packing.count { "pack:${it.id}" in flags }
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    var showWeather by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("준비물·날씨", onBack = actions.back)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().clickable { showWeather = !showWeather }.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("12월 기온·옷차림", Modifier.weight(1f))
                    Icon(if (showWeather) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                }
            }
            if (showWeather) items(data.weather, key = { it.region }) { w ->
                Card(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${w.region} · ${w.dates} · ${w.altitude}", style = MaterialTheme.typography.titleMedium)
                        Text("낮 ${w.tempDay} / 밤 ${w.tempNight} · ${w.feature}", style = MaterialTheme.typography.bodyMedium)
                        Text(w.outfit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("준비물 체크 $checked / ${data.packing.size}", style = MaterialTheme.typography.titleLarge)
                    LinearProgressIndicator(progress = { if (data.packing.isEmpty()) 0f else checked.toFloat() / data.packing.size }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            groups.forEach { (g, items) ->
                val done = items.count { "pack:${it.id}" in flags }
                item(key = "g:$g") {
                    Row(Modifier.fillMaxWidth().clickable { collapsed = if (g in collapsed) collapsed - g else collapsed + g }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(g, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text("$done/${items.size}", style = MaterialTheme.typography.bodyMedium)
                        Icon(if (g in collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, null)
                    }
                }
                if (g !in collapsed) items(items, key = { it.id }) { it ->
                    val key = "pack:${it.id}"
                    val on = key in flags
                    Row(Modifier.fillMaxWidth().clickable { onToggle(key) }.padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(on, onCheckedChange = { onToggle(key) })
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(it.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (on) FontWeight.Normal else FontWeight.Medium,
                                color = if (on) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                            val sub = listOf(it.qty, it.note).filter { s -> s.isNotBlank() }.joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
