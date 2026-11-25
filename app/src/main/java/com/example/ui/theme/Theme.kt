package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.example.model.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = TealPulse,
    onPrimary = OnTealPulse,
    primaryContainer = TealPulseContainer,
    onPrimaryContainer = OnTealPulseContainer,
    secondary = SkyIndigo,
    onSecondary = OnSkyIndigo,
    secondaryContainer = SkyIndigoContainer,
    onSecondaryContainer = OnSkyIndigoContainer,
    tertiary = MintAccent,
    onTertiary = OnTealPulse,
    background = Gray900,
    onBackground = Gray100,
    surface = Gray900,
    onSurface = Gray100,
    surfaceVariant = Gray850,
    onSurfaceVariant = Gray400,
    surfaceContainerLowest = Gray900,
    surfaceContainerLow = Gray850,
    surfaceContainer = Color(0xFF313131),
    surfaceContainerHigh = Color(0xFF3A3A3A),
    surfaceContainerHighest = Gray800,
    outline = Gray600,
    outlineVariant = Gray700,
    error = CoralError,
    onError = Color(0xFF3F0B0E),
    errorContainer = CoralErrorContainer,
    onErrorContainer = Color(0xFFFEE2E2)
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = Color(0xFF1E3A8A),
    background = LightBg,
    onBackground = Color(0xFF0F172A),
    surface = LightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = LightBg,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEDF2F7),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun XRayPulseTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    dynamicColor: Boolean = true,
    customAccentColorArgb: Long? = null,
    customBackgroundColorArgb: Long? = null,
    isErrorState: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val baseScheme: ColorScheme = remember(useDark, dynamicColor, context) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (useDark) {
                    dynamicDarkColorScheme(context).copy(
                        background = Gray900,
                        surface = Gray900,
                        surfaceContainerLowest = Gray900,
                        surfaceContainerLow = Gray850,
                        surfaceContainer = Color(0xFF313131),
                        surfaceContainerHigh = Color(0xFF3A3A3A),
                        surfaceContainerHighest = Gray800
                    )
                } else {
                    dynamicLightColorScheme(context)
                }
            }
            useDark -> DarkColorScheme
            else -> LightColorScheme
        }
    }

    val accentedScheme = remember(baseScheme, dynamicColor, customAccentColorArgb, useDark) {
        if (!dynamicColor && customAccentColorArgb != null) {
            applyCustomAccent(baseScheme, Color(customAccentColorArgb), useDark)
        } else {
            baseScheme
        }
    }

    val backgroundedScheme = remember(accentedScheme, customBackgroundColorArgb) {
        if (customBackgroundColorArgb != null) {
            applyCustomBackground(accentedScheme, Color(customBackgroundColorArgb))
        } else {
            accentedScheme
        }
    }

    val finalScheme = remember(backgroundedScheme, isErrorState) {
        if (isErrorState) {
            applyConnectionErrorRedPalette(backgroundedScheme)
        } else {
            backgroundedScheme
        }
    }

    val animatedScheme = animateColorSchemeSinglePass(finalScheme)

    MaterialTheme(
        colorScheme = animatedScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

private fun applyCustomAccent(
    scheme: ColorScheme,
    rawAccent: Color,
    useDark: Boolean
): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rawAccent.toArgb(), hsl)

    val primary = Color(
        ColorUtils.HSLToColor(
            floatArrayOf(
                hsl[0],
                hsl[1].coerceAtLeast(0.35f),
                if (useDark) hsl[2].coerceIn(0.55f, 0.78f) else hsl[2].coerceIn(0.32f, 0.52f)
            )
        )
    )
    val onPrimary = if (primary.luminance() > 0.45f) Color(0xFF0A1418) else Color.White
    val primaryContainer = Color(
        ColorUtils.HSLToColor(
            floatArrayOf(
                hsl[0],
                hsl[1].coerceIn(0.3f, 0.75f),
                if (useDark) 0.22f else 0.88f
            )
        )
    )
    val onPrimaryContainer = if (useDark) {
        lerp(primary, Color.White, 0.55f)
    } else {
        lerp(primary, Color.Black, 0.6f)
    }

    val secondary = Color(
        ColorUtils.HSLToColor(
            floatArrayOf(
                (hsl[0] + 34f) % 360f,
                hsl[1].coerceAtLeast(0.35f),
                if (useDark) 0.68f else 0.44f
            )
        )
    )
    val tertiary = Color(
        ColorUtils.HSLToColor(
            floatArrayOf(
                (hsl[0] + 325f) % 360f,
                hsl[1].coerceAtLeast(0.35f),
                if (useDark) 0.7f else 0.46f
            )
        )
    )

    return scheme.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = primaryContainer,
        secondary = secondary,
        onSecondary = if (secondary.luminance() > 0.45f) Color(0xFF0A1418) else Color.White,
        secondaryContainer = lerp(secondary, scheme.surface, if (useDark) 0.72f else 0.8f),
        onSecondaryContainer = if (useDark) lerp(secondary, Color.White, 0.6f) else lerp(secondary, Color.Black, 0.6f),
        tertiary = tertiary,
        onTertiary = if (tertiary.luminance() > 0.45f) Color(0xFF0A1418) else Color.White
    )
}

private fun applyCustomBackground(
    scheme: ColorScheme,
    bgColor: Color
): ColorScheme {
    val isDarkBg = bgColor.luminance() < 0.42f
    val onBg = if (isDarkBg) Color(0xFFF3F4F6) else Color(0xFF0F172A)
    val onVariant = if (isDarkBg) Color(0xFFBDBDBD) else Color(0xFF475569)
    val surfaceLow = if (isDarkBg) lerp(bgColor, Color.White, 0.05f) else lerp(bgColor, Color.White, 0.72f)
    val surfaceContainer = if (isDarkBg) lerp(bgColor, Color.White, 0.08f) else lerp(bgColor, Color.Black, 0.05f)
    val surfaceHigh = if (isDarkBg) lerp(bgColor, Color.White, 0.12f) else lerp(bgColor, Color.White, 0.85f)
    val surfaceHighest = if (isDarkBg) lerp(bgColor, Color.White, 0.17f) else lerp(bgColor, Color.Black, 0.09f)
    val outline = if (isDarkBg) lerp(bgColor, Color.White, 0.32f) else lerp(bgColor, Color.Black, 0.24f)

    return scheme.copy(
        background = bgColor,
        onBackground = onBg,
        surface = bgColor,
        onSurface = onBg,
        surfaceVariant = surfaceLow,
        onSurfaceVariant = onVariant,
        surfaceContainerLowest = bgColor,
        surfaceContainerLow = surfaceLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHighest,
        outline = outline,
        outlineVariant = lerp(bgColor, onBg, 0.18f)
    )
}

private fun applyConnectionErrorRedPalette(scheme: ColorScheme): ColorScheme {
    val isDark = scheme.background.luminance() < 0.42f
    return if (isDark) {
        scheme.copy(
            primary = Color(0xFFEF4444),
            onPrimary = Color(0xFF450A0A),
            primaryContainer = Color(0xFF7F1D1D),
            onPrimaryContainer = Color(0xFFFEE2E2),
            inversePrimary = Color(0xFFB91C1C),
            secondary = Color(0xFFF87171),
            onSecondary = Color(0xFF450A0A),
            secondaryContainer = Color(0xFF5C191B),
            onSecondaryContainer = Color(0xFFFECACA),
            tertiary = Color(0xFFFCA5A5),
            onTertiary = Color(0xFF450A0A),
            tertiaryContainer = Color(0xFF5C191B),
            onTertiaryContainer = Color(0xFFFEE2E2),
            error = Color(0xFFEF4444),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFF7F1D1D),
            onErrorContainer = Color(0xFFFEE2E2),
            outline = Color(0xFFEF4444).copy(alpha = 0.55f)
        )
    } else {
        scheme.copy(
            primary = Color(0xFFDC2626),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFEE2E2),
            onPrimaryContainer = Color(0xFF7F1D1D),
            inversePrimary = Color(0xFFFCA5A5),
            secondary = Color(0xFFB91C1C),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFFECACA),
            onSecondaryContainer = Color(0xFF7F1D1D),
            tertiary = Color(0xFFE11D48),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFE4E6),
            onTertiaryContainer = Color(0xFF881337),
            error = Color(0xFFDC2626),
            onError = Color.White,
            errorContainer = Color(0xFFFEE2E2),
            onErrorContainer = Color(0xFF7F1D1D),
            outline = Color(0xFFDC2626).copy(alpha = 0.45f)
        )
    }
}

/**
 * Transitions the full ColorScheme using a single [Animatable] progress driver instead of
 * 17 separate `animateColorAsState` coroutines, dramatically reducing CPU work during theme switches.
 */
@Composable
private fun animateColorSchemeSinglePass(target: ColorScheme): ColorScheme {
    var fromScheme by remember { mutableStateOf(target) }
    var toScheme by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (target != toScheme) {
            fromScheme = lerpColorScheme(fromScheme, toScheme, progress.value)
            toScheme = target
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        }
    }

    val t = progress.value
    return if (t >= 0.999f) toScheme else lerpColorScheme(fromScheme, toScheme, t)
}

private fun lerpColorScheme(from: ColorScheme, to: ColorScheme, t: Float): ColorScheme {
    if (t <= 0f) return from
    if (t >= 1f) return to
    return to.copy(
        primary = lerp(from.primary, to.primary, t),
        onPrimary = lerp(from.onPrimary, to.onPrimary, t),
        primaryContainer = lerp(from.primaryContainer, to.primaryContainer, t),
        onPrimaryContainer = lerp(from.onPrimaryContainer, to.onPrimaryContainer, t),
        inversePrimary = lerp(from.inversePrimary, to.inversePrimary, t),
        secondary = lerp(from.secondary, to.secondary, t),
        onSecondary = lerp(from.onSecondary, to.onSecondary, t),
        secondaryContainer = lerp(from.secondaryContainer, to.secondaryContainer, t),
        onSecondaryContainer = lerp(from.onSecondaryContainer, to.onSecondaryContainer, t),
        tertiary = lerp(from.tertiary, to.tertiary, t),
        onTertiary = lerp(from.onTertiary, to.onTertiary, t),
        tertiaryContainer = lerp(from.tertiaryContainer, to.tertiaryContainer, t),
        onTertiaryContainer = lerp(from.onTertiaryContainer, to.onTertiaryContainer, t),
        background = lerp(from.background, to.background, t),
        onBackground = lerp(from.onBackground, to.onBackground, t),
        surface = lerp(from.surface, to.surface, t),
        onSurface = lerp(from.onSurface, to.onSurface, t),
        surfaceVariant = lerp(from.surfaceVariant, to.surfaceVariant, t),
        onSurfaceVariant = lerp(from.onSurfaceVariant, to.onSurfaceVariant, t),
        surfaceTint = lerp(from.surfaceTint, to.surfaceTint, t),
        surfaceDim = lerp(from.surfaceDim, to.surfaceDim, t),
        surfaceBright = lerp(from.surfaceBright, to.surfaceBright, t),
        inverseSurface = lerp(from.inverseSurface, to.inverseSurface, t),
        inverseOnSurface = lerp(from.inverseOnSurface, to.inverseOnSurface, t),
        surfaceContainerLowest = lerp(from.surfaceContainerLowest, to.surfaceContainerLowest, t),
        surfaceContainerLow = lerp(from.surfaceContainerLow, to.surfaceContainerLow, t),
        surfaceContainer = lerp(from.surfaceContainer, to.surfaceContainer, t),
        surfaceContainerHigh = lerp(from.surfaceContainerHigh, to.surfaceContainerHigh, t),
        surfaceContainerHighest = lerp(from.surfaceContainerHighest, to.surfaceContainerHighest, t),
        outline = lerp(from.outline, to.outline, t),
        outlineVariant = lerp(from.outlineVariant, to.outlineVariant, t),
        scrim = lerp(from.scrim, to.scrim, t),
        error = lerp(from.error, to.error, t),
        onError = lerp(from.onError, to.onError, t),
        errorContainer = lerp(from.errorContainer, to.errorContainer, t),
        onErrorContainer = lerp(from.onErrorContainer, to.onErrorContainer, t)
    )
}
