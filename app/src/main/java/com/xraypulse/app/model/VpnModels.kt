package com.xraypulse.app.model

import android.graphics.drawable.Drawable
import kotlinx.serialization.Serializable

enum class VpnProtocol(val displayName: String, val scheme: String) {
    VLESS("VLESS", "vless"),
    VMESS("VMess", "vmess"),
    HYSTERIA2("Hysteria2", "hysteria2"),
    TROJAN("Trojan", "trojan")
}

enum class TransportType(val value: String, val displayName: String) {
    TCP("tcp", "TCP / RAW"),
    WS("ws", "WebSocket"),
    GRPC("grpc", "gRPC"),
    HTTPUPGRADE("httpupgrade", "HTTPUpgrade"),
    XHTTP("xhttp", "XHTTP"),
    QUIC("quic", "QUIC")
}

enum class SecurityType(val value: String, val displayName: String) {
    NONE("none", "None"),
    TLS("tls", "TLS"),
    REALITY("reality", "Reality")
}

enum class RoutingPreset {
    GLOBAL,
    BYPASS_LAN,
    BYPASS_RU,
    CUSTOM
}

enum class SplitTunnelMode {
    DISABLED,
    BYPASS_SELECTED,
    ONLY_SELECTED
}

enum class RuleType {
    DOMAIN,
    IP_CIDR
}

enum class RuleAction(val outboundTag: String) {
    PROXY("proxy"),
    DIRECT("direct"),
    BLOCK("block")
}

enum class AppThemeMode {
    DARK,
    SYSTEM,
    LIGHT
}

enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    RU("ru"),
    EN("en")
}

enum class ConnectButtonShape {
    SQUARE,
    ROUNDED_SQUARE,
    CIRCLE,
    SQUIRCLE,
    COOKIE_4,
    COOKIE_6,
    COOKIE_9,
    ARCH,
    PILL,
    OVAL
}

@Serializable
data class ButtonShapeParams(
    val squareSide: Float = 0.90f,
    val roundedSquareSide: Float = 0.90f,
    val roundedSquareRadius: Float = 0.22f,
    val circleRadius: Float = 0.46f,
    val squircleRadius: Float = 0.46f,
    val squirclePower: Float = 4.0f,
    val cookie4Radius: Float = 0.48f,
    val cookie4Depth: Float = 0.13f,
    val cookie6Radius: Float = 0.48f,
    val cookie6Depth: Float = 0.105f,
    val cookie9Radius: Float = 0.48f,
    val cookie9Depth: Float = 0.085f,
    val archWidth: Float = 0.84f,
    val archHeight: Float = 0.48f,
    val pillWidth: Float = 0.94f,
    val pillHeight: Float = 0.56f,
    val ovalRadiusX: Float = 0.47f,
    val ovalRadiusY: Float = 0.31f
) {
    fun coerced(): ButtonShapeParams {
        val safeSqSide = squareSide.coerceIn(0.64f, 0.96f)
        val safeRSqSide = roundedSquareSide.coerceIn(0.64f, 0.96f)
        val safeRSqRadius = roundedSquareRadius.coerceIn(0.04f, safeRSqSide * 0.49f)
        val safeC4R = cookie4Radius.coerceIn(0.34f, 0.49f)
        val safeC6R = cookie6Radius.coerceIn(0.34f, 0.49f)
        val safeC9R = cookie9Radius.coerceIn(0.34f, 0.49f)
        val safeArchW = archWidth.coerceIn(0.56f, 0.94f)
        val safeArchH = archHeight.coerceIn(0.18f, (0.96f - safeArchW * 0.5f).coerceAtLeast(0.18f))
        val safePillW = pillWidth.coerceIn(0.62f, 0.96f)
        val safePillH = pillHeight.coerceIn(0.34f, minOf(0.78f, safePillW))
        return copy(
            squareSide = safeSqSide,
            roundedSquareSide = safeRSqSide,
            roundedSquareRadius = safeRSqRadius,
            circleRadius = circleRadius.coerceIn(0.32f, 0.49f),
            squircleRadius = squircleRadius.coerceIn(0.32f, 0.49f),
            squirclePower = squirclePower.coerceIn(2.2f, 8.0f),
            cookie4Radius = safeC4R,
            cookie4Depth = cookie4Depth.coerceIn(0.02f, safeC4R * 0.65f),
            cookie6Radius = safeC6R,
            cookie6Depth = cookie6Depth.coerceIn(0.02f, safeC6R * 0.55f),
            cookie9Radius = safeC9R,
            cookie9Depth = cookie9Depth.coerceIn(0.01f, safeC9R * 0.45f),
            archWidth = safeArchW,
            archHeight = safeArchH,
            pillWidth = safePillW,
            pillHeight = safePillH,
            ovalRadiusX = ovalRadiusX.coerceIn(0.26f, 0.49f),
            ovalRadiusY = ovalRadiusY.coerceIn(0.20f, 0.46f)
        )
    }

    fun hasCustomParams(shape: ConnectButtonShape): Boolean {
        val d = ButtonShapeParams()
        return when (shape) {
            ConnectButtonShape.SQUARE -> squareSide != d.squareSide
            ConnectButtonShape.ROUNDED_SQUARE ->
                roundedSquareSide != d.roundedSquareSide || roundedSquareRadius != d.roundedSquareRadius
            ConnectButtonShape.CIRCLE -> circleRadius != d.circleRadius
            ConnectButtonShape.SQUIRCLE ->
                squircleRadius != d.squircleRadius || squirclePower != d.squirclePower
            ConnectButtonShape.COOKIE_4 ->
                cookie4Radius != d.cookie4Radius || cookie4Depth != d.cookie4Depth
            ConnectButtonShape.COOKIE_6 ->
                cookie6Radius != d.cookie6Radius || cookie6Depth != d.cookie6Depth
            ConnectButtonShape.COOKIE_9 ->
                cookie9Radius != d.cookie9Radius || cookie9Depth != d.cookie9Depth
            ConnectButtonShape.ARCH -> archWidth != d.archWidth || archHeight != d.archHeight
            ConnectButtonShape.PILL -> pillWidth != d.pillWidth || pillHeight != d.pillHeight
            ConnectButtonShape.OVAL -> ovalRadiusX != d.ovalRadiusX || ovalRadiusY != d.ovalRadiusY
        }
    }

    fun resetForShape(shape: ConnectButtonShape): ButtonShapeParams {
        val d = ButtonShapeParams()
        return when (shape) {
            ConnectButtonShape.SQUARE -> copy(squareSide = d.squareSide)
            ConnectButtonShape.ROUNDED_SQUARE -> copy(
                roundedSquareSide = d.roundedSquareSide,
                roundedSquareRadius = d.roundedSquareRadius
            )
            ConnectButtonShape.CIRCLE -> copy(circleRadius = d.circleRadius)
            ConnectButtonShape.SQUIRCLE -> copy(
                squircleRadius = d.squircleRadius,
                squirclePower = d.squirclePower
            )
            ConnectButtonShape.COOKIE_4 -> copy(
                cookie4Radius = d.cookie4Radius,
                cookie4Depth = d.cookie4Depth
            )
            ConnectButtonShape.COOKIE_6 -> copy(
                cookie6Radius = d.cookie6Radius,
                cookie6Depth = d.cookie6Depth
            )
            ConnectButtonShape.COOKIE_9 -> copy(
                cookie9Radius = d.cookie9Radius,
                cookie9Depth = d.cookie9Depth
            )
            ConnectButtonShape.ARCH -> copy(archWidth = d.archWidth, archHeight = d.archHeight)
            ConnectButtonShape.PILL -> copy(pillWidth = d.pillWidth, pillHeight = d.pillHeight)
            ConnectButtonShape.OVAL -> copy(ovalRadiusX = d.ovalRadiusX, ovalRadiusY = d.ovalRadiusY)
        }
    }
}

enum class VpnStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

data class AppSettings(
    val selectedProfileId: Long? = null,
    val lastConnectedProfileId: Long? = null,
    val routingPreset: RoutingPreset = RoutingPreset.BYPASS_LAN,
    val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.DISABLED,
    val splitTunnelPackages: Set<String> = emptySet(),
    val primaryDns: String = "1.1.1.1",
    val secondaryDns: String = "8.8.8.8",
    val socksPort: Int = 10808,
    val httpPort: Int = 10809,
    val enableMux: Boolean = false,
    val muxConcurrency: Int = 8,
    val muxXudpConcurrency: Int = 8,
    val enableFragment: Boolean = false,
    val fragmentPackets: String = "tlshello",
    val fragmentLength: String = "10-20",
    val fragmentInterval: String = "10-20",
    val bypassLan: Boolean = true,
    val mtu: Int = 1500,
    val enableIpv6: Boolean = false,
    val allowInsecureGlobal: Boolean = false,
    val autoUpdateSubscriptions: Boolean = false,
    val autoUpdateOnLaunch: Boolean = false,
    val autoUpdateBySchedule: Boolean = true,
    val autoUpdateIntervalHours: Int = 12,
    val marqueeEnabled: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val dynamicColorEnabled: Boolean = true,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val customAccentColorArgb: Long? = null,
    val customBackgroundColorArgb: Long? = null,
    val connectingRotationSpeed: Int = 20,
    val connectedRotationSpeed: Int = 60,
    val connectButtonShape: ConnectButtonShape = ConnectButtonShape.ROUNDED_SQUARE,
    val buttonShapeParams: ButtonShapeParams = ButtonShapeParams()
)

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val icon: Drawable? = null
)

data class VpnSessionState(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val activeProfileId: Long? = null,
    val activeProfileName: String? = null,
    val activeProtocol: VpnProtocol? = null,
    val activeEndpoint: String? = null,
    val startedAtMillis: Long = 0L,
    val rxBytesPerSec: Long = 0L,
    val txBytesPerSec: Long = 0L,
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L,
    val errorMessage: String? = null,
    val errorNonce: Long = 0L,
    val xrayVersion: String = "xray-core 26.11.9",
    val logs: List<String> = emptyList(),
    val lastGeneratedConfig: String = ""
)
