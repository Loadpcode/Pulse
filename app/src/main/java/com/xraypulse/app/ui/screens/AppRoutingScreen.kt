package com.xraypulse.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import com.xraypulse.app.ui.components.SyncedTopAppBar
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.xraypulse.app.R
import com.xraypulse.app.model.InstalledAppInfo
import com.xraypulse.app.model.SplitTunnelMode
import com.xraypulse.app.ui.components.SoftSlidingSelector
import com.xraypulse.app.ui.components.tvFocusHighlight
import com.xraypulse.app.ui.viewmodel.SettingsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AppIconBitmapCache = LruCache<String, ImageBitmap>(120)
private val RoutingControlsCardShape = RoundedCornerShape(24.dp)
private val RoutingSearchShape = RoundedCornerShape(14.dp)
private val AppRowShape = RoundedCornerShape(18.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoutingScreen(
    uiState: SettingsUiState,
    onEnsureAppsLoaded: () -> Unit,
    onSplitModeChange: (SplitTunnelMode) -> Unit,
    onToggleAppPackage: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val routingEnabled = uiState.settings.splitTunnelMode != SplitTunnelMode.DISABLED
    var lastActiveSplitMode by remember {
        mutableStateOf(
            if (uiState.settings.splitTunnelMode == SplitTunnelMode.DISABLED) {
                SplitTunnelMode.ONLY_SELECTED
            } else {
                uiState.settings.splitTunnelMode
            }
        )
    }
    if (uiState.settings.splitTunnelMode != SplitTunnelMode.DISABLED) {
        lastActiveSplitMode = uiState.settings.splitTunnelMode
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SyncedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.label_app_routing),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .tvFocusHighlight(cornerRadius = 14.dp)
                            .testTag("app_routing_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                },
                actions = {
                    Switch(
                        checked = routingEnabled,
                        onCheckedChange = { enabled ->
                            onSplitModeChange(
                                if (enabled) lastActiveSplitMode else SplitTunnelMode.DISABLED
                            )
                        },
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .tvFocusHighlight(cornerRadius = 18.dp)
                            .testTag("traffic_routing_switch")
                    )
                }
            )
        }
    ) { innerPadding ->
        AppRoutingContent(
            uiState = uiState,
            onEnsureAppsLoaded = onEnsureAppsLoaded,
            onSplitModeChange = onSplitModeChange,
            onToggleAppPackage = onToggleAppPackage,
            showInlineEnableToggle = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
fun AppRoutingContent(
    uiState: SettingsUiState,
    onEnsureAppsLoaded: () -> Unit,
    onSplitModeChange: (SplitTunnelMode) -> Unit,
    onToggleAppPackage: (String) -> Unit,
    showInlineEnableToggle: Boolean,
    modifier: Modifier = Modifier
) {
    val settings = uiState.settings
    var lastActiveSplitMode by remember {
        mutableStateOf(
            if (settings.splitTunnelMode == SplitTunnelMode.DISABLED) {
                SplitTunnelMode.ONLY_SELECTED
            } else {
                settings.splitTunnelMode
            }
        )
    }
    if (settings.splitTunnelMode != SplitTunnelMode.DISABLED) {
        lastActiveSplitMode = settings.splitTunnelMode
    }
    val routingEnabled = settings.splitTunnelMode != SplitTunnelMode.DISABLED
    var appSearch by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }

    LaunchedEffect(routingEnabled) {
        if (routingEnabled) {
            onEnsureAppsLoaded()
        }
    }

    val filteredApps = remember(
        routingEnabled,
        uiState.installedApps,
        appSearch,
        showSystemApps,
        settings.splitTunnelPackages
    ) {
        if (!routingEnabled) emptyList()
        else {
            uiState.installedApps
                .filter { app ->
                    val matchesSystem = showSystemApps || !app.isSystemApp || app.packageName in settings.splitTunnelPackages
                    val matchesQuery = appSearch.isBlank() ||
                        app.label.contains(appSearch, ignoreCase = true) ||
                        app.packageName.contains(appSearch, ignoreCase = true)
                    matchesSystem && matchesQuery
                }
                .sortedByDescending { it.packageName in settings.splitTunnelPackages }
        }
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter
    ) {
        val useSideBySide = maxWidth >= 680.dp

        @Composable
        fun ControlsCard(cardModifier: Modifier) {
            Surface(
                shape = RoutingControlsCardShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = cardModifier
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (showInlineEnableToggle) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.label_app_routing),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Switch(
                                checked = routingEnabled,
                                onCheckedChange = { enabled ->
                                    onSplitModeChange(
                                        if (enabled) lastActiveSplitMode else SplitTunnelMode.DISABLED
                                    )
                                },
                                modifier = Modifier
                                    .tvFocusHighlight(cornerRadius = 18.dp)
                                    .testTag("traffic_routing_switch")
                            )
                        }
                    }

                    val onlyLabel = stringResource(R.string.routing_mode_only_selected)
                    val ignoreLabel = stringResource(R.string.routing_mode_ignore_selected)
                    SoftSlidingSelector(
                        options = listOf(
                            SplitTunnelMode.ONLY_SELECTED,
                            SplitTunnelMode.BYPASS_SELECTED
                        ),
                        selectedOption = if (settings.splitTunnelMode == SplitTunnelMode.BYPASS_SELECTED) {
                            SplitTunnelMode.BYPASS_SELECTED
                        } else {
                            SplitTunnelMode.ONLY_SELECTED
                        },
                        optionLabel = { mode ->
                            if (mode == SplitTunnelMode.ONLY_SELECTED) onlyLabel else ignoreLabel
                        },
                        onSelect = { mode ->
                            lastActiveSplitMode = mode
                            onSplitModeChange(mode)
                        },
                        optionTestTag = { mode ->
                            if (mode == SplitTunnelMode.ONLY_SELECTED) {
                                "traffic_routing_mode_only"
                            } else {
                                "traffic_routing_mode_ignore"
                            }
                        }
                    )

                    Text(
                        text = if (lastActiveSplitMode == SplitTunnelMode.ONLY_SELECTED) {
                            stringResource(R.string.routing_mode_only_desc)
                        } else {
                            stringResource(R.string.routing_mode_ignore_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    AnimatedVisibility(
                        visible = routingEnabled,
                        enter = expandVertically(tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                        exit = shrinkVertically(tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(150))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = appSearch,
                                onValueChange = { appSearch = it },
                                placeholder = { Text(stringResource(R.string.search_apps_hint)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (appSearch.isNotEmpty()) {
                                        IconButton(onClick = { appSearch = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = stringResource(R.string.action_clear)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoutingSearchShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("traffic_routing_app_search")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.label_show_system_apps),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Switch(
                                    checked = showSystemApps,
                                    onCheckedChange = { showSystemApps = it },
                                    modifier = Modifier.tvFocusHighlight(cornerRadius = 18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun AppListPane(paneModifier: Modifier) {
            Box(
                modifier = paneModifier,
                contentAlignment = Alignment.Center
            ) {
                when {
                    !routingEnabled -> {
                        Text(
                            text = stringResource(R.string.label_app_routing_enable_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                    uiState.isLoadingApps -> {
                        CircularProgressIndicator()
                    }
                    filteredApps.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.empty_apps_search),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = filteredApps,
                                key = { "app_${it.packageName}" },
                                contentType = { "app_row" }
                            ) { app ->
                                AppRoutingItemRow(
                                    app = app,
                                    checked = app.packageName in settings.splitTunnelPackages,
                                    onToggle = { onToggleAppPackage(app.packageName) },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(200, easing = FastOutSlowInEasing),
                                        placementSpec = tween(240, easing = FastOutSlowInEasing),
                                        fadeOutSpec = tween(160, easing = FastOutSlowInEasing)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (useSideBySide) {
            Row(
                modifier = Modifier
                    .widthIn(max = 1040.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                ControlsCard(
                    cardModifier = Modifier.width(320.dp)
                )
                AppListPane(
                    paneModifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
            ) {
                ControlsCard(
                    cardModifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
                AppListPane(
                    paneModifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AppRoutingItemRow(
    app: InstalledAppInfo,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconBitmap by produceState<ImageBitmap?>(
        initialValue = AppIconBitmapCache.get(app.packageName),
        key1 = app.packageName
    ) {
        if (value == null && app.icon != null) {
            val decoded = withContext(Dispatchers.Default) {
                runCatching { app.icon.toBitmap(72, 72).asImageBitmap() }.getOrNull()
            }
            if (decoded != null) {
                AppIconBitmapCache.put(app.packageName, decoded)
                value = decoded
            }
        }
    }
    Surface(
        shape = AppRowShape,
        color = if (checked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = modifier
            .fillMaxWidth()
            .tvFocusHighlight(cornerRadius = 18.dp, scaleOnFocus = 1.015f)
            .clip(AppRowShape)
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val resolvedIcon = iconBitmap
            if (resolvedIcon != null) {
                Image(
                    bitmap = resolvedIcon,
                    contentDescription = app.label,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Box(modifier = Modifier.size(36.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
