package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.ui.components.AppIcons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import com.example.ui.components.SyncedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.SplitTunnelMode
import com.example.ui.components.EditableRangeSliderValue
import com.example.ui.components.EditableSliderValue
import com.example.ui.components.SoftRangeSlider
import com.example.ui.components.SoftSlidingSelector
import com.example.ui.components.SoftValueSlider
import com.example.ui.components.tvFocusHighlight
import com.example.ui.viewmodel.SettingsUiState
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficSettingsScreen(
    uiState: SettingsUiState,
    onSaveFragment: (Boolean, String, String, String) -> Unit,
    onSaveMux: (Boolean, Int, Int) -> Unit,
    onBypassLanChange: (Boolean) -> Unit,
    onMtuChange: (Int) -> Unit,
    onPreferIpv6Change: (Boolean) -> Unit,
    onSaveAutoUpdate: (Boolean, Boolean, Boolean, Int) -> Unit,
    onOpenAppRouting: () -> Unit,
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
                        text = stringResource(R.string.settings_item_traffic_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .tvFocusHighlight(cornerRadius = 14.dp)
                            .testTag("traffic_settings_back_button")
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
        TrafficSettingsContent(
            uiState = uiState,
            onSaveFragment = onSaveFragment,
            onSaveMux = onSaveMux,
            onBypassLanChange = onBypassLanChange,
            onMtuChange = onMtuChange,
            onPreferIpv6Change = onPreferIpv6Change,
            onSaveAutoUpdate = onSaveAutoUpdate,
            onOpenAppRouting = onOpenAppRouting,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
fun TrafficSettingsContent(
    uiState: SettingsUiState,
    onSaveFragment: (Boolean, String, String, String) -> Unit,
    onSaveMux: (Boolean, Int, Int) -> Unit,
    onBypassLanChange: (Boolean) -> Unit,
    onMtuChange: (Int) -> Unit,
    onPreferIpv6Change: (Boolean) -> Unit,
    onSaveAutoUpdate: (Boolean, Boolean, Boolean, Int) -> Unit,
    onOpenAppRouting: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = uiState.settings

    // Fragment draft state
    var fragEnabled by remember(settings.enableFragment) { mutableStateOf(settings.enableFragment) }
    var fragPackets by remember(settings.fragmentPackets) { mutableStateOf(settings.fragmentPackets) }
    var fragLengthRange by remember(settings.fragmentLength) {
        mutableStateOf(parseDashRange(settings.fragmentLength, 10, 20, 5, 200))
    }
    var fragIntervalRange by remember(settings.fragmentInterval) {
        mutableStateOf(parseDashRange(settings.fragmentInterval, 10, 20, 1, 100))
    }

    // Mux draft state
    var muxEnabled by remember(settings.enableMux) { mutableStateOf(settings.enableMux) }
    var muxConcurrency by remember(settings.muxConcurrency) {
        mutableIntStateOf(settings.muxConcurrency.coerceIn(1, 64))
    }
    var muxXudp by remember(settings.muxXudpConcurrency) {
        mutableIntStateOf(settings.muxXudpConcurrency.coerceIn(0, 64))
    }

    // MTU draft state
    var mtuValue by remember(settings.mtu) { mutableIntStateOf(settings.mtu.coerceIn(1200, 9000)) }

    // Auto-update draft state
    var autoUpdateEnabled by remember(settings.autoUpdateSubscriptions) {
        mutableStateOf(settings.autoUpdateSubscriptions)
    }
    var autoUpdateOnLaunch by remember(settings.autoUpdateOnLaunch) {
        mutableStateOf(settings.autoUpdateOnLaunch)
    }
    var autoUpdateBySchedule by remember(settings.autoUpdateBySchedule) {
        mutableStateOf(settings.autoUpdateBySchedule)
    }
    var autoUpdateHours by remember(settings.autoUpdateIntervalHours) {
        mutableIntStateOf(settings.autoUpdateIntervalHours.coerceIn(1, 72))
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Fragmentation + Settings
            item(key = "card_fragment") {
                TrafficSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(R.string.label_enable_fragment),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.label_enable_fragment_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = fragEnabled,
                            onCheckedChange = {
                                fragEnabled = it
                                onSaveFragment(
                                    it,
                                    fragPackets,
                                    "${fragLengthRange.first}-${fragLengthRange.last}",
                                    "${fragIntervalRange.first}-${fragIntervalRange.last}"
                                )
                            },
                            modifier = Modifier
                                .tvFocusHighlight(cornerRadius = 18.dp)
                                .testTag("traffic_fragment_switch")
                        )
                    }

                    AnimatedVisibility(
                        visible = fragEnabled,
                        enter = expandVertically(tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                        exit = shrinkVertically(tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(150))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = stringResource(R.string.label_fragment_packets),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val packetModes = remember { listOf("tlshello", "1-3", "1-2") }
                            SoftSlidingSelector(
                                options = packetModes,
                                selectedOption = if (fragPackets in packetModes) fragPackets else packetModes.first(),
                                optionLabel = { it },
                                onSelect = { preset ->
                                    fragPackets = preset
                                    onSaveFragment(
                                        fragEnabled,
                                        preset,
                                        "${fragLengthRange.first}-${fragLengthRange.last}",
                                        "${fragIntervalRange.first}-${fragIntervalRange.last}"
                                    )
                                }
                            )

                            val lengthTicks = remember {
                                listOf(
                                    0f to "5",
                                    (25 - 5) / 195f to "25",
                                    (50 - 5) / 195f to "50",
                                    (100 - 5) / 195f to "100",
                                    (150 - 5) / 195f to "150",
                                    1f to "200"
                                )
                            }
                            val lengthMagnets = remember {
                                listOf(5, 10, 20, 25, 50, 100, 150, 200).map { (it - 5) / 195f }
                            }
                            SoftRangeSlider(
                                label = stringResource(R.string.label_fragment_length),
                                valueText = "${fragLengthRange.first}–${fragLengthRange.last}",
                                startFraction = (fragLengthRange.first - 5) / 195f,
                                endFraction = (fragLengthRange.last - 5) / 195f,
                                ticks = lengthTicks,
                                magnetFractions = lengthMagnets,
                                editableRange = EditableRangeSliderValue(
                                    start = fragLengthRange.first,
                                    end = fragLengthRange.last,
                                    min = 5,
                                    max = 200,
                                    onCommit = { s, e ->
                                        fragLengthRange = s..e
                                        onSaveFragment(
                                            fragEnabled,
                                            fragPackets,
                                            "$s-$e",
                                            "${fragIntervalRange.first}-${fragIntervalRange.last}"
                                        )
                                    }
                                ),
                                sliderTestTag = "traffic_fragment_length_slider",
                                onRangeChange = { startF, endF ->
                                    val s = (5 + (startF * 195f).roundToInt()).coerceIn(5, 200)
                                    val e = (5 + (endF * 195f).roundToInt()).coerceIn(s, 200)
                                    fragLengthRange = s..e
                                },
                                onRangeChangeFinished = {
                                    onSaveFragment(
                                        fragEnabled,
                                        fragPackets,
                                        "${fragLengthRange.first}-${fragLengthRange.last}",
                                        "${fragIntervalRange.first}-${fragIntervalRange.last}"
                                    )
                                }
                            )

                            val intervalTicks = remember {
                                listOf(
                                    0f to "1",
                                    (10 - 1) / 99f to "10",
                                    (20 - 1) / 99f to "20",
                                    (50 - 1) / 99f to "50",
                                    (75 - 1) / 99f to "75",
                                    1f to "100"
                                )
                            }
                            SoftRangeSlider(
                                label = stringResource(R.string.label_fragment_interval),
                                valueText = "${fragIntervalRange.first}–${fragIntervalRange.last}",
                                startFraction = (fragIntervalRange.first - 1) / 99f,
                                endFraction = (fragIntervalRange.last - 1) / 99f,
                                ticks = intervalTicks,
                                editableRange = EditableRangeSliderValue(
                                    start = fragIntervalRange.first,
                                    end = fragIntervalRange.last,
                                    min = 1,
                                    max = 100,
                                    onCommit = { s, e ->
                                        fragIntervalRange = s..e
                                        onSaveFragment(
                                            fragEnabled,
                                            fragPackets,
                                            "${fragLengthRange.first}-${fragLengthRange.last}",
                                            "$s-$e"
                                        )
                                    }
                                ),
                                sliderTestTag = "traffic_fragment_interval_slider",
                                onRangeChange = { startF, endF ->
                                    val s = (1 + (startF * 99f).roundToInt()).coerceIn(1, 100)
                                    val e = (1 + (endF * 99f).roundToInt()).coerceIn(s, 100)
                                    fragIntervalRange = s..e
                                },
                                onRangeChangeFinished = {
                                    onSaveFragment(
                                        fragEnabled,
                                        fragPackets,
                                        "${fragLengthRange.first}-${fragLengthRange.last}",
                                        "${fragIntervalRange.first}-${fragIntervalRange.last}"
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 2. Multiplexing (Mux) + Settings
            item(key = "card_mux") {
                TrafficSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(R.string.label_enable_mux),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.label_enable_mux_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = muxEnabled,
                            onCheckedChange = {
                                muxEnabled = it
                                onSaveMux(it, muxConcurrency, muxXudp)
                            },
                            modifier = Modifier
                                .tvFocusHighlight(cornerRadius = 18.dp)
                                .testTag("traffic_mux_switch")
                        )
                    }

                    AnimatedVisibility(
                        visible = muxEnabled,
                        enter = expandVertically(tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                        exit = shrinkVertically(tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(150))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SoftValueSlider(
                                label = stringResource(R.string.label_mux_concurrency),
                                valueText = "$muxConcurrency",
                                fraction = (muxConcurrency - 1) / 63f,
                                captions = remember {
                                    listOf(
                                        0f to "1",
                                        (8 - 1) / 63f to "8",
                                        (16 - 1) / 63f to "16",
                                        (32 - 1) / 63f to "32",
                                        1f to "64"
                                    )
                                },
                                editableValue = EditableSliderValue(
                                    value = muxConcurrency,
                                    min = 1,
                                    max = 64,
                                    onCommit = { v ->
                                        muxConcurrency = v
                                        onSaveMux(muxEnabled, v, muxXudp)
                                    }
                                ),
                                sliderTestTag = "traffic_mux_concurrency_slider",
                                onFractionChange = { f ->
                                    muxConcurrency = (1 + (f * 63f).roundToInt()).coerceIn(1, 64)
                                },
                                onFractionChangeFinished = {
                                    onSaveMux(muxEnabled, muxConcurrency, muxXudp)
                                }
                            )
                            SoftValueSlider(
                                label = stringResource(R.string.label_mux_xudp),
                                valueText = "$muxXudp",
                                fraction = muxXudp / 64f,
                                captions = remember {
                                    listOf(
                                        0f to "0",
                                        8 / 64f to "8",
                                        16 / 64f to "16",
                                        0.5f to "32",
                                        1f to "64"
                                    )
                                },
                                editableValue = EditableSliderValue(
                                    value = muxXudp,
                                    min = 0,
                                    max = 64,
                                    onCommit = { v ->
                                        muxXudp = v
                                        onSaveMux(muxEnabled, muxConcurrency, v)
                                    }
                                ),
                                sliderTestTag = "traffic_mux_xudp_slider",
                                onFractionChange = { f ->
                                    muxXudp = (f * 64f).roundToInt().coerceIn(0, 64)
                                },
                                onFractionChangeFinished = {
                                    onSaveMux(muxEnabled, muxConcurrency, muxXudp)
                                }
                            )
                        }
                    }
                }
            }

            // 3. LAN mode [Bypass / Do not bypass]
            item(key = "card_lan") {
                TrafficSectionCard {
                    Text(
                        text = stringResource(R.string.label_lan_mode),
                        style = MaterialTheme.typography.titleMedium
                    )
                    val bypassLabel = stringResource(R.string.label_lan_bypass)
                    val proxyLabel = stringResource(R.string.label_lan_proxy)
                    SoftSlidingSelector(
                        options = listOf(true, false),
                        selectedOption = settings.bypassLan,
                        optionLabel = { bypass -> if (bypass) bypassLabel else proxyLabel },
                        onSelect = onBypassLanChange,
                        optionTestTag = { bypass ->
                            if (bypass) "traffic_lan_bypass" else "traffic_lan_proxy"
                        }
                    )
                }
            }

            // 4. Network Packet Size (MTU) with magnetic detents, vertical tick marks, and keyboard input
            item(key = "card_mtu") {
                TrafficSectionCard {
                    SoftValueSlider(
                        label = stringResource(R.string.label_mtu_size),
                        valueText = "$mtuValue",
                        fraction = mtuToFraction(mtuValue),
                        captions = remember {
                            listOf(
                                mtuToFraction(1280) to "1280",
                                mtuToFraction(1400) to "1400",
                                mtuToFraction(1500) to "1500",
                                mtuToFraction(8500) to "8500"
                            )
                        },
                        magnetFractions = remember {
                            MTU_MAGNETS.map { mtuToFraction(it) }
                        },
                        magnetRadius = 0.025f,
                        editableValue = EditableSliderValue(
                            value = mtuValue,
                            min = 1200,
                            max = 9000,
                            onCommit = { v ->
                                mtuValue = v
                                onMtuChange(v)
                            }
                        ),
                        sliderTestTag = "traffic_mtu_slider",
                        onFractionChange = { f ->
                            mtuValue = fractionToMtu(f)
                        },
                        onFractionChangeFinished = {
                            onMtuChange(mtuValue)
                        }
                    )
                }
            }

            // 5. Prefer IPv6
            item(key = "card_ipv6") {
                TrafficSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(R.string.label_prefer_ipv6),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.label_prefer_ipv6_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.enableIpv6,
                            onCheckedChange = onPreferIpv6Change,
                            modifier = Modifier
                                .tvFocusHighlight(cornerRadius = 18.dp)
                                .testTag("traffic_ipv6_switch")
                        )
                    }
                }
            }

            // 6. Dedicated App Routing Entry Card (opens full AppRoutingScreen)
            item(key = "card_app_routing_link") {
                val routingSummary = when (settings.splitTunnelMode) {
                    SplitTunnelMode.DISABLED -> stringResource(R.string.label_app_routing_summary_off)
                    SplitTunnelMode.ONLY_SELECTED -> stringResource(
                        R.string.label_app_routing_summary_count,
                        stringResource(R.string.routing_mode_only_selected),
                        settings.splitTunnelPackages.size
                    )
                    SplitTunnelMode.BYPASS_SELECTED -> stringResource(
                        R.string.label_app_routing_summary_count,
                        stringResource(R.string.routing_mode_ignore_selected),
                        settings.splitTunnelPackages.size
                    )
                }
                val cardShape = remember { RoundedCornerShape(24.dp) }
                Surface(
                    shape = cardShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusHighlight(cornerRadius = 24.dp, scaleOnFocus = 1.015f)
                        .clip(cardShape)
                        .clickable(onClick = onOpenAppRouting)
                        .testTag("traffic_open_app_routing")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = AppIcons.Apps,
                                    contentDescription = stringResource(R.string.label_app_routing),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.label_app_routing),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = routingSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (settings.splitTunnelMode == SplitTunnelMode.DISABLED) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
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

            // 7. Subscription Auto-Update (Master toggle + On launch + By schedule + Interval slider)
            item(key = "card_auto_update") {
                TrafficSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(R.string.label_auto_update_sub),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.label_auto_update_sub_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoUpdateEnabled,
                            onCheckedChange = { enabled ->
                                autoUpdateEnabled = enabled
                                if (enabled && !autoUpdateOnLaunch && !autoUpdateBySchedule) {
                                    autoUpdateBySchedule = true
                                }
                                onSaveAutoUpdate(
                                    enabled,
                                    autoUpdateOnLaunch,
                                    autoUpdateBySchedule,
                                    autoUpdateHours
                                )
                            },
                            modifier = Modifier
                                .tvFocusHighlight(cornerRadius = 18.dp)
                                .testTag("traffic_auto_update_switch")
                        )
                    }

                    AnimatedVisibility(
                        visible = autoUpdateEnabled,
                        enter = expandVertically(tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                        exit = shrinkVertically(tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(150))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = stringResource(R.string.label_auto_update_on_launch),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = stringResource(R.string.label_auto_update_on_launch_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = autoUpdateOnLaunch,
                                    onCheckedChange = { onLaunch ->
                                        autoUpdateOnLaunch = onLaunch
                                        onSaveAutoUpdate(
                                            autoUpdateEnabled,
                                            onLaunch,
                                            autoUpdateBySchedule,
                                            autoUpdateHours
                                        )
                                    },
                                    modifier = Modifier
                                        .tvFocusHighlight(cornerRadius = 18.dp)
                                        .testTag("traffic_auto_update_launch_switch")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = stringResource(R.string.label_auto_update_by_schedule),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = stringResource(R.string.label_auto_update_by_schedule_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = autoUpdateBySchedule,
                                    onCheckedChange = { bySchedule ->
                                        autoUpdateBySchedule = bySchedule
                                        onSaveAutoUpdate(
                                            autoUpdateEnabled,
                                            autoUpdateOnLaunch,
                                            bySchedule,
                                            autoUpdateHours
                                        )
                                    },
                                    modifier = Modifier
                                        .tvFocusHighlight(cornerRadius = 18.dp)
                                        .testTag("traffic_auto_update_schedule_switch")
                                )
                            }

                            if (!autoUpdateOnLaunch && !autoUpdateBySchedule) {
                                Text(
                                    text = stringResource(R.string.label_auto_update_choose_mode),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }

                            AnimatedVisibility(
                                visible = autoUpdateBySchedule,
                                enter = expandVertically(tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                                exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
                            ) {
                                val h1 = stringResource(R.string.label_hours_short, 1)
                                val h12 = stringResource(R.string.label_hours_short, 12)
                                val h24 = stringResource(R.string.label_hours_short, 24)
                                val h48 = stringResource(R.string.label_hours_short, 48)
                                val h72 = stringResource(R.string.label_hours_short, 72)
                                SoftValueSlider(
                                    label = stringResource(R.string.label_auto_update_interval),
                                    valueText = stringResource(R.string.label_hours_short, autoUpdateHours),
                                    fraction = (autoUpdateHours - 1) / 71f,
                                    captions = listOf(
                                        0f to h1,
                                        (12 - 1) / 71f to h12,
                                        (24 - 1) / 71f to h24,
                                        (48 - 1) / 71f to h48,
                                        1f to h72
                                    ),
                                    magnetFractions = remember {
                                        listOf(1, 6, 12, 24, 48, 72).map { (it - 1) / 71f }
                                    },
                                    editableValue = EditableSliderValue(
                                        value = autoUpdateHours,
                                        min = 1,
                                        max = 72,
                                        onCommit = { v ->
                                            autoUpdateHours = v
                                            onSaveAutoUpdate(
                                                autoUpdateEnabled,
                                                autoUpdateOnLaunch,
                                                autoUpdateBySchedule,
                                                v
                                            )
                                        }
                                    ),
                                    sliderTestTag = "traffic_auto_update_slider",
                                    onFractionChange = { f ->
                                        autoUpdateHours = (1 + (f * 71f).roundToInt()).coerceIn(1, 72)
                                    },
                                    onFractionChangeFinished = {
                                        onSaveAutoUpdate(
                                            autoUpdateEnabled,
                                            autoUpdateOnLaunch,
                                            autoUpdateBySchedule,
                                            autoUpdateHours
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseDashRange(
    raw: String,
    defaultStart: Int,
    defaultEnd: Int,
    minVal: Int,
    maxVal: Int
): IntRange {
    val parts = raw.split("-", "–").mapNotNull { it.trim().toIntOrNull() }
    return when (parts.size) {
        0 -> defaultStart..defaultEnd
        1 -> {
            val v = parts[0].coerceIn(minVal, maxVal)
            v..v
        }
        else -> {
            val a = parts[0].coerceIn(minVal, maxVal)
            val b = parts[1].coerceIn(a, maxVal)
            a..b
        }
    }
}

private val MTU_MAGNETS = setOf(1280, 1400, 1500, 8500)

private fun mtuToFraction(mtu: Int): Float {
    val clamped = mtu.coerceIn(1200, 9000)
    return if (clamped <= 1600) {
        ((clamped - 1200) / 400f) * 0.6f
    } else {
        0.6f + ((clamped - 1600) / 7400f) * 0.4f
    }
}

private fun fractionToMtu(fraction: Float): Int {
    val f = fraction.coerceIn(0f, 1f)
    for (anchor in MTU_MAGNETS) {
        if (abs(f - mtuToFraction(anchor)) <= 0.008f) {
            return anchor
        }
    }
    return if (f <= 0.6f) {
        val t = f / 0.6f
        val raw = 1200 + (t * 400f).roundToInt()
        ((raw + 5) / 10 * 10).coerceIn(1200, 1600)
    } else {
        val t = (f - 0.6f) / 0.4f
        val raw = 1600 + (t * 7400f).roundToInt()
        (1600 + ((raw - 1600 + 50) / 100) * 100).coerceIn(1600, 9000)
    }
}

@Composable
private fun TrafficSectionCard(
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
