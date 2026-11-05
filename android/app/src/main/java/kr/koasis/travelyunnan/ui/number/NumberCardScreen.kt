package kr.koasis.travelyunnan.ui.number

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.koasis.travelyunnan.domain.ChineseNumber
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.common.AppTopBar
import kr.koasis.travelyunnan.ui.nav.LocalActions
import kr.koasis.travelyunnan.util.ShowModeEffect

/** 숫자 카드(S6-3): 흥정·결제 금액을 크게 보여주고 중국어 읽기를 함께 표시한다. */
@Composable
fun NumberCardScreen(vm: AppViewModel) {
    NumberCardContent(onSpeak = { text -> vm.speak("num-${text.hashCode()}", text) })
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NumberCardContent(onSpeak: (String) -> Boolean) {
    val actions = LocalActions.current
    var digits by rememberSaveable { mutableStateOf("150") }
    val n = digits.toIntOrNull() ?: 0
    val reading = if (n in 0..99_999) ChineseNumber.read(n) else "-"
    ShowModeEffect()
    Column(Modifier.fillMaxSize()) {
        AppTopBar("숫자 카드", onBack = actions.back)
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("¥ ${"%,d".format(n)}", fontSize = 72.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 80.sp)
            Text("${reading}块", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, lineHeight = 44.sp)
            Text(ChineseNumber.pinyinHint(n), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = { onSpeak("${reading}块") }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, null); Spacer(Modifier.width(4.dp)); Text("읽기") }
                FilledTonalButton(onClick = { onSpeak("太贵了，便宜一点吧。") }) { Text("太贵了 · 便宜一点 (비싸요, 깎아 주세요)") }
                FilledTonalButton(onClick = { onSpeak("${reading}块，可以吗？") }) { Text("${reading}块，可以吗? (…위안 괜찮아요?)") }
            }
        }
        // 키패드
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "00", "0", "⌫")
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        OutlinedButton(
                            onClick = {
                                digits = when (k) {
                                    "⌫" -> digits.dropLast(1)
                                    else -> (if (digits == "0") "" else digits) + k
                                }.take(5).ifBlank { "0" }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) { if (k == "⌫") Icon(Icons.AutoMirrored.Filled.Backspace, "지우기") else Text(k, fontSize = 22.sp) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 50, 100, 500).forEach { p -> AssistChip(onClick = { digits = p.toString() }, label = { Text("¥$p") }) }
                AssistChip(onClick = { digits = "0" }, label = { Text("지움") })
            }
        }
    }
}
