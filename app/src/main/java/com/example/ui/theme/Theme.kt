package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val XThrexDarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = Color.White,
    primaryContainer = ElectricVioletSoft,
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = NeonCyan,
    onSecondary = Color(0xFF042F3B),
    secondaryContainer = NeonCyanSoft,
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = EmeraldSuccess,
    onTertiary = Color(0xFF022C22),
    background = NavyBg,
    onBackground = TextPrimaryDark,
    surface = CharcoalSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = CharcoalSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = CharcoalElevated,
    outline = SlateBorder,
    error = CoralError,
    onError = Color.White,
)

private val XThrexLightColorScheme = lightColorScheme(
    primary = VioletLightPrimary,
    onPrimary = Color.White,
    primaryContainer = VioletLightContainer,
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = CyanLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = CyanLightContainer,
    onSecondaryContainer = Color(0xFF083344),
    tertiary = EmeraldSuccess,
    onTertiary = Color.White,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerHigh = Color(0xFFE2E8F0),
    outline = LightBorder,
    error = CoralError,
    onError = Color.White,
)

val XThrexShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun XThrexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) XThrexDarkColorScheme else XThrexLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = XThrexTypography,
        shapes = XThrexShapes,
        content = content,
    )
}
