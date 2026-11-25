package com.example.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class DeviceFormFactor {
    PHONE,
    TABLET,
    TV
}

@Immutable
data class AdaptiveLayoutSpec(
    val formFactor: DeviceFormFactor = DeviceFormFactor.PHONE,
    val widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    val heightSizeClass: WindowHeightSizeClass = WindowHeightSizeClass.Medium,
    val isTv: Boolean = false,
    val useTwoPaneConnect: Boolean = false,
    val useTwoPaneSettings: Boolean = false,
    val screenHorizontalPadding: Dp = 20.dp,
    val screenVerticalPadding: Dp = 14.dp
) {
    val isCompactHeight: Boolean
        get() = heightSizeClass == WindowHeightSizeClass.Compact
}

val LocalAdaptiveLayout = compositionLocalOf { AdaptiveLayoutSpec() }

fun Context.isAndroidTv(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isTvUiMode =
        uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val noTouchscreen = !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    return isTvUiMode || (hasLeanback && noTouchscreen)
}

fun Context.hasCameraHardware(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

@Composable
fun rememberAdaptiveLayoutSpec(
    windowSizeClass: WindowSizeClass,
    context: Context
): AdaptiveLayoutSpec {
    val isTv = remember(context) { context.isAndroidTv() }
    val widthClass = windowSizeClass.widthSizeClass
    val heightClass = windowSizeClass.heightSizeClass

    return remember(isTv, widthClass, heightClass) {
        val formFactor = when {
            isTv -> DeviceFormFactor.TV
            widthClass != WindowWidthSizeClass.Compact -> DeviceFormFactor.TABLET
            else -> DeviceFormFactor.PHONE
        }
        val useTwoPaneConnect = isTv ||
            widthClass == WindowWidthSizeClass.Expanded ||
            (widthClass == WindowWidthSizeClass.Medium && heightClass == WindowHeightSizeClass.Compact)
        val useTwoPaneSettings = isTv || widthClass == WindowWidthSizeClass.Expanded

        AdaptiveLayoutSpec(
            formFactor = formFactor,
            widthSizeClass = widthClass,
            heightSizeClass = heightClass,
            isTv = isTv,
            useTwoPaneConnect = useTwoPaneConnect,
            useTwoPaneSettings = useTwoPaneSettings,
            screenHorizontalPadding = when (formFactor) {
                DeviceFormFactor.TV -> 42.dp
                DeviceFormFactor.TABLET -> 28.dp
                DeviceFormFactor.PHONE -> 20.dp
            },
            screenVerticalPadding = when (formFactor) {
                DeviceFormFactor.TV -> 26.dp
                DeviceFormFactor.TABLET -> 18.dp
                DeviceFormFactor.PHONE -> 14.dp
            }
        )
    }
}

/**
 * Lightweight, zero-overhead focus ring + scale highlight on touch phones, activating a single
 * animation driver only on Android TV or keyboard/D-pad navigation.
 */
fun Modifier.tvFocusHighlight(
    cornerRadius: Dp = 18.dp,
    scaleOnFocus: Float = 1.03f,
    strokeWidth: Dp = 2.5.dp,
    focusColor: Color? = null
): Modifier = composed {
    val isKeyboardOrTv = LocalAdaptiveLayout.current.isTv ||
        LocalInputModeManager.current.inputMode == InputMode.Keyboard
    if (!isKeyboardOrTv) return@composed this

    val resolvedFocusColor = focusColor ?: MaterialTheme.colorScheme.primary
    var isFocused by remember { mutableStateOf(false) }

    val focusProgressState = animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "tv_focus_progress"
    )

    this
        .onFocusChanged { isFocused = it.isFocused }
        .graphicsLayer {
            val scale = 1f + (scaleOnFocus - 1f) * focusProgressState.value
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            val alpha = focusProgressState.value
            if (alpha > 0.01f) {
                val radiusPx = cornerRadius.toPx()
                val strokePx = strokeWidth.toPx()
                drawRoundRect(
                    color = resolvedFocusColor.copy(alpha = alpha),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokePx)
                )
            }
        }
}
