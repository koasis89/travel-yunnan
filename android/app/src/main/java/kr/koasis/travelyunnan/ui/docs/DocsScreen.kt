package kr.koasis.travelyunnan.ui.docs

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.koasis.travelyunnan.data.DocStore
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.db.TripDoc
import kr.koasis.travelyunnan.domain.DayResolver
import kr.koasis.travelyunnan.domain.DocRules
import kr.koasis.travelyunnan.domain.TripPhase
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.common.AppTopBar
import kr.koasis.travelyunnan.ui.common.DayChips
import kr.koasis.travelyunnan.ui.nav.LocalActions
import java.io.File

private val KIND_COLOR = mapOf(
    "바우처" to Color(0xFF6F42C1), "기차표" to Color(0xFF1F4E79), "항공권" to Color(0xFF4B5563), "숙소 예약" to Color(0xFFB4442E),
    "영수증" to Color(0xFFE09A2B), "보험·신분" to Color(0xFF1F6F6B), "블로그" to Color(0xFF03A94F), "기타" to Color(0xFF6E6A64),
)

@Composable
fun KindBadge(kind: String) {
    Text(kind, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(KIND_COLOR[kind] ?: Color.Gray, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
private fun rememberFileImage(file: File?, sample: Int = 1): ImageBitmap? = produceState<ImageBitmap?>(null, file?.path) {
    value = file?.takeIf { it.exists() }?.let { f ->
        withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap() }.getOrNull() }
    }
}.value

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocsScreen(vm: AppViewModel, data: TripData, dayArg: String?) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val docs by vm.docs.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val todayId = (DayResolver.resolve(now, data.days) as? TripPhase.OnTrip)?.day?.id
    var day by rememberSaveable(dayArg) { mutableStateOf(dayArg) }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = "${uris.size}건 저장 중… (PDF 는 쪽을 그림으로 펼친다)"
        scope.launch {
            val added = vm.addDocs(uris, day ?: todayId)
            busy = null
            if (added.size == 1) actions.openDoc(added.first().id, true)
        }
    }
    val shown = remember(docs, kind, day, todayId) { DocRules.sort(DocRules.filter(docs, kind, day), todayId) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("문서함", onBack = actions.back)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(arrayOf("image/*", "application/pdf")) }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("문서 추가 (사진·캡처·PDF)")
                    }
                    busy?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                }
                val flags by vm.flags.collectAsStateWithLifecycle()
                kr.koasis.travelyunnan.ui.common.DriveBar(vm, docs.count { "drive:doc:${it.id}" in flags }, docs.size, docs = true, diary = false)
                DayChips(data.days, day, { day = it }, allLabel = "전체 일차", todayId = todayId)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(kind == null, onClick = { kind = null }, label = { Text("전체") }) }
                    items(DocRules.KINDS) { k -> FilterChip(kind == k, onClick = { kind = if (kind == k) null else k }, label = { Text(k) }) }
                }
            }
            if (shown.isEmpty()) item {
                Text(if (docs.isEmpty()) "아직 문서가 없다. 바우처·기차표 예매 확인·숙소 예약 확인·영수증 캡처를 넣어 두면 인터넷이 없어도 바로 꺼내 보여 줄 수 있다." else "조건에 맞는 문서가 없다",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            items(shown, key = { it.id }) { d ->
                val thumb = rememberFileImage(DocStore.thumb(ctx, d.id))
                ListItem(
                    leadingContent = {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                            if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Filled.Description, null)
                        }
                    },
                    headlineContent = { Text(d.title, maxLines = 2) },
                    supportingContent = {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            KindBadge(d.kind)
                            Text(listOfNotNull(d.dayId?.let { if (it == todayId) "$it 오늘" else it } ?: "일차 없음", d.pages.takeIf { it > 0 }?.let { "${it}쪽" }, d.note.ifBlank { null }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    modifier = Modifier.clickable { actions.openDoc(d.id, false) },
                )
            }
            item {
                Text("문서는 이 휴대폰 안에 저장된다. 구글 드라이브 백업을 설정하면 내 드라이브 폴더에도 올린다. 다른 가족에게는 문서를 열어 공유 버튼으로 보낸다. 앱을 지우면 휴대폰 쪽은 함께 지워진다.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocViewScreen(vm: AppViewModel, data: TripData, id: String, editArg: Boolean) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val docs by vm.docs.collectAsStateWithLifecycle()
    val d = docs.firstOrNull { it.id == id }
    var edit by rememberSaveable { mutableStateOf(editArg) }
    var zoomed by remember { mutableStateOf<File?>(null) }
    if (d == null) {
        Column(Modifier.fillMaxSize()) { AppTopBar("문서", onBack = actions.back); Text("불러오는 중이거나 삭제된 문서다.", Modifier.padding(16.dp)) }
        return
    }
    val pages = remember(d) { DocStore.pages(ctx, d) }
    fun share() {
        val uri = DocStore.shareUri(ctx, d)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(d.mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), d.title))
    }
    fun openOriginal() {
        val uri = DocStore.shareUri(ctx, d)
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, d.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
    }
    zoomed?.let { f -> ZoomView(f) { zoomed = null }; return }
    Column(Modifier.fillMaxSize()) {
        AppTopBar(d.title, onBack = actions.back) {
            IconButton(onClick = { edit = !edit }) { Icon(Icons.Filled.Edit, "정보 고치기") }
            IconButton(onClick = ::share) { Icon(Icons.Filled.Share, "공유") }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (edit) item {
                DocEdit(d, data.days.map { it.id }, onSave = { vm.updateDoc(it); edit = false }, onDelete = { vm.deleteDoc(d); actions.back() })
            }
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KindBadge(d.kind)
                    Text(listOfNotNull(d.dayId ?: "일차 없음", "${(d.size / 1024).coerceAtLeast(1)}KB", d.pages.takeIf { it > 0 }?.let { "${it}쪽" }, d.note.ifBlank { null }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            itemsIndexed(pages) { i, f ->
                val img = rememberFileImage(f)
                Box(Modifier.padding(horizontal = 6.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color.White).clickable { zoomed = f }) {
                    if (img != null) Image(img, "${d.title} ${i + 1}쪽", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    else Box(Modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            if (pages.isEmpty()) item {
                Text("이 문서는 앱 안에서 미리 볼 수 없다. 아래 '원본 열기'로 휴대폰의 기본 앱에서 연다.", Modifier.padding(16.dp))
            }
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::openOriginal, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("원본 열기") }
                    OutlinedButton(onClick = ::share, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("공유") }
                }
                if (kr.koasis.travelyunnan.data.DriveBackup.ready(vm.driveConfig())) {
                    val flags by vm.flags.collectAsStateWithLifecycle()
                    val done = "drive:doc:${d.id}" in flags
                    var up by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    OutlinedButton(enabled = !done && !up, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = {
                        up = true
                        scope.launch {
                            val r = runCatching { vm.backupDoc(d) }
                            up = false
                            android.widget.Toast.makeText(ctx, r.exceptionOrNull()?.let { "올리지 못했다: ${it.message}" } ?: "드라이브에 올렸다", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) { Icon(Icons.Filled.CloudUpload, null); Spacer(Modifier.width(6.dp)); Text(if (done) "드라이브에 올렸다" else if (up) "올리는 중…" else "드라이브로 올리기") }
                }
                Text("그림을 누르면 전체 화면에서 확대한다. 검표원·가이드에게 보여 줄 때는 화면 밝기를 올린다.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun ZoomView(file: File, onClose: () -> Unit) {
    val img = rememberFileImage(file)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.White).pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 6f); offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan }
    }) {
        img?.let { Image(it, null, Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit) }
        IconButton(onClick = onClose, Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp))) {
            Icon(Icons.Filled.Close, "닫기", tint = Color.Black)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocEdit(d: TripDoc, days: List<String>, onSave: (TripDoc) -> Unit, onDelete: () -> Unit) {
    var title by remember(d.id) { mutableStateOf(d.title) }
    var kind by remember(d.id) { mutableStateOf(d.kind) }
    var day by remember(d.id) { mutableStateOf(d.dayId) }
    var note by remember(d.id) { mutableStateOf(d.note) }
    var confirm by remember { mutableStateOf(false) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("이 문서를 삭제할까?") }, text = { Text("되돌릴 수 없다.") },
        confirmButton = { TextButton(onClick = onDelete) { Text("삭제") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } })
    Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("제목") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("분류", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { DocRules.KINDS.forEach { k -> FilterChip(kind == k, onClick = { kind = k }, label = { Text(k) }) } }
            Text("일차", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(day == null, onClick = { day = null }, label = { Text("없음") })
                days.forEach { x -> FilterChip(day == x, onClick = { day = x }, label = { Text(x) }) }
            }
            OutlinedTextField(note, { note = it }, label = { Text("메모(예약번호·좌석·주의사항)") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, null); Text("삭제") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(d.copy(title = title.trim(), kind = kind, dayId = day, note = note.trim())) }, enabled = title.isNotBlank()) { Text("저장") }
            }
        }
    }
}

/** 일정 화면용: 그 일차 문서 바로가기 */
@Composable
fun DayDocsCard(vm: AppViewModel, dayId: String) {
    val actions = LocalActions.current
    val docs by vm.docs.collectAsStateWithLifecycle()
    val mine = docs.filter { it.dayId == dayId }
    if (mine.isEmpty()) return
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { if (mine.size == 1) actions.openDoc(mine.first().id, false) else actions.openDocs(dayId) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Description, null)
            Column(Modifier.weight(1f)) {
                Text("$dayId 문서 ${mine.size}건", fontWeight = FontWeight.SemiBold)
                Text(mine.take(3).joinToString(" · ") { it.title }, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}
