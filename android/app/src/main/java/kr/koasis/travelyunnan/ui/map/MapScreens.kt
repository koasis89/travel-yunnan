package kr.koasis.travelyunnan.ui.map

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.json.MapItem
import kr.koasis.travelyunnan.domain.DayResolver
import kr.koasis.travelyunnan.domain.TargetType
import kr.koasis.travelyunnan.domain.TripPhase
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.common.AppTopBar
import kr.koasis.travelyunnan.ui.common.DayChips
import kr.koasis.travelyunnan.ui.common.SectionTitle
import kr.koasis.travelyunnan.ui.nav.LocalActions
import kr.koasis.travelyunnan.util.External
import java.time.LocalDateTime

private val imageCache = android.util.LruCache<String, ImageBitmap>(8)

/** assets/images/{file} 을 읽는다. 지도 이미지는 1MB 미만이라 동기 디코딩으로 충분하며, 8장을 캐시한다. */
@Composable
fun rememberAssetImage(file: String): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(file) {
        imageCache.get(file) ?: runCatching {
            ctx.assets.open("images/$file").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()?.also { imageCache.put(file, it) }
    }
}

@Composable
fun MapsScreen(vm: AppViewModel, data: TripData, dayArg: String?) {
    val now by vm.now.collectAsStateWithLifecycle()
    val flags by vm.flags.collectAsStateWithLifecycle()
    MapsContent(data, now, dayArg, flags) { vm.toggleFlag(it) }
}

@Composable
fun MapsContent(data: TripData, now: LocalDateTime, dayArg: String?, flags: Set<String>, onToggle: (String) -> Unit) {
    val phase = DayResolver.resolve(now, data.days)
    val todayId = (phase as? TripPhase.OnTrip)?.day?.id
    var day by rememberSaveable(dayArg) { mutableStateOf(dayArg ?: todayId) }
    val maps = remember(day) { data.mapsOf(day) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("지도")
        DayChips(data.days, day, { day = it }, allLabel = "전체", todayId = todayId)
        val pager = rememberPagerState { maps.size }
        LaunchedEffect(day) { pager.scrollToPage(0) }
        if (maps.size > 1) Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
            repeat(maps.size) { i ->
                Box(Modifier.padding(3.dp).size(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (pager.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
            }
        }
        HorizontalPager(pager, Modifier.weight(1f), key = { maps[it].id }) { page ->
            MapPage(data, maps[page], day, flags, onToggle)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapPage(data: TripData, map: MapItem, day: String?, flags: Set<String>, onToggle: (String) -> Unit) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val img = rememberAssetImage(map.file)
    val amapLinks = remember(day) {
        if (day == null) emptyList() else
            data.scheduleOf(day).mapNotNull { r -> r.amapUrl?.let { "${r.timeLabel} ${r.to.ifBlank { r.from }}" to it } } +
                data.mealsOf(day).flatMap { m -> m.options.mapNotNull { o -> o.amapUrl?.let { "${m.slot} · ${o.name}" to it } } }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(map.title, style = MaterialTheme.typography.titleLarge)
                Text(map.caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier.padding(horizontal = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .clickable { actions.openMapView(map.id) },
                contentAlignment = Alignment.Center,
            ) {
                if (img != null) Image(img, map.title, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                else Box(Modifier.height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                Icon(Icons.Filled.ZoomIn, "확대", Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(8.dp)).padding(4.dp))
            }
            Text("개략도다. 실제 위치는 고덕지도로 확인한다. 누르면 전체 화면으로 확대한다.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        if (map.progress.isNotEmpty()) item {
            SectionTitle("트레킹 진행")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                map.progress.forEachIndexed { i, label ->
                    val key = "trek:${map.id}:$i"
                    FilterChip(key in flags, onClick = { onToggle(key) }, label = { Text("${i + 1}. $label") },
                        leadingIcon = if (key in flags) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null)
                }
            }
        }
        if (map.pins.isNotEmpty()) {
            item { SectionTitle("번호별 식당 (남문 기준 거리)") }
            items(map.pins, key = { it.no }) { pin ->
                val opt = remember(pin) { data.findMealOption(pin.nameZh)?.second }
                ListItem(
                    headlineContent = { Text("${pin.no}. ${pin.nameKo}") },
                    supportingContent = { Text("${pin.kind} · ${pin.nameZh} · ${pin.distance}") },
                    trailingContent = { if (opt != null) Icon(Icons.Filled.LocalTaxi, "택시 카드", tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable(enabled = opt != null) { opt?.let { actions.openTaxi(TargetType.meal, it.id) } },
                )
            }
        }
        if (amapLinks.isNotEmpty()) {
            item { SectionTitle("실제 지도에서 보기") }
            items(amapLinks) { (label, url) ->
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = { Icon(Icons.Filled.Place, null) },
                    modifier = Modifier.clickable { External.openUrl(ctx, url) },
                )
            }
        }
    }
}

/** 전체 화면 확대 보기: 핀치 줌·더블 탭·드래그 */
@Composable
fun MapViewScreen(data: TripData, id: String) {
    val actions = LocalActions.current
    val map = data.map(id)
    if (map == null) { actions.back(); return }
    val img = rememberAssetImage(map.file)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun clamp(o: Offset, s: Float): Offset {
        val mx = (s - 1f) * size.width / 2f
        val my = (s - 1f) * size.height / 2f
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Box(
            Modifier.fillMaxSize().onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = clamp(offset + pan, scale)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { scale = if (scale > 1.1f) 1f else 2.5f; offset = clamp(Offset.Zero, scale) })
                },
            contentAlignment = Alignment.Center,
        ) {
            img?.let {
                Image(it, map.title, Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                    contentScale = ContentScale.Fit)
            } ?: CircularProgressIndicator()
        }
        Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.85f)).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.back) { Icon(Icons.Filled.Close, "닫기", tint = Color.Black) }
            Text(map.title, color = Color.Black, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("두 손가락 확대 · 두 번 탭", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp))
        }
    }
}
