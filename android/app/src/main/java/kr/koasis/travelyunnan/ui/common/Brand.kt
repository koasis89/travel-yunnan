package kr.koasis.travelyunnan.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.koasis.travelyunnan.R
import kr.koasis.travelyunnan.ui.theme.Brand

const val TAGLINE = "딸들과 함께 여행을"

/** 홈 상단 브랜드 헤더: 로고 + 앱 이름 + 문구 + 상태 한 줄. 그라데이션 배경 위 흰 글자 */
@Composable
fun BrandHeader(statusLine: String, subLine: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit = {}) {
    val dark = isSystemInDarkTheme()
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(if (dark) Brand.headerGradientDark else Brand.headerGradient)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("윈난 7박 8일", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
                Text(TAGLINE, color = Color(0xFFFCE6C2), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(statusLine, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(subLine, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
        content()
    }
}
