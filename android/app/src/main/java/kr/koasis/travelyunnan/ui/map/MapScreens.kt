package kr.koasis.travelyunnan.ui.map

import kr.koasis.travelyunnan.ui.common.KoGloss
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight as TextWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.json.MapItem
import kr.koasis.travelyunnan.data.json.Hotspot
import kr.koasis.travelyunnan.domain.DayResolver
import kr.koasis.travelyunnan.domain.HotspotInfo
import kr.koasis.travelyunnan.domain.HotspotResolver
import kr.koasis.travelyunnan.domain.TargetType
import kr.koasis.travelyunnan.domain.TripPhase
import kr.koasis.travelyunnan.domain.koName
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.common.AppTopBar
import kr.koasis.travelyunnan.ui.common.DayChips
import kr.koasis.travelyunnan.ui.common.SectionTitle
import kr.koasis.travelyunnan.ui.common.SmallAction
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Hotel
import kr.koasis.travelyunnan.ui.nav.LocalActions
import kr.koasis.travelyunnan.util.External
import java.time.LocalDateTime

private val imageCache = android.util.LruCache<String, ImageBitmap>(12)

/** assets/images/{file} 을 읽는다. 지도 이미지는 1MB 미만이라 동기 디코딩으로 충분하며, 12장을 캐시한다. */
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
    var selected by remember { mutableStateOf<Hotspot?>(null) }
    var pinKind by rememberSaveable(map.id) { mutableStateOf("전체") }
    selected?.let { h -> HotspotSheet(data, h, day) { selected = null } }
    val amapLinks = remember(day) {
        if (day == null) emptyList() else
            data.scheduleOf(day).mapNotNull { r -> r.amapUrl?.let { "${r.timeLabel} ${r.to.ifBlank { r.from }}" to it } } +
                data.mealsOf(day).flatMap { m -> m.options.mapNotNull { o -> o.amapUrl?.let { "${m.slot} · ${o.koName()}" to it } } }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(map.title, style = MaterialTheme.typography.titleLarge)
                Text(map.caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val spots = remember(map.id) { data.hotspotsOf(map.id) }
            Box(
                Modifier.padding(horizontal = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .clickable { actions.openMapView(map.id) },
                contentAlignment = Alignment.Center,
            ) {
                if (img != null) {
                    var box by remember { mutableStateOf(IntSize.Zero) }
                    Image(img, map.title, Modifier.fillMaxWidth().onSizeChanged { box = it }, contentScale = ContentScale.FillWidth)
                    if (box.width > 0) {
                        val s = box.width.toFloat() / img.width
                        spots.forEach { h -> HotspotMarker(h, (h.x * s).roundToInt(), (h.y * s).roundToInt()) { selected = h } }
                    }
                } else Box(Modifier.height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                Icon(Icons.Filled.ZoomIn, "확대", Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(8.dp)).padding(4.dp))
            }
            Text("표시된 점을 누르면 연관 일정·주소·택시 카드가 뜬다. 개략도이므로 실제 위치는 고덕지도로 확인한다. 지도를 누르면 전체 화면.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            if (spots.isNotEmpty()) FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                spots.forEach { h -> SuggestionChip(onClick = { selected = h }, label = { Text(h.label, style = MaterialTheme.typography.bodySmall) }) }
            }
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
            item {
                SectionTitle("번호별 식당 (남문 / 호텔 기준 거리)")
                FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("전체") + map.pins.map { it.kind }.distinct()).forEach { k ->
                        FilterChip(pinKind == k, onClick = { pinKind = k }, label = { Text(k) })
                    }
                }
            }
            val shown = map.pins.filter { pinKind == "전체" || it.kind == pinKind }
            items(shown, key = { it.no }) { pin ->
                val opt = remember(pin) { data.findMealOption(pin.nameZh)?.second }
                val spot = remember(pin) { data.hotspotsOf(map.id).firstOrNull { h -> h.label.endsWith(pin.nameKo) } }
                val badge = remember(pin) { runCatching { Color(android.graphics.Color.parseColor(pin.color)) }.getOrDefault(Color(0xFF6E6A64)) }
                ListItem(
                    leadingContent = {
                        Box(Modifier.size(30.dp).background(badge, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                            Text("${pin.no}", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    headlineContent = { Text(pin.nameKo) },
                    supportingContent = {
                        Text(listOf(pin.kind, pin.nameZh, "남문 ${pin.distance.removePrefix("약 ")}", pin.hotelDistance.takeIf { it.isNotBlank() }?.let { "호텔 ${it.removePrefix("약 ")}" })
                            .filterNotNull().joinToString(" · "))
                    },
                    trailingContent = { if (opt != null) Icon(Icons.Filled.LocalTaxi, "택시 카드", tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable(enabled = spot != null || opt != null) {
                        if (spot != null) selected = spot else opt?.let { actions.openTaxi(TargetType.meal, it.id) }
                    },
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
    val spots = remember(map.id) { data.hotspotsOf(map.id) }
    var selected by remember { mutableStateOf<Hotspot?>(null) }
    selected?.let { h -> HotspotSheet(data, h, null) { selected = null } }

    /** ContentScale.Fit 기준: 이미지 픽셀 → 화면 좌표(변환 전) */
    fun fitRect(): Triple<Float, Float, Float>? {
        val im = img ?: return null
        if (size.width == 0) return null
        val s = minOf(size.width.toFloat() / im.width, size.height.toFloat() / im.height)
        val ox = (size.width - im.width * s) / 2f
        val oy = (size.height - im.height * s) / 2f
        return Triple(s, ox, oy)
    }
    fun toScreen(px: Float, py: Float): Offset? {
        val (s, ox, oy) = fitRect() ?: return null
        // graphicsLayer: 중심 기준 scale 후 translation
        val cx = size.width / 2f; val cy = size.height / 2f
        val x = (ox + px * s - cx) * scale + cx + offset.x
        val y = (oy + py * s - cy) * scale + cy + offset.y
        return Offset(x, y)
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
                .pointerInput(spots) {
                    detectTapGestures(
                        onDoubleTap = { scale = if (scale > 1.1f) 1f else 2.5f; offset = clamp(Offset.Zero, scale) },
                        onTap = { tap ->
                            val hit = spots.mapNotNull { h -> toScreen(h.x, h.y)?.let { p -> h to (p - tap).getDistance() } }
                                .filter { it.second < 28.dp.toPx() }.minByOrNull { it.second }?.first
                            if (hit != null) selected = hit
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            img?.let {
                Image(it, map.title, Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                    contentScale = ContentScale.Fit)
            } ?: CircularProgressIndicator()
            if (img != null) spots.forEach { h ->
                toScreen(h.x, h.y)?.let { p -> HotspotMarker(h, p.x.roundToInt(), p.y.roundToInt(), small = true) { selected = h } }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.85f)).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.back) { Icon(Icons.Filled.Close, "닫기", tint = Color.Black) }
            Text(map.title, color = Color.Black, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("두 손가락 확대 · 두 번 탭", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp))
        }
    }
}


/** 지도 위 누르는 자리: 그림에 이미 점·번호가 그려져 있으므로 가리지 않게 색 테두리 고리만 그린다.
 *  숙소 빨강, 식당 주황, 장소 청록, 기타 회색. 부모 Box 의 좌상단 기준 픽셀 위치에 놓는다. */
@Composable
private fun BoxScope.HotspotMarker(h: Hotspot, cx: Int, cy: Int, small: Boolean = false, onClick: () -> Unit) {
    val color = when (h.type) { "lodging" -> Color(0xFFB4442E); "meal" -> Color(0xFFE09A2B); "place" -> Color(0xFF1F6F6B); else -> Color(0xFF6E6A64) }
    val d = if (small) 22.dp else 26.dp
    val half = with(LocalDensity.current) { (d / 2).roundToPx() }
    Box(
        Modifier.align(Alignment.TopStart).offset { IntOffset(cx - half, cy - half) }
            .size(d).border(2.dp, color.copy(alpha = 0.75f), androidx.compose.foundation.shape.CircleShape)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HotspotSheet(data: TripData, h: Hotspot, dayId: String?, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val info = remember(h, dayId) { HotspotResolver.resolve(data, h, dayId) }
    ModalBottomSheet(onDismissRequest = onDismiss) { HotspotSheetContent(info, onDismiss) }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HotspotSheetContent(info: HotspotInfo, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(info.title, style = MaterialTheme.typography.titleLarge)
            if (info.zh.isNotBlank()) Text(info.zh, fontSize = 22.sp, fontWeight = TextWeight.SemiBold)
            if (info.addrZh.isNotBlank()) Text(info.addrZh, style = MaterialTheme.typography.bodyLarge)
            KoGloss(info.addrZh)
            if (info.note.isNotBlank()) Text(info.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                info.taxi?.let { (t, id) -> SmallAction(Icons.Filled.LocalTaxi, "택시 카드") { onDismiss(); actions.openTaxi(t, id) } }
                if (info.addrZh.isNotBlank()) SmallAction(Icons.Filled.ContentCopy, "주소 복사") { External.copy(ctx, (info.zh + " " + info.addrZh).trim(), "주소를 복사했다") }
                info.amapUrl?.let { u -> SmallAction(Icons.Filled.Place, "지도") { External.openUrl(ctx, u) } }
                info.lodging?.let { l -> SmallAction(Icons.Filled.Hotel, "숙소 정보") { onDismiss(); actions.openLodgings(l.id) } }
            }
        }
        if (info.rows.isNotEmpty()) {
            item { Text("관련 일정", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            items(info.rows, key = { "r" + it.second.id }) { (d, r) ->
                Row(Modifier.fillMaxWidth().clickable { onDismiss(); actions.openSchedule(d.id) }.padding(vertical = 4.dp)) {
                    Text("${d.id} ${r.timeLabel}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(118.dp))
                    Column(Modifier.weight(1f)) {
                        Text(listOf(r.from, r.to).filter { it.isNotBlank() && it != "-" }.joinToString(" → "), style = MaterialTheme.typography.bodyMedium)
                        if (r.tip.isNotBlank() && r.tip != "-") Text(r.tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
            }
        }
        if (info.meals.isNotEmpty()) {
            item { Text("관련 식사", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            items(info.meals, key = { "m" + it.second.id }) { (m, o) ->
                Row(Modifier.fillMaxWidth().clickable { onDismiss(); actions.openMeals(m.dayId) }.padding(vertical = 4.dp)) {
                    Text("${m.dayId} ${m.slot}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(118.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.koName(), style = MaterialTheme.typography.bodyMedium)
                        if (o.menu.isNotBlank()) Text("추천: ${o.menu}" + kr.koasis.travelyunnan.data.KoGlossDict.ko(o.menu).let { if (it.isNotEmpty()) " ($it)" else "" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (info.todos.isNotEmpty()) {
            item { Text("숙소 할 일", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            items(info.todos, key = { "t" + it.id }) { Text("• ${it.text}", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
