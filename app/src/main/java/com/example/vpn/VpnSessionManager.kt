package com.example.vpn

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.data.local.ServerProfileEntity
import com.example.model.VpnProtocol
import com.example.model.VpnSessionState
import com.example.model.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VpnSessionManager {
    private const val MAX_LOG_LINES = 300
    private const val ACTION_VPN_STATE_CHANGED = "com.example.vpn.ACTION_VPN_STATE_CHANGED"
    private const val ACTION_VPN_LOG = "com.example.vpn.ACTION_VPN_LOG"
    private const val EXTRA_STATUS = "status"
    private const val EXTRA_PROFILE_ID = "profile_id"
    private const val EXTRA_PROFILE_NAME = "profile_name"
    private const val EXTRA_PROTOCOL = "protocol"
    private const val EXTRA_ENDPOINT = "endpoint"
    private const val EXTRA_STARTED_AT = "started_at"
    private const val EXTRA_ERROR_MESSAGE = "error_message"
    private const val EXTRA_ERROR_NONCE = "error_nonce"
    private const val EXTRA_RX_BPS = "rx_bps"
    private const val EXTRA_TX_BPS = "tx_bps"
    private const val EXTRA_TOTAL_RX = "total_rx"
    private const val EXTRA_TOTAL_TX = "total_tx"
    private const val EXTRA_XRAY_VERSION = "xray_version"
    private const val EXTRA_LOG_LINE = "log_line"
    private const val EXTRA_LOGS_SNAPSHOT = "logs_snapshot"

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _state = MutableStateFlow(VpnSessionState())
    val state: StateFlow<VpnSessionState> = _state.asStateFlow()

    @Volatile
    private var vpnAppContext: Context? = null

    @Volatile
    private var mainAppContext: Context? = null

    @Volatile
    private var mainReceiverRegistered = false

    fun initVpnProcess(context: Context) {
        vpnAppContext = context.applicationContext
    }

    fun initMainProcess(context: Context) {
        mainAppContext = context.applicationContext
        if (mainReceiverRegistered) return
        synchronized(this) {
            if (mainReceiverRegistered) return
            val appCtx = context.applicationContext
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (intent?.action) {
                        ACTION_VPN_LOG -> {
                            val line = intent.getStringExtra(EXTRA_LOG_LINE) ?: return
                            val incomingBody = line.substringAfter("] ", line)
                            _state.update { current ->
                                val lastLine = current.logs.lastOrNull()
                                val lastBody = lastLine?.substringAfter("] ", lastLine)
                                val isDualProcessStatusDup = lastBody == incomingBody &&
                                    (incomingBody.startsWith("Connecting to ") || incomingBody == "Stopping VPN tunnel...")
                                if (isDualProcessStatusDup) {
                                    current
                                } else {
                                    current.copy(logs = (current.logs + line).takeLast(MAX_LOG_LINES))
                                }
                            }
                        }

                        ACTION_VPN_STATE_CHANGED -> {
                            val statusName = intent.getStringExtra(EXTRA_STATUS) ?: return
                            val status = runCatching { VpnStatus.valueOf(statusName) }.getOrNull() ?: return
                            val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L).takeIf { it > 0L }
                            val profileName = intent.getStringExtra(EXTRA_PROFILE_NAME)
                            val protocol = intent.getStringExtra(EXTRA_PROTOCOL)?.let { name ->
                                runCatching { VpnProtocol.valueOf(name) }.getOrNull()
                            }
                            val endpoint = intent.getStringExtra(EXTRA_ENDPOINT)
                            val startedAt = intent.getLongExtra(EXTRA_STARTED_AT, 0L)
                            val errorMsg = intent.getStringExtra(EXTRA_ERROR_MESSAGE)
                            val errorNonce = intent.getLongExtra(EXTRA_ERROR_NONCE, currentErrorNonceFallback(status))
                            val rxBps = intent.getLongExtra(EXTRA_RX_BPS, 0L)
                            val txBps = intent.getLongExtra(EXTRA_TX_BPS, 0L)
                            val totalRx = intent.getLongExtra(EXTRA_TOTAL_RX, 0L)
                            val totalTx = intent.getLongExtra(EXTRA_TOTAL_TX, 0L)
                            val xrayVersion = intent.getStringExtra(EXTRA_XRAY_VERSION)
                            val logsSnapshot = intent.getStringArrayListExtra(EXTRA_LOGS_SNAPSHOT)

                            val prev = _state.value
                            _state.update { current ->
                                val resolvedLogs = if (!logsSnapshot.isNullOrEmpty() &&
                                    (current.logs.isEmpty() || logsSnapshot.size > current.logs.size)
                                ) {
                                    logsSnapshot.takeLast(MAX_LOG_LINES)
                                } else {
                                    current.logs
                                }
                                current.copy(
                                    status = status,
                                    activeProfileId = profileId ?: current.activeProfileId,
                                    activeProfileName = profileName ?: current.activeProfileName,
                                    activeProtocol = protocol ?: current.activeProtocol,
                                    activeEndpoint = endpoint ?: current.activeEndpoint,
                                    startedAtMillis = startedAt,
                                    errorMessage = errorMsg,
                                    errorNonce = if (errorNonce > 0L) errorNonce else current.errorNonce,
                                    rxBytesPerSec = rxBps,
                                    txBytesPerSec = txBps,
                                    totalRxBytes = totalRx,
                                    totalTxBytes = totalTx,
                                    xrayVersion = xrayVersion ?: current.xrayVersion,
                                    logs = resolvedLogs
                                )
                            }
                            if (prev.status != status || (profileId != null && prev.activeProfileId != profileId)) {
                                XrayQuickSettingsTileService.requestTileUpdate(appCtx)
                            }
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(ACTION_VPN_STATE_CHANGED)
                addAction(ACTION_VPN_LOG)
            }
            ContextCompat.registerReceiver(
                appCtx,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            mainReceiverRegistered = true
        }
    }

    @Volatile
    private var pendingSyncConnection: ServiceConnection? = null

    fun syncFromRunningService(context: Context) {
        val appCtx = context.applicationContext
        pendingSyncConnection?.let { prev ->
            runCatching { appCtx.unbindService(prev) }
            pendingSyncConnection = null
        }
        val intent = Intent(appCtx, XrayVpnService::class.java).apply {
            action = XrayVpnService.ACTION_QUERY_STATE
        }
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (pendingSyncConnection === this) {
                    pendingSyncConnection = null
                }
                runCatching { appCtx.unbindService(this) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (pendingSyncConnection === this) {
                    pendingSyncConnection = null
                }
            }
        }
        pendingSyncConnection = conn
        runCatching {
            val bound = appCtx.bindService(intent, conn, 0)
            if (!bound) {
                pendingSyncConnection = null
            }
        }.onFailure {
            pendingSyncConnection = null
        }
    }

    fun broadcastCurrentState(notifyTile: Boolean = false, includeLogs: Boolean = false) {
        val ctx = vpnAppContext ?: mainAppContext
        if (ctx != null && vpnAppContext != null) {
            val current = _state.value
            val intent = Intent(ACTION_VPN_STATE_CHANGED).apply {
                setPackage(ctx.packageName)
                putExtra(EXTRA_STATUS, current.status.name)
                putExtra(EXTRA_PROFILE_ID, current.activeProfileId ?: -1L)
                putExtra(EXTRA_PROFILE_NAME, current.activeProfileName)
                putExtra(EXTRA_PROTOCOL, current.activeProtocol?.name)
                putExtra(EXTRA_ENDPOINT, current.activeEndpoint)
                putExtra(EXTRA_STARTED_AT, current.startedAtMillis)
                putExtra(EXTRA_ERROR_MESSAGE, current.errorMessage)
                putExtra(EXTRA_ERROR_NONCE, current.errorNonce)
                putExtra(EXTRA_RX_BPS, current.rxBytesPerSec)
                putExtra(EXTRA_TX_BPS, current.txBytesPerSec)
                putExtra(EXTRA_TOTAL_RX, current.totalRxBytes)
                putExtra(EXTRA_TOTAL_TX, current.totalTxBytes)
                putExtra(EXTRA_XRAY_VERSION, current.xrayVersion)
                if (includeLogs && current.logs.isNotEmpty()) {
                    putStringArrayListExtra(EXTRA_LOGS_SNAPSHOT, ArrayList(current.logs))
                }
            }
            runCatching { ctx.sendBroadcast(intent) }
        }
        if (notifyTile) {
            val tileCtx = mainAppContext ?: vpnAppContext
            if (tileCtx != null) {
                XrayQuickSettingsTileService.requestTileUpdate(tileCtx)
            }
        }
    }

    fun setXrayVersion(version: String) {
        _state.update { it.copy(xrayVersion = version) }
        broadcastCurrentState(notifyTile = false)
    }

    fun setConnecting(profile: ServerProfileEntity, configJson: String) {
        setConnecting(
            profileId = profile.id,
            profileName = profile.name,
            protocol = profile.protocol,
            endpoint = "${profile.address}:${profile.port}",
            configJson = configJson
        )
    }

    fun setConnecting(
        profileId: Long,
        profileName: String,
        protocol: VpnProtocol,
        endpoint: String,
        configJson: String
    ) {
        _state.update {
            it.copy(
                status = VpnStatus.CONNECTING,
                activeProfileId = profileId,
                activeProfileName = profileName,
                activeProtocol = protocol,
                activeEndpoint = endpoint,
                errorMessage = null,
                rxBytesPerSec = 0L,
                txBytesPerSec = 0L,
                totalRxBytes = 0L,
                totalTxBytes = 0L,
                lastGeneratedConfig = configJson
            )
        }
        appendLog("Connecting to $profileName (${protocol.displayName} $endpoint)...")
        broadcastCurrentState(notifyTile = true)
    }

    fun setConnected() {
        _state.update {
            it.copy(
                status = VpnStatus.CONNECTED,
                startedAtMillis = System.currentTimeMillis(),
                errorMessage = null
            )
        }
        appendLog("Tunnel established and active.")
        broadcastCurrentState(notifyTile = true)
    }

    fun setDisconnecting() {
        _state.update { it.copy(status = VpnStatus.DISCONNECTING) }
        appendLog("Stopping VPN tunnel...")
        broadcastCurrentState(notifyTile = true)
    }

    fun setDisconnected() {
        _state.update {
            it.copy(
                status = VpnStatus.DISCONNECTED,
                startedAtMillis = 0L,
                rxBytesPerSec = 0L,
                txBytesPerSec = 0L,
                errorMessage = null
            )
        }
        appendLog("Tunnel disconnected.")
        broadcastCurrentState(notifyTile = true)
    }

    fun setError(message: String) {
        _state.update { current ->
            val nextNonce = maxOf(current.errorNonce + 1L, System.currentTimeMillis())
            current.copy(
                status = VpnStatus.ERROR,
                startedAtMillis = 0L,
                rxBytesPerSec = 0L,
                txBytesPerSec = 0L,
                errorMessage = message,
                errorNonce = nextNonce
            )
        }
        appendLog("ERROR: $message")
        broadcastCurrentState(notifyTile = true)
    }

    fun clearError() {
        if (_state.value.status != VpnStatus.ERROR) return
        _state.update { current ->
            current.copy(
                status = VpnStatus.DISCONNECTED,
                errorMessage = null
            )
        }
        broadcastCurrentState(notifyTile = true)
    }

    private fun currentErrorNonceFallback(status: VpnStatus): Long {
        return if (status == VpnStatus.ERROR) _state.value.errorNonce else 0L
    }

    fun updateTraffic(rxPerSec: Long, txPerSec: Long, totalRx: Long, totalTx: Long) {
        _state.update {
            it.copy(
                rxBytesPerSec = rxPerSec.coerceAtLeast(0L),
                txBytesPerSec = txPerSec.coerceAtLeast(0L),
                totalRxBytes = totalRx.coerceAtLeast(0L),
                totalTxBytes = totalTx.coerceAtLeast(0L)
            )
        }
        broadcastCurrentState()
    }

    fun updatePreviewConfig(configJson: String) {
        _state.update { it.copy(lastGeneratedConfig = configJson) }
    }

    fun appendLog(message: String) {
        val clean = message.trim()
        if (clean.isEmpty()) return
        val timestamp = synchronized(timeFormat) { timeFormat.format(Date()) }
        val line = "[$timestamp] $clean"
        _state.update { current ->
            val updated = (current.logs + line).takeLast(MAX_LOG_LINES)
            current.copy(logs = updated)
        }
        val vpnCtx = vpnAppContext
        if (vpnCtx != null && mainAppContext == null) {
            val intent = Intent(ACTION_VPN_LOG).apply {
                setPackage(vpnCtx.packageName)
                putExtra(EXTRA_LOG_LINE, line)
            }
            runCatching { vpnCtx.sendBroadcast(intent) }
        }
    }

    fun clearLogs() {
        _state.update { it.copy(logs = emptyList()) }
    }
}
