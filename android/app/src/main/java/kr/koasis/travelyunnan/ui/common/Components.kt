package kr.koasis.travelyunnan.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.koasis.travelyunnan.data.json.Day
import kr.koasis.travelyunnan.ui.theme.ShowText
import kr.koasis.travelyunnan.util.External
import kr.koasis.travelyunnan.util.ShowModeEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onBackground),
    )
}

/** D1~D8 가로 칩. allLabel 이 있으면 맨 앞에 "전체" 칩을 둔다. */
@Composable
fun DayChips(days: List<Day>, selected: String?, onSelect: (String?) -> Unit, allLabel: String? = null, todayId: String? = null) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allLabel != null) item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(allLabel) })
        }
        items(days, key = { it.id }) { d ->
            FilterChip(
                selected = selected == d.id,
                onClick = { onSelect(d.id) },
                label = { Text(if (d.id == todayId) "${d.id} 오늘" else "${d.id} ${d.dateLabel.substringBefore("(")}") },
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(16.dp).background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label)
    }
}

@Composable
fun InfoBanner(text: String, icon: ImageVector = Icons.Filled.Info, container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer) {
    Row(
        Modifier.fillMaxWidth().background(container, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, Modifier.size(20.dp).padding(top = 2.dp)); Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 보여주기 줄: 글자 크기 유형 */
enum class ShowStyle { Name, Address, Phrase, Long, Sub, Small }

data class ShowLine(val text: String, val style: ShowStyle)

/**
 * 전체 화면 보여주기 카드(S2-6). 택시 카드·문장 보여주기·메뉴 포인팅이 공유한다.
 * 밝기 최대·화면 유지, 화면을 누르면 버튼 영역을 숨겨 글자만 남긴다.
 */
@Composable
fun ShowCardScreen(
    lines: List<ShowLine>,
    onClose: () -> Unit,
    header: String? = null,
    bottom: @Composable ColumnScope.() -> Unit = {},
) {
    ShowModeEffect()
    var chrome by remember { mutableStateOf(true) }
    val bg = MaterialTheme.colorScheme.surface
    Column(
        Modifier.fillMaxSize().background(bg)
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { chrome = !chrome },
    ) {
        if (chrome) Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "닫기") }
            Text(header ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("화면을 누르면 버튼 숨김", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(end = 12.dp))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            lines.filter { it.text.isNotBlank() }.forEach { l ->
                val (style, color) = when (l.style) {
                    ShowStyle.Name -> ShowText.name to MaterialTheme.colorScheme.onSurface
                    ShowStyle.Address -> ShowText.address to MaterialTheme.colorScheme.onSurface
                    ShowStyle.Phrase -> ShowText.phrase to MaterialTheme.colorScheme.onSurface
                    ShowStyle.Long -> ShowText.longPhrase to MaterialTheme.colorScheme.onSurface
                    ShowStyle.Sub -> MaterialTheme.typography.titleLarge to MaterialTheme.colorScheme.primary
                    ShowStyle.Small -> MaterialTheme.typography.bodyMedium to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(l.text, style = style, color = color, textAlign = if (l.style == ShowStyle.Long) TextAlign.Start else TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            }
        }
        if (chrome) Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp), content = bottom)
    }
}

@Composable
fun PhoneButtons(phones: List<String>) {
    val ctx = LocalContext.current
    phones.forEach { p -> SmallAction(Icons.Filled.Call, p) { External.dial(ctx, p) } }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
