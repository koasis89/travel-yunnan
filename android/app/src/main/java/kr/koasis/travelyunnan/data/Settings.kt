package kr.koasis.travelyunnan.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    /** 날짜·시각 강제 지정(현지 시각, 예: 2026-12-20T10:15). null 이면 실제 시각 */
    val overrideDateTime: String? = null,
    val bigFont: Boolean = false,
    val ttsSlow: Boolean = false,
    val ttsRepeat3: Boolean = false,
)

class SettingsStore(private val context: Context) {
    private object K {
        val override = stringPreferencesKey("override_datetime")
        val bigFont = booleanPreferencesKey("big_font")
        val ttsSlow = booleanPreferencesKey("tts_slow")
        val ttsRepeat = booleanPreferencesKey("tts_repeat3")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            overrideDateTime = p[K.override]?.takeIf { it.isNotBlank() },
            bigFont = p[K.bigFont] ?: false,
            ttsSlow = p[K.ttsSlow] ?: false,
            ttsRepeat3 = p[K.ttsRepeat] ?: false,
        )
    }

    suspend fun setOverride(value: String?) = context.dataStore.edit { it[K.override] = value ?: "" }
    suspend fun setBigFont(v: Boolean) = context.dataStore.edit { it[K.bigFont] = v }
    suspend fun setTtsSlow(v: Boolean) = context.dataStore.edit { it[K.ttsSlow] = v }
    suspend fun setTtsRepeat(v: Boolean) = context.dataStore.edit { it[K.ttsRepeat] = v }
}
