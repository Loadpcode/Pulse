package com.xraypulse.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xraypulse.app.AppContainer
import com.xraypulse.app.data.local.ServerProfileEntity
import com.xraypulse.app.data.local.SubscriptionEntity
import com.xraypulse.app.data.xray.XrayConfigBuilder
import com.xraypulse.app.model.AppLanguage
import com.xraypulse.app.model.AppSettings
import com.xraypulse.app.model.AppThemeMode
import com.xraypulse.app.model.ButtonShapeParams
import com.xraypulse.app.model.ConnectButtonShape
import com.xraypulse.app.model.InstalledAppInfo
import com.xraypulse.app.model.SplitTunnelMode
import com.xraypulse.app.model.VpnSessionState
import android.os.SystemClock
import com.xraypulse.app.vpn.VpnSessionManager
import com.xraypulse.app.vpn.XrayVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConnectUiState(
    val session: VpnSessionState = VpnSessionState(),
    val settings: AppSettings = AppSettings(),
    val profiles: List<ServerProfileEntity> = emptyList(),
    val subscriptions: List<SubscriptionEntity> = emptyList(),
    val selectedProfile: ServerProfileEntity? = null,
    val testingProfileIds: Set<Long> = emptySet(),
    val isSyncing: Boolean = false,
    val syncingSubscriptionIds: Set<Long> = emptySet()
)

class ConnectViewModel(private val container: AppContainer) : ViewModel() {

    private val testingProfileIds = MutableStateFlow<Set<Long>>(emptySet())
    private val syncing = MutableStateFlow(false)
    private val syncingSubIds = MutableStateFlow<Set<Long>>(emptySet())

    // Ignore 1s traffic/log churn in ConnectUiState so only connection status, errors, or metadata recompose ConnectScreen
    private val structuralSessionFlow = VpnSessionManager.state
        .map { state ->
            state.copy(
                rxBytesPerSec = 0L,
                txBytesPerSec = 0L,
                totalRxBytes = 0L,
                totalTxBytes = 0L,
                logs = emptyList(),
                lastGeneratedConfig = ""
            )
        }
        .distinctUntilChanged()

    val sessionTrafficBytesFlow: StateFlow<Long> = VpnSessionManager.state
        .map { it.totalRxBytes + it.totalTxBytes }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VpnSessionManager.state.value.let { it.totalRxBytes + it.totalTxBytes }
        )

    private data class CombineRight(
        val subscriptions: List<SubscriptionEntity>,
        val testingProfileIds: Set<Long>,
        val isSyncing: Boolean,
        val syncingSubIds: Set<Long>
    )

    val uiState: StateFlow<ConnectUiState> = combine(
        combine(
            structuralSessionFlow,
            container.settingsRepository.settingsFlow,
            container.profileRepository.profilesFlow
        ) { session, settings, profiles ->
            Triple(session, settings, profiles)
        },
        combine(
            container.profileRepository.subscriptionsFlow,
            testingProfileIds,
            syncing,
            syncingSubIds
        ) { subscriptions, pIds, isSyncing, subIds ->
            CombineRight(subscriptions, pIds, isSyncing, subIds)
        }
    ) { left, right ->
        val (session, settings, profiles) = left
        val selected = profiles.find { it.id == settings.selectedProfileId } ?: profiles.firstOrNull()
        ConnectUiState(
            session = session,
            settings = settings,
            profiles = profiles,
            subscriptions = right.subscriptions,
            selectedProfile = selected,
            testingProfileIds = right.testingProfileIds,
            isSyncing = right.isSyncing,
            syncingSubscriptionIds = right.syncingSubIds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConnectUiState()
    )

    private val initialLaunchSyncDone = MutableStateFlow(false)
    private val scheduleResetTick = MutableStateFlow(0L)
    private var lastForegroundSyncElapsedMs: Long = -1L

    private data class AutoScheduleConfig(
        val enabled: Boolean,
        val bySchedule: Boolean,
        val intervalHours: Int
    )

    init {
        viewModelScope.launch {
            container.profileRepository.purgeLegacyTestSubscriptions()
            val initialSettings = container.settingsRepository.getSettingsOnce()
            if (initialSettings.autoUpdateSubscriptions && initialSettings.autoUpdateOnLaunch) {
                lastForegroundSyncElapsedMs = SystemClock.elapsedRealtime()
                container.profileRepository.syncAllAutoSubscriptions()
            }
            initialLaunchSyncDone.value = true
        }
        viewModelScope.launch {
            initialLaunchSyncDone.first { it }
            val scheduleConfigFlow = container.settingsRepository.settingsFlow
                .map {
                    AutoScheduleConfig(
                        enabled = it.autoUpdateSubscriptions,
                        bySchedule = it.autoUpdateBySchedule,
                        intervalHours = it.autoUpdateIntervalHours
                    )
                }
                .distinctUntilChanged()
            val httpsSubCountFlow = container.profileRepository.subscriptionsFlow
                .map { list -> list.count { it.url.startsWith("https://", ignoreCase = true) } }
                .distinctUntilChanged()

            combine(scheduleConfigFlow, httpsSubCountFlow, scheduleResetTick) { config, subCount, _ ->
                config to subCount
            }.collectLatest { (config, subCount) ->
                if (!config.enabled || !config.bySchedule || subCount <= 0) return@collectLatest
                while (true) {
                    val remainingMs = container.profileRepository.nextAutoUpdateDelayMs(config.intervalHours)
                        ?: break
                    if (remainingMs > 0L) {
                        delay(remainingMs.coerceAtMost(15 * 60_000L))
                    } else {
                        val updated = container.profileRepository.syncDueSubscriptions(config.intervalHours)
                        if (updated == 0) {
                            // Backoff if sync failed (e.g. offline) so we do not tight-loop
                            delay(5 * 60_000L)
                        }
                    }
                }
            }
        }
    }

    fun onAppEnteredForeground() {
        viewModelScope.launch {
            initialLaunchSyncDone.first { it }
            val currentSettings = container.settingsRepository.getSettingsOnce()
            if (!currentSettings.autoUpdateSubscriptions || !currentSettings.autoUpdateOnLaunch) {
                return@launch
            }
            val nowElapsed = SystemClock.elapsedRealtime()
            val last = lastForegroundSyncElapsedMs
            if (last >= 0L && (nowElapsed - last) < FOREGROUND_SYNC_MIN_GAP_MS) {
                return@launch
            }
            lastForegroundSyncElapsedMs = nowElapsed
            val updated = container.profileRepository.syncAllAutoSubscriptions()
            if (updated > 0) {
                scheduleResetTick.update { it + 1L }
            }
        }
    }

    companion object {
        private const val FOREGROUND_SYNC_MIN_GAP_MS = 3_000L
    }

    fun refreshSubscription(subscriptionId: Long?, onComplete: (Result<Int>) -> Unit = {}) {
        if (subscriptionId != null) {
            var acquired = false
            syncingSubIds.update { current ->
                if (subscriptionId in current) current
                else {
                    acquired = true
                    current + subscriptionId
                }
            }
            if (!acquired) return
        } else {
            if (syncing.value) return
            syncing.value = true
        }

        viewModelScope.launch {
            val current = uiState.value
            val targetSub = subscriptionId?.let { id ->
                current.subscriptions.find { it.id == id }
            } ?: current.subscriptions.firstOrNull()

            val result = if (targetSub != null) {
                container.profileRepository.syncSubscription(targetSub).also { res ->
                    if (res.isSuccess) {
                        scheduleResetTick.update { it + 1L }
                    }
                }
            } else {
                Result.success(0)
            }

            if (subscriptionId != null) {
                syncingSubIds.update { it - subscriptionId }
            } else {
                syncing.value = false
            }
            onComplete(result)
        }
    }

    fun renameSubscription(subscriptionId: Long, newName: String) {
        viewModelScope.launch {
            container.profileRepository.renameSubscription(subscriptionId, newName)
        }
    }

    fun deleteSubscription(subscriptionId: Long) {
        viewModelScope.launch {
            container.profileRepository.deleteSubscription(subscriptionId)
        }
    }

    fun selectProfile(profileId: Long) {
        viewModelScope.launch {
            container.settingsRepository.setSelectedProfileId(profileId)
        }
    }

    fun reorderProfiles(orderedIds: List<Long>) {
        if (orderedIds.isEmpty()) return
        viewModelScope.launch {
            container.profileRepository.reorderProfiles(orderedIds)
        }
    }

    fun startVpn(context: Context, profileId: Long) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val settings = container.settingsRepository.getSettingsOnce()
            val profile = container.profileRepository.getProfileById(profileId)
                ?: container.profileRepository.getFirstProfileOrNull()
            if (profile == null) {
                VpnSessionManager.setError(appContext.getString(com.xraypulse.app.R.string.error_no_profile_selected))
                return@launch
            }
            val customRules = container.routingRepository.getEnabledRulesOnce()
            val configJson = XrayConfigBuilder.buildFullConfig(profile, settings, customRules)
            com.xraypulse.app.vpn.VpnLaunchStore.write(appContext, profile, settings, configJson)
            container.settingsRepository.setLastConnectedProfileId(profile.id)
            VpnSessionManager.setConnecting(profile, configJson)
            XrayVpnService.start(appContext, profile.id)
        }
    }

    fun stopVpn(context: Context) {
        VpnSessionManager.setDisconnecting()
        XrayVpnService.stop(context.applicationContext)
    }

    fun testProfilePing(profile: ServerProfileEntity) {
        var acquired = false
        testingProfileIds.update { current ->
            if (profile.id in current) current
            else {
                acquired = true
                current + profile.id
            }
        }
        if (!acquired) return
        viewModelScope.launch {
            try {
                container.profileRepository.testProfileLatency(profile)
            } finally {
                testingProfileIds.update { it - profile.id }
            }
        }
    }

    fun testSubscriptionPing(subscriptionId: Long?) {
        val targets = uiState.value.profiles.filter { it.subscriptionId == subscriptionId }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            targets.chunked(4).forEach { batch ->
                batch.map { profile ->
                    async {
                        var acquired = false
                        testingProfileIds.update { current ->
                            if (profile.id in current) current
                            else {
                                acquired = true
                                current + profile.id
                            }
                        }
                        if (acquired) {
                            try {
                                container.profileRepository.testProfileLatency(profile)
                            } finally {
                                testingProfileIds.update { it - profile.id }
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    suspend fun importFromText(raw: String): Result<Int> {
        val trimmed = raw.trim()
        if (trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
        ) {
            return container.profileRepository.addAndSyncSubscription(name = "", url = trimmed)
        }
        val parsedResult = runCatching { container.profileRepository.importFromText(trimmed) }
        val importedCount = parsedResult.getOrNull() ?: 0
        if (importedCount > 0) {
            return parsedResult
        }
        val embeddedUrl = EMBEDDED_SUB_URL_REGEX.find(trimmed)
            ?.value
            ?.trimEnd(',', ';', '.', ')', ']', '>', '"', '\'')
        return if (!embeddedUrl.isNullOrBlank()) {
            container.profileRepository.addAndSyncSubscription(name = "", url = embeddedUrl)
        } else {
            parsedResult
        }
    }
}

private val EMBEDDED_SUB_URL_REGEX = Regex("""(?i)\bhttps?://[^\s<>"]+""")

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val xrayVersion: String = "xray-core 26.11.9",
    val previewConfigJson: String = "",
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val isLoadingApps: Boolean = false
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val previewJson = MutableStateFlow("")
    private val installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val isLoadingApps = MutableStateFlow(false)

    // Only observe core metadata needed for Settings UI so 1s VPN traffic ticks never recompose MainActivity/Theme
    private val coreMetadataFlow = VpnSessionManager.state
        .map { it.xrayVersion to it.lastGeneratedConfig }
        .distinctUntilChanged()

    val logsFlow: StateFlow<List<String>> = VpnSessionManager.state
        .map { it.logs }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VpnSessionManager.state.value.logs
        )

    val uiState: StateFlow<SettingsUiState> = combine(
        container.settingsRepository.settingsFlow,
        coreMetadataFlow,
        previewJson,
        installedApps,
        isLoadingApps
    ) { settings, coreMeta, json, apps, loadingApps ->
        SettingsUiState(
            settings = settings,
            xrayVersion = coreMeta.first,
            previewConfigJson = json.ifBlank { coreMeta.second },
            installedApps = apps,
            isLoadingApps = loadingApps
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { container.settingsRepository.setAppLanguage(lang) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { container.settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setDynamicColorEnabled(enabled) }
    }

    fun setMarqueeEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setMarqueeEnabled(enabled) }
    }

    fun setBypassLan(bypass: Boolean) {
        viewModelScope.launch { container.settingsRepository.setBypassLan(bypass) }
    }

    fun setMtu(mtu: Int) {
        viewModelScope.launch { container.settingsRepository.setMtu(mtu) }
    }

    fun setEnableIpv6(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setEnableIpv6(enabled) }
    }

    fun setMuxSettings(enabled: Boolean, concurrency: Int, xudpConcurrency: Int) {
        viewModelScope.launch {
            container.settingsRepository.setMuxSettings(enabled, concurrency, xudpConcurrency)
        }
    }

    fun setFragmentSettings(
        enabled: Boolean,
        packets: String,
        length: String,
        interval: String
    ) {
        viewModelScope.launch {
            container.settingsRepository.setFragmentSettings(enabled, packets, length, interval)
        }
    }

    fun setAutoUpdateSettings(
        enabled: Boolean,
        onLaunch: Boolean,
        bySchedule: Boolean,
        intervalHours: Int
    ) {
        viewModelScope.launch {
            container.settingsRepository.setAutoUpdateSettings(
                enabled = enabled,
                onLaunch = onLaunch,
                bySchedule = bySchedule,
                intervalHours = intervalHours
            )
        }
    }

    fun setCustomAccentColor(argb: Long?) {
        viewModelScope.launch { container.settingsRepository.setCustomAccentColor(argb) }
    }

    fun setCustomBackgroundColor(argb: Long?) {
        viewModelScope.launch { container.settingsRepository.setCustomBackgroundColor(argb) }
    }

    fun setConnectingRotationSpeed(degPerSec: Int) {
        viewModelScope.launch { container.settingsRepository.setConnectingRotationSpeed(degPerSec) }
    }

    fun setConnectedRotationSpeed(degPerSec: Int) {
        viewModelScope.launch { container.settingsRepository.setConnectedRotationSpeed(degPerSec) }
    }

    fun setConnectButtonShape(shape: ConnectButtonShape) {
        viewModelScope.launch { container.settingsRepository.setConnectButtonShape(shape) }
    }

    fun setButtonShapeParams(params: ButtonShapeParams) {
        viewModelScope.launch { container.settingsRepository.setButtonShapeParams(params) }
    }

    fun setSplitTunnelMode(mode: SplitTunnelMode) {
        viewModelScope.launch { container.settingsRepository.setSplitTunnelMode(mode) }
    }

    fun toggleSplitPackage(pkg: String) {
        viewModelScope.launch { container.settingsRepository.toggleSplitTunnelPackage(pkg) }
    }

    fun ensureAppsLoaded() {
        if (installedApps.value.isNotEmpty() || isLoadingApps.value) return
        viewModelScope.launch {
            isLoadingApps.value = true
            installedApps.value = container.routingRepository.loadLaunchableApps()
            isLoadingApps.value = false
        }
    }

    fun clearLogs() = VpnSessionManager.clearLogs()

    fun refreshConfigPreview() {
        viewModelScope.launch {
            val json = withContext(Dispatchers.Default) {
                val settings = container.settingsRepository.getSettingsOnce()
                val profile = settings.selectedProfileId?.let { container.profileRepository.getProfileById(it) }
                    ?: container.profileRepository.getFirstProfileOrNull()
                if (profile != null) {
                    val rules = container.routingRepository.getEnabledRulesOnce()
                    XrayConfigBuilder.buildFullConfig(profile, settings, rules)
                } else {
                    "{}"
                }
            }
            previewJson.value = json
            if (json != "{}") {
                VpnSessionManager.updatePreviewConfig(json)
            }
        }
    }
}

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ConnectViewModel::class.java) -> ConnectViewModel(container) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(container) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
