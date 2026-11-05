package kr.koasis.travelyunnan.ui.phrase

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.koasis.travelyunnan.data.AppSettings
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.json.Phrase
import kr.koasis.travelyunnan.tts.TtsPlayer
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.common.*
import kr.koasis.travelyunnan.ui.nav.LocalActions
import kr.koasis.travelyunnan.util.External

/** 회화 탭에서 공통으로 쓰는 항목(문장·단어·음식명) */
data class SpeakItem(val id: String, val zh: String, val pinyin: String, val koRead: String, val ko: String, val copyOnly: Boolean = false)

private fun Phrase.toItem() = SpeakItem(id, zh, pinyin, koRead, ko, copyOnly)

private const val TAB_WORDS = "_words"
private const val TAB_FOODS = "_foods"
private const val TAB_FAV = "_fav"
private const val TAB_RECENT = "_recent"

class PhraseCallbacks(
    val speak: (SpeakItem) -> Unit = {},
    val toggleFav: (String) -> Unit = {},
    val setSlow: (Boolean) -> Unit = {},
    val setRepeat: (Boolean) -> Unit = {},
    val installTts: () -> Unit = {},
)

@Composable
fun PhrasesScreen(vm: AppViewModel, data: TripData, catArg: String?) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val favs by vm.favs.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val cb = remember(vm) {
        PhraseCallbacks(
            speak = { item -> if (!vm.speak(item.id, item.zh)) ttsHelp(ctx, vm.tts) },
            toggleFav = { vm.toggleFav(it) },
            setSlow = { vm.setTtsSlow(it) },
            setRepeat = { vm.setTtsRepeat(it) },
            installTts = { runCatching { ctx.startActivity(vm.tts.installDataIntent()) } },
        )
    }
    PhrasesContent(data, catArg, settings, favs, recents, vm.tts.status == TtsPlayer.Status.MissingData, cb)
}

private fun ttsHelp(ctx: Context, tts: TtsPlayer) {
    val msg = when (tts.status) {
        TtsPlayer.Status.Initializing -> "음성 엔진을 준비 중이다. 잠시 후 다시 누른다"
        TtsPlayer.Status.MissingData -> "중국어 음성 데이터가 없다. 상단 안내에서 설치한다"
        else -> "이 기기에서 음성 재생을 할 수 없다"
    }
    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
}

@Composable
fun PhrasesContent(
    data: TripData, catArg: String?, settings: AppSettings, favs: List<String>, recents: List<String>,
    ttsMissing: Boolean, cb: PhraseCallbacks,
) {
    val tabs = remember(data) {
        data.phraseCategories.map { it.id to it.title } +
            listOf(TAB_WORDS to "단어장", TAB_FOODS to "음식·식당명", TAB_FAV to "★ 즐겨찾기", TAB_RECENT to "최근")
    }
    var tab by rememberSaveable(catArg) { mutableStateOf(catArg ?: tabs.first().first) }
    val items: List<SpeakItem> = when (tab) {
        TAB_WORDS -> data.words.map { SpeakItem(it.id, it.zh, it.pinyin, it.koRead, it.ko) }
        TAB_FOODS -> data.foodNames.map { SpeakItem(it.id, it.zh, it.pinyin, it.koRead, it.ko) }
        TAB_FAV -> favs.mapNotNull { data.phrase(it)?.toItem() }
        TAB_RECENT -> recents.mapNotNull { data.phrase(it)?.toItem() }
        else -> data.phrasesIn(tab).map { it.toItem() }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar("회화")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tabs, key = { it.first }) { (id, title) ->
                FilterChip(selected = tab == id, onClick = { tab = id }, label = { Text(title) })
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("재생", style = MaterialTheme.typography.labelLarge); Spacer(Modifier.width(8.dp))
            FilterChip(settings.ttsSlow, onClick = { cb.setSlow(!settings.ttsSlow) }, label = { Text("느리게") })
            Spacer(Modifier.width(8.dp))
            FilterChip(settings.ttsRepeat3, onClick = { cb.setRepeat(!settings.ttsRepeat3) }, label = { Text("3회 반복") })
        }
        if (ttsMissing) Box(Modifier.padding(horizontal = 16.dp)) {
            Column {
                InfoBanner("중국어 음성 데이터가 없다. 설치하면 소리로 들을 수 있다.", Icons.Filled.RecordVoiceOver)
                TextButton(onClick = cb.installTts) { Text("음성 데이터 설치") }
            }
        }
        if (items.isEmpty()) EmptyState(if (tab == TAB_FAV) "문장의 별을 누르면 여기에 모인다" else "아직 없다")
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { item ->
                PhraseCard(item, fav = item.id in favs, canFav = data.phrase(item.id) != null, cb = cb)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PhraseCard(item: SpeakItem, fav: Boolean, canFav: Boolean, cb: PhraseCallbacks) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val showable = canFav // 회화 문장만 보여주기 화면 지원
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = { if (showable) actions.openShow(item.id) }, onLongClick = { if (showable) actions.openShow(item.id) })) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.zh, fontSize = if (item.copyOnly) 17.sp else 24.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = if (item.copyOnly) 4 else Int.MAX_VALUE)
                if (!item.copyOnly && item.pinyin.isNotBlank()) Text(item.pinyin, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                if (item.koRead.isNotBlank()) Text(item.koRead, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.ko, style = MaterialTheme.typography.bodyLarge, maxLines = if (item.copyOnly) 3 else Int.MAX_VALUE)
                if (item.copyOnly) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    SmallAction(Icons.Filled.ContentCopy, "복사") { External.copy(ctx, item.zh, "문장을 복사했다") }
                    SmallAction(Icons.AutoMirrored.Filled.Chat, "위챗으로") { External.openWechat(ctx, item.zh) }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!item.copyOnly) IconButton(onClick = { cb.speak(item) }) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, "듣기", tint = MaterialTheme.colorScheme.primary)
                }
                if (canFav) IconButton(onClick = { cb.toggleFav(item.id) }) {
                    Icon(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, "즐겨찾기",
                        tint = if (fav) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/** 문장 보여주기(S3-5): 중국어만 크게 */
@Composable
fun ShowPhraseScreen(vm: AppViewModel, data: TripData, id: String) {
    val ctx = LocalContext.current
    ShowPhraseContent(data, id, onSpeak = { p -> if (!vm.speak(p.id, p.zh)) ttsHelp(ctx, vm.tts) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShowPhraseContent(data: TripData, id: String, onSpeak: (Phrase) -> Unit = {}) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val p = data.phrase(id)
    if (p == null) { Column { AppTopBar("보여주기", onBack = actions.back); EmptyState("문장을 찾지 못했다") }; return }
    val long = p.copyOnly || p.zh.length > 30
    val lines = listOfNotNull(
        ShowLine(p.zh, if (long) ShowStyle.Long else ShowStyle.Phrase),
        if (!long) ShowLine(p.pinyin, ShowStyle.Small) else null,
        ShowLine(p.ko, if (long) ShowStyle.Small else ShowStyle.Sub),
    )
    ShowCardScreen(lines, onClose = actions.back, header = if (p.copyOnly) "매니저에게 보여주세요" else "보여주기") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!p.copyOnly) SmallAction(Icons.AutoMirrored.Filled.VolumeUp, "듣기") { onSpeak(p) }
            SmallAction(Icons.Filled.ContentCopy, "복사") { External.copy(ctx, p.zh, "문장을 복사했다") }
            if (p.copyOnly) SmallAction(Icons.AutoMirrored.Filled.Chat, "위챗으로") { External.openWechat(ctx, p.zh) }
        }
    }
}
