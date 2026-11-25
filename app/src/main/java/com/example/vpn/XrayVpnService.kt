package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.XrayPulseApp
import com.example.model.SplitTunnelMode
import com.example.model.VpnStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.amnezia.awg.hevtunnel.TProxyService
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

class XrayVpnService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tunnelMutex = Mutex()
    private val tunnelGeneration = AtomicLong(0L)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var statsJob: Job? = null
    private var hevJob: Job? = null
    @Volatile
    private var intentionalStop = false
    private val queryBinder = Binder()

    private val coreBridge: XrayCoreBridge
        get() = (application as XrayPulseApp).vpnCoreBridge

    override fun onCreate() {
        super.onCreate()
        VpnSessionManager.initVpnProcess(this)
        createNotificationChannel()
    }

    private val pingMessenger: Messenger by lazy {
        Messenger(
            object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    if (msg.what == MSG_PING_REQUEST) {
                        val requestId = msg.arg1
                        val replyTo = msg.replyTo ?: return
                        val data = msg.data
                        val configJson = data.getString(EXTRA_PING_CONFIG).orEmpty()
                        val host = data.getString(EXTRA_PING_HOST).orEmpty()
                        val port = data.getInt(EXTRA_PING_PORT, 443)
                        serviceScope.launch {
                            val latency = if (configJson.isNotBlank() && host.isNotBlank() && port > 0) {
                                coreBridge.measureOutboundDelay(configJson, host, port)
                            } else {
                                -1L
                            }
                            val response = Message.obtain(null, MSG_PING_RESULT, requestId, 0).apply {
                                this.data = Bundle().apply {
                                    putLong(EXTRA_PING_LATENCY_MS, latency)
                                }
                            }
                            runCatching { replyTo.send(response) }
                        }
                    }
                }
            }
        )
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == ACTION_PING_SERVICE) {
            return pingMessenger.binder
        }
        if (intent?.action == ACTION_QUERY_STATE) {
            VpnSessionManager.broadcastCurrentState(includeLogs = true)
            return queryBinder
        }
        return super.onBind(intent)
    }

    override fun onRebind(intent: Intent?) {
        if (intent?.action == ACTION_QUERY_STATE) {
            VpnSessionManager.broadcastCurrentState(includeLogs = true)
        }
        super.onRebind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpnTunnel()
                return START_NOT_STICKY
            }
            else -> {
                val requestedProfileId = intent?.getLongExtra(EXTRA_PROFILE_ID, -1L) ?: -1L
                startVpnTunnel(requestedProfileId)
                return START_STICKY
            }
        }
    }

    override fun onRevoke() {
        stopVpnTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        tearDownSync()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startVpnTunnel(requestedProfileId: Long) {
        val myGeneration = tunnelGeneration.incrementAndGet()
        serviceScope.launch {
            tunnelMutex.withLock {
                if (myGeneration != tunnelGeneration.get()) return@withLock

                val payload = VpnLaunchStore.read(this@XrayVpnService, requestedProfileId)
                if (payload == null) {
                    VpnSessionManager.setError(getString(R.string.error_no_profile_selected))
                    stopSelf()
                    return@withLock
                }

                VpnSessionManager.setConnecting(
                    profileId = payload.profileId,
                    profileName = payload.profileName,
                    protocol = payload.protocol,
                    endpoint = payload.endpoint,
                    configJson = payload.configJson
                )

                startForegroundWithType(
                    buildNotification(
                        title = getString(R.string.notif_connecting_title),
                        subtitle = "${payload.profileName} (${payload.protocol.displayName})"
                    )
                )

                // Tear down any previous run cleanly before starting newly selected server
                intentionalStop = false
                tearDownTunAndEngines()
                previousHevJobJoin()

                if (myGeneration != tunnelGeneration.get()) return@withLock

                val pfd = establishTun(payload)
                if (pfd == null) {
                    VpnSessionManager.setError("Failed to establish Android TUN interface.")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@withLock
                }
                vpnInterface = pfd

                // Xray runs purely on local SOCKS5/HTTP inbounds (tunFd = 0); hev-socks5-tunnel exclusively owns pfd.fd.
                val coreResult = coreBridge.startCore(payload.configJson, 0)
                if (coreResult.isFailure) {
                    val msg = coreResult.exceptionOrNull()?.message ?: "Xray-core startup failed"
                    VpnSessionManager.setError(msg)
                    tearDownTunAndEngines()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@withLock
                }

                val effectiveSocksPort = payload.socksPort.coerceIn(1024, 65535)
                waitForLocalSocksPort(effectiveSocksPort)

                if (myGeneration != tunnelGeneration.get()) {
                    tearDownTunAndEngines()
                    return@withLock
                }

                val hevConfigFile = writeHevTunnelConfig(payload)
                hevJob = serviceScope.launch {
                    runCatching {
                        VpnSessionManager.appendLog(
                            "Starting hev-socks5-tunnel (fd=${pfd.fd}, socks=127.0.0.1:$effectiveSocksPort, ipv6=${payload.enableIpv6})"
                        )
                        TProxyService.TProxyStartService(hevConfigFile.absolutePath, pfd.fd)
                    }.onFailure { err ->
                        VpnSessionManager.appendLog("hev-socks5-tunnel stopped: ${err.message}")
                        if (!intentionalStop && vpnInterface != null && myGeneration == tunnelGeneration.get()) {
                            VpnSessionManager.setError(err.message ?: "Tunnel engine failed")
                            tearDownTunAndEngines()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }

                VpnSessionManager.setConnected()
                val notifManager = getSystemService(NotificationManager::class.java)
                notifManager.notify(
                    NOTIFICATION_ID,
                    buildNotification(
                        title = getString(R.string.notif_connected_title),
                        subtitle = "${payload.profileName} • ${payload.protocol.displayName}"
                    )
                )

                startStatsPolling()
            }
        }
    }

    private fun establishTun(payload: VpnLaunchPayload): ParcelFileDescriptor? {
        return runCatching {
            val effectiveMtu = payload.mtu.coerceIn(1200, 9000)
            val builder = Builder()
                .setSession("XRay Pulse")
                .setMtu(effectiveMtu)
                .addAddress("198.18.0.1", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(payload.primaryDns.ifBlank { "1.1.1.1" })

            if (payload.secondaryDns.isNotBlank()) {
                builder.addDnsServer(payload.secondaryDns)
            }

            if (payload.enableIpv6) {
                builder.addAddress("fd00::1", 126)
                builder.addRoute("::", 0)
            }

            when (payload.splitTunnelMode) {
                SplitTunnelMode.DISABLED -> {
                    runCatching { builder.addDisallowedApplication(packageName) }
                }
                SplitTunnelMode.BYPASS_SELECTED -> {
                    runCatching { builder.addDisallowedApplication(packageName) }
                    payload.splitTunnelPackages.forEach { pkg ->
                        if (pkg != packageName) {
                            runCatching { builder.addDisallowedApplication(pkg) }
                        }
                    }
                }
                SplitTunnelMode.ONLY_SELECTED -> {
                    val allowed = payload.splitTunnelPackages.filter { it != packageName }
                    if (allowed.isEmpty()) {
                        runCatching { builder.addDisallowedApplication(packageName) }
                    } else {
                        allowed.forEach { pkg ->
                            runCatching { builder.addAllowedApplication(pkg) }
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            builder.establish()
        }.onFailure { err ->
            VpnSessionManager.appendLog("TUN Builder error: ${err.message}")
        }.getOrNull()
    }

    private suspend fun previousHevJobJoin() {
        val job = hevJob ?: return
        withTimeoutOrNull(1500L) { job.join() }
        hevJob = null
    }

    private suspend fun waitForLocalSocksPort(port: Int) {
        val deadline = System.currentTimeMillis() + 1500L
        while (System.currentTimeMillis() < deadline) {
            val ready = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 150)
                }
                true
            }.getOrDefault(false)
            if (ready) return
            delay(30L)
        }
    }

    private fun writeHevTunnelConfig(payload: VpnLaunchPayload): File {
        val file = File(cacheDir, "hev-socks5-tunnel.yml")
        file.writeText(buildHevTunnelConfigYaml(payload))
        return file
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            var lastSampleAtMs = System.currentTimeMillis()
            var lastRx = 0L
            var lastTx = 0L
            var initializedBaseline = false
            var lastEmittedSpeedNonZero = false
            while (isActive) {
                delay(1000L)
                val hevStats = runCatching { TProxyService.TProxyGetStats() }.getOrNull()
                if (hevStats != null && hevStats.size >= 4) {
                    // TProxyGetStats returns monotonic cumulative session counters:
                    // [txPackets, txBytes, rxPackets, rxBytes]
                    val currTx = hevStats[1].coerceAtLeast(0L)
                    val currRx = hevStats[3].coerceAtLeast(0L)
                    val nowMs = System.currentTimeMillis()
                    val elapsedMs = (nowMs - lastSampleAtMs).coerceAtLeast(1L)
                    lastSampleAtMs = nowMs

                    val wasInitialized = initializedBaseline
                    val rxPerSec: Long
                    val txPerSec: Long
                    if (!wasInitialized || currRx < lastRx || currTx < lastTx) {
                        initializedBaseline = true
                        rxPerSec = 0L
                        txPerSec = 0L
                    } else {
                        rxPerSec = ((currRx - lastRx) * 1000L) / elapsedMs
                        txPerSec = ((currTx - lastTx) * 1000L) / elapsedMs
                    }
                    val totalsChanged = currRx != lastRx || currTx != lastTx
                    lastRx = currRx
                    lastTx = currTx
                    if (!wasInitialized || totalsChanged || lastEmittedSpeedNonZero) {
                        VpnSessionManager.updateTraffic(
                            rxPerSec = rxPerSec,
                            txPerSec = txPerSec,
                            totalRx = currRx,
                            totalTx = currTx
                        )
                        lastEmittedSpeedNonZero = rxPerSec > 0L || txPerSec > 0L
                    }
                }
            }
        }
    }

    private fun stopVpnTunnel() {
        val myGeneration = tunnelGeneration.incrementAndGet()
        intentionalStop = true
        VpnSessionManager.setDisconnecting()
        serviceScope.launch {
            tunnelMutex.withLock {
                if (myGeneration != tunnelGeneration.get()) return@withLock
                tearDownTunAndEngines()
                previousHevJobJoin()
                VpnSessionManager.setDisconnected()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun tearDownSync() {
        intentionalStop = true
        val currentStatus = VpnSessionManager.state.value.status
        val hadActiveSession = currentStatus != VpnStatus.ERROR && (
            vpnInterface != null ||
                coreBridge.isRunning() ||
                currentStatus != VpnStatus.DISCONNECTED
            )
        tearDownTunAndEngines()
        if (hadActiveSession) {
            VpnSessionManager.setDisconnected()
        }
    }

    private fun tearDownTunAndEngines() {
        statsJob?.cancel()
        statsJob = null
        runCatching { TProxyService.TProxyStopService() }
        coreBridge.stopCore()
        runCatching { vpnInterface?.close() }
        vpnInterface = null
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(title: String, subtitle: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, XrayVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_xr)
            .setColor(0xFF4F51FA.toInt())
            .setContentTitle(title)
            .setContentText(subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.action_disconnect), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.example.vpn.START"
        const val ACTION_STOP = "com.example.vpn.STOP"
        const val ACTION_QUERY_STATE = "com.example.vpn.QUERY_STATE"
        const val ACTION_PING_SERVICE = "com.example.vpn.PING_SERVICE"
        const val MSG_PING_REQUEST = 1001
        const val MSG_PING_RESULT = 1002
        const val EXTRA_PING_CONFIG = "extra_ping_config"
        const val EXTRA_PING_HOST = "extra_ping_host"
        const val EXTRA_PING_PORT = "extra_ping_port"
        const val EXTRA_PING_LATENCY_MS = "extra_ping_latency_ms"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        private const val CHANNEL_ID = "xray_pulse_vpn_channel"
        private const val NOTIFICATION_ID = 4201

        internal fun buildHevTunnelConfigYaml(payload: VpnLaunchPayload): String {
            val effectiveMtu = payload.mtu.coerceIn(1200, 9000)
            val effectiveSocksPort = payload.socksPort.coerceIn(1024, 65535)
            return buildString {
                appendLine("tunnel:")
                appendLine("  mtu: $effectiveMtu")
                appendLine("  ipv4: 198.18.0.1")
                if (payload.enableIpv6) {
                    appendLine("  ipv6: 'fd00::1'")
                }
                appendLine("socks5:")
                appendLine("  port: $effectiveSocksPort")
                appendLine("  address: 127.0.0.1")
                appendLine("  udp: 'udp'")
                appendLine("misc:")
                appendLine("  task-stack-size: 81920")
            }
        }

        fun start(context: Context, profileId: Long) {
            val intent = Intent(context, XrayVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROFILE_ID, profileId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, XrayVpnService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                context.startService(intent)
            }.onFailure {
                VpnSessionManager.setDisconnected()
            }
        }
    }
}
