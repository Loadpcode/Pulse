package com.xraypulse.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xraypulse.app.data.parser.ShareUriParser
import com.xraypulse.app.model.AppThemeMode
import com.xraypulse.app.model.VpnStatus
import com.xraypulse.app.ui.components.LocalAdaptiveLayout
import com.xraypulse.app.ui.components.ShareUtils
import com.xraypulse.app.ui.components.rememberAdaptiveLayoutSpec
import com.xraypulse.app.ui.components.rememberLocalizedContext
import com.xraypulse.app.ui.navigation.AppRoutingRoute
import com.xraypulse.app.ui.navigation.AppSettingsRoute
import com.xraypulse.app.ui.navigation.ConfigPreviewRoute
import com.xraypulse.app.ui.navigation.ConnectRoute
import com.xraypulse.app.ui.navigation.LogsRoute
import com.xraypulse.app.ui.navigation.QrScannerRoute
import com.xraypulse.app.ui.navigation.SettingsRoute
import com.xraypulse.app.ui.navigation.TrafficSettingsRoute
import com.xraypulse.app.ui.navigation.VisualSettingsRoute
import com.xraypulse.app.ui.screens.AppRoutingScreen
import com.xraypulse.app.ui.screens.AppSettingsScreen
import com.xraypulse.app.ui.screens.ConfigPreviewScreen
import com.xraypulse.app.ui.screens.ConnectScreen
import com.xraypulse.app.ui.screens.LogsScreen
import com.xraypulse.app.ui.screens.QrScannerScreen
import com.xraypulse.app.ui.screens.SettingsScreen
import com.xraypulse.app.ui.screens.TrafficSettingsScreen
import com.xraypulse.app.ui.screens.VisualSettingsScreen
import com.xraypulse.app.ui.theme.Gray900
import com.xraypulse.app.ui.theme.LightBg
import com.xraypulse.app.ui.theme.XRayPulseTheme
import com.xraypulse.app.ui.viewmodel.AppViewModelFactory
import com.xraypulse.app.ui.viewmodel.ConnectViewModel
import com.xraypulse.app.ui.viewmodel.SettingsUiState
import com.xraypulse.app.ui.viewmodel.SettingsViewModel
import com.xraypulse.app.vpn.VpnSessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private class ErrorSnackbarVisuals(
    override val message: String,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = true,
    override val duration: SnackbarDuration = SnackbarDuration.Long
) : SnackbarVisuals

class MainActivity : ComponentActivity() {
    private val tileConnectProfileIdState = mutableLongStateOf(-1L)
    private val incomingShareSequence = mutableIntStateOf(0)
    private var incomingSharePayloadState by mutableStateOf<String?>(null)
    private val foregroundReturnSequence = mutableIntStateOf(0)
    private var wentToBackground = false
    private var suppressNextBackgroundSync = false

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        extractTileConnectProfile(intent)
        extractIncomingShareIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val container = (application as XrayPulseApp).container
        val factory = AppViewModelFactory(container)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
            val adaptiveLayout = rememberAdaptiveLayoutSpec(windowSizeClass, this@MainActivity)

            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
            val settingsUi by settingsViewModel.uiState.collectAsStateWithLifecycle()

            // Observe ONLY error-theme boolean at root so 1s traffic counter updates do not recompose the theme
            val isErrorTheme by remember {
                VpnSessionManager.state
                    .map { it.status == VpnStatus.ERROR }
                    .distinctUntilChanged()
            }.collectAsStateWithLifecycle(
                initialValue = VpnSessionManager.state.value.status == VpnStatus.ERROR
            )

            val useDark = when (settingsUi.settings.themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val customBg = settingsUi.settings.customBackgroundColorArgb
            val barColor = customBg?.let { Color(it) } ?: if (useDark) Gray900 else LightBg
            val barIsDark = barColor.luminance() < 0.42f

            LaunchedEffect(barIsDark) {
                if (barIsDark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    )
                }
            }

            val baseContext = LocalContext.current
            val localizedContext = rememberLocalizedContext(baseContext, settingsUi.settings.appLanguage)

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
                LocalActivityResultRegistryOwner provides this@MainActivity,
                LocalOnBackPressedDispatcherOwner provides this@MainActivity,
                LocalAdaptiveLayout provides adaptiveLayout
            ) {
                XRayPulseTheme(
                    themeMode = settingsUi.settings.themeMode,
                    dynamicColor = settingsUi.settings.dynamicColorEnabled,
                    customAccentColorArgb = settingsUi.settings.customAccentColorArgb,
                    customBackgroundColorArgb = settingsUi.settings.customBackgroundColorArgb,
                    isErrorState = isErrorTheme
                ) {
                    XRayPulseSingleRoot(
                        factory = factory,
                        settingsViewModel = settingsViewModel,
                        settingsUi = settingsUi,
                        tileConnectProfileId = tileConnectProfileIdState.longValue,
                        onConsumeTileConnectRequest = { tileConnectProfileIdState.longValue = -1L },
                        incomingShareSeq = incomingShareSequence.intValue,
                        incomingShareText = incomingSharePayloadState,
                        onConsumeIncomingShareRequest = { incomingSharePayloadState = null },
                        foregroundReturnTick = foregroundReturnSequence.intValue,
                        onWillLaunchExternalPrompt = { suppressNextBackgroundSync = true }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractTileConnectProfile(intent)
        extractIncomingShareIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        VpnSessionManager.syncFromRunningService(this)
        suppressNextBackgroundSync = false
        if (wentToBackground) {
            wentToBackground = false
            foregroundReturnSequence.intValue += 1
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            if (suppressNextBackgroundSync) {
                suppressNextBackgroundSync = false
            } else {
                wentToBackground = true
            }
        }
    }

    private fun extractTileConnectProfile(intent: Intent?) {
        val profileId = intent?.getLongExtra(EXTRA_TILE_CONNECT_PROFILE_ID, -1L) ?: -1L
        if (profileId > 0L) {
            tileConnectProfileIdState.longValue = profileId
            intent?.removeExtra(EXTRA_TILE_CONNECT_PROFILE_ID)
        }
    }

    private fun extractIncomingShareIntent(intent: Intent?) {
        val payload = ShareUtils.extractIncomingShareText(intent) ?: return
        incomingSharePayloadState = payload
        incomingShareSequence.intValue += 1
        ShareUtils.clearIncomingSharePayload(intent)
    }

    companion object {
        const val EXTRA_TILE_CONNECT_PROFILE_ID = "extra_tile_connect_profile_id"
    }
}

@Composable
private fun XRayPulseSingleRoot(
    factory: AppViewModelFactory,
    settingsViewModel: SettingsViewModel,
    settingsUi: SettingsUiState,
    tileConnectProfileId: Long,
    onConsumeTileConnectRequest: () -> Unit,
    incomingShareSeq: Int,
    incomingShareText: String?,
    onConsumeIncomingShareRequest: () -> Unit,
    foregroundReturnTick: Int,
    onWillLaunchExternalPrompt: () -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val connectViewModel: ConnectViewModel = viewModel(factory = factory)
    val connectUi by connectViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(foregroundReturnTick) {
        if (foregroundReturnTick > 0) {
            connectViewModel.onAppEnteredForeground()
        }
    }

    var pendingConnectProfileId by remember { mutableLongStateOf(-1L) }
    var activeErrorSnackbarJob by remember { mutableStateOf<Job?>(null) }

    suspend fun displayTimedErrorSnackbar(message: String, allowLogsAction: Boolean) {
        snackbarHostState.currentSnackbarData?.dismiss()
        kotlinx.coroutines.coroutineScope {
            val dismissTimer = launch {
                delay(3500L)
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            val result = snackbarHostState.showSnackbar(
                ErrorSnackbarVisuals(
                    message = message,
                    actionLabel = if (allowLogsAction) context.getString(R.string.action_logs) else null,
                    withDismissAction = true,
                    duration = SnackbarDuration.Indefinite
                )
            )
            dismissTimer.cancel()
            if (result == SnackbarResult.ActionPerformed) {
                navController.navigate(LogsRoute) {
                    launchSingleTop = true
                }
            }
        }
    }

    fun showErrorNotification(message: String, allowLogsAction: Boolean = true) {
        activeErrorSnackbarJob?.cancel()
        activeErrorSnackbarJob = scope.launch {
            displayTimedErrorSnackbar(message, allowLogsAction)
        }
    }

    LaunchedEffect(connectUi.session.status, connectUi.session.errorNonce) {
        if (connectUi.session.status == VpnStatus.ERROR) {
            val msg = connectUi.session.errorMessage?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.status_error)
            displayTimedErrorSnackbar(message = msg, allowLogsAction = true)
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingConnectProfileId > 0L) {
            connectViewModel.startVpn(context, pendingConnectProfileId)
        } else if (result.resultCode != Activity.RESULT_OK) {
            val msg = context.getString(R.string.error_vpn_permission_denied)
            VpnSessionManager.setError(msg)
        }
        pendingConnectProfileId = -1L
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val id = pendingConnectProfileId
        if (id > 0L) {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                onWillLaunchExternalPrompt()
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                connectViewModel.startVpn(context, id)
                pendingConnectProfileId = -1L
            }
        }
    }

    fun startOrSwitchVpnToProfile(profileId: Long) {
        if (connectUi.session.status == VpnStatus.ERROR) {
            VpnSessionManager.clearError()
        }
        pendingConnectProfileId = profileId
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onWillLaunchExternalPrompt()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            onWillLaunchExternalPrompt()
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            connectViewModel.startVpn(context, profileId)
            pendingConnectProfileId = -1L
        }
    }

    LaunchedEffect(tileConnectProfileId) {
        if (tileConnectProfileId > 0L) {
            val targetId = tileConnectProfileId
            onConsumeTileConnectRequest()
            val status = connectUi.session.status
            if (status != VpnStatus.CONNECTED && status != VpnStatus.CONNECTING) {
                startOrSwitchVpnToProfile(targetId)
            }
        }
    }

    fun launchVpnConnection() {
        val status = connectUi.session.status
        if (status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING) {
            connectViewModel.stopVpn(context)
            return
        }
        val profile = connectUi.selectedProfile
        if (profile == null) {
            VpnSessionManager.setError(context.getString(R.string.error_no_profile_selected))
            return
        }
        startOrSwitchVpnToProfile(profile.id)
    }

    fun importRawLink(raw: String) {
        val clean = raw.trim()
        if (clean.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_clipboard_empty)) }
            return
        }
        scope.launch {
            val result = connectViewModel.importFromText(clean)
            result.fold(
                onSuccess = { count ->
                    if (count > 0) {
                        snackbarHostState.showSnackbar(context.getString(R.string.msg_imported_count, count))
                    } else {
                        showErrorNotification(
                            context.getString(R.string.msg_import_failed),
                            allowLogsAction = false
                        )
                    }
                },
                onFailure = { err ->
                    showErrorNotification(
                        context.getString(R.string.msg_sub_error, err.message ?: "error"),
                        allowLogsAction = false
                    )
                }
            )
        }
    }

    fun shareText(payload: String, subject: String? = null, preferTail: Boolean = false) {
        ShareUtils.launchShareText(
            context = context,
            text = payload,
            chooserTitle = context.getString(R.string.share_chooser_title),
            subject = subject,
            preferTail = preferTail,
            onWillLaunchExternalPrompt = onWillLaunchExternalPrompt
        )
    }

    LaunchedEffect(incomingShareSeq) {
        val payload = incomingShareText
        if (incomingShareSeq > 0 && !payload.isNullOrBlank()) {
            onConsumeIncomingShareRequest()
            navController.popBackStack(ConnectRoute, inclusive = false)
            importRawLink(payload)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                val isError = data.visuals is ErrorSnackbarVisuals
                if (isError) {
                    Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        actionColor = MaterialTheme.colorScheme.primary,
                        dismissActionContentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.testTag("error_bottom_snackbar")
                    )
                } else {
                    Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ConnectRoute,
            enterTransition = {
                fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                        initialOffsetX = { it / 6 }
                    )
            },
            exitTransition = {
                fadeOut(tween(220, easing = FastOutSlowInEasing)) +
                    scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    )
            },
            popEnterTransition = {
                fadeIn(tween(260, easing = LinearEasing)) +
                    scaleIn(
                        initialScale = 0.93f,
                        transformOrigin = TransformOrigin.Center,
                        animationSpec = tween(260, easing = LinearEasing)
                    )
            },
            popExitTransition = {
                fadeOut(tween(240, easing = LinearEasing)) +
                    slideOutHorizontally(
                        animationSpec = tween(240, easing = LinearEasing),
                        targetOffsetX = { it / 6 }
                    ) +
                    scaleOut(
                        targetScale = 0.90f,
                        transformOrigin = TransformOrigin.Center,
                        animationSpec = tween(240, easing = LinearEasing)
                    )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable<ConnectRoute> {
                ConnectScreen(
                    uiState = connectUi,
                    sessionTrafficBytesFlow = connectViewModel.sessionTrafficBytesFlow,
                    onToggleConnection = ::launchVpnConnection,
                    onSelectProfile = { profileId ->
                        connectViewModel.selectProfile(profileId)
                        val status = connectUi.session.status
                        if ((status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING) &&
                            connectUi.session.activeProfileId != profileId
                        ) {
                            startOrSwitchVpnToProfile(profileId)
                        }
                    },
                    onReorderProfiles = connectViewModel::reorderProfiles,
                    onRefreshSubscription = { subscriptionId ->
                        connectViewModel.refreshSubscription(subscriptionId) { result ->
                            result.onSuccess { count ->
                                if (count > 0) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.msg_sub_added, count)
                                        )
                                    }
                                }
                            }.onFailure { err ->
                                showErrorNotification(
                                    context.getString(R.string.msg_sub_error, err.message ?: "error"),
                                    allowLogsAction = false
                                )
                            }
                        }
                    },
                    onRenameSubscription = connectViewModel::renameSubscription,
                    onDeleteSubscription = connectViewModel::deleteSubscription,
                    onPingProfile = connectViewModel::testProfilePing,
                    onPingSubscription = connectViewModel::testSubscriptionPing,
                    onImportClipboard = {
                        val text = clipboard.getText()?.text?.trim().orEmpty()
                        importRawLink(text)
                    },
                    onImportManualLink = ::importRawLink,
                    onScanQr = { navController.navigate(QrScannerRoute) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onShareProfile = { profile ->
                        shareText(
                            payload = ShareUriParser.toShareUri(profile),
                            subject = profile.name
                        )
                    },
                    onShareSubscription = { sub ->
                        shareText(
                            payload = sub.url,
                            subject = sub.name
                        )
                    },
                    onShareServers = { profiles ->
                        val bundle = profiles.joinToString("\n") { ShareUriParser.toShareUri(it) }
                        if (bundle.isNotBlank()) {
                            shareText(
                                payload = bundle,
                                subject = context.getString(R.string.app_name)
                            )
                        }
                    }
                )
            }

            composable<QrScannerRoute> {
                QrScannerScreen(
                    onBack = { navController.popBackStack() },
                    onQrScanned = { raw ->
                        importRawLink(raw)
                        navController.popBackStack()
                    }
                )
            }

            composable<SettingsRoute> {
                val logs by settingsViewModel.logsFlow.collectAsStateWithLifecycle()
                SettingsScreen(
                    uiState = settingsUi,
                    logs = logs,
                    onOpenAppSettings = { navController.navigate(AppSettingsRoute) },
                    onOpenTrafficSettings = { navController.navigate(TrafficSettingsRoute) },
                    onOpenLogs = { navController.navigate(LogsRoute) },
                    onOpenConfigPreview = { navController.navigate(ConfigPreviewRoute) },
                    onBack = { navController.popBackStack() },
                    onThemeModeChange = settingsViewModel::setThemeMode,
                    onDynamicColorChange = settingsViewModel::setDynamicColor,
                    onLanguageChange = settingsViewModel::setLanguage,
                    onMarqueeChange = settingsViewModel::setMarqueeEnabled,
                    onCustomAccentColorChange = settingsViewModel::setCustomAccentColor,
                    onCustomBackgroundColorChange = settingsViewModel::setCustomBackgroundColor,
                    onConnectingSpeedChange = settingsViewModel::setConnectingRotationSpeed,
                    onConnectedSpeedChange = settingsViewModel::setConnectedRotationSpeed,
                    onButtonShapeChange = settingsViewModel::setConnectButtonShape,
                    onButtonShapeParamsChange = settingsViewModel::setButtonShapeParams,
                    onSaveFragment = settingsViewModel::setFragmentSettings,
                    onSaveMux = settingsViewModel::setMuxSettings,
                    onBypassLanChange = settingsViewModel::setBypassLan,
                    onMtuChange = settingsViewModel::setMtu,
                    onPreferIpv6Change = settingsViewModel::setEnableIpv6,
                    onSaveAutoUpdate = settingsViewModel::setAutoUpdateSettings,
                    onEnsureAppsLoaded = settingsViewModel::ensureAppsLoaded,
                    onSplitModeChange = settingsViewModel::setSplitTunnelMode,
                    onToggleAppPackage = settingsViewModel::toggleSplitPackage,
                    onClearLogs = settingsViewModel::clearLogs,
                    onRefreshConfigPreview = settingsViewModel::refreshConfigPreview,
                    onCopied = {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.msg_copied))
                        }
                    },
                    onShareLogs = {
                        shareText(
                            payload = logs.joinToString("\n"),
                            subject = context.getString(R.string.label_xray_logs),
                            preferTail = true
                        )
                    },
                    onShareConfig = {
                        shareText(
                            payload = settingsUi.previewConfigJson,
                            subject = context.getString(R.string.label_config_preview)
                        )
                    }
                )
            }

            composable<VisualSettingsRoute> {
                VisualSettingsScreen(
                    uiState = settingsUi,
                    onDynamicColorChange = settingsViewModel::setDynamicColor,
                    onCustomAccentColorChange = settingsViewModel::setCustomAccentColor,
                    onCustomBackgroundColorChange = settingsViewModel::setCustomBackgroundColor,
                    onConnectingSpeedChange = settingsViewModel::setConnectingRotationSpeed,
                    onConnectedSpeedChange = settingsViewModel::setConnectedRotationSpeed,
                    onButtonShapeChange = settingsViewModel::setConnectButtonShape,
                    onButtonShapeParamsChange = settingsViewModel::setButtonShapeParams,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<AppSettingsRoute> {
                AppSettingsScreen(
                    uiState = settingsUi,
                    onThemeModeChange = settingsViewModel::setThemeMode,
                    onDynamicColorChange = settingsViewModel::setDynamicColor,
                    onLanguageChange = settingsViewModel::setLanguage,
                    onMarqueeChange = settingsViewModel::setMarqueeEnabled,
                    onOpenVisualSettings = { navController.navigate(VisualSettingsRoute) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<TrafficSettingsRoute> {
                TrafficSettingsScreen(
                    uiState = settingsUi,
                    onSaveFragment = settingsViewModel::setFragmentSettings,
                    onSaveMux = settingsViewModel::setMuxSettings,
                    onBypassLanChange = settingsViewModel::setBypassLan,
                    onMtuChange = settingsViewModel::setMtu,
                    onPreferIpv6Change = settingsViewModel::setEnableIpv6,
                    onSaveAutoUpdate = settingsViewModel::setAutoUpdateSettings,
                    onOpenAppRouting = { navController.navigate(AppRoutingRoute) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<AppRoutingRoute> {
                AppRoutingScreen(
                    uiState = settingsUi,
                    onEnsureAppsLoaded = settingsViewModel::ensureAppsLoaded,
                    onSplitModeChange = settingsViewModel::setSplitTunnelMode,
                    onToggleAppPackage = settingsViewModel::toggleSplitPackage,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<LogsRoute> {
                val logs by settingsViewModel.logsFlow.collectAsStateWithLifecycle()
                LogsScreen(
                    logs = logs,
                    onClearLogs = settingsViewModel::clearLogs,
                    onCopied = {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.msg_copied))
                        }
                    },
                    onShareLogs = {
                        shareText(
                            payload = logs.joinToString("\n"),
                            subject = context.getString(R.string.label_xray_logs),
                            preferTail = true
                        )
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<ConfigPreviewRoute> {
                ConfigPreviewScreen(
                    configJson = settingsUi.previewConfigJson,
                    onRefresh = settingsViewModel::refreshConfigPreview,
                    onCopied = {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.msg_copied))
                        }
                    },
                    onShareConfig = {
                        shareText(
                            payload = settingsUi.previewConfigJson,
                            subject = context.getString(R.string.label_config_preview)
                        )
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
