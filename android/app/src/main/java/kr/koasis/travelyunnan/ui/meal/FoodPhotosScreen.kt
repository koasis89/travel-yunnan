package kr.koasis.travelyunnan.ui.meal

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.koasis.travelyunnan.data.FoodPhotoStore
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.ui.common.AppTopBar
import kr.koasis.travelyunnan.ui.nav.LocalActions
import java.io.File

/** 음식 사진(웹 pages/FoodPhotos.tsx): 이 기기에 넣은 리장 음식 사진을 1줄 3장으로 보고, 누르면 크게 본다 */
@Composable
fun FoodPhotosScreen(data: TripData, foodId: String? = null) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val all by produceState<List<String>?>(null, version) { value = withContext(Dispatchers.IO) { FoodPhotoStore.ids(ctx) } }
    val ids = all?.let { a -> foodId?.let { data.photoIdsForFood(it, a) } ?: a }
    val food = foodId?.let { id -> data.foods.firstOrNull { it.id == id } }
    var filter by remember { mutableStateOf("all") }
    var open by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    val name = { id: String -> data.foodPhotoName(id)?.takeIf { it.nameKo.isNotBlank() } }
    // 음식 사진(같은 음식끼리) → 한 상 차림 → 가게 외관 → 이름 모르는 사진
    val rank = { id: String -> val n = name(id)?.nameKo; when { n == null -> 3; n.startsWith("가게 외관") -> 2; n == "한 상 차림" -> 1; else -> 0 } }
    val sorted = remember(ids) { ids.orEmpty().sortedWith(compareBy({ rank(it) }, { name(it)?.nameKo.orEmpty() }, { it })) }
    val shown = sorted.filter { filter == "all" || (filter == "named") == (name(it) != null) }
    val named = sorted.count { name(it) != null }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = "사진을 여는 중…"
        scope.launch {
            val n = withContext(Dispatchers.IO) { runCatching { FoodPhotoStore.import(ctx, uris, data.foodPhotoFood.keys) { busy = "사진 넣는 중… $it" } }.getOrDefault(0) }
            busy = null; version++
            Toast.makeText(ctx, if (n > 0) "사진 ${n}장을 넣었다" else "넣을 사진을 찾지 못했다", Toast.LENGTH_SHORT).show()
        }
    }
    val pick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image/*")) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(food?.let { "${it.nameKo} 사진" } ?: "음식 사진", onBack = actions.back)
        when {
            ids == null -> Text("불러오는 중…", Modifier.padding(16.dp))
            ids!!.isEmpty() -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("받아 둔 리장 음식 사진 묶음(zip)이나 음식별 사진 워드(docx)를 한 번 넣으면 이 휴대폰에만 저장되어 인터넷 없이 본다. 사진을 보고 음식 이름을 알 수 있는 것은 이름을 붙여 보여 준다.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = pick, enabled = busy == null, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text(busy ?: "사진 묶음(zip·워드)·사진 넣기") }
                Text("남이 찍은 사진(携程 등)이 섞여 있어 앱·웹 서버에는 올리지 않는다. 사진은 기기마다 한 번씩 넣는다.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            else -> LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item(span = { GridItemSpan(3) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("all" to "전체 ${sorted.size}", "named" to "이름 있음 $named", "unknown" to "이름 모름 ${sorted.size - named}")) { (k, t) ->
                                FilterChip(filter == k, onClick = { filter = k }, label = { Text(t) })
                            }
                        }
                        if (data.foodPhotos.restaurant.isNotBlank()) Text("대부분 리장 ${data.foodPhotos.restaurant}(구리 솥 소과반 식당) 사진이다. 이름은 사진을 보고 붙인 것이라 틀릴 수 있다.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(shown, key = { it }) { id ->
                    val i = shown.indexOf(id)
                    Column(Modifier.clickable { open = i }) {
                        val bm = rememberPhoto(FoodPhotoStore.thumb(ctx, id))
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            bm?.let { Image(it, name(id)?.nameKo ?: id, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                        name(id)?.let { Text(it.nameKo, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
                    }
                }
                item(span = { GridItemSpan(3) }) {
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = pick, enabled = busy == null) { Icon(Icons.Filled.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text(busy ?: "사진 더 넣기") }
                        var ask by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { ask = true }) { Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text("모두 지우기") }
                        if (ask) AlertDialog(onDismissRequest = { ask = false }, text = { Text("이 기기에 넣은 음식 사진을 모두 지울까? (zip 을 다시 넣으면 된다)") },
                            confirmButton = { TextButton(onClick = { ask = false; scope.launch { withContext(Dispatchers.IO) { FoodPhotoStore.clear(ctx) }; version++ } }) { Text("지우기") } },
                            dismissButton = { TextButton(onClick = { ask = false }) { Text("그만두기") } })
                    }
                }
            }
        }
    }
    open?.let { i -> if (i in shown.indices) PhotoViewer(data, shown, i, onIndex = { open = it }, onClose = { open = null }) }
}

@Composable
private fun rememberPhoto(file: File): ImageBitmap? = produceState<ImageBitmap?>(null, file.path) {
    value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull() }
}.value

@Composable
private fun PhotoViewer(data: TripData, ids: List<String>, index: Int, onIndex: (Int) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val id = ids[index]
    val n = data.foodPhotoName(id)?.takeIf { it.nameKo.isNotBlank() }
    val go = { d: Int -> onIndex((index + d + ids.size) % ids.size) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var drag by remember(index) { mutableFloatStateOf(0f) }
        Column(Modifier.fillMaxSize().background(Color(0xF0000000)).pointerInput(index) {
            detectHorizontalDragGestures(onDragEnd = { if (kotlin.math.abs(drag) > 80) go(if (drag < 0) 1 else -1); drag = 0f }) { _, d -> drag += d }
        }) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1} / ${ids.size}", color = Color.White, modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "닫기", tint = Color.White) }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                rememberPhoto(FoodPhotoStore.full(ctx, id))?.let { Image(it, n?.nameKo ?: id, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp, 8.dp, 8.dp, 20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { go(-1) }) { Icon(Icons.Filled.ChevronLeft, "이전", tint = Color.White, modifier = Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (n != null) {
                        Text(n.nameKo, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        if (n.nameZh.isNotBlank()) Text(n.nameZh, color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center)
                    } else Text("이름 모름 · $id", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { go(1) }) { Icon(Icons.Filled.ChevronRight, "다음", tint = Color.White, modifier = Modifier.size(32.dp)) }
            }
        }
    }
}
