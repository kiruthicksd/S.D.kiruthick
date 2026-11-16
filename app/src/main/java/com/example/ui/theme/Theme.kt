package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FuturisticDarkScheme =
  darkColorScheme(
    primary = NeonCyan,
    onPrimary = ObsidianBg,
    primaryContainer = Color(0xFF003B4A),
    onPrimaryContainer = IceWhite,
    secondary = ElectricBlue,
    onSecondary = IceWhite,
    secondaryContainer = Color(0xFF102A5C),
    onSecondaryContainer = ElectricBlueSoft,
    tertiary = NebulaViolet,
    onTertiary = IceWhite,
    background = ObsidianBg,
    onBackground = IceWhite,
    surface = DeepNavySurface,
    onSurface = IceWhite,
    surfaceVariant = GlassCardBg,
    onSurfaceVariant = SlateMuted,
    surfaceContainerHigh = GlassCardElevated,
    outline = CyanOutline,
    outlineVariant = Color(0xFF162744),
    error = ErrorCoral,
    onError = ObsidianBg,
  )

private val DaylightLightScheme =
  lightColorScheme(
    primary = DeepCyanPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF4FC),
    onPrimaryContainer = InkDark,
    secondary = DeepBlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = InkDark,
    tertiary = Color(0xFF6D28D9),
    onTertiary = Color.White,
    background = DaylightBg,
    onBackground = InkDark,
    surface = DaylightSurface,
    onSurface = InkDark,
    surfaceVariant = DaylightCard,
    onSurfaceVariant = InkMuted,
    surfaceContainerHigh = DaylightCard,
    outline = DaylightOutline,
    outlineVariant = Color(0xFFD5E3F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
  )

val AppShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun SmartAiMapTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) FuturisticDarkScheme else DaylightLightScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    shapes = AppShapes,
    content = content,
  )
}
