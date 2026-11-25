package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.local.ServerProfileEntity
import com.example.data.local.SubscriptionEntity
import com.example.model.ButtonShapeParams
import com.example.model.ConnectButtonShape
import com.example.model.TransportType
import com.example.model.VpnProtocol
import com.example.model.VpnStatus
import com.example.ui.components.AppIcons
import com.example.ui.components.LocalAdaptiveLayout
import com.example.ui.components.PredictiveBackAlertDialog
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDuration
import com.example.ui.components.formatExpireDate
import com.example.ui.components.latencyBadgeColor
import com.example.ui.components.toComposeShape
import com.example.ui.components.tvFocusHighlight
import com.example.ui.viewmodel.ConnectUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.math.roundToInt

private val HeaderButtonShape = RoundedCornerShape(14.dp)
private val MenuCornerShape = RoundedCornerShape(16.dp)
private val SubscriptionCardShape = RoundedCornerShape(28.dp)
private val ActionTileShape = RoundedCornerShape(12.dp)
private val ServerRowShape = RoundedCornerShape(16.dp)
private val ProgressTrackShape = RoundedCornerShape(6.dp)
private val DialogCornerShape = RoundedCornerShape(24.dp)
private val DialogInputShape = RoundedCornerShape(14.dp)
private val StatusPillShape = RoundedCornerShape(18.dp)

@Composable
private fun rememberSmoothFlingBehavior(frictionMultiplier: Float = 0.68f): FlingBehavior {
    val decaySpec = remember(frictionMultiplier) {
        exponentialDecay<Float>(
            frictionMultiplier = frictionMultiplier,
            absVelocityThreshold = 0.1f
        )
    }
    return remember(decaySpec) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                if (abs(initialVelocity) <= 1f) return initialVelocity
                var velocityLeft = initialVelocity
                var lastValue = 0f
                AnimationState(
                    initialValue = 0f,
                    initialVelocity = initialVelocity
                ).animateDecay(decaySpec) {
                    val delta = value - lastValue
                    val consumed = scrollBy(delta)
                    lastValue = value
                    velocityLeft = this.velocity
                    if (abs(delta - consumed) > 0.5f) {
                        cancelAnimation()
                    }
                }
                return velocityLeft
            }
        }
    }
}

@Composable
fun ConnectScreen(
    uiState: ConnectUiState,
    sessionTrafficBytesFlow: StateFlow<Long>,
    onToggleConnection: () -> Unit,
    onSelectProfile: (Long) -> Unit,
    onReorderProfiles: (List<Long>) -> Unit = {},
    onRefreshSubscription: (Long?) -> Unit,
    onRenameSubscription: (Long, String) -> Unit,
    onDeleteSubscription: (Long) -> Unit,
    onPingProfile: (ServerProfileEntity) -> Unit,
    onPingSubscription: (Long?) -> Unit,
    onImportClipboard: () -> Unit,
    onImportManualLink: (String) -> Unit,
    onScanQr: () -> Unit,
    onOpenSettings: () -> Unit,
    onShareProfile: (ServerProfileEntity) -> Unit = {},
    onShareSubscription: (SubscriptionEntity) -> Unit = {},
    onShareServers: (List<ServerProfileEntity>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val adaptive = LocalAdaptiveLayout.current
    val haptic = LocalHapticFeedback.current
    val session = uiState.session
    val selectedProfile = uiState.selectedProfile
    var addMenuExpanded by remember { mutableStateOf(false) }
    var showManualLinkDialog by remember { mutableStateOf(false) }
    var renamingSubscription by remember { mutableStateOf<SubscriptionEntity?>(null) }
    var isDraggingServer by remember { mutableStateOf(false) }
    val expandedCardIds = remember { mutableStateMapOf<Long, Boolean>() }

    val heroFocusRequester = remember { FocusRequester() }
    LaunchedEffect(adaptive.isTv) {
        if (adaptive.isTv) {
            runCatching { heroFocusRequester.requestFocus() }
        }
    }

    val listState = rememberLazyListState()
    val smoothFlingBehavior = rememberSmoothFlingBehavior()
    val density = LocalDensity.current
    val squareBottomFromItemTopPx = remember(density) { with(density) { 198.dp.toPx() } }
    val squareHeightPx = remember(density) { with(density) { 148.dp.toPx() } }

    val profilesBySubscription = remember(uiState.profiles) {
        uiState.profiles.groupBy { it.subscriptionId }
    }
    val localProfiles = remember(profilesBySubscription) {
        profilesBySubscription[null].orEmpty()
    }

    val showMiniConnectButton by remember(squareBottomFromItemTopPx, squareHeightPx) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val heroInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == "hero_section" }
            if (heroInfo == null) {
                listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
            } else {
                val visibleSquareBottomPx = heroInfo.offset - layoutInfo.viewportStartOffset + squareBottomFromItemTopPx
                visibleSquareBottomPx <= squareHeightPx * 0.15f
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                horizontal = adaptive.screenHorizontalPadding,
                vertical = adaptive.screenVerticalPadding
            )
    ) {
        // Pinned top header: App brand + xray version on the left; "+" and Settings buttons side-by-side on the right
        Row(
            modifier = Modifier
                .widthIn(max = 1120.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = session.xrayVersion,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("xray_version_label")
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    val addInteractionSource = remember { MutableInteractionSource() }
                    val isAddPressed by addInteractionSource.collectIsPressedAsState()
                    val plusHighlight by animateFloatAsState(
                        targetValue = if (isAddPressed || addMenuExpanded) 1f else 0f,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        label = "add_plus_highlight"
                    )
                    val plusColor = lerp(
                        MaterialTheme.colorScheme.onSurface,
                        MaterialTheme.colorScheme.primary,
                        plusHighlight
                    )

                    Surface(
                        shape = HeaderButtonShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(48.dp)
                            .tvFocusHighlight(cornerRadius = 14.dp, scaleOnFocus = 1.06f)
                            .clip(HeaderButtonShape)
                            .clickable(
                                interactionSource = addInteractionSource,
                                indication = ripple(),
                                onClick = { addMenuExpanded = true }
                            )
                            .testTag("top_add_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.size(20.dp)) {
                                val stroke = 3.2.dp.toPx()
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val c = plusColor
                                drawLine(
                                    color = c,
                                    start = Offset(cx, stroke / 2f),
                                    end = Offset(cx, size.height - stroke / 2f),
                                    strokeWidth = stroke,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = c,
                                    start = Offset(stroke / 2f, cy),
                                    end = Offset(size.width - stroke / 2f, cy),
                                    strokeWidth = stroke,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = addMenuExpanded,
                        onDismissRequest = { addMenuExpanded = false },
                        shape = MenuCornerShape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.menu_import_clipboard),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = AppIcons.ContentPaste,
                                    contentDescription = stringResource(R.string.menu_import_clipboard)
                                )
                            },
                            onClick = {
                                addMenuExpanded = false
                                onImportClipboard()
                            },
                            modifier = Modifier.testTag("menu_item_clipboard")
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.menu_manual_input),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.menu_manual_input)
                                )
                            },
                            onClick = {
                                addMenuExpanded = false
                                showManualLinkDialog = true
                            },
                            modifier = Modifier.testTag("menu_item_manual")
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.menu_qr),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = AppIcons.QrCodeScanner,
                                    contentDescription = stringResource(R.string.menu_qr)
                                )
                            },
                            onClick = {
                                addMenuExpanded = false
                                onScanQr()
                            },
                            modifier = Modifier.testTag("menu_item_qr")
                        )
                    }
                }

                val settingsInteractionSource = remember { MutableInteractionSource() }
                val isSettingsPressed by settingsInteractionSource.collectIsPressedAsState()
                val settingsHighlight by animateFloatAsState(
                    targetValue = if (isSettingsPressed) 1f else 0f,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "settings_gear_highlight"
                )
                val settingsTint = lerp(
                    MaterialTheme.colorScheme.onSurface,
                    MaterialTheme.colorScheme.primary,
                    settingsHighlight
                )

                Surface(
                    shape = HeaderButtonShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .size(48.dp)
                        .tvFocusHighlight(cornerRadius = 14.dp, scaleOnFocus = 1.06f)
                        .clip(HeaderButtonShape)
                        .clickable(
                            interactionSource = settingsInteractionSource,
                            indication = ripple(),
                            onClick = onOpenSettings
                        )
                        .testTag("top_settings_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = settingsTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Responsive body: Two-pane side-by-side on Tablets/TV/Landscape, vertical scrolling column on Phones
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val useTwoPane = adaptive.useTwoPaneConnect || maxWidth >= 680.dp

            if (useTwoPane) {
                val compactHeight = maxHeight < 420.dp
                val heroOuterSize = if (compactHeight) 144.dp else 212.dp
                val heroSquareSize = if (compactHeight) 114.dp else 160.dp
                val heroIconSize = if (compactHeight) 38.dp else 54.dp

                Row(
                    modifier = Modifier
                        .widthIn(max = 1120.dp)
                        .fillMaxSize()
                        .align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Pinned Hero Pane
                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                    ) {
                        ConnectSquareButton(
                            status = session.status,
                            buttonShape = uiState.settings.connectButtonShape,
                            buttonShapeParams = uiState.settings.buttonShapeParams,
                            connectingSpeedDegPerSec = uiState.settings.connectingRotationSpeed,
                            connectedSpeedDegPerSec = uiState.settings.connectedRotationSpeed,
                            outerBoxSize = heroOuterSize,
                            squareSize = heroSquareSize,
                            cornerRadius = 36.dp,
                            borderWidth = 2.dp,
                            iconSize = heroIconSize,
                            spinnerSize = heroIconSize * 0.92f,
                            spinnerStrokeWidth = 3.dp,
                            testTagName = "connect_toggle_button",
                            buttonModifier = Modifier.focusRequester(heroFocusRequester),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleConnection()
                            }
                        )

                        Box(
                            modifier = Modifier.height(26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ConnectionTimer(
                                isConnected = session.status == VpnStatus.CONNECTED,
                                startedAtMillis = session.startedAtMillis
                            )
                        }

                        if (!compactHeight) {
                            val statusText = when (session.status) {
                                VpnStatus.DISCONNECTED -> stringResource(R.string.status_disconnected)
                                VpnStatus.CONNECTING -> stringResource(R.string.status_connecting)
                                VpnStatus.CONNECTED -> stringResource(R.string.status_connected)
                                VpnStatus.DISCONNECTING -> stringResource(R.string.status_disconnecting)
                                VpnStatus.ERROR -> stringResource(R.string.status_error)
                            }
                            Surface(
                                shape = StatusPillShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (session.status == VpnStatus.ERROR) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                                    Text(
                                        text = selectedProfile?.name ?: stringResource(R.string.label_no_server_selected),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Right Scrollable Subscriptions & Server List Pane
                    LazyColumn(
                        state = listState,
                        flingBehavior = smoothFlingBehavior,
                        userScrollEnabled = !isDraggingServer,
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(
                            items = uiState.subscriptions,
                            key = { "sub_${it.id}" },
                            contentType = { "subscription_card" }
                        ) { sub ->
                            val subProfiles = profilesBySubscription[sub.id].orEmpty()
                            val isExpanded = expandedCardIds[sub.id] ?: false
                            SubscriptionCardWindow(
                                subscription = sub,
                                profiles = subProfiles,
                                selectedProfile = selectedProfile,
                                testingProfileIds = uiState.testingProfileIds,
                                sessionTrafficBytesFlow = sessionTrafficBytesFlow,
                                isSyncing = uiState.isSyncing || sub.id in uiState.syncingSubscriptionIds,
                                isListExpanded = isExpanded,
                                marqueeEnabled = uiState.settings.marqueeEnabled,
                                onRefresh = { onRefreshSubscription(sub.id) },
                                onRename = { renamingSubscription = sub },
                                onDelete = { onDeleteSubscription(sub.id) },
                                onToggleList = { expandedCardIds[sub.id] = !isExpanded },
                                onSelectProfile = onSelectProfile,
                                onReorderProfiles = onReorderProfiles,
                                onDraggingChange = { isDraggingServer = it },
                                onPingProfile = onPingProfile,
                                onPingAll = { onPingSubscription(sub.id) },
                                onShareProfile = onShareProfile,
                                onShareSubscription = { onShareSubscription(sub) },
                                onShareServers = { onShareServers(subProfiles) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(220, easing = FastOutSlowInEasing),
                                    placementSpec = null,
                                    fadeOutSpec = tween(180, easing = FastOutSlowInEasing)
                                )
                            )
                        }

                        if (localProfiles.isNotEmpty() || uiState.subscriptions.isEmpty()) {
                            item(key = "local_profiles_card", contentType = "local_card") {
                                val isExpanded = expandedCardIds[-1L] ?: false
                                SubscriptionCardWindow(
                                    subscription = null,
                                    profiles = localProfiles,
                                    selectedProfile = selectedProfile,
                                    testingProfileIds = uiState.testingProfileIds,
                                    sessionTrafficBytesFlow = sessionTrafficBytesFlow,
                                    isSyncing = uiState.isSyncing,
                                    isListExpanded = isExpanded,
                                    marqueeEnabled = uiState.settings.marqueeEnabled,
                                    onRefresh = { onRefreshSubscription(null) },
                                    onRename = {},
                                    onDelete = {},
                                    onToggleList = { expandedCardIds[-1L] = !isExpanded },
                                    onSelectProfile = onSelectProfile,
                                    onReorderProfiles = onReorderProfiles,
                                    onDraggingChange = { isDraggingServer = it },
                                    onPingProfile = onPingProfile,
                                    onPingAll = { onPingSubscription(null) },
                                    onShareProfile = onShareProfile,
                                    onShareSubscription = {},
                                    onShareServers = { onShareServers(localProfiles) },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(220, easing = FastOutSlowInEasing),
                                        placementSpec = null,
                                        fadeOutSpec = tween(180, easing = FastOutSlowInEasing)
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                // Single-pane vertical scrolling layout for Smartphones
                LazyColumn(
                    state = listState,
                    flingBehavior = smoothFlingBehavior,
                    userScrollEnabled = !isDraggingServer,
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize()
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item(key = "hero_section", contentType = "hero") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ConnectSquareButton(
                                status = session.status,
                                buttonShape = uiState.settings.connectButtonShape,
                                buttonShapeParams = uiState.settings.buttonShapeParams,
                                connectingSpeedDegPerSec = uiState.settings.connectingRotationSpeed,
                                connectedSpeedDegPerSec = uiState.settings.connectedRotationSpeed,
                                outerBoxSize = 200.dp,
                                squareSize = 148.dp,
                                cornerRadius = 34.dp,
                                borderWidth = 2.dp,
                                iconSize = 50.dp,
                                spinnerSize = 46.dp,
                                spinnerStrokeWidth = 3.dp,
                                testTagName = "connect_toggle_button",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleConnection()
                                }
                            )

                            Box(
                                modifier = Modifier.height(26.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ConnectionTimer(
                                    isConnected = session.status == VpnStatus.CONNECTED,
                                    startedAtMillis = session.startedAtMillis
                                )
                            }
                        }
                    }

                    items(
                        items = uiState.subscriptions,
                        key = { "sub_${it.id}" },
                        contentType = { "subscription_card" }
                    ) { sub ->
                        val subProfiles = profilesBySubscription[sub.id].orEmpty()
                        val isExpanded = expandedCardIds[sub.id] ?: false
                        SubscriptionCardWindow(
                            subscription = sub,
                            profiles = subProfiles,
                            selectedProfile = selectedProfile,
                            testingProfileIds = uiState.testingProfileIds,
                            sessionTrafficBytesFlow = sessionTrafficBytesFlow,
                            isSyncing = uiState.isSyncing || sub.id in uiState.syncingSubscriptionIds,
                            isListExpanded = isExpanded,
                            marqueeEnabled = uiState.settings.marqueeEnabled,
                            onRefresh = { onRefreshSubscription(sub.id) },
                            onRename = { renamingSubscription = sub },
                            onDelete = { onDeleteSubscription(sub.id) },
                            onToggleList = { expandedCardIds[sub.id] = !isExpanded },
                            onSelectProfile = onSelectProfile,
                            onReorderProfiles = onReorderProfiles,
                            onDraggingChange = { isDraggingServer = it },
                            onPingProfile = onPingProfile,
                            onPingAll = { onPingSubscription(sub.id) },
                            onShareProfile = onShareProfile,
                            onShareSubscription = { onShareSubscription(sub) },
                            onShareServers = { onShareServers(subProfiles) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220, easing = FastOutSlowInEasing),
                                placementSpec = null,
                                fadeOutSpec = tween(180, easing = FastOutSlowInEasing)
                            )
                        )
                    }

                    if (localProfiles.isNotEmpty() || uiState.subscriptions.isEmpty()) {
                        item(key = "local_profiles_card", contentType = "local_card") {
                            val isExpanded = expandedCardIds[-1L] ?: false
                            SubscriptionCardWindow(
                                subscription = null,
                                profiles = localProfiles,
                                selectedProfile = selectedProfile,
                                testingProfileIds = uiState.testingProfileIds,
                                sessionTrafficBytesFlow = sessionTrafficBytesFlow,
                                isSyncing = uiState.isSyncing,
                                isListExpanded = isExpanded,
                                marqueeEnabled = uiState.settings.marqueeEnabled,
                                onRefresh = { onRefreshSubscription(null) },
                                onRename = {},
                                onDelete = {},
                                onToggleList = { expandedCardIds[-1L] = !isExpanded },
                                onSelectProfile = onSelectProfile,
                                onReorderProfiles = onReorderProfiles,
                                onDraggingChange = { isDraggingServer = it },
                                onPingProfile = onPingProfile,
                                onPingAll = { onPingSubscription(null) },
                                onShareProfile = onShareProfile,
                                onShareSubscription = {},
                                onShareServers = { onShareServers(localProfiles) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(220, easing = FastOutSlowInEasing),
                                    placementSpec = null,
                                    fadeOutSpec = tween(180, easing = FastOutSlowInEasing)
                                )
                            )
                        }
                    }
                }

                // Bottom-left mini copy of the connect button when the hero button is scrolled off-screen
                androidx.compose.animation.AnimatedVisibility(
                    visible = showMiniConnectButton,
                    enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                        scaleIn(
                            initialScale = 0.72f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ),
                    exit = fadeOut(tween(240, easing = FastOutSlowInEasing)) +
                        scaleOut(
                            targetScale = 0.72f,
                            animationSpec = tween(240, easing = FastOutSlowInEasing)
                        ),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 8.dp)
                ) {
                    ConnectSquareButton(
                        status = session.status,
                        buttonShape = uiState.settings.connectButtonShape,
                        buttonShapeParams = uiState.settings.buttonShapeParams,
                        connectingSpeedDegPerSec = uiState.settings.connectingRotationSpeed,
                        connectedSpeedDegPerSec = uiState.settings.connectedRotationSpeed,
                        outerBoxSize = 76.dp,
                        squareSize = 56.dp,
                        cornerRadius = 16.dp,
                        borderWidth = 1.5.dp,
                        iconSize = 24.dp,
                        spinnerSize = 22.dp,
                        spinnerStrokeWidth = 2.5.dp,
                        elevated = true,
                        testTagName = "mini_connect_toggle_button",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleConnection()
                        }
                    )
                }
            }
        }
    }

    if (showManualLinkDialog) {
        ManualLinkInputDialog(
            onDismiss = { showManualLinkDialog = false },
            onConfirm = { linkText ->
                showManualLinkDialog = false
                onImportManualLink(linkText)
            }
        )
    }

    renamingSubscription?.let { targetSub ->
        RenameSubscriptionDialog(
            currentName = targetSub.name,
            onDismiss = { renamingSubscription = null },
            onConfirm = { newName ->
                onRenameSubscription(targetSub.id, newName)
                renamingSubscription = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubscriptionCardWindow(
    subscription: SubscriptionEntity?,
    profiles: List<ServerProfileEntity>,
    selectedProfile: ServerProfileEntity?,
    testingProfileIds: Set<Long>,
    sessionTrafficBytesFlow: StateFlow<Long>,
    isSyncing: Boolean,
    isListExpanded: Boolean,
    marqueeEnabled: Boolean = false,
    onRefresh: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleList: () -> Unit,
    onSelectProfile: (Long) -> Unit,
    onReorderProfiles: (List<Long>) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    onPingProfile: (ServerProfileEntity) -> Unit,
    onPingAll: () -> Unit,
    onShareProfile: (ServerProfileEntity) -> Unit,
    onShareSubscription: () -> Unit,
    onShareServers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var showCardMenu by remember { mutableStateOf(false) }

    var displayProfiles by remember { mutableStateOf(profiles) }
    var draggedProfileId by remember { mutableStateOf<Long?>(null) }
    var dragDeltaY by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<Long, Int>() }
    val pendingDropOffsets = remember { mutableStateMapOf<Long, Float>() }

    LaunchedEffect(profiles) {
        if (draggedProfileId == null) {
            displayProfiles = profiles
        }
    }

    val currentProfiles by rememberUpdatedState(displayProfiles)
    val currentSourceProfiles by rememberUpdatedState(profiles)
    val currentOnReorder by rememberUpdatedState(onReorderProfiles)
    val currentOnDraggingChange by rememberUpdatedState(onDraggingChange)

    val spacingPx = remember(density) { with(density) { 6.dp.toPx() } }
    val defaultRowHeightPx = remember(density) { with(density) { 56.dp.toPx() } }
    val boundaryOvershootPx = remember(density) { with(density) { 18.dp.toPx() } }
    val dragShadowPx = remember(density) { with(density) { 12.dp.toPx() } }

    val cardSelectedProfile = remember(displayProfiles, selectedProfile?.id) {
        displayProfiles.find { it.id == selectedProfile?.id } ?: displayProfiles.firstOrNull()
    }
    val titleText = subscription?.name?.takeIf { it.isNotBlank() }
        ?: cardSelectedProfile?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.label_servers_card_title)

    val isAnyServerPinging = remember(displayProfiles, testingProfileIds) {
        displayProfiles.any { it.id in testingProfileIds }
    }

    val chevronRotationState = animateFloatAsState(
        targetValue = if (isListExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "chevron_rotation"
    )

    Surface(
        shape = SubscriptionCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .testTag("bottom_subscription_card_${subscription?.id ?: "local"}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusHighlight(cornerRadius = 14.dp, scaleOnFocus = 1.01f)
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickable(
                            onClick = onToggleList,
                            onLongClick = {
                                if (subscription != null || displayProfiles.isNotEmpty()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showCardMenu = true
                                }
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .then(if (marqueeEnabled) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier)
                            .testTag("bottom_card_title")
                    )

                    val actionBg = MaterialTheme.colorScheme.surfaceContainer

                    IconButton(
                        onClick = onRefresh,
                        enabled = !isSyncing,
                        modifier = Modifier
                            .size(48.dp)
                            .tvFocusHighlight(cornerRadius = 12.dp, scaleOnFocus = 1.05f)
                            .clip(ActionTileShape)
                            .background(actionBg)
                            .testTag("bottom_refresh_button")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleList,
                        modifier = Modifier
                            .size(48.dp)
                            .tvFocusHighlight(cornerRadius = 12.dp, scaleOnFocus = 1.05f)
                            .clip(ActionTileShape)
                            .background(actionBg)
                            .testTag("bottom_toggle_list_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.action_toggle_list),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.graphicsLayer { rotationZ = chevronRotationState.value }
                        )
                    }
                }

                if (subscription != null || displayProfiles.isNotEmpty()) {
                    DropdownMenu(
                        expanded = showCardMenu,
                        onDismissRequest = { showCardMenu = false },
                        shape = MenuCornerShape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        if (subscription != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.action_rename),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.action_rename)
                                    )
                                },
                                onClick = {
                                    showCardMenu = false
                                    onRename()
                                },
                                modifier = Modifier.testTag("sub_menu_rename")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.action_refresh),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.action_refresh)
                                    )
                                },
                                onClick = {
                                    showCardMenu = false
                                    onRefresh()
                                },
                                modifier = Modifier.testTag("sub_menu_refresh")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.action_share_subscription),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppIcons.Share,
                                        contentDescription = stringResource(R.string.action_share_subscription)
                                    )
                                },
                                onClick = {
                                    showCardMenu = false
                                    onShareSubscription()
                                },
                                modifier = Modifier.testTag("sub_menu_share_url")
                            )
                        }
                        if (displayProfiles.isNotEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.action_share_servers),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppIcons.Share,
                                        contentDescription = stringResource(R.string.action_share_servers)
                                    )
                                },
                                onClick = {
                                    showCardMenu = false
                                    onShareServers()
                                },
                                modifier = Modifier.testTag("sub_menu_share_servers_${subscription?.id ?: "local"}")
                            )
                        }
                        if (subscription != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.action_delete),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showCardMenu = false
                                    onDelete()
                                },
                                modifier = Modifier.testTag("sub_menu_delete")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SubscriptionTrafficUsageSection(
                subscription = subscription,
                sessionTrafficBytesFlow = sessionTrafficBytesFlow
            )

            AnimatedVisibility(
                visible = isListExpanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing),
                    expandFrom = Alignment.Top
                ) + fadeIn(tween(durationMillis = 200, easing = LinearOutSlowInEasing)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 240, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(tween(durationMillis = 180, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                    if (displayProfiles.isEmpty()) {
                        Text(
                            text = stringResource(R.string.empty_servers_short),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.label_servers_count, displayProfiles.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )

                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .tvFocusHighlight(cornerRadius = 12.dp, scaleOnFocus = 1.04f)
                                    .clip(ActionTileShape)
                                    .clickable {
                                        if (subscription != null) onShareSubscription() else onShareServers()
                                    }
                                    .padding(vertical = 6.dp)
                                    .testTag("sub_share_button_${subscription?.id ?: "local"}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = ActionTileShape,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Share,
                                            contentDescription = stringResource(R.string.action_share),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.action_share),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .tvFocusHighlight(cornerRadius = 12.dp, scaleOnFocus = 1.04f)
                                    .clip(ActionTileShape)
                                    .clickable(enabled = !isAnyServerPinging, onClick = onPingAll)
                                    .padding(vertical = 6.dp)
                                    .testTag("sub_ping_all_button_${subscription?.id ?: "local"}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = ActionTileShape,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (isAnyServerPinging) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(13.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = AppIcons.NetworkCheck,
                                                contentDescription = stringResource(R.string.action_ping_servers),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = stringResource(R.string.action_ping_servers),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        val innerScrollState = rememberScrollState()
                        val needsInnerScroll = displayProfiles.size > 5
                        val listContainerModifier = if (needsInnerScroll) {
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(innerScrollState, enabled = draggedProfileId == null)
                        } else {
                            Modifier.fillMaxWidth()
                        }

                        Column(
                            modifier = listContainerModifier,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            displayProfiles.forEachIndexed { index, profile ->
                                key(profile.id) {
                                    val isSelected = profile.id == selectedProfile?.id
                                    val isTestingPing = profile.id in testingProfileIds
                                    val isDragged = draggedProfileId == profile.id

                                    val selectedProgress by animateFloatAsState(
                                        targetValue = if (isSelected) 1f else 0f,
                                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                                        label = "server_selected_progress"
                                    )
                                    val dragScale by animateFloatAsState(
                                        targetValue = if (isDragged) 1.045f else 1f,
                                        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                                        label = "server_drag_scale"
                                    )
                                    val dragElevFactor by animateFloatAsState(
                                        targetValue = if (isDragged) 1f else 0f,
                                        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                                        label = "server_drag_elevation"
                                    )

                                    var previousIndex by remember { mutableIntStateOf(index) }
                                    val swapOffsetY = remember { Animatable(0f) }

                                    LaunchedEffect(index, isDragged) {
                                        if (isDragged) {
                                            previousIndex = index
                                        } else {
                                            val dropResidual = pendingDropOffsets.remove(profile.id)
                                            if (dropResidual != null && abs(dropResidual) > 0.5f) {
                                                swapOffsetY.snapTo(dropResidual)
                                                swapOffsetY.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
                                                )
                                            } else if (previousIndex != index) {
                                                val rowStepPx = (itemHeights[profile.id]?.toFloat() ?: defaultRowHeightPx) + spacingPx
                                                val delta = (previousIndex - index) * rowStepPx
                                                swapOffsetY.snapTo(swapOffsetY.value + delta)
                                                swapOffsetY.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                                )
                                            }
                                            previousIndex = index
                                        }
                                    }

                                    val selectedRowBg = MaterialTheme.colorScheme.primary
                                        .copy(alpha = 0.16f)
                                        .compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    val rowBgColor = lerp(
                                        MaterialTheme.colorScheme.surfaceContainer,
                                        selectedRowBg,
                                        selectedProgress
                                    )
                                    val rowTitleColor = lerp(
                                        MaterialTheme.colorScheme.onSurface,
                                        MaterialTheme.colorScheme.primary,
                                        selectedProgress
                                    )
                                    val rowInteractionSource = remember(profile.id) { MutableInteractionSource() }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .zIndex(if (isDragged) 10f else 0f)
                                            .onSizeChanged { itemHeights[profile.id] = it.height }
                                            .graphicsLayer {
                                                scaleX = dragScale
                                                scaleY = dragScale
                                                translationY = if (isDragged) dragDeltaY else swapOffsetY.value
                                                shadowElevation = dragShadowPx * dragElevFactor
                                                shape = ServerRowShape
                                                clip = true
                                            }
                                            .tvFocusHighlight(cornerRadius = 16.dp, scaleOnFocus = 1.015f)
                                            .then(
                                                if (isDragged) {
                                                    Modifier.border(
                                                        width = 1.5.dp,
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                        shape = ServerRowShape
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            .drawBehind { drawRect(rowBgColor) }
                                            .pointerInput(profile.id) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        draggedProfileId = profile.id
                                                        dragDeltaY = 0f
                                                        currentOnDraggingChange(true)
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        if (draggedProfileId != profile.id) {
                                                            return@detectDragGesturesAfterLongPress
                                                        }
                                                        change.consume()
                                                        dragDeltaY += dragAmount.y

                                                        val working = currentProfiles.toMutableList()
                                                        var curIndex = working.indexOfFirst { it.id == profile.id }
                                                        if (curIndex < 0) return@detectDragGesturesAfterLongPress

                                                        var swapped = false
                                                        while (curIndex < working.lastIndex) {
                                                            val nextItem = working[curIndex + 1]
                                                            val step = (itemHeights[nextItem.id]?.toFloat()
                                                                ?: defaultRowHeightPx) + spacingPx
                                                            if (dragDeltaY > step * 0.52f) {
                                                                val moving = working[curIndex]
                                                                working[curIndex] = nextItem
                                                                working[curIndex + 1] = moving
                                                                dragDeltaY -= step
                                                                curIndex++
                                                                swapped = true
                                                            } else {
                                                                break
                                                            }
                                                        }
                                                        while (curIndex > 0) {
                                                            val prevItem = working[curIndex - 1]
                                                            val step = (itemHeights[prevItem.id]?.toFloat()
                                                                ?: defaultRowHeightPx) + spacingPx
                                                            if (dragDeltaY < -step * 0.52f) {
                                                                val moving = working[curIndex]
                                                                working[curIndex] = prevItem
                                                                working[curIndex - 1] = moving
                                                                dragDeltaY += step
                                                                curIndex--
                                                                swapped = true
                                                            } else {
                                                                break
                                                            }
                                                        }

                                                        if (curIndex == 0 && dragDeltaY < -boundaryOvershootPx) {
                                                            dragDeltaY = -boundaryOvershootPx
                                                        } else if (curIndex == working.lastIndex && dragDeltaY > boundaryOvershootPx) {
                                                            dragDeltaY = boundaryOvershootPx
                                                        }

                                                        if (swapped) {
                                                            displayProfiles = working
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        if (draggedProfileId == profile.id) {
                                                            val finalOrder = currentProfiles.map { it.id }
                                                            val oldOrder = currentSourceProfiles.map { it.id }
                                                            pendingDropOffsets[profile.id] = dragDeltaY
                                                            draggedProfileId = null
                                                            dragDeltaY = 0f
                                                            currentOnDraggingChange(false)
                                                            if (finalOrder != oldOrder && finalOrder.size > 1) {
                                                                currentOnReorder(finalOrder)
                                                            }
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        if (draggedProfileId == profile.id) {
                                                            displayProfiles = currentSourceProfiles
                                                            draggedProfileId = null
                                                            dragDeltaY = 0f
                                                            currentOnDraggingChange(false)
                                                        }
                                                    }
                                                )
                                            }
                                            .clickable(
                                                interactionSource = rowInteractionSource,
                                                indication = if (isDragged) null else ripple(),
                                                enabled = draggedProfileId == null
                                            ) {
                                                onSelectProfile(profile.id)
                                            }
                                            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                                            .testTag("server_list_item_${profile.id}"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = profile.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = rowTitleColor,
                                                maxLines = 1,
                                                overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                                                modifier = if (marqueeEnabled) {
                                                    Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            val transportLabel = if (profile.protocol == VpnProtocol.HYSTERIA2) {
                                                TransportType.QUIC.value
                                            } else {
                                                profile.transport.value
                                            }
                                            Text(
                                                text = "${profile.protocol.scheme} $transportLabel",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        IconButton(
                                            onClick = { onShareProfile(profile) },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .tvFocusHighlight(cornerRadius = 12.dp, scaleOnFocus = 1.05f)
                                                .clip(ActionTileShape)
                                                .testTag("server_share_button_${profile.id}")
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.Share,
                                                contentDescription = stringResource(R.string.action_share_server),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        ServerPingPill(
                                            latencyMs = profile.latencyMs,
                                            isTesting = isTestingPing,
                                            onClick = { onPingProfile(profile) },
                                            testTagName = "server_ping_button_${profile.id}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionTrafficUsageSection(
    subscription: SubscriptionEntity?,
    sessionTrafficBytesFlow: StateFlow<Long>
) {
    // Only the local profile card (subscription == null) needs live 1s session byte updates;
    // subscription cards read their static quota values without subscribing to live traffic ticks.
    val usedBytes = if (subscription != null) {
        subscription.uploadBytes + subscription.downloadBytes
    } else {
        val liveSessionBytes by sessionTrafficBytesFlow.collectAsStateWithLifecycle()
        liveSessionBytes
    }
    val totalBytes = subscription?.totalBytes ?: 0L
    val hasQuota = totalBytes > 0L
    val progress = if (hasQuota) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val expireText = remember(subscription?.expireTimestampSec) {
        subscription?.expireTimestampSec?.let { formatExpireDate(it) }.orEmpty()
    }
    val animatedProgressState = animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "usage_progress"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { animatedProgressState.value },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(ProgressTrackShape)
                .testTag("bottom_usage_progress"),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainer,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {}
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (hasQuota) {
                    "${formatBytes(usedBytes)} / ${formatBytes(totalBytes)}"
                } else {
                    formatBytes(usedBytes)
                },
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (expireText.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.label_expire_until, expireText),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("sub_expire_date")
                    )
                }
                Text(
                    text = if (hasQuota) {
                        "${(progress * 100f).roundToInt()}%"
                    } else {
                        stringResource(R.string.label_usage_unlimited)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ServerPingPill(
    latencyMs: Long?,
    isTesting: Boolean,
    onClick: () -> Unit,
    testTagName: String
) {
    val accentColor = when {
        isTesting -> MaterialTheme.colorScheme.primary
        latencyMs != null -> latencyBadgeColor(latencyMs)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val pillBg = when {
        isTesting -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        latencyMs != null -> latencyBadgeColor(latencyMs).copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .tvFocusHighlight(cornerRadius = 12.dp, scaleOnFocus = 1.05f)
            .clip(ActionTileShape)
            .clickable(enabled = !isTesting, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag(testTagName),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = ActionTileShape,
            color = pillBg
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 2.dp,
                        color = accentColor
                    )
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor
                    )
                } else {
                    Icon(
                        imageVector = AppIcons.NetworkCheck,
                        contentDescription = stringResource(R.string.action_test_delay),
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    val label = when {
                        latencyMs == null -> stringResource(R.string.action_test_delay)
                        latencyMs < 0L -> "ERR"
                        else -> "${latencyMs} ms"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = accentColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionTimer(
    isConnected: Boolean,
    startedAtMillis: Long
) {
    val active = isConnected && startedAtMillis > 0L
    var elapsedMillis by remember { mutableLongStateOf(0L) }

    LaunchedEffect(active, startedAtMillis) {
        if (active) {
            while (isActive) {
                elapsedMillis = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)
                delay(500L)
            }
        } else {
            elapsedMillis = 0L
        }
    }

    AnimatedVisibility(
        visible = active,
        enter = fadeIn(tween(340, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(240, easing = FastOutSlowInEasing))
    ) {
        Text(
            text = formatDuration(elapsedMillis),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("connection_timer_text")
        )
    }
}

@Composable
private fun ManualLinkInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var link by remember { mutableStateOf("") }

    PredictiveBackAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = DialogCornerShape,
        title = {
            Text(
                text = stringResource(R.string.menu_manual_input),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.hint_manual_link_input),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    maxLines = 4,
                    shape = DialogInputShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_link_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(link) },
                enabled = link.isNotBlank(),
                modifier = Modifier.testTag("manual_link_confirm_button")
            ) {
                Text(stringResource(R.string.action_add))
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
private fun RenameSubscriptionDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(currentName) { mutableStateOf(currentName) }

    PredictiveBackAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = DialogCornerShape,
        title = {
            Text(
                text = stringResource(R.string.dialog_rename_sub_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.hint_sub_name),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                shape = DialogInputShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_subscription_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("rename_subscription_confirm")
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
private fun ConnectSquareButton(
    status: VpnStatus,
    buttonShape: ConnectButtonShape,
    buttonShapeParams: ButtonShapeParams,
    connectingSpeedDegPerSec: Int,
    connectedSpeedDegPerSec: Int,
    outerBoxSize: Dp,
    squareSize: Dp,
    cornerRadius: Dp,
    borderWidth: Dp,
    iconSize: Dp,
    spinnerSize: Dp,
    spinnerStrokeWidth: Dp,
    testTagName: String,
    elevated: Boolean = false,
    buttonModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isConnected = status == VpnStatus.CONNECTED
    val isConnecting = status == VpnStatus.CONNECTING || status == VpnStatus.DISCONNECTING

    // Store angle in State without `by` delegate so ONLY graphicsLayer reads it (zero Composition recompositions)
    val angleState = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(status, buttonShape, connectingSpeedDegPerSec, connectedSpeedDegPerSec) {
        if (buttonShape == ConnectButtonShape.CIRCLE) {
            angleState.floatValue = 0f
            return@LaunchedEffect
        }
        when (status) {
            VpnStatus.CONNECTING, VpnStatus.DISCONNECTING -> {
                val speed = connectingSpeedDegPerSec.coerceIn(0, 360).toFloat()
                if (speed > 0f) {
                    var lastNanos = 0L
                    while (isActive) {
                        val frameNanos = withFrameNanos { it }
                        if (lastNanos != 0L) {
                            val deltaSec = (frameNanos - lastNanos) / 1_000_000_000f
                            angleState.floatValue = (angleState.floatValue + speed * deltaSec) % 360f
                        }
                        lastNanos = frameNanos
                    }
                }
            }

            VpnStatus.CONNECTED -> {
                val speed = connectedSpeedDegPerSec.coerceIn(0, 360).toFloat()
                if (speed > 0f) {
                    var lastNanos = 0L
                    while (isActive) {
                        val frameNanos = withFrameNanos { it }
                        if (lastNanos != 0L) {
                            val deltaSec = (frameNanos - lastNanos) / 1_000_000_000f
                            angleState.floatValue = (angleState.floatValue + speed * deltaSec) % 360f
                        }
                        lastNanos = frameNanos
                    }
                }
            }

            VpnStatus.DISCONNECTED, VpnStatus.ERROR -> {
                val cur = angleState.floatValue
                if (cur != 0f) {
                    val symmetryStep = when (buttonShape) {
                        ConnectButtonShape.SQUARE,
                        ConnectButtonShape.ROUNDED_SQUARE,
                        ConnectButtonShape.SQUIRCLE,
                        ConnectButtonShape.COOKIE_4 -> 90f
                        ConnectButtonShape.COOKIE_6 -> 60f
                        ConnectButtonShape.COOKIE_9 -> 40f
                        ConnectButtonShape.PILL,
                        ConnectButtonShape.OVAL -> 180f
                        ConnectButtonShape.ARCH -> 360f
                        ConnectButtonShape.CIRCLE -> 90f
                    }
                    val target = (cur / symmetryStep).roundToInt() * symmetryStep
                    animate(
                        initialValue = cur,
                        targetValue = target,
                        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                    ) { value, _ ->
                        angleState.floatValue = value
                    }
                    angleState.floatValue = 0f
                }
            }
        }
    }

    var prevStatus by remember { mutableStateOf(status) }
    var activeTargetStatus by remember { mutableStateOf(status) }
    val statusTransition = remember { Animatable(1f) }
    LaunchedEffect(status) {
        if (activeTargetStatus != status) {
            prevStatus = activeTargetStatus
            activeTargetStatus = status
            statusTransition.snapTo(0f)
            statusTransition.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
            )
            prevStatus = status
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val transitionT = statusTransition.value
    val buttonColor = lerp(
        connectButtonBgFor(prevStatus, colorScheme),
        connectButtonBgFor(activeTargetStatus, colorScheme),
        transitionT
    )
    val iconTint = lerp(
        connectButtonIconTintFor(prevStatus, colorScheme),
        connectButtonIconTintFor(activeTargetStatus, colorScheme),
        transitionT
    )
    val borderColor = lerp(
        connectButtonBorderFor(prevStatus, colorScheme),
        connectButtonBorderFor(activeTargetStatus, colorScheme),
        transitionT
    )

    val resolvedShape = remember(buttonShape, buttonShapeParams) {
        buttonShape.toComposeShape(buttonShapeParams)
    }

    Box(
        modifier = Modifier.size(outerBoxSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = buttonModifier
                .size(squareSize)
                .tvFocusHighlight(cornerRadius = cornerRadius, scaleOnFocus = 1.05f, strokeWidth = 3.dp)
                .graphicsLayer {
                    rotationZ = angleState.floatValue
                    shape = resolvedShape
                    clip = true
                    if (elevated) {
                        shadowElevation = 10.dp.toPx()
                    }
                }
                .drawBehind {
                    drawRect(buttonColor)
                    val outline = resolvedShape.createOutline(size, layoutDirection, this)
                    drawOutline(
                        outline = outline,
                        color = borderColor,
                        style = Stroke(width = borderWidth.toPx() * 2f)
                    )
                }
                .clickable(onClick = onClick)
                .testTag(testTagName)
        )

        AnimatedContent(
            targetState = isConnecting,
            transitionSpec = {
                (fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.82f, animationSpec = tween(260, easing = FastOutSlowInEasing)))
                    .togetherWith(
                        fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                            scaleOut(targetScale = 0.82f, animationSpec = tween(180, easing = FastOutSlowInEasing))
                    )
            },
            label = "button_center_icon"
        ) { loading ->
            if (loading) {
                CircularProgressIndicator(
                    color = iconTint,
                    strokeWidth = spinnerStrokeWidth,
                    modifier = Modifier.size(spinnerSize)
                )
            } else {
                Icon(
                    imageVector = AppIcons.PowerSettingsNew,
                    contentDescription = if (isConnected) {
                        stringResource(R.string.action_disconnect)
                    } else {
                        stringResource(R.string.action_connect)
                    },
                    tint = iconTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

private fun connectButtonBgFor(status: VpnStatus, cs: ColorScheme): Color = when (status) {
    VpnStatus.DISCONNECTED -> cs.surfaceContainerHighest
    VpnStatus.CONNECTING, VpnStatus.DISCONNECTING -> cs.primary.copy(alpha = 0.78f)
    VpnStatus.CONNECTED -> cs.primary
    VpnStatus.ERROR -> cs.error
}

private fun connectButtonIconTintFor(status: VpnStatus, cs: ColorScheme): Color = when (status) {
    VpnStatus.DISCONNECTED -> cs.onSurface
    VpnStatus.CONNECTING, VpnStatus.DISCONNECTING, VpnStatus.CONNECTED -> cs.onPrimary
    VpnStatus.ERROR -> cs.onError
}

private fun connectButtonBorderFor(status: VpnStatus, cs: ColorScheme): Color = when (status) {
    VpnStatus.DISCONNECTED -> cs.outline.copy(alpha = 0.45f)
    VpnStatus.CONNECTING, VpnStatus.DISCONNECTING -> cs.primary.copy(alpha = 0.6f)
    VpnStatus.CONNECTED -> cs.primaryContainer
    VpnStatus.ERROR -> cs.errorContainer
}
