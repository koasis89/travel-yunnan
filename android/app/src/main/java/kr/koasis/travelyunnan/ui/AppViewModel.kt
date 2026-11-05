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
import kr.koasis.travelyunnan.data.db.Flag
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

    init {
        viewModelScope.launch { _data.value = withContext(Dispatchers.IO) { c.tripData } }
    }

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
