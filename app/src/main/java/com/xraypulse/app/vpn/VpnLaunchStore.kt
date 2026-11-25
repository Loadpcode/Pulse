package com.xraypulse.app.vpn

import android.content.Context
import com.xraypulse.app.data.local.ServerProfileEntity
import com.xraypulse.app.model.AppSettings
import com.xraypulse.app.model.SplitTunnelMode
import com.xraypulse.app.model.VpnProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class VpnLaunchPayload(
    val profileId: Long,
    val profileName: String,
    val protocol: VpnProtocol,
    val endpoint: String,
    val configJson: String,
    val primaryDns: String,
    val secondaryDns: String,
    val socksPort: Int,
    val mtu: Int = 1500,
    val enableIpv6: Boolean,
    val splitTunnelMode: SplitTunnelMode,
    val splitTunnelPackages: Set<String>
)

object VpnLaunchStore {
    private const val FILE_NAME = "vpn_launch_payload.json"

    @Synchronized
    fun write(
        context: Context,
        profile: ServerProfileEntity,
        settings: AppSettings,
        configJson: String
    ) {
        val json = JSONObject().apply {
            put("profileId", profile.id)
            put("profileName", profile.name)
            put("protocol", profile.protocol.name)
            put("endpoint", "${profile.address}:${profile.port}")
            put("configJson", configJson)
            put("primaryDns", settings.primaryDns)
            put("secondaryDns", settings.secondaryDns)
            put("socksPort", settings.socksPort)
            put("mtu", settings.mtu)
            put("enableIpv6", settings.enableIpv6)
            put("splitTunnelMode", settings.splitTunnelMode.name)
            put("splitTunnelPackages", JSONArray(settings.splitTunnelPackages.toList()))
        }
        val target = File(context.filesDir, FILE_NAME)
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(target)) {
            target.writeText(json.toString())
            tmp.delete()
        }
    }

    @Synchronized
    fun read(context: Context, expectedProfileId: Long = -1L): VpnLaunchPayload? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val profileId = json.getLong("profileId")
            if (expectedProfileId > 0L && profileId != expectedProfileId) {
                return null
            }
            val packagesArr = json.optJSONArray("splitTunnelPackages")
            val packages = buildSet {
                if (packagesArr != null) {
                    for (i in 0 until packagesArr.length()) {
                        packagesArr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }
            VpnLaunchPayload(
                profileId = profileId,
                profileName = json.optString("profileName", "Server"),
                protocol = runCatching { VpnProtocol.valueOf(json.getString("protocol")) }
                    .getOrDefault(VpnProtocol.VLESS),
                endpoint = json.optString("endpoint", ""),
                configJson = json.getString("configJson"),
                primaryDns = json.optString("primaryDns", "1.1.1.1"),
                secondaryDns = json.optString("secondaryDns", "8.8.8.8"),
                socksPort = json.optInt("socksPort", 10808),
                mtu = json.optInt("mtu", 1500).coerceIn(1200, 9000),
                enableIpv6 = json.optBoolean("enableIpv6", false),
                splitTunnelMode = runCatching { SplitTunnelMode.valueOf(json.getString("splitTunnelMode")) }
                    .getOrDefault(SplitTunnelMode.DISABLED),
                splitTunnelPackages = packages
            )
        }.getOrNull()
    }
}
