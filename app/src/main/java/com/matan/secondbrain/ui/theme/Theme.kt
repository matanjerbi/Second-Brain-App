package com.matan.secondbrain.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Ground = Color(0xFF0E1116)
    val Surface = Color(0xFF181C23)
    val Surface2 = Color(0xFF1F242C)
    val Line = Color(0xFF2A303A)
    val Text = Color(0xFFE8EAED)
    val Muted = Color(0xFF9AA3AE)
    val Lime = Color(0xFFC6F36B)
    val LimeDeep = Color(0xFF1A2014)
    val LimeChip = Color(0xFF232A1A)
    val Blue = Color(0xFF8DB4FF)
    val BlueDeep = Color(0xFF1A2333)
    val Amber = Color(0xFFF2B66B)
    val AmberDeep = Color(0xFF2B2218)
    val Pink = Color(0xFFE7A3E8)
    val PinkDeep = Color(0xFF2A1C2C)
    val Red = Color(0xFFFF8A80)
}

private val colors = darkColorScheme(
    primary = Palette.Lime,
    onPrimary = Palette.Ground,
    secondary = Palette.Blue,
    background = Palette.Ground,
    onBackground = Palette.Text,
    surface = Palette.Ground,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Surface,
    onSurfaceVariant = Palette.Muted,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface2,
    surfaceContainerHighest = Palette.Surface2,
    surfaceContainerLow = Palette.Surface,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    error = Palette.Red,
)

private val type = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelMedium = TextStyle(fontSize = 13.sp),
    labelSmall = TextStyle(fontSize = 12.sp),
)

@Composable
fun SbpTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type) {
        CompositionLocalProvider(LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            content()
        }
    }
}
