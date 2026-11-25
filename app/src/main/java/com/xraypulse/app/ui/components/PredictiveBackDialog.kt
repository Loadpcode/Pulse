package com.xraypulse.app.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Material 3 dialog wrapper that connects to the dialog window's own OnBackPressedDispatcher
 * and animates scale, edge offset, and alpha interactively during Android predictive back gestures.
 */
@Composable
fun PredictiveBackAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable ColumnScope.() -> Unit,
    confirmButton: @Composable RowScope.() -> Unit,
    dismissButton: (@Composable RowScope.() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = true
        )
    ) {
        val dialogView = LocalView.current
        val dialogBackOwner = remember(dialogView) {
            dialogView.findViewTreeOnBackPressedDispatcherOwner()
        }
        val activeOwner = dialogBackOwner ?: LocalOnBackPressedDispatcherOwner.current

        val dialogContent: @Composable () -> Unit = {
            val backProgress = remember { Animatable(0f) }
            var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

            PredictiveBackHandler(enabled = true) { progressFlow: Flow<BackEventCompat> ->
                try {
                    progressFlow.collect { event ->
                        swipeEdge = event.swipeEdge
                        backProgress.snapTo(event.progress.coerceIn(0f, 1f))
                    }
                    onDismissRequest()
                } catch (_: CancellationException) {
                    backProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    )
                }
            }

            val p = backProgress.value
            val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f

            Surface(
                shape = shape,
                color = containerColor,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 280.dp, max = 560.dp)
                    .graphicsLayer {
                        val s = 1f - (0.14f * p)
                        scaleX = s
                        scaleY = s
                        translationX = direction * (p * 28.dp.toPx())
                        alpha = 1f - (0.45f * p)
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurface
                    ) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                            title()
                        }
                    }

                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                content = text
                            )
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            dismissButton?.invoke(this)
                            confirmButton(this)
                        }
                    }
                }
            }
        }
        if (activeOwner != null) {
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides activeOwner) {
                dialogContent()
            }
        } else {
            dialogContent()
        }
    }
}
