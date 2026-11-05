package kr.koasis.travelyunnan.ui.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.json.Note
import kr.koasis.travelyunnan.ui.common.AppTopBar
import kr.koasis.travelyunnan.ui.common.SectionTitle
import kr.koasis.travelyunnan.ui.common.SmallAction
import kr.koasis.travelyunnan.ui.nav.LocalActions

@Composable
fun NotesScreen(data: TripData) {
    val actions = LocalActions.current
    val (linked, common) = remember(data) { data.notes.partition { it.dayIds.isNotEmpty() } }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("유의사항", onBack = actions.back)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionTitle("일차와 연결된 항목") }
            items(linked.sortedBy { it.dayIds.first() }, key = { it.id }) { NoteCard(data, it) }
            item { SectionTitle("공통") }
            items(common, key = { it.id }) { NoteCard(data, it) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NoteCard(data: TripData, n: Note) {
    val actions = LocalActions.current
    val phrases = remember(n) { n.phrasesInline.mapNotNull { data.phraseByZh(it) }.distinctBy { it.id } }
    Card(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(n.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (n.dayIds.isNotEmpty()) Text("  " + n.dayIds.joinToString(" "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            }
            Text(n.body, style = MaterialTheme.typography.bodyLarge)
            if (phrases.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                phrases.forEach { p -> SmallAction(Icons.AutoMirrored.Filled.Chat, p.ko.take(14) + if (p.ko.length > 14) "…" else "") { actions.openShow(p.id) } }
            }
        }
    }
}
