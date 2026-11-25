package com.xraypulse.app

import android.util.Base64
import com.xraypulse.app.data.local.RoutingRuleEntity
import com.xraypulse.app.data.parser.ShareUriParser
import com.xraypulse.app.data.xray.XrayConfigBuilder
import com.xraypulse.app.model.AppSettings
import com.xraypulse.app.model.RoutingPreset
import com.xraypulse.app.model.RuleAction
import com.xraypulse.app.model.RuleType
import com.xraypulse.app.model.SecurityType
import com.xraypulse.app.model.TransportType
import com.xraypulse.app.model.VpnProtocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VpnCoreConfigTests {

    @Test
    fun `parse vless reality vision uri and build xray json`() {
        val uri = "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
            "?type=tcp&security=reality&pbk=PUBLICKEY123&fp=chrome&sni=google.com" +
            "&sid=abcd&spx=%2F&flow=xtls-rprx-vision#Frankfurt-Reality"

        val parsed = ShareUriParser.parseSingle(uri)
        assertNotNull(parsed)
        parsed!!
        assertEquals(VpnProtocol.VLESS, parsed.protocol)
        assertEquals("Frankfurt-Reality", parsed.name)
        assertEquals("example.com", parsed.address)
        assertEquals(443, parsed.port)
        assertEquals(SecurityType.REALITY, parsed.security)
        assertEquals("xtls-rprx-vision", parsed.flow)
        assertEquals("PUBLICKEY123", parsed.publicKey)

        val jsonStr = XrayConfigBuilder.buildFullConfig(
            profile = parsed,
            settings = AppSettings(routingPreset = RoutingPreset.BYPASS_RU, enableMux = true),
            customRules = listOf(
                RoutingRuleEntity(type = RuleType.DOMAIN, pattern = "domain:corp.local", action = RuleAction.DIRECT)
            )
        )
        val json = JSONObject(jsonStr)
        val proxyOutbound = json.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vless", proxyOutbound.getString("protocol"))
        // Vision flow must automatically skip Mux
        assertTrue(!proxyOutbound.has("mux"))
        val reality = proxyOutbound.getJSONObject("streamSettings").getJSONObject("realitySettings")
        assertEquals("PUBLICKEY123", reality.getString("publicKey"))
        assertEquals("abcd", reality.getString("shortId"))
    }

    @Test
    fun `parse vmess base64 uri and round-trip`() {
        val vmessJson = """
            {"v":"2","ps":"Helsinki WS","add":"vmess.example.org","port":"8443","id":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee","aid":"0","scy":"aes-128-gcm","net":"ws","type":"none","host":"cdn.example.org","path":"/ws","tls":"tls","sni":"cdn.example.org","fp":"firefox"}
        """.trimIndent()
        val encoded = Base64.encodeToString(vmessJson.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val parsed = ShareUriParser.parseSingle("vmess://$encoded")
        assertNotNull(parsed)
        parsed!!
        assertEquals(VpnProtocol.VMESS, parsed.protocol)
        assertEquals("Helsinki WS", parsed.name)
        assertEquals(TransportType.WS, parsed.transport)
        assertEquals(SecurityType.TLS, parsed.security)
        assertEquals("/ws", parsed.path)

        val rebuilt = ShareUriParser.parseSingle(ShareUriParser.toShareUri(parsed))
        assertNotNull(rebuilt)
        assertEquals(parsed.address, rebuilt!!.address)
        assertEquals(parsed.port, rebuilt.port)
        assertEquals(parsed.credential, rebuilt.credential)
    }

    @Test
    fun `parse trojan and hysteria2 uris and build xray hysteria2 outbound`() {
        val trojan = ShareUriParser.parseSingle(
            "trojan://secretPass@tr.example.com:443?type=grpc&security=tls&serviceName=trgrpc&sni=tr.example.com#Trojan-gRPC"
        )
        assertNotNull(trojan)
        assertEquals(VpnProtocol.TROJAN, trojan!!.protocol)
        assertEquals(TransportType.GRPC, trojan.transport)
        assertEquals("trgrpc", trojan.serviceName)

        val hy2 = ShareUriParser.parseSingle(
            "hy2://hy2secret@hy.example.net:443?sni=hy.example.net&insecure=1&obfs=salamander&obfs-password=obfspass&mport=20000-30000&upmbps=30&downmbps=150#Hysteria2-Node"
        )
        assertNotNull(hy2)
        hy2!!
        assertEquals(VpnProtocol.HYSTERIA2, hy2.protocol)
        assertEquals("hy2secret", hy2.credential)
        assertEquals("obfspass", hy2.obfsPassword)
        assertEquals("20000-30000", hy2.portHopping)
        assertTrue(hy2.allowInsecure)

        val config = JSONObject(XrayConfigBuilder.buildFullConfig(hy2, AppSettings(), emptyList()))
        val outbound = config.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("hysteria", outbound.getString("protocol"))
        assertEquals(2, outbound.getJSONObject("settings").getInt("version"))
        val stream = outbound.getJSONObject("streamSettings")
        assertEquals("hysteria", stream.getString("network"))
        assertEquals("hy2secret", stream.getJSONObject("hysteriaSettings").getString("auth"))
        assertEquals("salamander", stream.getJSONObject("finalmask").getJSONArray("udp").getJSONObject(0).getString("type"))
    }

    @Test
    fun `traffic settings wire fragment mux lan bypass and ipv6 into xray config`() {
        val vlessWs = ShareUriParser.parseSingle(
            "vless://11111111-2222-3333-4444-555555555555@ws.example.com:443?type=ws&security=tls&path=%2Fws#WS-Node"
        )!!
        val settings = AppSettings(
            enableFragment = true,
            fragmentPackets = "1-3",
            fragmentLength = "16-32",
            fragmentInterval = "5-15",
            enableMux = true,
            muxConcurrency = 12,
            muxXudpConcurrency = 8,
            bypassLan = false,
            enableIpv6 = false
        )

        val fullJson = JSONObject(XrayConfigBuilder.buildFullConfig(vlessWs, settings, emptyList()))
        assertEquals("UseIPv4", fullJson.getJSONObject("dns").getString("queryStrategy"))

        val outbounds = fullJson.getJSONArray("outbounds")
        val proxy = outbounds.getJSONObject(0)
        assertEquals("fragment", proxy.getJSONObject("streamSettings").getJSONObject("sockopt").getString("dialerProxy"))
        assertEquals(12, proxy.getJSONObject("mux").getInt("concurrency"))
        assertEquals(8, proxy.getJSONObject("mux").getInt("xudpConcurrency"))

        var foundFragment = false
        for (i in 0 until outbounds.length()) {
            val item = outbounds.getJSONObject(i)
            if (item.getString("tag") == "fragment") {
                foundFragment = true
                val frag = item.getJSONObject("settings").getJSONObject("fragment")
                assertEquals("1-3", frag.getString("packets"))
                assertEquals("16-32", frag.getString("length"))
                assertEquals("5-15", frag.getString("interval"))
            }
        }
        assertTrue(foundFragment)

        val latencyJson = JSONObject(XrayConfigBuilder.buildLatencyTestConfig(vlessWs, settings))
        assertEquals(2, latencyJson.getJSONArray("outbounds").length())
    }

    @Test
    fun `hev socks5 tunnel yaml maintains valid column zero root keys when ipv6 is enabled`() {
        val payload = com.xraypulse.app.vpn.VpnLaunchPayload(
            profileId = 1L,
            profileName = "Test",
            protocol = VpnProtocol.VLESS,
            endpoint = "example.com:443",
            configJson = "{}",
            primaryDns = "1.1.1.1",
            secondaryDns = "8.8.8.8",
            socksPort = 10808,
            mtu = 1500,
            enableIpv6 = true,
            splitTunnelMode = com.xraypulse.app.model.SplitTunnelMode.DISABLED,
            splitTunnelPackages = emptySet()
        )

        val yaml = com.xraypulse.app.vpn.XrayVpnService.buildHevTunnelConfigYaml(payload)
        val lines = yaml.lines()
        assertTrue(lines.contains("tunnel:"))
        assertTrue(lines.contains("  mtu: 1500"))
        assertTrue(lines.contains("  ipv4: 198.18.0.1"))
        assertTrue(lines.contains("  ipv6: 'fd00::1'"))
        assertTrue(lines.contains("socks5:"))
        assertTrue(lines.contains("  port: 10808"))
        assertTrue(lines.contains("  address: 127.0.0.1"))
        assertTrue(lines.contains("misc:"))

        val ipv4OnlyYaml = com.xraypulse.app.vpn.XrayVpnService.buildHevTunnelConfigYaml(
            payload.copy(enableIpv6 = false, socksPort = 0)
        )
        val ipv4Lines = ipv4OnlyYaml.lines()
        assertTrue(ipv4Lines.none { it.contains("ipv6:") })
        assertTrue(ipv4Lines.contains("  port: 1024"))
        assertTrue(ipv4Lines.contains("  address: 127.0.0.1"))
    }

    @Test
    fun `vpn launch store respects expected profile id`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val profile = com.xraypulse.app.data.local.ServerProfileEntity(
            id = 42L,
            name = "Main Node",
            protocol = VpnProtocol.VLESS,
            address = "vpn.example.org",
            port = 443,
            credential = "11111111-2222-3333-4444-555555555555"
        )
        com.xraypulse.app.vpn.VpnLaunchStore.write(context, profile, AppSettings(), "{}")

        assertNotNull(com.xraypulse.app.vpn.VpnLaunchStore.read(context, expectedProfileId = 42L))
        assertNotNull(com.xraypulse.app.vpn.VpnLaunchStore.read(context, expectedProfileId = -1L))
        assertEquals(null, com.xraypulse.app.vpn.VpnLaunchStore.read(context, expectedProfileId = 99L))
    }
}
