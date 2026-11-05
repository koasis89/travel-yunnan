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
    fun addDiary(e: DiaryEntry) = viewModelScope.launch(Dispatchers.IO) { dao.addDiary(e) }
    fun updateDiary(e: DiaryEntry) = viewModelScope.launch(Dispatchers.IO) { dao.updateDiary(e) }
    fun deleteDiary(e: DiaryEntry) = viewModelScope.launch(Dispatchers.IO) { dao.deleteDiary(e) }

    val secure get() = c.secure

    val docs: StateFlow<List<TripDoc>> = flowOf(Unit).flatMapLatest { dao.docs() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 고른 파일들을 문서함에 복사한다. 하나만 넣으면 그 문서 id 를 돌려준다(정보 편집 화면으로 이동용) */
    suspend fun addDocs(uris: List<android.net.Uri>, defaultDay: String?): List<TripDoc> = withContext(Dispatchers.IO) {
        val days = _data.value?.days.orEmpty()
        uris.mapNotNull { u -> runCatching { DocStore.import(getApplication(), u, days, defaultDay).also { dao.upsertDoc(it) } }.getOrNull() }
    }
    fun updateDoc(d: TripDoc) = viewModelScope.launch(Dispatchers.IO) { dao.upsertDoc(d) }
    fun deleteDoc(d: TripDoc) = viewModelScope.launch(Dispatchers.IO) { dao.deleteDoc(d.id); DocStore.delete(getApplication(), d.id) }

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
