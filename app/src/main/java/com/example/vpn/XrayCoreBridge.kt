package com.example.vpn

import android.content.Context
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

class XrayCoreBridge(private val context: Context) {

    @Volatile
    private var initialized = false

    @Volatile
    private var controller: CoreController? = null

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            runCatching {
                val assetDir = File(context.filesDir, "xray_assets").apply { mkdirs() }
                extractAssetIfMissing("geoip.dat", File(assetDir, "geoip.dat"))
                extractAssetIfMissing("geosite.dat", File(assetDir, "geosite.dat"))
                Libv2ray.initCoreEnv(assetDir.absolutePath, "")
                val rawVersion = runCatching { Libv2ray.checkVersionX() }.getOrDefault("")
                val cleanVersion = rawVersion.substringBefore("(").trim()
                    .takeIf { it.isNotBlank() }
                    ?.let {
                        if (it.startsWith("Xray ", ignoreCase = true)) {
                            "xray-core " + it.substring(5).trim()
                        } else {
                            it
                        }
                    } ?: "xray-core 26.11.9"
                VpnSessionManager.setXrayVersion(cleanVersion)
                initialized = true
                VpnSessionManager.appendLog("Initialized $cleanVersion")
            }.onFailure { err ->
                VpnSessionManager.appendLog("Core init warning: ${err.message}")
            }
        }
    }

    fun startCore(configJson: String, tunFd: Int = 0): Result<Unit> {
        ensureInitialized()
        return runCatching {
            stopCore()
            val callback = object : CoreCallbackHandler {
                override fun startup(): Long {
                    VpnSessionManager.appendLog("Xray-core loop started")
                    return 0L
                }

                override fun shutdown(): Long {
                    VpnSessionManager.appendLog("Xray-core loop shutdown")
                    return 0L
                }

                override fun onEmitStatus(status: Long, message: String?): Long {
                    if (!message.isNullOrBlank()) {
                        VpnSessionManager.appendLog("[core:$status] $message")
                    }
                    return 0L
                }
            }
            val core = Libv2ray.newCoreController(callback)
            controller = core
            core.startLoop(configJson, tunFd)
        }
    }

    fun stopCore() {
        runCatching {
            controller?.let { core ->
                if (core.isRunning) {
                    core.stopLoop()
                }
            }
        }.onFailure { err ->
            VpnSessionManager.appendLog("Core stop warning: ${err.message}")
        }
        controller = null
    }

    fun isRunning(): Boolean = runCatching { controller?.isRunning == true }.getOrDefault(false)

    fun measureOutboundDelay(configJson: String, host: String, port: Int, testUrl: String = DEFAULT_TEST_URL): Long {
        ensureInitialized()
        val xrayDelay = runCatching {
            Libv2ray.measureOutboundDelay(configJson, testUrl)
        }.getOrDefault(-1L)
        if (xrayDelay > 0L) return xrayDelay

        // Fallback TCP handshake latency if outbound real-delay test fails or times out
        return measureTcpPing(host, port)
    }

    fun queryOutboundTraffic(): Pair<Long, Long>? {
        val statsRaw = runCatching { controller?.queryAllOutboundTrafficStats() }.getOrNull()
        if (statsRaw.isNullOrBlank()) return null
        var uplink = 0L
        var downlink = 0L
        statsRaw.split(";").forEach { entry ->
            val parts = entry.split(",")
            if (parts.size == 3 && parts[0] == "proxy") {
                val value = parts[2].toLongOrNull() ?: 0L
                if (parts[1] == "uplink") uplink += value
                if (parts[1] == "downlink") downlink += value
            }
        }
        return downlink to uplink
    }

    private fun measureTcpPing(host: String, port: Int): Long {
        val start = System.currentTimeMillis()
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3000)
            }
            (System.currentTimeMillis() - start).coerceAtLeast(1L)
        }.getOrDefault(-1L)
    }

    private fun extractAssetIfMissing(assetName: String, target: File) {
        if (target.exists() && target.length() > 0L) return
        runCatching {
            context.assets.open(assetName).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    companion object {
        const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"
    }
}
