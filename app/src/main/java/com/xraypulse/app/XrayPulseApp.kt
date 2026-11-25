package com.xraypulse.app

import android.app.Application
import android.os.Build
import com.xraypulse.app.data.local.AppDatabase
import com.xraypulse.app.data.local.SettingsDataStore
import com.xraypulse.app.data.repository.ProfileRepository
import com.xraypulse.app.data.repository.RoutingRepository
import com.xraypulse.app.data.repository.SettingsRepository
import com.xraypulse.app.vpn.VpnPingClient
import com.xraypulse.app.vpn.VpnSessionManager
import com.xraypulse.app.vpn.XrayCoreBridge
import java.io.File

class AppContainer(application: Application) {
    private val appContext = application.applicationContext
    private val database by lazy { AppDatabase.getInstance(appContext) }
    private val settingsDataStore by lazy { SettingsDataStore(appContext) }
    val vpnPingClient by lazy { VpnPingClient(appContext) }

    val settingsRepository by lazy { SettingsRepository(settingsDataStore) }
    val profileRepository by lazy {
        ProfileRepository(
            profileDao = database.serverProfileDao(),
            subscriptionDao = database.subscriptionDao(),
            settingsDataStore = settingsDataStore,
            vpnPingClient = vpnPingClient
        )
    }
    val routingRepository by lazy {
        RoutingRepository(
            context = appContext,
            routingRuleDao = database.routingRuleDao()
        )
    }
}

class XrayPulseApp : Application() {
    lateinit var container: AppContainer
        private set

    val vpnCoreBridge: XrayCoreBridge by lazy { XrayCoreBridge(this) }

    override fun onCreate() {
        super.onCreate()
        if (isVpnProcess()) {
            VpnSessionManager.initVpnProcess(this)
        } else {
            VpnSessionManager.initMainProcess(this)
            container = AppContainer(this)
        }
    }

    private fun isVpnProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            runCatching {
                File("/proc/self/cmdline").readText().trim('\u0000', ' ', '\n', '\r')
            }.getOrDefault("")
        }
        return processName.endsWith(":vpn")
    }
}
