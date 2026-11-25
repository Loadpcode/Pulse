package com.xraypulse.app.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.xraypulse.app.MainActivity
import com.xraypulse.app.R
import com.xraypulse.app.XrayPulseApp
import com.xraypulse.app.data.local.ServerProfileEntity
import com.xraypulse.app.data.xray.XrayConfigBuilder
import com.xraypulse.app.model.AppSettings
import com.xraypulse.app.model.VpnSessionState
import com.xraypulse.app.model.VpnStatus
import com.xraypulse.app.ui.components.createLocalizedContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class XrayQuickSettingsTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listeningJob: Job? = null

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTileOnce()
    }

    override fun onStartListening() {
        super.onStartListening()
        VpnSessionManager.syncFromRunningService(this)
        listeningJob?.cancel()
        listeningJob = serviceScope.launch {
            VpnSessionManager.state.collectLatest { session ->
                updateTileForSession(session)
            }
        }
    }

    override fun onStopListening() {
        listeningJob?.cancel()
        listeningJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { handleToggleClick() }
        } else {
            handleToggleClick()
        }
    }

    override fun onDestroy() {
        listeningJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun refreshTileOnce() {
        serviceScope.launch {
            updateTileForSession(VpnSessionManager.state.value)
        }
    }

    private fun handleToggleClick() {
        val currentStatus = VpnSessionManager.state.value.status
        if (currentStatus == VpnStatus.CONNECTED || currentStatus == VpnStatus.CONNECTING) {
            VpnSessionManager.setDisconnecting()
            XrayVpnService.stop(applicationContext)
            refreshTileOnce()
            return
        }

        serviceScope.launch {
            val container = (applicationContext as? XrayPulseApp)?.container
            if (container == null) {
                launchMainActivity(profileId = -1L)
                return@launch
            }

            val (settings, targetProfile) = resolveTargetProfile()
            if (targetProfile == null) {
                launchMainActivity(profileId = -1L)
                return@launch
            }

            val prepareIntent = runCatching { VpnService.prepare(this@XrayQuickSettingsTileService) }.getOrNull()
            if (prepareIntent != null) {
                launchMainActivity(profileId = targetProfile.id)
                return@launch
            }

            val customRules = container.routingRepository.getEnabledRulesOnce()
            val configJson = XrayConfigBuilder.buildFullConfig(targetProfile, settings, customRules)
            VpnLaunchStore.write(applicationContext, targetProfile, settings, configJson)
            container.settingsRepository.setLastConnectedProfileId(targetProfile.id)
            VpnSessionManager.setConnecting(targetProfile, configJson)
            XrayVpnService.start(applicationContext, targetProfile.id)
            updateTileForSession(VpnSessionManager.state.value)
        }
    }

    private suspend fun resolveTargetProfile(): Pair<AppSettings, ServerProfileEntity?> {
        val container = (applicationContext as? XrayPulseApp)?.container
            ?: return AppSettings() to null
        val settings = container.settingsRepository.getSettingsOnce()
        val launchStoreId = VpnLaunchStore.read(applicationContext)?.profileId
        val candidates = listOfNotNull(
            settings.lastConnectedProfileId,
            launchStoreId,
            settings.selectedProfileId
        ).distinct()

        for (id in candidates) {
            val found = container.profileRepository.getProfileById(id)
            if (found != null) return settings to found
        }
        return settings to container.profileRepository.getFirstProfileOrNull()
    }

    private suspend fun updateTileForSession(session: VpnSessionState) {
        val (settings, targetProfile) = resolveTargetProfile()
        val localizedCtx = createLocalizedContext(settings.appLanguage)

        val tileLabel = localizedCtx.getString(R.string.qs_tile_label)
        val profileTitle = when (session.status) {
            VpnStatus.CONNECTED, VpnStatus.CONNECTING -> {
                session.activeProfileName ?: targetProfile?.name
            }
            else -> targetProfile?.name
        }

        val tileState = when (session.status) {
            VpnStatus.CONNECTED, VpnStatus.CONNECTING -> Tile.STATE_ACTIVE
            VpnStatus.DISCONNECTING, VpnStatus.DISCONNECTED, VpnStatus.ERROR -> Tile.STATE_INACTIVE
        }

        val tileSubtitle = when (session.status) {
            VpnStatus.CONNECTED -> profileTitle ?: localizedCtx.getString(R.string.qs_tile_connected)
            VpnStatus.CONNECTING -> localizedCtx.getString(R.string.status_connecting)
            VpnStatus.DISCONNECTING -> localizedCtx.getString(R.string.status_disconnecting)
            VpnStatus.DISCONNECTED, VpnStatus.ERROR -> {
                profileTitle ?: localizedCtx.getString(R.string.qs_tile_no_server)
            }
        }

        withContext(Dispatchers.Main.immediate) {
            val tile = qsTile ?: return@withContext
            tile.icon = Icon.createWithResource(this@XrayQuickSettingsTileService, R.drawable.ic_qs_xr)
            tile.state = tileState
            tile.label = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tileLabel
            } else {
                profileTitle ?: tileLabel
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = tileSubtitle
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tile.stateDescription = tileSubtitle
            }
            tile.contentDescription = "$tileLabel, $tileSubtitle"
            runCatching { tile.updateTile() }
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private suspend fun launchMainActivity(profileId: Long) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            if (profileId > 0L) {
                putExtra(MainActivity.EXTRA_TILE_CONNECT_PROFILE_ID, profileId)
            }
        }
        withContext(Dispatchers.Main.immediate) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this@XrayQuickSettingsTileService,
                    REQUEST_CODE_OPEN_APP,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                runCatching { startActivityAndCollapse(pendingIntent) }
            } else {
                @Suppress("DEPRECATION")
                runCatching { startActivityAndCollapse(intent) }
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_OPEN_APP = 5104

        fun requestTileUpdate(context: Context) {
            val appCtx = context.applicationContext
            runCatching {
                requestListeningState(
                    appCtx,
                    ComponentName(appCtx, XrayQuickSettingsTileService::class.java)
                )
            }
        }
    }
}
