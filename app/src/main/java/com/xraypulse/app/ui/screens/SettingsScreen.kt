package com.xraypulse.app.ui.screens

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import com.xraypulse.app.ui.components.SyncedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xraypulse.app.R
import com.xraypulse.app.model.AppLanguage
import com.xraypulse.app.model.AppThemeMode
import com.xraypulse.app.model.ButtonShapeParams
import com.xraypulse.app.model.ConnectButtonShape
import com.xraypulse.app.model.SplitTunnelMode
import com.xraypulse.app.ui.components.AppIcons
import com.xraypulse.app.ui.components.LocalAdaptiveLayout
import com.xraypulse.app.ui.components.tvFocusHighlight
import com.xraypulse.app.ui.viewmodel.SettingsUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

private enum class SettingsDetailPane {
    APP,
    VISUAL,
    TRAFFIC,
    APP_ROUTING,
    LOGS,
    CONFIG_PREVIEW
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    logs: List<String>,
    onOpenAppSettings: () -> Unit,
    onOpenTrafficSettings: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenConfigPreview: () -> Unit,
    onBack: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onMarqueeChange: (Boolean) -> Unit,
    onCustomAccentColorChange: (Long?) -> Unit,
    onCustomBackgroundColorChange: (Long?) -> Unit,
    onConnectingSpeedChange: (Int) -> Unit,
    onConnectedSpeedChange: (Int) -> Unit,
    onButtonShapeChange: (ConnectButtonShape) -> Unit,
    onButtonShapeParamsChange: (ButtonShapeParams) -> Unit,
    onSaveFragment: (Boolean, String, String, String) -> Unit,
    onSaveMux: (Boolean, Int, Int) -> Unit,
    onBypassLanChange: (Boolean) -> Unit,
    onMtuChange: (Int) -> Unit,
    onPreferIpv6Change: (Boolean) -> Unit,
    onSaveAutoUpdate: (Boolean, Boolean, Boolean, Int) -> Unit,
    onEnsureAppsLoaded: () -> Unit,
    onSplitModeChange: (SplitTunnelMode) -> Unit,
    onToggleAppPackage: (String) -> Unit,
    onClearLogs: () -> Unit,
    onRefreshConfigPreview: () -> Unit,
    onCopied: () -> Unit,
    onShareLogs: () -> Unit,
    onShareConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val adaptive = LocalAdaptiveLayout.current
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(adaptive.useTwoPaneSettings) }
    var paneStackEncoded by rememberSaveable { mutableStateOf(SettingsDetailPane.APP.name) }
    val paneBackStack = remember(paneStackEncoded) {
        paneStackEncoded.split(",").mapNotNull { token ->
            runCatching { SettingsDetailPane.valueOf(token) }.getOrNull()
        }.ifEmpty { listOf(SettingsDetailPane.APP) }
    }
    val activePane = paneBackStack.last()
    val previousPane = paneBackStack.getOrNull(paneBackStack.lastIndex - 1)

    val detailBackProgress = remember { Animatable(0f) }
    var detailBackEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    fun openDetailPane(pane: SettingsDetailPane) {
        if (paneBackStack.lastOrNull() == pane) return
        val next = (paneBackStack + pane).takeLast(12)
        paneStackEncoded = next.joinToString(",") { it.name }
    }

    fun navigateDetailBack(): Boolean {
        if (paneBackStack.size <= 1) return false
        paneStackEncoded = paneBackStack.dropLast(1).joinToString(",") { it.name }
        return true
    }

    // Smooth interactive Predictive Back for all Two-Pane tabs & sub-screens (App, Visual, Traffic, App Routing, Logs, Config Preview)
    PredictiveBackHandler(
        enabled = adaptive.useTwoPaneSettings && paneBackStack.size > 1
    ) { progressFlow: Flow<BackEventCompat> ->
        try {
            progressFlow.collect { event ->
                detailBackEdge = event.swipeEdge
                detailBackProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            navigateDetailBack()
            detailBackProgress.snapTo(0f)
        } catch (_: CancellationException) {
            detailBackProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SyncedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!(adaptive.useTwoPaneSettings && navigateDetailBack())) {
                                onBack()
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .tvFocusHighlight(cornerRadius = 14.dp)
                            .testTag("settings_back_button")
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
        if (adaptive.useTwoPaneSettings) {
            // Canonical Two-Pane List-Detail layout for Tablets & Android TV
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = adaptive.screenHorizontalPadding / 2),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsHubCard(
                        icon = AppIcons.Palette,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_item_app_title),
                        subtitle = stringResource(R.string.settings_item_app_desc),
                        selected = activePane == SettingsDetailPane.APP || activePane == SettingsDetailPane.VISUAL,
                        onClick = { openDetailPane(SettingsDetailPane.APP) },
                        testTagName = "settings_hub_app"
                    )

                    SettingsHubCard(
                        icon = AppIcons.Tune,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = stringResource(R.string.settings_item_traffic_title),
                        subtitle = stringResource(R.string.settings_item_traffic_desc),
                        selected = activePane == SettingsDetailPane.TRAFFIC || activePane == SettingsDetailPane.APP_ROUTING,
                        onClick = { openDetailPane(SettingsDetailPane.TRAFFIC) },
                        testTagName = "settings_hub_traffic"
                    )

                    DiagnosticsHubCard(
                        xrayVersion = uiState.xrayVersion,
                        expanded = diagnosticsExpanded,
                        selectedLogs = activePane == SettingsDetailPane.LOGS,
                        selectedConfig = activePane == SettingsDetailPane.CONFIG_PREVIEW,
                        onToggleExpand = { diagnosticsExpanded = !diagnosticsExpanded },
                        onOpenLogs = { openDetailPane(SettingsDetailPane.LOGS) },
                        onOpenConfigPreview = { openDetailPane(SettingsDetailPane.CONFIG_PREVIEW) }
                    )
                }

                VerticalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                val detailSwipeShape = remember { RoundedCornerShape(24.dp) }
                val detailOutlineColor = MaterialTheme.colorScheme.outline

                @Composable
                fun renderPane(pane: SettingsDetailPane) {
                    when (pane) {
                        SettingsDetailPane.APP -> AppSettingsContent(
                            uiState = uiState,
                            onThemeModeChange = onThemeModeChange,
                            onDynamicColorChange = onDynamicColorChange,
                            onLanguageChange = onLanguageChange,
                            onMarqueeChange = onMarqueeChange,
                            onOpenVisualSettings = { openDetailPane(SettingsDetailPane.VISUAL) },
                            modifier = Modifier.fillMaxSize()
                        )
                        SettingsDetailPane.VISUAL -> VisualSettingsContent(
                            uiState = uiState,
                            onDynamicColorChange = onDynamicColorChange,
                            onCustomAccentColorChange = onCustomAccentColorChange,
                            onCustomBackgroundColorChange = onCustomBackgroundColorChange,
                            onConnectingSpeedChange = onConnectingSpeedChange,
                            onConnectedSpeedChange = onConnectedSpeedChange,
                            onButtonShapeChange = onButtonShapeChange,
                            onButtonShapeParamsChange = onButtonShapeParamsChange,
                            modifier = Modifier.fillMaxSize()
                        )
                        SettingsDetailPane.TRAFFIC -> TrafficSettingsContent(
                            uiState = uiState,
                            onSaveFragment = onSaveFragment,
                            onSaveMux = onSaveMux,
                            onBypassLanChange = onBypassLanChange,
                            onMtuChange = onMtuChange,
                            onPreferIpv6Change = onPreferIpv6Change,
                            onSaveAutoUpdate = onSaveAutoUpdate,
                            onOpenAppRouting = { openDetailPane(SettingsDetailPane.APP_ROUTING) },
                            modifier = Modifier.fillMaxSize()
                        )
                        SettingsDetailPane.APP_ROUTING -> AppRoutingContent(
                            uiState = uiState,
                            onEnsureAppsLoaded = onEnsureAppsLoaded,
                            onSplitModeChange = onSplitModeChange,
                            onToggleAppPackage = onToggleAppPackage,
                            showInlineEnableToggle = true,
                            modifier = Modifier.fillMaxSize()
                        )
                        SettingsDetailPane.LOGS -> LogsContent(
                            logs = logs,
                            onClearLogs = onClearLogs,
                            onCopied = onCopied,
                            onShareLogs = onShareLogs,
                            showInlineActions = true,
                            modifier = Modifier.fillMaxSize()
                        )
                        SettingsDetailPane.CONFIG_PREVIEW -> ConfigPreviewContent(
                            configJson = uiState.previewConfigJson,
                            onRefresh = onRefreshConfigPreview,
                            onCopied = onCopied,
                            onShareConfig = onShareConfig,
                            showInlineActions = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val backP = detailBackProgress.value
                    if (backP > 0.01f && previousPane != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .graphicsLayer {
                                    val underScale = 0.94f + 0.06f * backP
                                    scaleX = underScale
                                    scaleY = underScale
                                    alpha = 0.55f + 0.45f * backP
                                }
                        ) {
                            renderPane(previousPane)
                        }
                    }

                    AnimatedContent(
                        targetState = activePane,
                        transitionSpec = {
                            fadeIn(tween(220, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(160, easing = FastOutSlowInEasing))
                        },
                        label = "settings_two_pane_detail",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = detailBackProgress.value
                                if (p > 0.01f) {
                                    val s = 1f - (0.10f * p)
                                    val dir = if (detailBackEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                                    scaleX = s
                                    scaleY = s
                                    translationX = dir * (p * 42.dp.toPx())
                                    alpha = 1f - (0.25f * p)
                                    shape = detailSwipeShape
                                    clip = true
                                }
                            }
                            .background(MaterialTheme.colorScheme.background)
                            .drawWithContent {
                                drawContent()
                                val p = detailBackProgress.value
                                if (p > 0.01f) {
                                    val rPx = 24.dp.toPx()
                                    drawRoundRect(
                                        color = detailOutlineColor.copy(alpha = 0.42f * p),
                                        cornerRadius = CornerRadius(rPx, rPx),
                                        style = Stroke(width = 1.5.dp.toPx())
                                    )
                                }
                            }
                    ) { pane ->
                        renderPane(pane)
                    }
                }
            }
        } else {
            // Single-pane hub for Smartphones
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SettingsHubCard(
                        icon = AppIcons.Palette,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_item_app_title),
                        subtitle = stringResource(R.string.settings_item_app_desc),
                        selected = false,
                        onClick = onOpenAppSettings,
                        testTagName = "settings_hub_app"
                    )

                    SettingsHubCard(
                        icon = AppIcons.Tune,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = stringResource(R.string.settings_item_traffic_title),
                        subtitle = stringResource(R.string.settings_item_traffic_desc),
                        selected = false,
                        onClick = onOpenTrafficSettings,
                        testTagName = "settings_hub_traffic"
                    )

                    DiagnosticsHubCard(
                        xrayVersion = uiState.xrayVersion,
                        expanded = diagnosticsExpanded,
                        selectedLogs = false,
                        selectedConfig = false,
                        onToggleExpand = { diagnosticsExpanded = !diagnosticsExpanded },
                        onOpenLogs = onOpenLogs,
                        onOpenConfigPreview = onOpenConfigPreview
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsHubCard(
    xrayVersion: String,
    expanded: Boolean,
    selectedLogs: Boolean,
    selectedConfig: Boolean,
    onToggleExpand: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenConfigPreview: () -> Unit
) {
    val diagShape = remember { RoundedCornerShape(24.dp) }
    val chevronAngleState = animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "diag_chevron"
    )
    Surface(
        shape = diagShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_hub_diagnostics")
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusHighlight(cornerRadius = 24.dp, scaleOnFocus = 1.015f)
                    .clip(diagShape)
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = AppIcons.Terminal,
                            contentDescription = stringResource(R.string.settings_item_diagnostics_title),
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_item_diagnostics_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${stringResource(R.string.settings_item_diagnostics_desc)} • $xrayVersion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronAngleState.value }
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(160))
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    DiagnosticsSubRow(
                        icon = AppIcons.Terminal,
                        tint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.label_xray_logs),
                        subtitle = stringResource(R.string.label_xray_logs_desc),
                        selected = selectedLogs,
                        onClick = onOpenLogs,
                        testTagName = "settings_open_logs"
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    DiagnosticsSubRow(
                        icon = AppIcons.Code,
                        tint = MaterialTheme.colorScheme.secondary,
                        title = stringResource(R.string.label_config_preview),
                        subtitle = stringResource(R.string.label_config_preview_desc),
                        selected = selectedConfig,
                        onClick = onOpenConfigPreview,
                        testTagName = "settings_open_config_preview"
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHubCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTagName: String
) {
    val cardShape = remember { RoundedCornerShape(24.dp) }
    Surface(
        shape = cardShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.5.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusHighlight(cornerRadius = 24.dp, scaleOnFocus = 1.015f)
            .clip(cardShape)
            .clickable(onClick = onClick)
            .testTag(testTagName)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = iconTint.copy(alpha = 0.16f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiagnosticsSubRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTagName: String
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusHighlight(cornerRadius = 16.dp, scaleOnFocus = 1.01f)
            .clickable(onClick = onClick)
            .testTag(testTagName)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
