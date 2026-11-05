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
    /** 위안 환율(원). null 이면 rates.json 값 */
    val cnyRate: Double? = null,
    /** 가져온 현금(위안) */
    val cashCny: Double = 0.0,
    /** 끈 알림 id */
    val disabledNotifications: Set<String> = emptySet(),
    val notificationsRegistered: Boolean = false,
)

class SettingsStore(private val context: Context) {
    private object K {
        val override = stringPreferencesKey("override_datetime")
        val bigFont = booleanPreferencesKey("big_font")
        val ttsSlow = booleanPreferencesKey("tts_slow")
        val ttsRepeat = booleanPreferencesKey("tts_repeat3")
        val cnyRate = stringPreferencesKey("cny_rate")
        val cash = stringPreferencesKey("cash_cny")
        val disabledNotif = stringPreferencesKey("disabled_notifications")
        val notifRegistered = booleanPreferencesKey("notifications_registered")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            overrideDateTime = p[K.override]?.takeIf { it.isNotBlank() },
            bigFont = p[K.bigFont] ?: false,
            ttsSlow = p[K.ttsSlow] ?: false,
            ttsRepeat3 = p[K.ttsRepeat] ?: false,
            cnyRate = p[K.cnyRate]?.toDoubleOrNull(),
            cashCny = p[K.cash]?.toDoubleOrNull() ?: 0.0,
            disabledNotifications = p[K.disabledNotif]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
            notificationsRegistered = p[K.notifRegistered] ?: false,
        )
    }

    suspend fun setOverride(value: String?) = context.dataStore.edit { it[K.override] = value ?: "" }
    suspend fun setBigFont(v: Boolean) = context.dataStore.edit { it[K.bigFont] = v }
    suspend fun setTtsSlow(v: Boolean) = context.dataStore.edit { it[K.ttsSlow] = v }
    suspend fun setTtsRepeat(v: Boolean) = context.dataStore.edit { it[K.ttsRepeat] = v }
    suspend fun setCnyRate(v: Double?) = context.dataStore.edit { it[K.cnyRate] = v?.toString() ?: "" }
    suspend fun setCash(v: Double) = context.dataStore.edit { it[K.cash] = v.toString() }
    suspend fun setDisabledNotifications(ids: Set<String>) = context.dataStore.edit { it[K.disabledNotif] = ids.joinToString(",") }
    suspend fun setNotificationsRegistered(v: Boolean) = context.dataStore.edit { it[K.notifRegistered] = v }
}
