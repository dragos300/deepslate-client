package com.deepslate.cobbled.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.deepslate.cobbled.R

val SlateBg = Color(0xFF06080A)
val SlateInk = Color(0xFFF2F5F3)
val SlateText = Color(0xFFD5DDD8)
val SlateMuted = Color(0xFF8B968F)
val SlateFaint = Color(0xFF5C6660)
val SlateLine = Color(0x14FFFFFF)
val SlateLineStrong = Color(0x24FFFFFF)
val SlateGlass = Color(0x8C0A0E10)
val Accent = Color(0xFF3FD4B8)
val Accent2 = Color(0xFF2BB89E)
val AccentSoft = Color(0x243FD4B8)
val PlayTop = Color(0xFF45E0C2)
val PlayBot = Color(0xFF1FA88F)
val PlayInk = Color(0xFF04241E)
val Danger = Color(0xFFE06B6B)

@OptIn(ExperimentalTextApi::class)
private val ManropeFamily = FontFamily(
    Font(R.font.manrope, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.manrope, weight = FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

@OptIn(ExperimentalTextApi::class)
private val SoraFamily = FontFamily(
    Font(R.font.sora, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.sora, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.sora, weight = FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

val CobbledTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        lineHeight = 40.sp,
        letterSpacing = (-1.2).sp,
        color = SlateInk,
    ),
    titleLarge = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = SlateInk,
    ),
    titleMedium = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = SlateInk,
    ),
    bodyLarge = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = SlateText,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = SlateText,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        letterSpacing = 2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.2.sp,
        color = SlateMuted,
    ),
)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = PlayInk,
    secondary = Accent2,
    background = SlateBg,
    onBackground = SlateInk,
    surface = Color(0xFF0C1113),
    onSurface = SlateInk,
    surfaceVariant = Color(0xFF12181A),
    onSurfaceVariant = SlateText,
    error = Danger,
    outline = SlateLineStrong,
)

@Composable
fun CobbledTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = CobbledTypography,
        content = content,
    )
}

val DisplayFont = SoraFamily
val BodyFont = ManropeFamily
