package kr.koasis.travelyunnan.ui.emergency

import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.secure.SecureStore
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.common.*
import kr.koasis.travelyunnan.ui.nav.LocalActions
import kr.koasis.travelyunnan.util.External
import kr.koasis.travelyunnan.util.findActivity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Serializable
data class Passport(val role: String = "", val nameKo: String = "", val surname: String = "", val given: String = "", val number: String = "", val expiry: String = "")

@Serializable
data class SecureData(val passports: List<Passport> = listOf(Passport("아빠"), Passport("첫째"), Passport("둘째")), val insurance: String = "", val memo: String = "")

private const val KEY = "secure_data"
private val json = Json { ignoreUnknownKeys = true }

fun loadSecure(store: SecureStore): SecureData = store.get(KEY)?.let { runCatching { json.decodeFromString(SecureData.serializer(), it) }.getOrNull() } ?: SecureData()
fun saveSecure(store: SecureStore, d: SecureData) = store.put(KEY, json.encodeToString(SecureData.serializer(), d))

/** 출국일 기준 잔여 개월. 6개월 미만이면 경고 */
fun monthsLeft(expiry: String, departure: LocalDate): Long? = runCatching { ChronoUnit.MONTHS.between(departure, LocalDate.parse(expiry)) }.getOrNull()

@Composable
fun EmergencyScreen(vm: AppViewModel, data: TripData) {
    val ctx = LocalContext.current
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val activity = ctx.findActivity() as? FragmentActivity
    val canAuth = remember { BiometricManager.from(ctx).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS }

    // 화면 캡처 차단(여권 정보)
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    fun authenticate() {
        if (activity == null) { error = "인증 화면을 열 수 없다"; return }
        if (!canAuth) { unlocked = true; return } // 잠금 설정이 없는 기기는 그대로 연다
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(ctx), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true; error = null }
            override fun onAuthenticationError(code: Int, msg: CharSequence) { error = msg.toString() }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle("여권 정보 잠금 해제").setSubtitle("지문·얼굴 또는 기기 잠금")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL).build()
        )
    }

    EmergencyContent(
        data = data, unlocked = unlocked, lockedMessage = error,
        onUnlock = ::authenticate, onLock = { unlocked = false },
        load = { loadSecure(vm.secure) }, save = { saveSecure(vm.secure, it) }, canAuth = canAuth,
    )
}

@Composable
fun EmergencyContent(
    data: TripData, unlocked: Boolean, lockedMessage: String?, onUnlock: () -> Unit, onLock: () -> Unit,
    load: () -> SecureData, save: (SecureData) -> Unit, canAuth: Boolean,
) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val groups = remember(data) { data.emergency.contacts.groupBy { it.group } }
    val phrases = remember(data) { data.phrasesIn("emergency") }
    val departure = remember(data) { LocalDate.parse(data.days.first().date) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("긴급·여권", onBack = actions.back)
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction(Icons.Filled.LocalHospital, "구급차 120") { External.dial(ctx, "120") }
                    SmallAction(Icons.Filled.LocalPolice, "경찰 110") { External.dial(ctx, "110") }
                    SmallAction(Icons.Filled.Call, "영사콜센터") { External.dial(ctx, "+82-2-3210-0404") }
                }
            }
            item { SectionTitle("도움 요청 문장 (누르면 크게)") }
            items(phrases, key = { it.id }) { p ->
                ListItem(
                    headlineContent = { Text(p.zh, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text("${p.ko} · ${p.koRead}") },
                    modifier = Modifier.clickable { actions.openShow(p.id) },
                )
            }
            groups.forEach { (g, list) ->
                item { SectionTitle(g) }
                items(list, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text(c.name) },
                        supportingContent = { Text(listOf(c.phone, c.note).filter { it.isNotBlank() }.joinToString(" · ")) },
                        trailingContent = { IconButton(onClick = { External.dial(ctx, c.phone) }) { Icon(Icons.Filled.Call, "전화", tint = MaterialTheme.colorScheme.primary) } },
                    )
                }
            }
            item {
                Text(data.emergency.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
            item { SectionTitle("고산병") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    data.emergency.altitude.forEach { InfoBanner(it, Icons.Filled.Terrain) }
                }
            }
            item { SectionTitle("여권 (잠금)") }
            item {
                if (!unlocked) {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (canAuth) "지문·얼굴 또는 기기 잠금으로 확인한 뒤 볼 수 있다. 이 화면은 캡처가 막힌다." else "기기에 화면 잠금이 없어 바로 연다. 화면 잠금 설정을 권장한다.",
                            style = MaterialTheme.typography.bodyMedium)
                        lockedMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(onClick = onUnlock) { Icon(Icons.Filled.Fingerprint, null); Spacer(Modifier.width(6.dp)); Text("잠금 해제") }
                    }
                } else {
                    PassportEditor(departure, load, save, onLock)
                }
            }
        }
    }
}

@Composable
private fun PassportEditor(departure: LocalDate, load: () -> SecureData, save: (SecureData) -> Unit, onLock: () -> Unit) {
    var d by remember { mutableStateOf(load()) }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("영문 성명은 여권과 동일하게. 출국일 ${departure} 기준 잔여 6개월 이상 권장.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        d.passports.forEachIndexed { i, p ->
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.role.ifBlank { "여행자 ${i + 1}" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        monthsLeft(p.expiry, departure)?.let { m ->
                            AssistChip(onClick = {}, label = { Text(if (m >= 6) "잔여 ${m}개월" else "잔여 ${m}개월 · 6개월 미만!") },
                                colors = AssistChipDefaults.assistChipColors(containerColor = if (m >= 6) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer))
                        }
                    }
                    fun upd(f: (Passport) -> Passport) { d = d.copy(passports = d.passports.mapIndexed { j, q -> if (j == i) f(q) else q }); saved = false }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(p.role, { v -> upd { it.copy(role = v) } }, label = { Text("구분") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(p.nameKo, { v -> upd { it.copy(nameKo = v) } }, label = { Text("한글 성명") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(p.surname, { v -> upd { it.copy(surname = v.uppercase()) } }, label = { Text("영문 성") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(p.given, { v -> upd { it.copy(given = v.uppercase()) } }, label = { Text("영문 이름") }, modifier = Modifier.weight(1.4f), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(p.number, { v -> upd { it.copy(number = v.uppercase()) } }, label = { Text("여권번호") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(p.expiry, { v -> upd { it.copy(expiry = v) } }, label = { Text("만료일 YYYY-MM-DD") }, modifier = Modifier.weight(1.2f), singleLine = true)
                    }
                }
            }
        }
        OutlinedTextField(d.insurance, { d = d.copy(insurance = it); saved = false }, label = { Text("여행자보험 (보험사·증권번호·연락처)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(d.memo, { d = d.copy(memo = it); saved = false }, label = { Text("메모 (혈액형·복용약·알레르기 등)") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { save(d); saved = true }) { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(6.dp)); Text(if (saved) "저장됨" else "저장") }
            OutlinedButton(onClick = onLock) { Icon(Icons.Filled.Lock, null); Spacer(Modifier.width(6.dp)); Text("잠그기") }
        }
    }
}
