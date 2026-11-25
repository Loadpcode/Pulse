package com.xraypulse.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.xraypulse.app.R
import com.xraypulse.app.ui.theme.MonoMetricStyle
import kotlin.math.abs
import kotlin.math.roundToInt

data class EditableSliderValue(
    val value: Int,
    val min: Int,
    val max: Int,
    val suffix: String = "",
    val onCommit: (Int) -> Unit
)

data class EditableRangeSliderValue(
    val start: Int,
    val end: Int,
    val min: Int,
    val max: Int,
    val onCommit: (Int, Int) -> Unit
)

/**
 * Tracks finger press down/up on any PointerEventPass.Initial without consuming scroll/drag events,
 * enabling immediate scale-up feedback when the user touches a slider.
 */
private fun Modifier.pressScaleObserver(onPressedChange: (Boolean) -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onPressedChange(true)
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
            onPressedChange(false)
        }
    }

private fun snapFractionToMagnet(
    fraction: Float,
    magnets: List<Float>,
    radius: Float
): Pair<Float, Float?> {
    val clamped = fraction.coerceIn(0f, 1f)
    if (magnets.isEmpty() || radius <= 0f) return clamped to null
    var closestAnchor: Float? = null
    var minDist = Float.MAX_VALUE
    for (anchor in magnets) {
        val d = abs(clamped - anchor.coerceIn(0f, 1f))
        if (d <= radius && d < minDist) {
            minDist = d
            closestAnchor = anchor.coerceIn(0f, 1f)
        }
    }
    return if (closestAnchor != null) closestAnchor to closestAnchor else clamped to null
}

/**
 * Smooth S-curve in [0f, 1f] that equals 0.0 when [position] is aligned on a discrete option
 * and rises smoothly to 1.0 at the midpoint between two options.
 */
internal fun computeSelectorPillBetweenness(position: Float): Float {
    val dist = abs(position - position.roundToInt())
    val u = (dist * 2f).coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

/**
 * Smooth continuous-drag pill selector with dual-layer text masking and zero per-frame recompositions:
 * - Animation values are read strictly in Layout (`offset`) and Draw (`drawWithContent`, `graphicsLayer`) phases.
 * - Full D-pad / keyboard support (Left/Right switches options cleanly).
 */
@Composable
fun <T> SoftSlidingSelector(
    options: List<T>,
    selectedOption: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionTestTag: ((T) -> String)? = null
) {
    if (options.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val selectedIndex = options.indexOf(selectedOption).coerceAtLeast(0)

    var isPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var suppressNextTapPulse by remember { mutableStateOf(false) }
    var lastSelectedIdx by remember { mutableIntStateOf(selectedIndex) }

    val targetPos = if (isDragging) dragPosition else selectedIndex.toFloat()
    val animatedPosState = animateFloatAsState(
        targetValue = targetPos,
        animationSpec = if (isDragging) {
            snap()
        } else {
            spring(
                dampingRatio = 0.78f,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "sliding_selector_pos"
    )

    val containerScaleState = animateFloatAsState(
        targetValue = if (isPressed || isDragging) 1.024f else 1f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "sliding_selector_scale"
    )

    val dragBetweenness = if (isDragging) {
        computeSelectorPillBetweenness(dragPosition)
    } else {
        0f
    }
    val dragStretchState = animateFloatAsState(
        targetValue = dragBetweenness,
        animationSpec = spring(
            dampingRatio = 0.48f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "sliding_selector_drag_stretch"
    )

    val tapStretchAnim = remember { Animatable(0f) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != lastSelectedIdx) {
            lastSelectedIdx = selectedIndex
            if (suppressNextTapPulse) {
                suppressNextTapPulse = false
            } else {
                tapStretchAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
                )
                tapStretchAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.48f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        } else {
            suppressNextTapPulse = false
        }
    }

    val containerShape = remember { RoundedCornerShape(18.dp) }
    val thumbShape = remember { RoundedCornerShape(14.dp) }
    val latestOnSelect by rememberUpdatedState(onSelect)
    val latestSelectedIndex by rememberUpdatedState(selectedIndex)

    fun currentPillStretchFactor(): Float {
        val d = dragStretchState.value
        val t = tapStretchAnim.value
        return if (isDragging || abs(d) >= abs(t)) d else t
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .graphicsLayer {
                val scale = containerScaleState.value
                scaleX = scale
                scaleY = scale
            }
            .tvFocusHighlight(cornerRadius = 18.dp, scaleOnFocus = 1.02f)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            val nextIdx = (latestSelectedIndex - 1).coerceAtLeast(0)
                            if (nextIdx != latestSelectedIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnSelect(options[nextIdx])
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            val nextIdx = (latestSelectedIndex + 1).coerceAtMost(options.lastIndex)
                            if (nextIdx != latestSelectedIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnSelect(options[nextIdx])
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .clip(containerShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                shape = containerShape
            )
            .pressScaleObserver { isPressed = it }
            .padding(4.dp)
    ) {
        val innerWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val slotWidth = maxWidth / options.size
        val slotWidthPx = innerWidthPx / options.size
        val maxPos = (options.size - 1).toFloat().coerceAtLeast(0f)
        val maxPillLeftPx = (innerWidthPx - slotWidthPx).coerceAtLeast(0f)

        // 1. Base gray layer (unselected appearance, identical layout/typography to active mask)
        SelectorLabelsRow(
            options = options,
            optionLabel = optionLabel,
            activeIndex = selectedIndex,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            optionTestTag = optionTestTag
        )

        // 2. Sliding pill surface (Layout-phase offset + GPU-phase horizontal elongation, zero recompositions)
        Box(
            modifier = Modifier
                .offset {
                    val left = (animatedPosState.value * slotWidthPx).coerceIn(0f, maxPillLeftPx)
                    IntOffset(left.roundToInt(), 0)
                }
                .width(slotWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleX = (1f + 0.065f * currentPillStretchFactor()).coerceAtLeast(0.96f)
                }
                .clip(thumbShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    shape = thumbShape
                )
        )

        // 3. Masked theme-colored layer clipped in Draw-phase strictly to the sliding pill bounds
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val pillScale = (1f + 0.065f * currentPillStretchFactor()).coerceAtLeast(0.96f)
                    val baseLeft = (animatedPosState.value * slotWidthPx).coerceIn(0f, maxPillLeftPx).roundToInt().toFloat()
                    val center = baseLeft + slotWidthPx * 0.5f
                    val halfW = slotWidthPx * 0.5f * pillScale
                    clipRect(
                        left = (center - halfW).coerceAtLeast(0f),
                        top = 0f,
                        right = (center + halfW).coerceAtMost(size.width),
                        bottom = size.height
                    ) {
                        this@drawWithContent.drawContent()
                    }
                }
        ) {
            SelectorLabelsRow(
                options = options,
                optionLabel = optionLabel,
                activeIndex = selectedIndex,
                textColor = MaterialTheme.colorScheme.primary,
                optionTestTag = null
            )
        }

        // 4. Transparent interaction layer: tap to pick, drag 1:1 continuously, snap ONLY on release
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(options, innerWidthPx) {
                    detectTapGestures { offset ->
                        val idx = ((offset.x / innerWidthPx) * options.size)
                            .toInt()
                            .coerceIn(0, options.lastIndex)
                        if (idx != latestSelectedIndex) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnSelect(options[idx])
                        }
                    }
                }
                .pointerInput(options, slotWidthPx, maxPos) {
                    detectHorizontalDragGestures(
                        onDragStart = { startOffset ->
                            suppressNextTapPulse = false
                            isDragging = true
                            val curLeft = latestSelectedIndex * slotWidthPx
                            val curRight = curLeft + slotWidthPx
                            dragPosition = if (startOffset.x in curLeft..curRight) {
                                latestSelectedIndex.toFloat()
                            } else {
                                ((startOffset.x - slotWidthPx / 2f) / slotWidthPx).coerceIn(0f, maxPos)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragPosition = (dragPosition + dragAmount / slotWidthPx).coerceIn(0f, maxPos)
                        },
                        onDragEnd = {
                            val snapped = dragPosition.roundToInt().coerceIn(0, options.lastIndex)
                            suppressNextTapPulse = snapped != latestSelectedIndex
                            isDragging = false
                            if (snapped != latestSelectedIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnSelect(options[snapped])
                            }
                        },
                        onDragCancel = {
                            suppressNextTapPulse = false
                            isDragging = false
                        }
                    )
                }
        )
    }
}

@Composable
private fun <T> SelectorLabelsRow(
    options: List<T>,
    optionLabel: (T) -> String,
    activeIndex: Int,
    textColor: Color,
    optionTestTag: ((T) -> String)?
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == activeIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (optionTestTag != null) {
                            Modifier
                                .semantics {
                                    role = Role.Tab
                                    selected = isSelected
                                }
                                .testTag(optionTestTag(option))
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = optionLabel(option),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
    }
}

/**
 * Plush pill-track continuous slider with zero-recomposition Draw/Layout animations:
 * - Scale-up feedback on touch/drag
 * - Magnetic detents + haptic tick at standard values
 * - Vertical tick lines aligned mathematically to exact thumb-center fractions
 * - Tappable value pill for direct keyboard numeric entry
 * - Full D-pad Left/Right step & Enter/Center edit dialog support for TV/Keyboard
 */
@Composable
fun SoftValueSlider(
    label: String,
    valueText: String,
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    captions: List<Pair<Float, String>> = emptyList(),
    magnetFractions: List<Float> = captions.map { it.first },
    magnetRadius: Float = 0.022f,
    editableValue: EditableSliderValue? = null,
    onFractionChangeFinished: (() -> Unit)? = null,
    sliderTestTag: String? = null
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var lastSnappedMagnet by remember { mutableStateOf<Float?>(null) }
    val clamped = fraction.coerceIn(0f, 1f)

    // While dragging, the thumb tracks the raw finger fraction 1:1 (no spring lag,
    // no magnet-boundary ping-pong). Magnet snapping applies to the emitted value only.
    var dragRawFraction by remember { mutableFloatStateOf(clamped) }

    val animatedFractionState = animateFloatAsState(
        targetValue = if (isDragging) dragRawFraction else clamped,
        animationSpec = if (isDragging) {
            snap()
        } else {
            tween(durationMillis = 200, easing = FastOutSlowInEasing)
        },
        label = "soft_slider_fraction"
    )

    val isInteracting = isPressed || isDragging
    val trackScaleXState = animateFloatAsState(
        targetValue = if (isInteracting) 1.015f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
        label = "soft_slider_track_scale_x"
    )
    val trackScaleYState = animateFloatAsState(
        targetValue = if (isInteracting) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
        label = "soft_slider_track_scale_y"
    )
    val thumbScaleState = animateFloatAsState(
        targetValue = if (isInteracting) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "soft_slider_thumb_scale"
    )

    val thumbSizeDp = 26.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val trackHeightPx = with(density) { 14.dp.toPx() }
    val borderStrokePx = with(density) { 1.dp.toPx() }

    val trackBgColor = MaterialTheme.colorScheme.surfaceContainer
    val trackBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)

    val latestFraction by rememberUpdatedState(clamped)
    val latestOnChange by rememberUpdatedState(onFractionChange)
    val latestOnFinished by rememberUpdatedState(onFractionChangeFinished)
    val latestMagnets by rememberUpdatedState(magnetFractions)

    fun emitWithMagnet(rawFraction: Float) {
        val (snapped, hitAnchor) = snapFractionToMagnet(rawFraction, latestMagnets, magnetRadius)
        if (hitAnchor != null && hitAnchor != lastSnappedMagnet) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastSnappedMagnet = hitAnchor
        latestOnChange(snapped)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            )
            ValuePillBadge(
                valueText = valueText,
                editable = editableValue != null,
                onClick = { if (editableValue != null) showEditDialog = true },
                badgeTestTag = sliderTestTag?.let { "${it}_badge" }
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .tvFocusHighlight(cornerRadius = 16.dp, scaleOnFocus = 1.015f)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                val next = (latestFraction - 0.04f).coerceIn(0f, 1f)
                                emitWithMagnet(next)
                                latestOnFinished?.invoke()
                                true
                            }
                            Key.DirectionRight -> {
                                val next = (latestFraction + 0.04f).coerceIn(0f, 1f)
                                emitWithMagnet(next)
                                latestOnFinished?.invoke()
                                true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                if (editableValue != null) {
                                    showEditDialog = true
                                    true
                                } else false
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .pressScaleObserver { isPressed = it }
                .then(sliderTestTag?.let { Modifier.testTag(it) } ?: Modifier),
            contentAlignment = Alignment.CenterStart
        ) {
            val trackWidthPx = with(density) { maxWidth.toPx() }
            val usablePx = (trackWidthPx - thumbSizePx).coerceAtLeast(1f)

            val trackGestureModifier = Modifier
                .fillMaxSize()
                .pointerInput(usablePx, thumbSizePx) {
                    detectTapGestures { offset ->
                        val next = ((offset.x - thumbSizePx / 2f) / usablePx).coerceIn(0f, 1f)
                        emitWithMagnet(next)
                        latestOnFinished?.invoke()
                    }
                }
                .pointerInput(usablePx, thumbSizePx) {
                    var dragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { startOffset ->
                            isDragging = true
                            lastSnappedMagnet = null
                            dragX = startOffset.x
                            val next = ((dragX - thumbSizePx / 2f) / usablePx).coerceIn(0f, 1f)
                            dragRawFraction = next
                            emitWithMagnet(next)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragX += dragAmount
                            val next = ((dragX - thumbSizePx / 2f) / usablePx).coerceIn(0f, 1f)
                            dragRawFraction = next
                            emitWithMagnet(next)
                        },
                        onDragEnd = {
                            isDragging = false
                            lastSnappedMagnet = null
                            latestOnFinished?.invoke()
                        },
                        onDragCancel = {
                            isDragging = false
                            lastSnappedMagnet = null
                            latestOnFinished?.invoke()
                        }
                    )
                }

            Box(
                modifier = trackGestureModifier
                    .graphicsLayer { scaleX = trackScaleXState.value }
                    .drawBehind {
                        // Single-pass Draw-phase pill background & active fill (zero layout passes per frame)
                        val scaledTrackH = trackHeightPx * trackScaleYState.value
                        val topY = (size.height - scaledTrackH) * 0.5f
                        val cornerR = CornerRadius(scaledTrackH * 0.5f, scaledTrackH * 0.5f)
                        drawRoundRect(
                            color = trackBgColor,
                            topLeft = Offset(0f, topY),
                            size = Size(size.width, scaledTrackH),
                            cornerRadius = cornerR
                        )
                        drawRoundRect(
                            color = trackBorderColor,
                            topLeft = Offset(0f, topY),
                            size = Size(size.width, scaledTrackH),
                            cornerRadius = cornerR,
                            style = Stroke(width = borderStrokePx)
                        )
                        val activeW = (thumbSizePx * 0.5f + usablePx * animatedFractionState.value)
                            .coerceIn(scaledTrackH, size.width)
                        drawRoundRect(
                            color = activeTrackColor,
                            topLeft = Offset(0f, topY),
                            size = Size(activeW, scaledTrackH),
                            cornerRadius = cornerR
                        )
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                // Plush tactile thumb (Layout-phase offset + GPU-phase scale)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .offset {
                            IntOffset((usablePx * animatedFractionState.value).roundToInt(), 0)
                        }
                        .size(thumbSizeDp)
                        .graphicsLayer {
                            val s = thumbScaleState.value
                            scaleX = s
                            scaleY = s
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                    }
                }
            }
        }

        if (captions.isNotEmpty()) {
            PositionedSliderCaptions(
                captions = captions,
                thumbSizePx = thumbSizePx,
                activeFractions = listOf(clamped)
            )
        }
    }

    if (showEditDialog && editableValue != null) {
        SingleValueEditDialog(
            title = label,
            spec = editableValue,
            onDismiss = { showEditDialog = false }
        )
    }
}

@Composable
private fun ValuePillBadge(
    valueText: String,
    editable: Boolean,
    onClick: () -> Unit,
    badgeTestTag: String? = null
) {
    val pillShape = remember { RoundedCornerShape(12.dp) }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .then(if (editable) Modifier.tvFocusHighlight(cornerRadius = 12.dp) else Modifier)
            .clip(pillShape)
            .then(
                if (editable) Modifier.clickable(onClick = onClick) else Modifier
            )
            .then(badgeTestTag?.let { Modifier.testTag(it) } ?: Modifier),
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            shape = pillShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = valueText,
                    style = MonoMetricStyle.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                if (editable) {
                    Icon(
                        imageVector = AppIcons.EditOutlined,
                        contentDescription = stringResource(R.string.dialog_edit_value_title),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

/**
 * Places a crisp vertical tick mark centered at the exact X coordinate of the thumb center
 * for each standard value, with its numeric label centered beneath it.
 */
@Composable
private fun PositionedSliderCaptions(
    captions: List<Pair<Float, String>>,
    thumbSizePx: Float,
    activeFractions: List<Float> = emptyList()
) {
    val density = LocalDensity.current
    val tickTopGapPx = with(density) { 2.dp.roundToPx() }
    val primaryColor = MaterialTheme.colorScheme.primary
    val inactiveTickColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)
    val inactiveTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)

    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            captions.forEach { (frac, _) ->
                val isMatched = activeFractions.any { abs(it - frac) <= 0.015f }
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(if (isMatched) primaryColor else inactiveTickColor)
                )
            }
            captions.forEach { (frac, text) ->
                val isMatched = activeFractions.any { abs(it - frac) <= 0.015f }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isMatched) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isMatched) primaryColor else inactiveTextColor,
                    maxLines = 1
                )
            }
        }
    ) { measurables, constraints ->
        val count = captions.size
        val tickPlaceables = measurables.take(count).map { it.measure(constraints.copy(minWidth = 0)) }
        val labelPlaceables = measurables.drop(count).map { it.measure(constraints.copy(minWidth = 0)) }

        val tickHeight = tickPlaceables.maxOfOrNull { it.height } ?: 0
        val labelHeight = labelPlaceables.maxOfOrNull { it.height } ?: 0
        val totalWidth = constraints.maxWidth
        val totalHeight = tickHeight + tickTopGapPx + labelHeight
        val usable = (totalWidth - thumbSizePx).coerceAtLeast(1f)

        layout(totalWidth, totalHeight) {
            for (index in 0 until count) {
                val f = captions[index].first.coerceIn(0f, 1f)
                val centerX = thumbSizePx / 2f + usable * f

                val tick = tickPlaceables[index]
                val tickX = (centerX - tick.width / 2f)
                    .roundToInt()
                    .coerceIn(0, (totalWidth - tick.width).coerceAtLeast(0))
                tick.placeRelative(tickX, 0)

                val label = labelPlaceables[index]
                val labelX = (centerX - label.width / 2f)
                    .roundToInt()
                    .coerceIn(0, (totalWidth - label.width).coerceAtLeast(0))
                label.placeRelative(labelX, tickHeight + tickTopGapPx)
            }
        }
    }
}

/**
 * Plush dual-thumb range slider with zero-recomposition Draw/Layout animations:
 * - Press scale-up
 * - Magnetic detents + haptic tick at standard values
 * - Vertical tick marks + exact aligned captions
 * - Tappable value pill for keyboard min/max entry
 * - D-pad Left/Right & Enter/Center edit dialog support
 */
@Composable
fun SoftRangeSlider(
    label: String,
    valueText: String,
    startFraction: Float,
    endFraction: Float,
    onRangeChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    ticks: List<Pair<Float, String>> = emptyList(),
    magnetFractions: List<Float> = ticks.map { it.first },
    magnetRadius: Float = 0.02f,
    editableRange: EditableRangeSliderValue? = null,
    minCaption: String? = null,
    maxCaption: String? = null,
    onRangeChangeFinished: (() -> Unit)? = null,
    sliderTestTag: String? = null
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    var activeThumb by remember { mutableIntStateOf(0) } // 0 = none, 1 = start, 2 = end
    var showEditDialog by remember { mutableStateOf(false) }
    var lastSnappedStart by remember { mutableStateOf<Float?>(null) }
    var lastSnappedEnd by remember { mutableStateOf<Float?>(null) }

    val clampedStart = startFraction.coerceIn(0f, 1f)
    val clampedEnd = endFraction.coerceIn(clampedStart, 1f)

    // While a thumb is being dragged it tracks the raw finger fraction 1:1 (no spring lag,
    // no magnet-boundary ping-pong). Magnet snapping applies to the emitted value only.
    var dragRawStart by remember { mutableFloatStateOf(clampedStart) }
    var dragRawEnd by remember { mutableFloatStateOf(clampedEnd) }

    val animStartState = animateFloatAsState(
        targetValue = if (activeThumb == 1) dragRawStart else clampedStart,
        animationSpec = if (activeThumb == 1) snap() else tween(200),
        label = "range_start"
    )
    val animEndState = animateFloatAsState(
        targetValue = if (activeThumb == 2) dragRawEnd else clampedEnd,
        animationSpec = if (activeThumb == 2) snap() else tween(200),
        label = "range_end"
    )

    val isInteracting = isPressed || activeThumb != 0
    val trackScaleXState = animateFloatAsState(
        targetValue = if (isInteracting) 1.015f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
        label = "range_track_scale_x"
    )
    val trackScaleYState = animateFloatAsState(
        targetValue = if (isInteracting) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
        label = "range_track_scale_y"
    )
    val startScaleState = animateFloatAsState(
        targetValue = if (activeThumb == 1 || (isPressed && activeThumb == 0)) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "start_thumb"
    )
    val endScaleState = animateFloatAsState(
        targetValue = if (activeThumb == 2 || (isPressed && activeThumb == 0)) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "end_thumb"
    )

    var localStart by remember { mutableFloatStateOf(clampedStart) }
    var localEnd by remember { mutableFloatStateOf(clampedEnd) }
    if (activeThumb == 0) {
        localStart = clampedStart
        localEnd = clampedEnd
    }

    val thumbSizeDp = 24.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val trackHeightPx = with(density) { 12.dp.toPx() }
    val borderStrokePx = with(density) { 1.dp.toPx() }

    val trackBgColor = MaterialTheme.colorScheme.surfaceContainer
    val trackBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)

    val latestStart by rememberUpdatedState(clampedStart)
    val latestEnd by rememberUpdatedState(clampedEnd)
    val latestMagnets by rememberUpdatedState(magnetFractions)
    val latestOnRangeChange by rememberUpdatedState(onRangeChange)
    val latestOnRangeFinished by rememberUpdatedState(onRangeChangeFinished)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            )
            ValuePillBadge(
                valueText = valueText,
                editable = editableRange != null,
                onClick = { if (editableRange != null) showEditDialog = true },
                badgeTestTag = sliderTestTag?.let { "${it}_badge" }
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .tvFocusHighlight(cornerRadius = 16.dp, scaleOnFocus = 1.015f)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                val nextEnd = (latestEnd - 0.04f).coerceIn(latestStart, 1f)
                                val (snapped, hit) = snapFractionToMagnet(nextEnd, latestMagnets, magnetRadius)
                                if (hit != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnRangeChange(latestStart, snapped.coerceAtLeast(latestStart))
                                latestOnRangeFinished?.invoke()
                                true
                            }
                            Key.DirectionRight -> {
                                val nextEnd = (latestEnd + 0.04f).coerceIn(latestStart, 1f)
                                val (snapped, hit) = snapFractionToMagnet(nextEnd, latestMagnets, magnetRadius)
                                if (hit != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnRangeChange(latestStart, snapped.coerceAtLeast(latestStart))
                                latestOnRangeFinished?.invoke()
                                true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                if (editableRange != null) {
                                    showEditDialog = true
                                    true
                                } else false
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .pressScaleObserver { isPressed = it }
                .then(sliderTestTag?.let { Modifier.testTag(it) } ?: Modifier),
            contentAlignment = Alignment.CenterStart
        ) {
            val trackWidthPx = with(density) { maxWidth.toPx() }
            val usablePx = (trackWidthPx - thumbSizePx).coerceAtLeast(1f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = trackScaleXState.value }
                    .drawBehind {
                        val scaledTrackH = trackHeightPx * trackScaleYState.value
                        val topY = (size.height - scaledTrackH) * 0.5f
                        val cornerR = CornerRadius(scaledTrackH * 0.5f, scaledTrackH * 0.5f)
                        drawRoundRect(
                            color = trackBgColor,
                            topLeft = Offset(0f, topY),
                            size = Size(size.width, scaledTrackH),
                            cornerRadius = cornerR
                        )
                        drawRoundRect(
                            color = trackBorderColor,
                            topLeft = Offset(0f, topY),
                            size = Size(size.width, scaledTrackH),
                            cornerRadius = cornerR,
                            style = Stroke(width = borderStrokePx)
                        )
                        val startCenterPx = thumbSizePx * 0.5f + usablePx * animStartState.value
                        val endCenterPx = thumbSizePx * 0.5f + usablePx * animEndState.value
                        val activeLeft = (startCenterPx - 4f).coerceAtLeast(0f)
                        val activeW = (endCenterPx - startCenterPx + 8f).coerceAtLeast(scaledTrackH)
                        drawRoundRect(
                            color = activeTrackColor,
                            topLeft = Offset(activeLeft, topY),
                            size = Size(activeW.coerceAtMost(size.width - activeLeft), scaledTrackH),
                            cornerRadius = cornerR
                        )
                    }
                    .pointerInput(usablePx, thumbSizePx) {
                        var dragX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { startOffset ->
                                dragX = startOffset.x
                                lastSnappedStart = null
                                lastSnappedEnd = null
                                val s = latestStart
                                val e = latestEnd
                                val tapFrac = ((dragX - thumbSizePx / 2f) / usablePx).coerceIn(0f, 1f)
                                val distStart = abs(tapFrac - s)
                                val distEnd = abs(tapFrac - e)
                                activeThumb = if (distStart < distEnd || (distStart == distEnd && tapFrac < s)) 1 else 2
                                localStart = s
                                localEnd = e
                                dragRawStart = s
                                dragRawEnd = e
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragX += dragAmount
                                val rawFrac = ((dragX - thumbSizePx / 2f) / usablePx).coerceIn(0f, 1f)
                                val (snapped, hitAnchor) = snapFractionToMagnet(rawFrac, latestMagnets, magnetRadius)
                                if (activeThumb == 1) {
                                    if (hitAnchor != null && hitAnchor != lastSnappedStart) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    lastSnappedStart = hitAnchor
                                    localStart = snapped.coerceAtMost(localEnd)
                                    dragRawStart = rawFrac.coerceAtMost(localEnd)
                                } else if (activeThumb == 2) {
                                    if (hitAnchor != null && hitAnchor != lastSnappedEnd) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    lastSnappedEnd = hitAnchor
                                    localEnd = snapped.coerceAtLeast(localStart)
                                    dragRawEnd = rawFrac.coerceAtLeast(localStart)
                                }
                                latestOnRangeChange(localStart, localEnd)
                            },
                            onDragEnd = {
                                activeThumb = 0
                                lastSnappedStart = null
                                lastSnappedEnd = null
                                latestOnRangeFinished?.invoke()
                            },
                            onDragCancel = {
                                activeThumb = 0
                                lastSnappedStart = null
                                lastSnappedEnd = null
                                latestOnRangeFinished?.invoke()
                            }
                        )
                    }
                    .pointerInput(usablePx, thumbSizePx) {
                        detectTapGestures { offset ->
                            val rawFrac = ((offset.x - thumbSizePx / 2f) / usablePx).coerceIn(0f, 1f)
                            val (snapped, hitAnchor) = snapFractionToMagnet(rawFrac, latestMagnets, magnetRadius)
                            if (hitAnchor != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            val s = latestStart
                            val e = latestEnd
                            val distStart = abs(snapped - s)
                            val distEnd = abs(snapped - e)
                            if (distStart <= distEnd) {
                                latestOnRangeChange(snapped.coerceAtMost(e), e)
                            } else {
                                latestOnRangeChange(s, snapped.coerceAtLeast(s))
                            }
                            latestOnRangeFinished?.invoke()
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                RangeThumbCircle(
                    sizeDp = thumbSizeDp,
                    offsetPxProvider = { (usablePx * animStartState.value).roundToInt() },
                    scaleProvider = { startScaleState.value }
                )
                RangeThumbCircle(
                    sizeDp = thumbSizeDp,
                    offsetPxProvider = { (usablePx * animEndState.value).roundToInt() },
                    scaleProvider = { endScaleState.value }
                )
            }
        }

        val resolvedCaptions = if (ticks.isNotEmpty()) {
            ticks
        } else {
            buildList {
                if (minCaption != null) add(0f to minCaption)
                if (maxCaption != null) add(1f to maxCaption)
            }
        }
        if (resolvedCaptions.isNotEmpty()) {
            PositionedSliderCaptions(
                captions = resolvedCaptions,
                thumbSizePx = thumbSizePx,
                activeFractions = listOf(clampedStart, clampedEnd)
            )
        }
    }

    if (showEditDialog && editableRange != null) {
        RangeValueEditDialog(
            title = label,
            spec = editableRange,
            onDismiss = { showEditDialog = false }
        )
    }
}

@Composable
private fun RangeThumbCircle(
    sizeDp: Dp,
    offsetPxProvider: () -> Int,
    scaleProvider: () -> Float
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .offset { IntOffset(offsetPxProvider(), 0) }
            .size(sizeDp)
            .graphicsLayer {
                val scale = scaleProvider()
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
    }
}

@Composable
private fun SingleValueEditDialog(
    title: String,
    spec: EditableSliderValue,
    onDismiss: () -> Unit
) {
    var inputText by remember(spec.value) { mutableStateOf(spec.value.toString()) }
    val parsed = inputText.trim().toIntOrNull()
    val isValid = parsed != null

    PredictiveBackAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.label_valid_range, spec.min, spec.max),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { raw ->
                        inputText = raw.filter { it.isDigit() || (spec.min < 0 && it == '-') }
                    },
                    singleLine = true,
                    suffix = if (spec.suffix.isNotBlank()) {
                        { Text(spec.suffix, style = MaterialTheme.typography.bodyMedium) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (parsed != null) {
                                spec.onCommit(parsed.coerceIn(spec.min, spec.max))
                                onDismiss()
                            }
                        }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_value_edit_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val value = (parsed ?: spec.value).coerceIn(spec.min, spec.max)
                    spec.onCommit(value)
                    onDismiss()
                },
                enabled = isValid,
                modifier = Modifier.testTag("slider_value_edit_confirm")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun RangeValueEditDialog(
    title: String,
    spec: EditableRangeSliderValue,
    onDismiss: () -> Unit
) {
    var startText by remember(spec.start) { mutableStateOf(spec.start.toString()) }
    var endText by remember(spec.end) { mutableStateOf(spec.end.toString()) }
    val parsedStart = startText.trim().toIntOrNull()
    val parsedEnd = endText.trim().toIntOrNull()
    val isValid = parsedStart != null && parsedEnd != null

    PredictiveBackAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.label_valid_range, spec.min, spec.max),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it.filter { ch -> ch.isDigit() } },
                        singleLine = true,
                        label = { Text(stringResource(R.string.label_range_start)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("slider_range_start_input")
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it.filter { ch -> ch.isDigit() } },
                        singleLine = true,
                        label = { Text(stringResource(R.string.label_range_end)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (parsedStart != null && parsedEnd != null) {
                                    val s = parsedStart.coerceIn(spec.min, spec.max)
                                    val e = parsedEnd.coerceIn(spec.min, spec.max)
                                    spec.onCommit(minOf(s, e), maxOf(s, e))
                                    onDismiss()
                                }
                            }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("slider_range_end_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = (parsedStart ?: spec.start).coerceIn(spec.min, spec.max)
                    val e = (parsedEnd ?: spec.end).coerceIn(spec.min, spec.max)
                    spec.onCommit(minOf(s, e), maxOf(s, e))
                    onDismiss()
                },
                enabled = isValid,
                modifier = Modifier.testTag("slider_range_edit_confirm")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
