package kr.koasis.travelyunnan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 옥룡설산 옥빛(teal) + 고원 햇빛(amber) + 사원 붉은색(accent)
object Brand {
    val Teal = Color(0xFF1F6F6B)
    val TealDeep = Color(0xFF14504D)
    val TealLight = Color(0xFF7FC8C1)
    val Mint = Color(0xFFD3ECE8)
    val Amber = Color(0xFFE09A2B)
    val Sand = Color(0xFFFCE6C2)
    val Red = Color(0xFFB4442E)
    val Rose = Color(0xFFFADCD4)
    val Paper = Color(0xFFF6F3EC)
    val Ink = Color(0xFF1C1B19)

    val headerGradient = Brush.linearGradient(listOf(TealDeep, Teal, Color(0xFF2E8F89)))
    val headerGradientDark = Brush.linearGradient(listOf(Color(0xFF0E2E2C), Color(0xFF14504D), Color(0xFF1F6F6B)))
}

private val Light = lightColorScheme(
    primary = Brand.Teal, onPrimary = Color.White,
    primaryContainer = Brand.Mint, onPrimaryContainer = Color(0xFF00201E),
    secondary = Brand.Amber, onSecondary = Color.White,
    secondaryContainer = Brand.Sand, onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = Brand.Red, onTertiary = Color.White,
    tertiaryContainer = Brand.Rose, onTertiaryContainer = Color(0xFF3B0A02),
    background = Brand.Paper, onBackground = Brand.Ink,
    surface = Color(0xFFFFFDF9), onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFECE7DD), onSurfaceVariant = Color(0xFF4D4740),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFBF9F4), surfaceContainerHigh = Color(0xFFF1EDE4),
    outline = Color(0xFF8A8278), outlineVariant = Color(0xFFDCD6CB),
    error = Color(0xFFB3261E), errorContainer = Color(0xFFF9DEDC),
)
private val Dark = darkColorScheme(
    primary = Brand.TealLight, onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF13504C), onPrimaryContainer = Brand.Mint,
    secondary = Color(0xFFF2C47A), onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF5E4100), onSecondaryContainer = Brand.Sand,
    tertiary = Color(0xFFF2B4A4), onTertiary = Color(0xFF5E1607),
    tertiaryContainer = Color(0xFF7F2C1B), onTertiaryContainer = Brand.Rose,
    background = Color(0xFF131312), onBackground = Color(0xFFE8E2D9),
    surface = Color(0xFF1B1B19), onSurface = Color(0xFFE8E2D9),
    surfaceVariant = Color(0xFF3A3631), onSurfaceVariant = Color(0xFFCFC6BA),
    surfaceContainer = Color(0xFF222220), surfaceContainerLow = Color(0xFF1B1B19), surfaceContainerHigh = Color(0xFF2C2C29),
    outline = Color(0xFF9A9186), outlineVariant = Color(0xFF45413B),
)

// 기본 본문 17sp, 제목 22sp. 보여주기 모드는 별도 스타일
private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp),
        bodyMedium = bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 보여주기 모드 글자(상호 38sp, 주소 28sp) */
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
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
