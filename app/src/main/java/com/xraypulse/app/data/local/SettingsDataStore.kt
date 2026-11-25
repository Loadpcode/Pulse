package com.xraypulse.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xraypulse.app.model.AppLanguage
import com.xraypulse.app.model.AppSettings
import com.xraypulse.app.model.AppThemeMode
import com.xraypulse.app.model.ButtonShapeParams
import com.xraypulse.app.model.ConnectButtonShape
import com.xraypulse.app.model.RoutingPreset
import com.xraypulse.app.model.SplitTunnelMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "xray_pulse_settings")

class SettingsDataStore(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val SELECTED_PROFILE_ID = longPreferencesKey("selected_profile_id")
        val LAST_CONNECTED_PROFILE_ID = longPreferencesKey("last_connected_profile_id")
        val ROUTING_PRESET = stringPreferencesKey("routing_preset")
        val SPLIT_TUNNEL_MODE = stringPreferencesKey("split_tunnel_mode")
        val SPLIT_TUNNEL_PACKAGES = stringSetPreferencesKey("split_tunnel_packages")
        val PRIMARY_DNS = stringPreferencesKey("primary_dns")
        val SECONDARY_DNS = stringPreferencesKey("secondary_dns")
        val SOCKS_PORT = intPreferencesKey("socks_port")
        val HTTP_PORT = intPreferencesKey("http_port")
        val ENABLE_MUX = booleanPreferencesKey("enable_mux")
        val MUX_CONCURRENCY = intPreferencesKey("mux_concurrency")
        val MUX_XUDP_CONCURRENCY = intPreferencesKey("mux_xudp_concurrency")
        val ENABLE_FRAGMENT = booleanPreferencesKey("enable_fragment")
        val FRAGMENT_PACKETS = stringPreferencesKey("fragment_packets")
        val FRAGMENT_LENGTH = stringPreferencesKey("fragment_length")
        val FRAGMENT_INTERVAL = stringPreferencesKey("fragment_interval")
        val BYPASS_LAN = booleanPreferencesKey("bypass_lan")
        val MTU = intPreferencesKey("mtu")
        val ENABLE_IPV6 = booleanPreferencesKey("enable_ipv6")
        val ALLOW_INSECURE_GLOBAL = booleanPreferencesKey("allow_insecure_global")
        val AUTO_UPDATE_SUBS = booleanPreferencesKey("auto_update_subs")
        val AUTO_UPDATE_ON_LAUNCH = booleanPreferencesKey("auto_update_on_launch")
        val AUTO_UPDATE_BY_SCHEDULE = booleanPreferencesKey("auto_update_by_schedule")
        val AUTO_UPDATE_HOURS = intPreferencesKey("auto_update_hours")
        val MARQUEE_ENABLED = booleanPreferencesKey("marquee_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val CUSTOM_ACCENT_COLOR = longPreferencesKey("custom_accent_color")
        val CUSTOM_BACKGROUND_COLOR = longPreferencesKey("custom_background_color")
        val CONNECTING_ROTATION_SPEED = intPreferencesKey("connecting_rotation_speed")
        val CONNECTED_ROTATION_SPEED = intPreferencesKey("connected_rotation_speed")
        val CONNECT_BUTTON_SHAPE = stringPreferencesKey("connect_button_shape")
        val BUTTON_SHAPE_PARAMS = stringPreferencesKey("button_shape_params")
    }

    private val shapeParamsJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            selectedProfileId = prefs[Keys.SELECTED_PROFILE_ID],
            lastConnectedProfileId = prefs[Keys.LAST_CONNECTED_PROFILE_ID],
            routingPreset = prefs[Keys.ROUTING_PRESET]?.let {
                runCatching { RoutingPreset.valueOf(it) }.getOrNull()
            } ?: RoutingPreset.BYPASS_LAN,
            splitTunnelMode = prefs[Keys.SPLIT_TUNNEL_MODE]?.let {
                runCatching { SplitTunnelMode.valueOf(it) }.getOrNull()
            } ?: SplitTunnelMode.DISABLED,
            splitTunnelPackages = prefs[Keys.SPLIT_TUNNEL_PACKAGES] ?: emptySet(),
            primaryDns = prefs[Keys.PRIMARY_DNS] ?: "1.1.1.1",
            secondaryDns = prefs[Keys.SECONDARY_DNS] ?: "8.8.8.8",
            socksPort = prefs[Keys.SOCKS_PORT] ?: 10808,
            httpPort = prefs[Keys.HTTP_PORT] ?: 10809,
            enableMux = prefs[Keys.ENABLE_MUX] ?: false,
            muxConcurrency = prefs[Keys.MUX_CONCURRENCY] ?: 8,
            muxXudpConcurrency = prefs[Keys.MUX_XUDP_CONCURRENCY] ?: 8,
            enableFragment = prefs[Keys.ENABLE_FRAGMENT] ?: false,
            fragmentPackets = prefs[Keys.FRAGMENT_PACKETS] ?: "tlshello",
            fragmentLength = prefs[Keys.FRAGMENT_LENGTH] ?: "10-20",
            fragmentInterval = prefs[Keys.FRAGMENT_INTERVAL] ?: "10-20",
            bypassLan = prefs[Keys.BYPASS_LAN] ?: true,
            mtu = prefs[Keys.MTU] ?: 1500,
            enableIpv6 = prefs[Keys.ENABLE_IPV6] ?: false,
            allowInsecureGlobal = prefs[Keys.ALLOW_INSECURE_GLOBAL] ?: false,
            autoUpdateSubscriptions = prefs[Keys.AUTO_UPDATE_SUBS] ?: false,
            autoUpdateOnLaunch = prefs[Keys.AUTO_UPDATE_ON_LAUNCH] ?: false,
            autoUpdateBySchedule = prefs[Keys.AUTO_UPDATE_BY_SCHEDULE] ?: true,
            autoUpdateIntervalHours = prefs[Keys.AUTO_UPDATE_HOURS] ?: 12,
            marqueeEnabled = prefs[Keys.MARQUEE_ENABLED] ?: true,
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { AppThemeMode.valueOf(it) }.getOrNull()
            } ?: AppThemeMode.DARK,
            dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR] ?: true,
            appLanguage = prefs[Keys.APP_LANGUAGE]?.let {
                runCatching { AppLanguage.valueOf(it) }.getOrNull()
            } ?: AppLanguage.SYSTEM,
            customAccentColorArgb = prefs[Keys.CUSTOM_ACCENT_COLOR],
            customBackgroundColorArgb = prefs[Keys.CUSTOM_BACKGROUND_COLOR],
            connectingRotationSpeed = (prefs[Keys.CONNECTING_ROTATION_SPEED] ?: 20).coerceIn(0, 360),
            connectedRotationSpeed = (prefs[Keys.CONNECTED_ROTATION_SPEED] ?: 60).coerceIn(0, 360),
            connectButtonShape = prefs[Keys.CONNECT_BUTTON_SHAPE]?.let {
                runCatching { ConnectButtonShape.valueOf(it) }.getOrNull()
            } ?: ConnectButtonShape.ROUNDED_SQUARE,
            buttonShapeParams = prefs[Keys.BUTTON_SHAPE_PARAMS]?.let { raw ->
                runCatching { shapeParamsJson.decodeFromString<ButtonShapeParams>(raw).coerced() }.getOrNull()
            } ?: ButtonShapeParams()
        )
    }

    suspend fun getOnce(): AppSettings = settingsFlow.first()

    suspend fun setSelectedProfileId(id: Long?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.SELECTED_PROFILE_ID)
            else prefs[Keys.SELECTED_PROFILE_ID] = id
        }
    }

    suspend fun setLastConnectedProfileId(id: Long?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.LAST_CONNECTED_PROFILE_ID)
            else prefs[Keys.LAST_CONNECTED_PROFILE_ID] = id
        }
    }

    suspend fun setSplitTunnelMode(mode: SplitTunnelMode) {
        dataStore.edit { it[Keys.SPLIT_TUNNEL_MODE] = mode.name }
    }

    suspend fun toggleSplitTunnelPackage(packageName: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.SPLIT_TUNNEL_PACKAGES] ?: emptySet()
            prefs[Keys.SPLIT_TUNNEL_PACKAGES] = if (packageName in current) {
                current - packageName
            } else {
                current + packageName
            }
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { it[Keys.APP_LANGUAGE] = language.name }
    }

    suspend fun setMarqueeEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.MARQUEE_ENABLED] = enabled }
    }

    suspend fun setBypassLan(bypass: Boolean) {
        dataStore.edit { it[Keys.BYPASS_LAN] = bypass }
    }

    suspend fun setMtu(mtu: Int) {
        dataStore.edit { it[Keys.MTU] = mtu.coerceIn(1200, 9000) }
    }

    suspend fun setEnableIpv6(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLE_IPV6] = enabled }
    }

    suspend fun setMuxSettings(enabled: Boolean, concurrency: Int, xudpConcurrency: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.ENABLE_MUX] = enabled
            prefs[Keys.MUX_CONCURRENCY] = concurrency.coerceIn(1, 128)
            prefs[Keys.MUX_XUDP_CONCURRENCY] = xudpConcurrency.coerceIn(0, 128)
        }
    }

    suspend fun setFragmentSettings(
        enabled: Boolean,
        packets: String,
        length: String,
        interval: String
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.ENABLE_FRAGMENT] = enabled
            prefs[Keys.FRAGMENT_PACKETS] = packets.trim().ifBlank { "tlshello" }
            prefs[Keys.FRAGMENT_LENGTH] = length.trim().ifBlank { "10-20" }
            prefs[Keys.FRAGMENT_INTERVAL] = interval.trim().ifBlank { "10-20" }
        }
    }

    suspend fun setAutoUpdateSettings(
        enabled: Boolean,
        onLaunch: Boolean,
        bySchedule: Boolean,
        intervalHours: Int
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_UPDATE_SUBS] = enabled
            prefs[Keys.AUTO_UPDATE_ON_LAUNCH] = onLaunch
            prefs[Keys.AUTO_UPDATE_BY_SCHEDULE] = bySchedule
            prefs[Keys.AUTO_UPDATE_HOURS] = intervalHours.coerceIn(1, 168)
        }
    }

    suspend fun setCustomAccentColor(argb: Long?) {
        dataStore.edit { prefs ->
            if (argb == null) {
                prefs.remove(Keys.CUSTOM_ACCENT_COLOR)
            } else {
                prefs[Keys.CUSTOM_ACCENT_COLOR] = argb
            }
        }
    }

    suspend fun setCustomBackgroundColor(argb: Long?) {
        dataStore.edit { prefs ->
            if (argb == null) {
                prefs.remove(Keys.CUSTOM_BACKGROUND_COLOR)
            } else {
                prefs[Keys.CUSTOM_BACKGROUND_COLOR] = argb
            }
        }
    }

    suspend fun setConnectingRotationSpeed(degPerSec: Int) {
        dataStore.edit { it[Keys.CONNECTING_ROTATION_SPEED] = degPerSec.coerceIn(0, 360) }
    }

    suspend fun setConnectedRotationSpeed(degPerSec: Int) {
        dataStore.edit { it[Keys.CONNECTED_ROTATION_SPEED] = degPerSec.coerceIn(0, 360) }
    }

    suspend fun setConnectButtonShape(shape: ConnectButtonShape) {
        dataStore.edit { it[Keys.CONNECT_BUTTON_SHAPE] = shape.name }
    }

    suspend fun setButtonShapeParams(params: ButtonShapeParams) {
        val encoded = shapeParamsJson.encodeToString(params.coerced())
        dataStore.edit { it[Keys.BUTTON_SHAPE_PARAMS] = encoded }
    }
}
