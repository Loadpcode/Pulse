package com.xraypulse.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import com.xraypulse.app.ui.components.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import com.xraypulse.app.ui.components.SyncedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xraypulse.app.R
import com.xraypulse.app.model.ButtonShapeParams
import com.xraypulse.app.model.ConnectButtonShape
import com.xraypulse.app.ui.components.EditableSliderValue
import com.xraypulse.app.ui.components.PredictiveBackAlertDialog
import com.xraypulse.app.ui.components.SoftSlidingSelector
import com.xraypulse.app.ui.components.SoftValueSlider
import com.xraypulse.app.ui.components.toComposeShape
import com.xraypulse.app.ui.components.tvFocusHighlight
import com.xraypulse.app.ui.theme.MonoMetricStyle
import com.xraypulse.app.ui.viewmodel.SettingsUiState
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.roundToInt

private val ACCENT_PRESETS = listOf(
    0xFF2DD4BF, // Teal Pulse
    0xFF60A5FA, // Sky Indigo
    0xFFA78BFA, // Electric Violet
    0xFF34D399, // Emerald
    0xFFFBBF24, // Amber Gold
    0xFFFB7185, // Rose Coral
    0xFF22D3EE, // Cyan
    0xFFF97316  // Tangerine
)

private val BACKGROUND_PRESETS = listOf(
    0xFF212121, // Material Gray 900
    0xFF09090B, // OLED Obsidian
    0xFF0F172A, // Midnight Slate
    0xFF0B1A16, // Deep Forest
    0xFF1A1221, // Night Plum
    0xFF1C1614, // Espresso
    0xFFF4F7FA, // Crisp Ice Light
    0xFFFAF6F0  // Warm Paper Light
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualSettingsScreen(
    uiState: SettingsUiState,
    onDynamicColorChange: (Boolean) -> Unit,
    onCustomAccentColorChange: (Long?) -> Unit,
    onCustomBackgroundColorChange: (Long?) -> Unit,
    onConnectingSpeedChange: (Int) -> Unit,
    onConnectedSpeedChange: (Int) -> Unit,
    onButtonShapeChange: (ConnectButtonShape) -> Unit,
    onButtonShapeParamsChange: (ButtonShapeParams) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SyncedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_item_visual_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .tvFocusHighlight(cornerRadius = 14.dp)
                            .testTag("visual_settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        VisualSettingsContent(
            uiState = uiState,
            onDynamicColorChange = onDynamicColorChange,
            onCustomAccentColorChange = onCustomAccentColorChange,
            onCustomBackgroundColorChange = onCustomBackgroundColorChange,
            onConnectingSpeedChange = onConnectingSpeedChange,
            onConnectedSpeedChange = onConnectedSpeedChange,
            onButtonShapeChange = onButtonShapeChange,
            onButtonShapeParamsChange = onButtonShapeParamsChange,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
fun VisualSettingsContent(
    uiState: SettingsUiState,
    onDynamicColorChange: (Boolean) -> Unit,
    onCustomAccentColorChange: (Long?) -> Unit,
    onCustomBackgroundColorChange: (Long?) -> Unit,
    onConnectingSpeedChange: (Int) -> Unit,
    onConnectedSpeedChange: (Int) -> Unit,
    onButtonShapeChange: (ConnectButtonShape) -> Unit,
    onButtonShapeParamsChange: (ButtonShapeParams) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = uiState.settings
    var connectingSpeedDraft by remember(settings.connectingRotationSpeed) {
        mutableIntStateOf(settings.connectingRotationSpeed.coerceIn(0, 360))
    }
    var connectedSpeedDraft by remember(settings.connectedRotationSpeed) {
        mutableIntStateOf(settings.connectedRotationSpeed.coerceIn(0, 360))
    }
    var shapeParamsDraft by remember(settings.buttonShapeParams) {
        mutableStateOf(settings.buttonShapeParams)
    }
    var previewMode by remember { mutableStateOf(true) } // true = connected, false = connecting
    var editingAccentColorDialog by remember { mutableStateOf(false) }
    var editingBgColorDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 860.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 0. Live Preview Card
            item(key = "visual_preview") {
                VisualSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.label_visual_preview),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (settings.connectButtonShape == ConnectButtonShape.CIRCLE) {
                                stringResource(R.string.label_deg_per_sec, 0)
                            } else if (previewMode) {
                                stringResource(R.string.label_deg_per_sec, connectedSpeedDraft)
                            } else {
                                stringResource(R.string.label_deg_per_sec, connectingSpeedDraft)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    LiveShapePreviewBanner(
                        shape = settings.connectButtonShape,
                        shapeParams = shapeParamsDraft,
                        rotationDegPerSec = if (previewMode) connectedSpeedDraft else connectingSpeedDraft
                    )

                    val connectingLabel = stringResource(R.string.label_visual_preview_connecting)
                    val connectedLabel = stringResource(R.string.label_visual_preview_connected)
                    SoftSlidingSelector(
                        options = listOf(false, true),
                        selectedOption = previewMode,
                        optionLabel = { isConnected ->
                            if (isConnected) connectedLabel else connectingLabel
                        },
                        onSelect = { previewMode = it }
                    )
                }
            }

            // 1. Follow Device Theme & Custom Colors
            item(key = "visual_theme_colors") {
                VisualSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.label_follow_device_theme),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.label_follow_device_theme_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.dynamicColorEnabled,
                            onCheckedChange = onDynamicColorChange,
                            modifier = Modifier
                                .tvFocusHighlight(cornerRadius = 18.dp)
                                .testTag("visual_follow_theme_switch")
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.label_custom_theme_color),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!settings.dynamicColorEnabled || settings.customAccentColorArgb != null) {
                                TextButton(
                                    onClick = {
                                        onCustomAccentColorChange(null)
                                        onDynamicColorChange(true)
                                    },
                                    modifier = Modifier
                                        .tvFocusHighlight(cornerRadius = 14.dp)
                                        .testTag("visual_reset_accent")
                                ) {
                                    Icon(
                                        imageVector = AppIcons.RestartAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.label_default_color),
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }

                        ColorSwatchRow(
                            presets = ACCENT_PRESETS,
                            selectedArgb = if (!settings.dynamicColorEnabled) settings.customAccentColorArgb else null,
                            onSelectPreset = { argb ->
                                onDynamicColorChange(false)
                                onCustomAccentColorChange(argb)
                            },
                            onOpenCustomPicker = { editingAccentColorDialog = true },
                            testTagPrefix = "visual_accent"
                        )
                    }

                    // Background color picker (works independently of dynamic accent)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.label_custom_bg_color),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (settings.customBackgroundColorArgb != null) {
                                TextButton(
                                    onClick = { onCustomBackgroundColorChange(null) },
                                    modifier = Modifier
                                        .tvFocusHighlight(cornerRadius = 14.dp)
                                        .testTag("visual_reset_bg")
                                ) {
                                    Icon(
                                        imageVector = AppIcons.RestartAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.label_default_color),
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }

                        ColorSwatchRow(
                            presets = BACKGROUND_PRESETS,
                            selectedArgb = settings.customBackgroundColorArgb,
                            onSelectPreset = onCustomBackgroundColorChange,
                            onOpenCustomPicker = { editingBgColorDialog = true },
                            testTagPrefix = "visual_bg"
                        )
                    }
                }
            }

            // 2. Rotation Speed Sliders (with ticks, magnets & keyboard editing)
            item(key = "visual_rotation_speeds") {
                VisualSectionCard {
                    if (settings.connectButtonShape == ConnectButtonShape.CIRCLE) {
                        Text(
                            text = stringResource(R.string.label_circle_no_rotation),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    val speedTicks = remember {
                        listOf(
                            0f to "0",
                            20 / 360f to "20",
                            60 / 360f to "60",
                            120 / 360f to "120",
                            180 / 360f to "180",
                            1f to "360"
                        )
                    }
                    val connectedSpeedTicks = remember {
                        listOf(
                            0f to "0",
                            30 / 360f to "30",
                            60 / 360f to "60",
                            120 / 360f to "120",
                            180 / 360f to "180",
                            1f to "360"
                        )
                    }

                    SoftValueSlider(
                        label = stringResource(R.string.label_connecting_rotation_speed),
                        valueText = stringResource(R.string.label_deg_per_sec, connectingSpeedDraft),
                        fraction = connectingSpeedDraft / 360f,
                        captions = speedTicks,
                        editableValue = EditableSliderValue(
                            value = connectingSpeedDraft,
                            min = 0,
                            max = 360,
                            suffix = "°/s",
                            onCommit = { v ->
                                connectingSpeedDraft = v
                                onConnectingSpeedChange(v)
                            }
                        ),
                        sliderTestTag = "visual_connecting_speed_slider",
                        onFractionChange = { f ->
                            connectingSpeedDraft = (f * 360f).roundToInt().coerceIn(0, 360)
                        },
                        onFractionChangeFinished = {
                            onConnectingSpeedChange(connectingSpeedDraft)
                        }
                    )

                    SoftValueSlider(
                        label = stringResource(R.string.label_connected_rotation_speed),
                        valueText = stringResource(R.string.label_deg_per_sec, connectedSpeedDraft),
                        fraction = connectedSpeedDraft / 360f,
                        captions = connectedSpeedTicks,
                        editableValue = EditableSliderValue(
                            value = connectedSpeedDraft,
                            min = 0,
                            max = 360,
                            suffix = "°/s",
                            onCommit = { v ->
                                connectedSpeedDraft = v
                                onConnectedSpeedChange(v)
                            }
                        ),
                        sliderTestTag = "visual_connected_speed_slider",
                        onFractionChange = { f ->
                            connectedSpeedDraft = (f * 360f).roundToInt().coerceIn(0, 360)
                        },
                        onFractionChangeFinished = {
                            onConnectedSpeedChange(connectedSpeedDraft)
                        }
                    )
                }
            }

            // 3. Material Design 3 Connect Button Shapes & per-shape formula parameter customization
            item(key = "visual_button_shapes") {
                VisualSectionCard {
                    Text(
                        text = stringResource(R.string.label_button_shape),
                        style = MaterialTheme.typography.titleMedium
                    )
                    val shapes = ConnectButtonShape.entries
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val columns = when {
                            maxWidth >= 540.dp -> 5
                            else -> 2
                        }
                        val rows = remember(shapes, columns) { shapes.chunked(columns) }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            rows.forEach { rowShapes ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowShapes.forEach { shapeOption ->
                                        val isSelected = settings.connectButtonShape == shapeOption
                                        ButtonShapeTile(
                                            shapeOption = shapeOption,
                                            shapeParams = if (isSelected) shapeParamsDraft else ButtonShapeParams(),
                                            selected = isSelected,
                                            onClick = { onButtonShapeChange(shapeOption) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(columns - rowShapes.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))

                    ShapeFormulaParametersSection(
                        shape = settings.connectButtonShape,
                        params = shapeParamsDraft,
                        onDraftChange = { shapeParamsDraft = it },
                        onCommit = { onButtonShapeParamsChange(shapeParamsDraft.coerced()) },
                        onReset = {
                            val reset = shapeParamsDraft.resetForShape(settings.connectButtonShape)
                            shapeParamsDraft = reset
                            onButtonShapeParamsChange(reset)
                        }
                    )
                }
            }
        }
    }

    if (editingAccentColorDialog) {
        ColorPickerDialog(
            initialColor = settings.customAccentColorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
            onDismiss = { editingAccentColorDialog = false },
            onConfirm = { picked ->
                onDynamicColorChange(false)
                onCustomAccentColorChange(picked.toArgb().toLong() and 0xFFFFFFFFL)
                editingAccentColorDialog = false
            }
        )
    }

    if (editingBgColorDialog) {
        ColorPickerDialog(
            initialColor = settings.customBackgroundColorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.background,
            onDismiss = { editingBgColorDialog = false },
            onConfirm = { picked ->
                onCustomBackgroundColorChange(picked.toArgb().toLong() and 0xFFFFFFFFL)
                editingBgColorDialog = false
            }
        )
    }
}

@Composable
private fun LiveShapePreviewBanner(
    shape: ConnectButtonShape,
    shapeParams: ButtonShapeParams,
    rotationDegPerSec: Int
) {
    // Store angle in State object without `by` delegate so only graphicsLayer reads it (zero recompositions)
    val angleState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(shape, rotationDegPerSec) {
        if (shape == ConnectButtonShape.CIRCLE || rotationDegPerSec <= 0) {
            angleState.floatValue = 0f
            return@LaunchedEffect
        }
        var lastNanos = 0L
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            if (lastNanos != 0L) {
                val deltaSec = (frameNanos - lastNanos) / 1_000_000_000f
                angleState.floatValue = (angleState.floatValue + rotationDegPerSec * deltaSec) % 360f
            }
            lastNanos = frameNanos
        }
    }

    val composeShape = remember(shape, shapeParams) { shape.toComposeShape(shapeParams) }
    val bannerShape = remember { RoundedCornerShape(18.dp) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .clip(bannerShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                bannerShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .graphicsLayer { rotationZ = angleState.floatValue }
                .clip(composeShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(2.dp, MaterialTheme.colorScheme.primaryContainer, composeShape)
        )
        Icon(
            imageVector = AppIcons.PowerSettingsNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun ColorSwatchRow(
    presets: List<Long>,
    selectedArgb: Long?,
    onSelectPreset: (Long) -> Unit,
    onOpenCustomPicker: () -> Unit,
    testTagPrefix: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEachIndexed { index, rawColor ->
            val color = Color(rawColor)
            val isSelected = selectedArgb != null && (selectedArgb and 0xFFFFFFFFL) == (rawColor and 0xFFFFFFFFL)
            val iconTint = if (color.luminance() > 0.45f) Color(0xFF0F172A) else Color.White
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .tvFocusHighlight(cornerRadius = 24.dp, scaleOnFocus = 1.06f)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.5.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        },
                        shape = CircleShape
                    )
                    .clickable { onSelectPreset(rawColor) }
                    .testTag("${testTagPrefix}_preset_$index"),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Custom color button
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier
                .size(48.dp)
                .tvFocusHighlight(cornerRadius = 24.dp, scaleOnFocus = 1.06f)
                .clip(CircleShape)
                .clickable(onClick = onOpenCustomPicker)
                .testTag("${testTagPrefix}_custom_picker")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = AppIcons.Colorize,
                    contentDescription = stringResource(R.string.label_custom_color_pick),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ShapeFormulaParametersSection(
    shape: ConnectButtonShape,
    params: ButtonShapeParams,
    onDraftChange: (ButtonShapeParams) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit
) {
    val safe = params.coerced()
    val formulaText = when (shape) {
        ConnectButtonShape.SQUARE -> "-s/2 ≤ x, y ≤ s/2"
        ConnectButtonShape.ROUNDED_SQUARE -> "(x - cx)² + (y - cy)² = r²"
        ConnectButtonShape.CIRCLE -> "x² + y² = R²"
        ConnectButtonShape.SQUIRCLE -> "|x|ⁿ + |y|ⁿ = Rⁿ"
        ConnectButtonShape.COOKIE_4 -> "r(θ) = R - a/2 · (1 - cos(4θ))"
        ConnectButtonShape.COOKIE_6 -> "r(θ) = R - a/2 · (1 - cos(6θ))"
        ConnectButtonShape.COOKIE_9 -> "r(θ) = R - a/2 · (1 - cos(9θ))"
        ConnectButtonShape.ARCH -> "x² + (y - h)² = (w/2)², H = h + w/2"
        ConnectButtonShape.PILL -> "(x ± (w/2 - h/2))² + y² = (h/2)²"
        ConnectButtonShape.OVAL -> "x²/a² + y²/b² = 1"
    }

    fun formatFloat2(v: Float): String = String.format(Locale.US, "%.2f", v)
    fun formatFloat1(v: Float): String = String.format(Locale.US, "%.1f", v)
    fun lerpRange(min: Float, max: Float, f: Float, step: Float): Float {
        val raw = min + (max - min) * f.coerceIn(0f, 1f)
        return ((raw / step).roundToInt() * step).coerceIn(min, max)
    }
    fun toFraction(v: Float, min: Float, max: Float): Float =
        ((v - min) / (max - min).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_shape_formula_params),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (safe.hasCustomParams(shape)) {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier
                        .tvFocusHighlight(cornerRadius = 14.dp)
                        .testTag("visual_reset_shape_params")
                ) {
                    Icon(
                        imageVector = AppIcons.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.action_reset_shape_params),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = formulaText,
                style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        when (shape) {
            ConnectButtonShape.SQUARE -> {
                SoftValueSlider(
                    label = stringResource(R.string.param_square_side),
                    valueText = formatFloat2(safe.squareSide),
                    fraction = toFraction(safe.squareSide, 0.64f, 0.96f),
                    sliderTestTag = "shape_param_1_slider",
                    onFractionChange = { f ->
                        onDraftChange(safe.copy(squareSide = lerpRange(0.64f, 0.96f, f, 0.01f)))
                    },
                    onFractionChangeFinished = onCommit
                )
            }

            ConnectButtonShape.ROUNDED_SQUARE -> {
                SoftValueSlider(
                    label = stringResource(R.string.param_square_side),
                    valueText = formatFloat2(safe.roundedSquareSide),
                    fraction = toFraction(safe.roundedSquareSide, 0.64f, 0.96f),
                    sliderTestTag = "shape_param_1_slider",
                    onFractionChange = { f ->
                        val nextS = lerpRange(0.64f, 0.96f, f, 0.01f)
                        onDraftChange(safe.copy(roundedSquareSide = nextS).coerced())
                    },
                    onFractionChangeFinished = onCommit
                )
                val maxR = (safe.roundedSquareSide * 0.49f).coerceAtLeast(0.08f)
                SoftValueSlider(
                    label = stringResource(R.string.param_corner_radius),
                    valueText = formatFloat2(safe.roundedSquareRadius),
                    fraction = toFraction(safe.roundedSquareRadius, 0.04f, maxR),
                    sliderTestTag = "shape_param_2_slider",
                    onFractionChange = { f ->
                        val nextR = lerpRange(0.04f, maxR, f, 0.01f)
                        onDraftChange(safe.copy(roundedSquareRadius = nextR).coerced())
                    },
                    onFractionChangeFinished = onCommit
                )
            }

            ConnectButtonShape.CIRCLE -> {
                SoftValueSlider(
                    label = stringResource(R.string.param_radius),
                    valueText = formatFloat2(safe.circleRadius),
                    fraction = toFraction(safe.circleRadius, 0.32f, 0.49f),
                    sliderTestTag = "shape_param_1_slider",
                    onFractionChange = { f ->
                        onDraftChange(safe.copy(circleRadius = lerpRange(0.32f, 0.49f, f, 0.01f)))
                    },
                    onFractionChangeFinished = onCommit
                )
            }

            ConnectButtonShape.SQUIRCLE -> {
                SoftValueSlider(
                    label = stringResource(R.string.param_radius),
                    valueText = formatFloat2(safe.squircleRadius),
                    fraction = toFraction(safe.squircleRadius, 0.32f, 0.49f),
                    sliderTestTag = "shape_param_1_slider",
                    onFractionChange = { f ->
                        onDraftChange(safe.copy(squircleRadius = lerpRange(0.32f, 0.49f, f, 0.01f)))
                    },
                    onFractionChangeFinished = onCommit
                )
                SoftValueSlider(
                    label = stringResource(R.string.param_squircle_power),
                    valueText = formatFloat1(safe.squirclePower),
                    fraction = toFraction(safe.squirclePower, 2.2f, 8.0f),
                    captions = remember {
                        listOf(
                            0f to "2.2",
                            (4.0f - 2.2f) / (8.0f - 2.2f) to "4",
                            (6.0f - 2.2f) / (8.0f - 2.2f) to "6",
                            1f to "8"
                        )
                    },
                    sliderTestTag = "shape_param_2_slider",
                    onFractionChange = { f ->
                        onDraftChange(safe.copy(squirclePower = lerpRange(2.2f, 8.0f, f, 0.2f)))
                    },
                    onFractionChangeFinished = onCommit
                )
            }

            ConnectButtonShape.COOKIE_4 -> {
                SoftValueSlider(
                    label = stringResource(R.string.param_radius),
                    valueText = formatFloat2(safe.cookie4Radius),
                    fraction = toFraction(safe.cookie4Radius, 0.34f, 0.49f),
                    sliderTestTag = "shape_param_1_slider",
                    onFractionChange = { f ->
                        val nextR = lerpRange(0.34f, 0.49f, f, 0.01f)
                        onDraftChange(safe.copy(cookie4Radius = nextR).coerced())
                    },
                    onFractionChangeFinished = onCommit
                )
                val maxA = (safe.cookie4Radius * 0.65f).coerceAtMost(0.24f)
                SoftValueSlider(
                    label = stringResource(R.string.param_cookie_depth),
                    valueText = formatFloat2(safe.cookie4Depth),
                    fraction = toFraction(safe.cookie4Depth, 0.02f, maxA),
                    sliderTestTag = "shape_param_2_slider",
                    onFractionChange = { f ->
                        val nextA = lerpRange(0.02f, maxA, f, 0.005f)
                        onDraftChange(safe.copy(cookie4Depth = nextA).coerced())
                    },
                    onFractionChangeFinished = onCommit
                )
            }

            ConnectButtonShape.COOKIE_6 -> {
                SoftValueSlider(
                    label = stringResource(R.string.param_radius),
                    valueText = formatFloat2(safe.cookie6Radius),
                    fraction = toFraction(safe.cookie6Radius, 0.34f, 0.49f),
                    sliderTestTag = "shape_param_1_slider",
                    onFractionChange = { f ->
                        val nextR = lerpRange(0.34f, 0.49f, f, 0.01f)
                        onDraftChange(safe.copy(cookie6Radius = nextR).coerced())
                    },
                    onFractionChangeFinished = onCommit
                )
                val maxA = (safe.cookie6Radius * 0.55f).coerceAtMost(0.20f)
                SoftValueSlider(
                    label = stringResource(R.string.param_cookie_depth),
                    valueText = formatFloat2(safe.cookie6Depth),
                    fraction = toFraction(safe.cookie6Depth, 0.02f, maxA),
                    sliderTestTag = "shape_param_2_slider",
                    onFractionChange = { f ->
                        val nextA = lerpRange(0.02f, maxA, f, 0.005f)
                        onDraftChange(safe.copy(cookie6Depth = nextA).coerced())
                    },
                    onFractionChangeFinished = onCommit
                )
            }

            ConnectButtonShape.COOKIE_9 -> {
                SoftValueSlider(
                    label = stringResource(R.string.param_radius),
                    valueText = formatFloat2(safe.cookie9Radius),
                    fraction = toFraction(safe.cookie9Radius, 0.34f, 0.49f),
                    sliderTestTag = "shape_param_1_slider",
                    onFractionChange = { f ->
                        val nextR = lerpRange(0.34f, 0.49f, f, 0.01f)
                        onDraftChange(safe.copy(cookie9Radius = nextR).coerced())
                    },
                    onFractionChangeFinished = onCommit
                )
                val maxA = (safe.cookie9Radius * 0.45f).coerceAtMost(0.16f)
                SoftValueSlider(
                    label = stringResource(R.string.param_cookie_depth),
                    valueText = formatFloat2(safe.cookie9Depth),
                    fraction = toFraction(safe.cookie9Depth, 0.01f, maxA),
                    sliderTestTag = "shape_param_2_slider",
                    onFractionChange = { f ->
                        val nextA = lerpRange(0.01f, maxA, f, 0.005f)
                        onDraftChange(safe.copy(cookie9Depth = nextA).coerced())
                    },
                    onFractionChangeFinished = onCommit
                )
            }

            ConnectButtonShape.ARCH -> {
                SoftValueSlider(
                    label = stringResource(R.string.param_arch_width),
                    valueText = formatFloat2(safe.archWidth),
                    fraction = toFraction(safe.archWidth, 0.56f, 0.94f),
                    sliderTestTag = "shape_param_1_slider",
                    onFractionChange = { f ->
                        val nextW = lerpRange(0.56f, 0.94f, f, 0.01f)
                        onDraftChange(safe.copy(archWidth = nextW).coerced())
                    },
                    onFractionChangeFinished = onCommit
                )
                val maxH = (0.96f - safe.archWidth * 0.5f).coerceAtLeast(0.24f)
                SoftValueSlider(
                    label = stringResource(R.string.param_arch_height),
                    valueText = formatFloat2(safe.archHeight),
                    fraction = toFraction(safe.archHeight, 0.18f, maxH),
                    sliderTestTag = "shape_param_2_slider",
                    onFractionChange = { f ->
                        val nextH = lerpRange(0.18f, maxH, f, 0.01f)
                        onDraftChange(safe.copy(archHeight = nextH).coerced())
                    },
                    onFractionChangeFinished = onCommit
                )
            }

            ConnectButtonShape.PILL -> {
                SoftValueSlider(
                    label = stringResource(R.string.param_pill_width),
                    valueText = formatFloat2(safe.pillWidth),
                    fraction = toFraction(safe.pillWidth, 0.62f, 0.96f),
                    sliderTestTag = "shape_param_1_slider",
                    onFractionChange = { f ->
                        val nextW = lerpRange(0.62f, 0.96f, f, 0.01f)
                        onDraftChange(safe.copy(pillWidth = nextW).coerced())
                    },
                    onFractionChangeFinished = onCommit
                )
                val maxH = minOf(0.78f, safe.pillWidth).coerceAtLeast(0.38f)
                SoftValueSlider(
                    label = stringResource(R.string.param_pill_height),
                    valueText = formatFloat2(safe.pillHeight),
                    fraction = toFraction(safe.pillHeight, 0.34f, maxH),
                    sliderTestTag = "shape_param_2_slider",
                    onFractionChange = { f ->
                        val nextH = lerpRange(0.34f, maxH, f, 0.01f)
                        onDraftChange(safe.copy(pillHeight = nextH).coerced())
                    },
                    onFractionChangeFinished = onCommit
                )
            }

            ConnectButtonShape.OVAL -> {
                SoftValueSlider(
                    label = stringResource(R.string.param_oval_a),
                    valueText = formatFloat2(safe.ovalRadiusX),
                    fraction = toFraction(safe.ovalRadiusX, 0.26f, 0.49f),
                    sliderTestTag = "shape_param_1_slider",
                    onFractionChange = { f ->
                        onDraftChange(safe.copy(ovalRadiusX = lerpRange(0.26f, 0.49f, f, 0.01f)))
                    },
                    onFractionChangeFinished = onCommit
                )
                SoftValueSlider(
                    label = stringResource(R.string.param_oval_b),
                    valueText = formatFloat2(safe.ovalRadiusY),
                    fraction = toFraction(safe.ovalRadiusY, 0.20f, 0.46f),
                    sliderTestTag = "shape_param_2_slider",
                    onFractionChange = { f ->
                        onDraftChange(safe.copy(ovalRadiusY = lerpRange(0.20f, 0.46f, f, 0.01f)))
                    },
                    onFractionChangeFinished = onCommit
                )
            }
        }
    }
}

@Composable
private fun ButtonShapeTile(
    shapeOption: ConnectButtonShape,
    shapeParams: ButtonShapeParams,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when (shapeOption) {
        ConnectButtonShape.SQUARE -> stringResource(R.string.shape_square)
        ConnectButtonShape.ROUNDED_SQUARE -> stringResource(R.string.shape_rounded_square)
        ConnectButtonShape.CIRCLE -> stringResource(R.string.shape_circle)
        ConnectButtonShape.SQUIRCLE -> stringResource(R.string.shape_squircle)
        ConnectButtonShape.COOKIE_4 -> stringResource(R.string.shape_cookie_4)
        ConnectButtonShape.COOKIE_6 -> stringResource(R.string.shape_cookie_6)
        ConnectButtonShape.COOKIE_9 -> stringResource(R.string.shape_cookie_9)
        ConnectButtonShape.ARCH -> stringResource(R.string.shape_arch)
        ConnectButtonShape.PILL -> stringResource(R.string.shape_pill)
        ConnectButtonShape.OVAL -> stringResource(R.string.shape_oval)
    }
    val previewShape = remember(shapeOption, shapeParams) { shapeOption.toComposeShape(shapeParams) }
    val tileShape = remember { RoundedCornerShape(18.dp) }

    Surface(
        shape = tileShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            }
        ),
        modifier = modifier
            .tvFocusHighlight(cornerRadius = 18.dp, scaleOnFocus = 1.04f)
            .clip(tileShape)
            .clickable(onClick = onClick)
            .testTag("visual_shape_${shapeOption.name}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(previewShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        },
                        shape = previewShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.PowerSettingsNew,
                    contentDescription = label,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    val initArgb = initialColor.toArgb()
    var red by remember { mutableIntStateOf((initArgb shr 16) and 0xFF) }
    var green by remember { mutableIntStateOf((initArgb shr 8) and 0xFF) }
    var blue by remember { mutableIntStateOf(initArgb and 0xFF) }
    var hexInput by remember {
        mutableStateOf(String.format(Locale.US, "#%02X%02X%02X", red, green, blue))
    }

    val currentColor = Color(red, green, blue)

    fun syncHexFromRgb() {
        hexInput = String.format(Locale.US, "#%02X%02X%02X", red, green, blue)
    }

    PredictiveBackAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.dialog_pick_color_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(currentColor)
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            RoundedCornerShape(16.dp)
                        )
                )

                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        hexInput = raw.take(7)
                        val clean = raw.removePrefix("#")
                        if (clean.length == 6) {
                            clean.toLongOrNull(16)?.let { rgb ->
                                red = ((rgb shr 16) and 0xFF).toInt()
                                green = ((rgb shr 8) and 0xFF).toInt()
                                blue = (rgb and 0xFF).toInt()
                            }
                        }
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_color_hex)) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                SoftValueSlider(
                    label = "R",
                    valueText = "$red",
                    fraction = red / 255f,
                    captions = remember { listOf(0f to "0", 0.5f to "128", 1f to "255") },
                    editableValue = EditableSliderValue(red, 0, 255) { v ->
                        red = v
                        syncHexFromRgb()
                    },
                    onFractionChange = { f ->
                        red = (f * 255f).roundToInt().coerceIn(0, 255)
                        syncHexFromRgb()
                    }
                )
                SoftValueSlider(
                    label = "G",
                    valueText = "$green",
                    fraction = green / 255f,
                    captions = remember { listOf(0f to "0", 0.5f to "128", 1f to "255") },
                    editableValue = EditableSliderValue(green, 0, 255) { v ->
                        green = v
                        syncHexFromRgb()
                    },
                    onFractionChange = { f ->
                        green = (f * 255f).roundToInt().coerceIn(0, 255)
                        syncHexFromRgb()
                    }
                )
                SoftValueSlider(
                    label = "B",
                    valueText = "$blue",
                    fraction = blue / 255f,
                    captions = remember { listOf(0f to "0", 0.5f to "128", 1f to "255") },
                    editableValue = EditableSliderValue(blue, 0, 255) { v ->
                        blue = v
                        syncHexFromRgb()
                    },
                    onFractionChange = { f ->
                        blue = (f * 255f).roundToInt().coerceIn(0, 255)
                        syncHexFromRgb()
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(currentColor) }) {
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
private fun VisualSectionCard(
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}
