package kr.koasis.travelyunnan.ui.taxi

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.domain.TargetType
import kr.koasis.travelyunnan.domain.TaxiCards
import kr.koasis.travelyunnan.domain.koName
import kr.koasis.travelyunnan.ui.common.*
import kr.koasis.travelyunnan.ui.nav.LocalActions
import kr.koasis.travelyunnan.util.External

/** 택시 카드(S3-4): 기사에게 보여주는 큰 글씨 화면 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaxiScreen(data: TripData, type: TargetType?, id: String, appInstalled: (String) -> Boolean? = { null }) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    var currentId by remember(id) { mutableStateOf(id) }
    val card = remember(type, currentId) { type?.let { TaxiCards.build(data, it, currentId) } }
    if (card == null) {
        Column { AppTopBar("택시 카드", onBack = actions.back); EmptyState("대상 없음") }
        return
    }
    val head = data.phrase("px-001")
    val hasAmap = appInstalled(External.AMAP) ?: External.isInstalled(ctx, External.AMAP)
    val hasDidi = appInstalled(External.DIDI) ?: External.isInstalled(ctx, External.DIDI)
    val target = card.addrZh.ifBlank { card.nameZh }

    val lines = buildList {
        head?.let { add(ShowLine(it.zh.trimEnd('。'), ShowStyle.Sub)); add(ShowLine(if (it.ko.isNotBlank()) "${it.pinyin} · ${it.ko}" else it.pinyin, ShowStyle.Small)) }
        add(ShowLine(card.nameZh, ShowStyle.Name))
        add(ShowLine(card.addrZh, ShowStyle.Address))
        kr.koasis.travelyunnan.data.KoGlossDict.ko(card.addrZh).takeIf { it.isNotEmpty() }?.let { add(ShowLine(it, ShowStyle.Small)) }
        card.helpers.forEach { add(ShowLine(it.zh, ShowStyle.Sub)); add(ShowLine(it.ko, ShowStyle.Small)) }
        if (card.phones.isNotEmpty()) add(ShowLine("☎ " + card.phones.joinToString("  "), ShowStyle.Sub))
        add(ShowLine(card.nameKo + if (card.bookingInfo.isNotBlank()) "\n예약 ${card.bookingInfo}" else "", ShowStyle.Small))
        card.warning?.let { add(ShowLine("⚠ $it", ShowStyle.Small)) }
    }

    ShowCardScreen(lines, onClose = actions.back, header = "기사님께 보여주세요") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(Icons.Filled.ContentCopy, "주소 복사") { External.copy(ctx, target, "주소 복사 완료") }
            // 지도: 고덕지도 링크(공유받은 위치·검색)가 있으면 그것을, 없으면 고덕지도 앱 검색
            if (card.amapUrl != null) SmallAction(Icons.Filled.Map, "지도") { External.openUrl(ctx, card.amapUrl) }
            // 지도 검색: 주소로 고덕지도 검색(앱이 있으면 앱, 없으면 웹)
            SmallAction(Icons.Filled.Search, "지도 검색") { if (hasAmap) External.openAmapSearch(ctx, target) else External.openUrl(ctx, External.amapSearchUrl(target)) }
            if (hasDidi) SmallAction(Icons.Filled.LocalTaxi, "디디") { External.openDidi(ctx, target) }
        }
        if (card.siblings.isNotEmpty()) {
            Text("다른 선택지", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                card.siblings.forEach { o ->
                    FilterChip(selected = false, onClick = { currentId = o.id }, label = { Text(o.koName().substringBefore("(")) })
                }
            }
        }
    }
}
