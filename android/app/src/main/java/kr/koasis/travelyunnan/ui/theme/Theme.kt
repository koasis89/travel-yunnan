package kr.koasis.travelyunnan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

// 옥룡설산 옥빛(teal) + 고원 햇빛(amber) + 사원 붉은색(accent)
private val Teal = Color(0xFF1F6F6B)
private val TealLight = Color(0xFF7FC8C1)
private val Amber = Color(0xFFE09A2B)
private val Red = Color(0xFFB4442E)

private val Light = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    primaryContainer = Color(0xFFD3ECE8), onPrimaryContainer = Color(0xFF00201E),
    secondary = Amber, onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE6C2), onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = Red, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFADCD4), onTertiaryContainer = Color(0xFF3B0A02),
    background = Color(0xFFF7F4EE), onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFFFFCF7), onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFEAE4DA), onSurfaceVariant = Color(0xFF4D4740),
    outline = Color(0xFF8A8278),
)
private val Dark = darkColorScheme(
    primary = TealLight, onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF13504C), onPrimaryContainer = Color(0xFFD3ECE8),
    secondary = Color(0xFFF2C47A), onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF5E4100), onSecondaryContainer = Color(0xFFFCE6C2),
    tertiary = Color(0xFFF2B4A4), onTertiary = Color(0xFF5E1607),
    tertiaryContainer = Color(0xFF7F2C1B), onTertiaryContainer = Color(0xFFFADCD4),
    background = Color(0xFF151413), onBackground = Color(0xFFE8E2D9),
    surface = Color(0xFF1D1C1A), onSurface = Color(0xFFE8E2D9),
    surfaceVariant = Color(0xFF3A3631), onSurfaceVariant = Color(0xFFCFC6BA),
    outline = Color(0xFF9A9186),
)

// 기본 본문 18sp, 제목 22sp (S2-6)
private val AppTypography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = bodyMedium.copy(fontSize = 16.sp, lineHeight = 23.sp),
        bodySmall = bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
        titleLarge = titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontSize = 15.sp),
    )
}

/** 보여주기 모드 글자(상호 36sp, 주소 28sp) */
object ShowText {
    val name = TextStyle(fontSize = 38.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold)
    val address = TextStyle(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium)
    val phrase = TextStyle(fontSize = 40.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold)
    val longPhrase = TextStyle(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun TravelTheme(bigFont: Boolean = false, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * if (bigFont) 1.3f else 1f)) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
    }
}
