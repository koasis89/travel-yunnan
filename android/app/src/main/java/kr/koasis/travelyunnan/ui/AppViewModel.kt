package kr.koasis.travelyunnan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.koasis.travelyunnan.TravelApp
import kr.koasis.travelyunnan.data.AppSettings
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.db.DiaryEntry
import kr.koasis.travelyunnan.data.db.Expense
import kr.koasis.travelyunnan.data.db.TripDoc
import kr.koasis.travelyunnan.data.DocStore
import kr.koasis.travelyunnan.data.db.Flag
import kr.koasis.travelyunnan.notify.TripNotifier
import kr.koasis.travelyunnan.data.db.PhraseFav
import kr.koasis.travelyunnan.data.db.PhraseRecent
import kr.koasis.travelyunnan.data.json.Phrase
import kr.koasis.travelyunnan.domain.tripNow
import kr.koasis.travelyunnan.tts.TtsPlayer
import java.time.LocalDateTime

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TravelApp).container
    private val dao by lazy { c.db.userDao() }

    private val _data = MutableStateFlow<TripData?>(null)
    val data: StateFlow<TripData?> = _data

    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val ticker = flow { while (true) { emit(Unit); delay(30_000) } }

    /** 현지 시각(설정의 날짜 지정 모드 반영). 30초마다 갱신 */
    val now: StateFlow<LocalDateTime> = combine(ticker, settings) { _, s -> tripNow(s.overrideDateTime) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, tripNow(null))

    val flags: StateFlow<Set<String>> = flowOf(Unit).flatMapLatest { dao.flagKeys() }.map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val favs: StateFlow<List<String>> = flowOf(Unit).flatMapLatest { dao.favIds() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recents: StateFlow<List<String>> = flowOf(Unit).flatMapLatest { dao.recentIds() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val expenses: StateFlow<List<Expense>> = flowOf(Unit).flatMapLatest { dao.expenses() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            _data.value = withContext(Dispatchers.IO) { c.tripData }
            registerNotifications()
        }
    }

    /** 알림 등록(앱 시작·설정 변경 시). 권한이 없으면 등록만 해 두고 표시 시점에 건너뛴다. */
    fun registerNotifications() = viewModelScope.launch(Dispatchers.IO) {
        val d = _data.value ?: return@launch
        runCatching { TripNotifier.registerAll(getApplication(), d.notifications, settings.value.disabledNotifications) }
        c.settings.setNotificationsRegistered(true)
    }

    fun toggleNotification(id: String) = viewModelScope.launch {
        val cur = settings.value.disabledNotifications
        c.settings.setDisabledNotifications(if (id in cur) cur - id else cur + id)
        registerNotifications()
    }

    val diary: StateFlow<List<DiaryEntry>> = flowOf(Unit).flatMapLatest { dao.diary() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun addDiary(e: DiaryEntry) = viewModelScope.launch(Dispatchers.IO) {
        val id = dao.addDiary(e)
        if (kr.koasis.travelyunnan.data.DriveBackup.ready(driveConfig())) runCatching { backupDiary(e.copy(id = id)) }
    }
    fun updateDiary(e: DiaryEntry) = viewModelScope.launch(Dispatchers.IO) { dao.updateDiary(e) }
    fun deleteDiary(e: DiaryEntry) = viewModelScope.launch(Dispatchers.IO) { dao.deleteDiary(e) }

    val secure get() = c.secure

    val docs: StateFlow<List<TripDoc>> = flowOf(Unit).flatMapLatest { dao.docs() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 고른 파일들을 문서함에 복사한다. 하나만 넣으면 그 문서 id 를 돌려준다(정보 편집 화면으로 이동용) */
    suspend fun addDocs(uris: List<android.net.Uri>, defaultDay: String?): List<TripDoc> = withContext(Dispatchers.IO) {
        val days = _data.value?.days.orEmpty()
        val added = uris.mapNotNull { u -> runCatching { DocStore.import(getApplication(), u, days, defaultDay).also { dao.upsertDoc(it) } }.getOrNull() }
        // 드라이브 백업을 설정했으면 넣자마자 올린다(실패하면 나중에 '드라이브로 백업')
        if (kr.koasis.travelyunnan.data.DriveBackup.ready(driveConfig())) viewModelScope.launch(Dispatchers.IO) { added.forEach { runCatching { backupDoc(it) } } }
        added
    }

    // ---- 구글 드라이브 백업(data/DriveBackup) ----
    fun driveConfig() = kr.koasis.travelyunnan.data.DriveBackup.config(getApplication())
    fun saveDriveConfig(c: kr.koasis.travelyunnan.data.DriveBackup.Config?) = kr.koasis.travelyunnan.data.DriveBackup.save(getApplication(), c)

    /** 문서 1건을 드라이브(문서함/D3/…)로. 올렸으면 flags 'drive:doc:<id>' */
    suspend fun backupDoc(d: TripDoc) = withContext(Dispatchers.IO) {
        val f = DocStore.original(getApplication(), d)
        kr.koasis.travelyunnan.data.DriveBackup.upload(driveConfig(), "문서함", d.dayId ?: "공통",
            kr.koasis.travelyunnan.data.DriveBackup.fileName(d.dayId, d.title, d.id, d.fileName), d.mime, f.readBytes(), listOf(d.kind, d.note).filter { it.isNotBlank() }.joinToString(" · "))
        dao.setFlag(Flag("drive:doc:${d.id}"))
    }

    /** 사진 일기 1건(사진 또는 메모)을 드라이브(사진일기/D2/…)로. 올렸으면 flags 'drive:diary:<id>' */
    suspend fun backupDiary(e: DiaryEntry): Unit = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val c = driveConfig(); val name = "${e.dayId}_${java.text.SimpleDateFormat("MMdd-HHmm", java.util.Locale.KOREA).format(java.util.Date(e.at))}_${e.id}"
        if (e.uri != null) {
            val uri = android.net.Uri.parse(e.uri)
            val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("사진을 열 수 없음")
            val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
            kr.koasis.travelyunnan.data.DriveBackup.upload(c, "사진일기", e.dayId, "$name.$ext", mime, bytes, e.memo)
        } else if (e.memo.isNotBlank()) {
            kr.koasis.travelyunnan.data.DriveBackup.upload(c, "사진일기", e.dayId, "${name}_메모.txt", "text/plain", null, text = e.memo)
        } else return@withContext
        dao.setFlag(Flag("drive:diary:${e.id}"))
    }

    /** 아직 안 올린 문서·사진 일기를 모두 올린다. (올림, 실패) */
    /** force: 이미 올린 것도 다시 보낸다(드라이브에서 지웠을 때). 드라이브에 같은 이름이 있으면 그쪽에서 건너뛴다 */
    suspend fun backupAll(docsToo: Boolean = true, diaryToo: Boolean = true, force: Boolean = false, progress: (String) -> Unit = {}): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val f = flags.value; var ok = 0; var fail = 0
        val ds = if (docsToo) docs.value.filter { force || "drive:doc:${it.id}" !in f } else emptyList()
        val es = if (diaryToo) diary.value.filter { (force || "drive:diary:${it.id}" !in f) && (it.uri != null || it.memo.isNotBlank()) } else emptyList()
        val total = ds.size + es.size; var i = 0
        ds.forEach { d -> progress("업로드 중 ${++i}/$total"); if (runCatching { backupDoc(d) }.isSuccess) ok++ else fail++ }
        es.forEach { e -> progress("업로드 중 ${++i}/$total"); if (runCatching { backupDiary(e) }.isSuccess) ok++ else fail++ }
        ok to fail
    }
    fun updateDoc(d: TripDoc) = viewModelScope.launch(Dispatchers.IO) { dao.upsertDoc(d) }
    fun deleteDoc(d: TripDoc) = viewModelScope.launch(Dispatchers.IO) {
        // 드라이브에서 가져오기가 다시 들여오지 않게 기억한다(원래 id·드라이브 이름·가져온 드라이브 파일 id)
        val imported = flags.value.filter { it.startsWith("drive:imported:") && it.endsWith(":${d.id}") }.map { it.removePrefix("drive:imported:").substringBefore(':') }
        (listOf(d.id, kr.koasis.travelyunnan.data.DriveBackup.fileName(d.dayId, d.title, d.id, d.fileName)) + imported).forEach { dao.setFlag(Flag("drive:gone:$it")) }
        dao.deleteDoc(d.id); DocStore.delete(getApplication(), d.id)
    }

    /** 드라이브(문서함)에 있는데 이 휴대폰에 없는 문서 목록(가져오기 전 확인용) */
    suspend fun driveToPull(): List<kr.koasis.travelyunnan.data.DriveBackup.DriveFile> = withContext(Dispatchers.IO) {
        val D = kr.koasis.travelyunnan.data.DriveBackup
        val f = flags.value; val local = docs.value
        val imported = f.filter { it.startsWith("drive:imported:") }.map { it.removePrefix("drive:imported:").substringBefore(':') }.toSet()
        val gone = f.filter { it.startsWith("drive:gone:") }.map { it.removePrefix("drive:gone:") }.toSet()
        val localIds = local.map { it.id }.toSet()
        val localNames = local.map { D.fileName(it.dayId, it.title, it.id, it.fileName) }.toSet()
        D.list(driveConfig(), "문서함").filter { x ->
            val pid = D.parseName(x.name).id
            x.id !in imported && x.id !in gone && x.name !in gone && (pid == null || (pid !in gone && pid !in localIds)) && x.name !in localNames
        }
    }

    /** 드라이브 문서들을 이 휴대폰 문서함으로. 제목·일차·분류·메모를 이름·설명에서 되살린다. (가져옴, 실패) */
    suspend fun pullFromDrive(files: List<kr.koasis.travelyunnan.data.DriveBackup.DriveFile>, progress: (String) -> Unit = {}): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val D = kr.koasis.travelyunnan.data.DriveBackup
        val days = _data.value?.days.orEmpty(); var ok = 0; var fail = 0
        files.forEachIndexed { i, x ->
            progress("가져오는 중 ${i + 1}/${files.size}")
            runCatching {
                val (name, mime, bytes) = D.download(driveConfig(), x.id)
                val p = D.parseName(name)
                val doc = DocStore.importBytes(getApplication(), name, mime.ifBlank { x.mime }, bytes, days, null)
                val parts = x.memo.split(" · ")
                val kind = parts.firstOrNull()?.takeIf { it in kr.koasis.travelyunnan.domain.DocRules.KINDS }
                val dayId = x.sub.takeIf { Regex("^D\\d+$").matches(it) } ?: p.dayId ?: doc.dayId
                val fixed = doc.copy(title = p.title.ifBlank { doc.title }, dayId = dayId, kind = kind ?: doc.kind,
                    note = if (kind != null) parts.drop(1).joinToString(" · ") else x.memo)
                dao.upsertDoc(fixed)
                dao.setFlag(Flag("drive:imported:${x.id}:${fixed.id}")); dao.setFlag(Flag("drive:doc:${fixed.id}"))
            }.onSuccess { ok++ }.onFailure { fail++ }
        }
        ok to fail
    }

    fun updateExpense(e: Expense) = viewModelScope.launch(Dispatchers.IO) { dao.updateExpense(e) }
    fun addExpense(e: Expense) = viewModelScope.launch(Dispatchers.IO) { dao.addExpense(e) }
    fun deleteExpense(e: Expense) = viewModelScope.launch(Dispatchers.IO) { dao.deleteExpense(e) }
    fun setCnyRate(v: Double?) = viewModelScope.launch { c.settings.setCnyRate(v) }
    fun setCash(v: Double) = viewModelScope.launch { c.settings.setCash(v) }

    fun toggleFlag(key: String) = viewModelScope.launch(Dispatchers.IO) {
        if (key in flags.value) dao.clearFlag(key) else dao.setFlag(Flag(key))
    }

    fun toggleFav(id: String) = viewModelScope.launch(Dispatchers.IO) {
        if (id in favs.value) dao.removeFav(id) else dao.addFav(PhraseFav(id))
    }

    val tts: TtsPlayer get() = c.tts

    /** 재생 실패(음성 데이터 없음) 시 false */
    fun speak(id: String, text: String): Boolean {
        viewModelScope.launch(Dispatchers.IO) { dao.touchRecent(PhraseRecent(id)) }
        val s = settings.value
        return c.tts.speak(id, text, s.ttsSlow, if (s.ttsRepeat3) 3 else 1)
    }

    fun touchRecent(p: Phrase) = viewModelScope.launch(Dispatchers.IO) { dao.touchRecent(PhraseRecent(p.id)) }

    fun setOverride(v: String?) = viewModelScope.launch { c.settings.setOverride(v) }
    fun setBigFont(v: Boolean) = viewModelScope.launch { c.settings.setBigFont(v) }
    fun setTtsSlow(v: Boolean) = viewModelScope.launch { c.settings.setTtsSlow(v) }
    fun setTtsRepeat(v: Boolean) = viewModelScope.launch { c.settings.setTtsRepeat(v) }

    override fun onCleared() { c.tts.stop() }
}
