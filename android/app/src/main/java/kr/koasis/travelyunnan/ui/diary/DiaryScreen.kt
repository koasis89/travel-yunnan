package kr.koasis.travelyunnan.ui.diary

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.db.DiaryEntry
import kr.koasis.travelyunnan.domain.DayResolver
import kr.koasis.travelyunnan.domain.TripPhase
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.common.*
import kr.koasis.travelyunnan.ui.nav.LocalActions
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 사진 일기(S6-5): 일차별 사진·메모. 사진은 복사하지 않고 URI 만 저장한다(영구 권한). */
@Composable
fun DiaryScreen(vm: AppViewModel, data: TripData, dayArg: String?) {
    val list by vm.diary.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val flags by vm.flags.collectAsStateWithLifecycle()
    val backable = list.filter { it.uri != null || it.memo.isNotBlank() }
    DiaryContent(data, list, now, dayArg, driveBar = { kr.koasis.travelyunnan.ui.common.DriveBar(vm, backable.count { "drive:diary:${it.id}" in flags }, backable.size, docs = false, diary = true) },
        onAdd = { vm.addDiary(it) }, onUpdate = { vm.updateDiary(it) }, onDelete = { vm.deleteDiary(it) },
        persist = { uri -> runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } },
        loadImage = { uri -> runCatching { ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() })
}

@Composable
fun DiaryContent(
    data: TripData, list: List<DiaryEntry>, now: LocalDateTime, dayArg: String?,
    onAdd: (DiaryEntry) -> Unit, onUpdate: (DiaryEntry) -> Unit, onDelete: (DiaryEntry) -> Unit,
    persist: (Uri) -> Unit, loadImage: (Uri) -> ImageBitmap?, driveBar: @Composable () -> Unit = {},
) {
    val actions = LocalActions.current
    val todayId = (DayResolver.resolve(now, data.days) as? TripPhase.OnTrip)?.day?.id
    var day by rememberSaveable(dayArg) { mutableStateOf(dayArg ?: todayId ?: data.days.first().id) }
    val entries = list.filter { it.dayId == day }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persist(it); onAdd(DiaryEntry(dayId = day, uri = it.toString())) }
    }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("사진 일기", onBack = actions.back)
        DayChips(data.days, day, { if (it != null) day = it }, todayId = todayId)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(Icons.Filled.AddPhotoAlternate, "사진 추가") { picker.launch(arrayOf("image/*")) }
            SmallAction(Icons.Filled.NoteAdd, "메모 추가") { onAdd(DiaryEntry(dayId = day, memo = "")) }
        }
        driveBar()
        if (entries.isEmpty()) EmptyState("${day}의 사진·메모가 없다")
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.id }) { e -> DiaryCard(e, onUpdate, onDelete, loadImage) }
        }
    }
}

@Composable
private fun DiaryCard(e: DiaryEntry, onUpdate: (DiaryEntry) -> Unit, onDelete: (DiaryEntry) -> Unit, loadImage: (Uri) -> ImageBitmap?) {
    var memo by remember(e.id) { mutableStateOf(e.memo) }
    val img = remember(e.uri) { e.uri?.let { loadImage(Uri.parse(it)) } }
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.at), ZoneId.of("Asia/Shanghai"))
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            img?.let { Image(it, null, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) }
            if (e.uri != null && img == null) Text("사진을 불러올 수 없다(삭제되었거나 권한 없음)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(memo, { memo = it }, label = { Text("메모") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.format(DateTimeFormatter.ofPattern("M/d HH:mm")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (memo != e.memo) TextButton(onClick = { onUpdate(e.copy(memo = memo)) }) { Text("저장") }
                IconButton(onClick = { onDelete(e) }) { Icon(Icons.Filled.Delete, "삭제", tint = MaterialTheme.colorScheme.outline) }
            }
        }
    }
}
