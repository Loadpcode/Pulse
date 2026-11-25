package com.xraypulse.app.data.parser

import android.util.Base64
import com.xraypulse.app.data.local.ServerProfileEntity
import com.xraypulse.app.model.SecurityType
import com.xraypulse.app.model.TransportType
import com.xraypulse.app.model.VpnProtocol
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ShareUriParser {

    private val embeddedProxyUriRegex =
        Regex("""(?i)(?:vless|vmess|trojan|hysteria2|hy2)://[^\s<>"]+""")

    fun parseBulk(rawInput: String, subscriptionId: Long? = null): List<ServerProfileEntity> {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return emptyList()

        val lines = if (containsSupportedScheme(trimmed)) {
            trimmed.lines()
        } else {
            // Try decoding Base64 subscription payload
            decodeBase64Safe(trimmed)?.lines() ?: trimmed.lines()
        }

        return lines.flatMap { line ->
            val candidate = line.trim()
            if (candidate.isBlank()) {
                emptyList()
            } else {
                val direct = parseSingle(candidate, subscriptionId)
                if (direct != null) {
                    listOf(direct)
                } else if (containsSupportedScheme(candidate)) {
                    embeddedProxyUriRegex.findAll(candidate).mapNotNull { match ->
                        val cleaned = cleanExtractedUri(match.value)
                        parseSingle(cleaned, subscriptionId)
                            ?: parseSingle(match.value, subscriptionId)
                    }.toList()
                } else {
                    emptyList()
                }
            }
        }
    }

    private fun cleanExtractedUri(raw: String): String {
        var s = raw.trimEnd(',', ';', '.', '>', '"', '\'')
        while (s.endsWith(')') && s.count { it == '(' } < s.count { it == ')' }) {
            s = s.dropLast(1)
        }
        while (s.endsWith(']') && s.count { it == '[' } < s.count { it == ']' }) {
            s = s.dropLast(1)
        }
        return s.trimEnd(',', ';', '.', '>', '"', '\'')
    }

    fun parseSingle(uriString: String, subscriptionId: Long? = null): ServerProfileEntity? {
        val raw = uriString.trim()
        return runCatching {
            when {
                raw.startsWith("vless://", ignoreCase = true) -> parseVless(raw, subscriptionId)
                raw.startsWith("vmess://", ignoreCase = true) -> parseVmess(raw, subscriptionId)
                raw.startsWith("trojan://", ignoreCase = true) -> parseTrojan(raw, subscriptionId)
                raw.startsWith("hysteria2://", ignoreCase = true) ||
                    raw.startsWith("hy2://", ignoreCase = true) -> parseHysteria2(raw, subscriptionId)
                else -> null
            }
        }.getOrNull()
    }

    fun toShareUri(profile: ServerProfileEntity): String {
        return when (profile.protocol) {
            VpnProtocol.VLESS -> buildVlessUri(profile)
            VpnProtocol.VMESS -> buildVmessUri(profile)
            VpnProtocol.TROJAN -> buildTrojanUri(profile)
            VpnProtocol.HYSTERIA2 -> buildHysteria2Uri(profile)
        }
    }

    private fun containsSupportedScheme(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("vless://") ||
            lower.contains("vmess://") ||
            lower.contains("trojan://") ||
            lower.contains("hysteria2://") ||
            lower.contains("hy2://")
    }

    private fun parseVless(raw: String, subscriptionId: Long?): ServerProfileEntity? {
        val uri = URI(raw)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val uuid = uri.userInfo?.let { urlDecode(it) }?.takeIf { it.isNotBlank() } ?: return null
        val query = parseQueryParams(uri.rawQuery)
        val name = uri.rawFragment?.let { urlDecode(it) }?.takeIf { it.isNotBlank() } ?: "$host:$port"

        val transport = mapTransport(query["type"])
        val security = mapSecurity(query["security"])
        val allowInsecure = query["allowInsecure"] == "1" || query["allowInsecure"].equals("true", true)

        return ServerProfileEntity(
            name = name,
            protocol = VpnProtocol.VLESS,
            address = host,
            port = port,
            credential = uuid,
            flow = query["flow"].orEmpty(),
            encryption = query["encryption"]?.ifBlank { "none" } ?: "none",
            transport = transport,
            hostHeader = query["host"].orEmpty(),
            path = query["path"].orEmpty(),
            serviceName = query["serviceName"].orEmpty(),
            grpcMode = query["mode"]?.ifBlank { "gun" } ?: "gun",
            headerType = query["headerType"]?.ifBlank { "none" } ?: "none",
            security = security,
            sni = query["sni"].orEmpty(),
            fingerprint = query["fp"]?.ifBlank { "chrome" } ?: "chrome",
            alpn = query["alpn"].orEmpty(),
            publicKey = query["pbk"].orEmpty(),
            shortId = query["sid"].orEmpty(),
            spiderX = query["spx"].orEmpty(),
            allowInsecure = allowInsecure,
            subscriptionId = subscriptionId
        )
    }

    private fun parseVmess(raw: String, subscriptionId: Long?): ServerProfileEntity? {
        val encoded = raw.substringAfter("://").trim()
        val jsonText = decodeBase64Safe(encoded) ?: return null
        val json = JSONObject(jsonText)

        val host = json.optString("add").takeIf { it.isNotBlank() } ?: return null
        val port = json.optString("port").toIntOrNull() ?: json.optInt("port", 443)
        val uuid = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = json.optString("ps").takeIf { it.isNotBlank() } ?: "$host:$port"

        val net = json.optString("net", "tcp")
        val tlsRaw = json.optString("tls", "")
        val security = when {
            tlsRaw.equals("reality", true) -> SecurityType.REALITY
            tlsRaw.equals("tls", true) -> SecurityType.TLS
            else -> SecurityType.NONE
        }

        val transport = mapTransport(net)
        val rawPath = json.optString("path")
        val serviceName = if (transport == TransportType.GRPC) rawPath else ""

        return ServerProfileEntity(
            name = name,
            protocol = VpnProtocol.VMESS,
            address = host,
            port = port,
            credential = uuid,
            alterId = json.optString("aid").toIntOrNull() ?: json.optInt("aid", 0),
            cipher = json.optString("scy", "auto").ifBlank { "auto" },
            transport = transport,
            hostHeader = json.optString("host"),
            path = if (transport == TransportType.GRPC) "" else rawPath,
            serviceName = serviceName,
            headerType = json.optString("type", "none").ifBlank { "none" },
            security = security,
            sni = json.optString("sni").ifBlank { json.optString("host") },
            fingerprint = json.optString("fp", "chrome").ifBlank { "chrome" },
            alpn = json.optString("alpn"),
            allowInsecure = json.optBoolean("allowInsecure", false) ||
                json.optInt("allowInsecure", 0) == 1,
            subscriptionId = subscriptionId
        )
    }

    private fun parseTrojan(raw: String, subscriptionId: Long?): ServerProfileEntity? {
        val uri = URI(raw)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val password = uri.userInfo?.let { urlDecode(it) }?.takeIf { it.isNotBlank() } ?: return null
        val query = parseQueryParams(uri.rawQuery)
        val name = uri.rawFragment?.let { urlDecode(it) }?.takeIf { it.isNotBlank() } ?: "$host:$port"

        val transport = mapTransport(query["type"])
        val security = mapSecurity(query["security"] ?: "tls")
        val allowInsecure = query["allowInsecure"] == "1" || query["allowInsecure"].equals("true", true)

        return ServerProfileEntity(
            name = name,
            protocol = VpnProtocol.TROJAN,
            address = host,
            port = port,
            credential = password,
            transport = transport,
            hostHeader = query["host"].orEmpty(),
            path = query["path"].orEmpty(),
            serviceName = query["serviceName"].orEmpty(),
            grpcMode = query["mode"]?.ifBlank { "gun" } ?: "gun",
            headerType = query["headerType"]?.ifBlank { "none" } ?: "none",
            security = security,
            sni = query["sni"]?.ifBlank { host } ?: host,
            fingerprint = query["fp"]?.ifBlank { "chrome" } ?: "chrome",
            alpn = query["alpn"].orEmpty(),
            publicKey = query["pbk"].orEmpty(),
            shortId = query["sid"].orEmpty(),
            spiderX = query["spx"].orEmpty(),
            allowInsecure = allowInsecure,
            subscriptionId = subscriptionId
        )
    }

    private fun parseHysteria2(raw: String, subscriptionId: Long?): ServerProfileEntity? {
        val normalized = if (raw.startsWith("hy2://", true)) {
            "hysteria2://" + raw.substring(6)
        } else raw
        val uri = URI(normalized)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val auth = uri.userInfo?.let { urlDecode(it) }?.takeIf { it.isNotBlank() } ?: return null
        val query = parseQueryParams(uri.rawQuery)
        val name = uri.rawFragment?.let { urlDecode(it) }?.takeIf { it.isNotBlank() } ?: "$host:$port"

        val insecure = query["insecure"] == "1" ||
            query["insecure"].equals("true", true) ||
            query["allowInsecure"] == "1"

        return ServerProfileEntity(
            name = name,
            protocol = VpnProtocol.HYSTERIA2,
            address = host,
            port = port,
            credential = auth,
            transport = TransportType.QUIC,
            security = SecurityType.TLS,
            sni = query["sni"]?.ifBlank { host } ?: host,
            alpn = query["alpn"]?.ifBlank { "h3" } ?: "h3",
            allowInsecure = insecure,
            upMbps = query["upmbps"]?.toIntOrNull() ?: 50,
            downMbps = query["downmbps"]?.toIntOrNull() ?: 100,
            obfsType = query["obfs"].orEmpty(),
            obfsPassword = query["obfs-password"].orEmpty(),
            portHopping = query["mport"].orEmpty(),
            subscriptionId = subscriptionId
        )
    }

    private fun buildVlessUri(p: ServerProfileEntity): String {
        val params = buildMap {
            put("type", p.transport.value)
            put("security", p.security.value)
            if (p.encryption.isNotBlank()) put("encryption", p.encryption)
            if (p.flow.isNotBlank()) put("flow", p.flow)
            if (p.sni.isNotBlank()) put("sni", p.sni)
            if (p.fingerprint.isNotBlank()) put("fp", p.fingerprint)
            if (p.alpn.isNotBlank()) put("alpn", p.alpn)
            if (p.hostHeader.isNotBlank()) put("host", p.hostHeader)
            if (p.path.isNotBlank()) put("path", p.path)
            if (p.serviceName.isNotBlank()) put("serviceName", p.serviceName)
            if (p.publicKey.isNotBlank()) put("pbk", p.publicKey)
            if (p.shortId.isNotBlank()) put("sid", p.shortId)
            if (p.spiderX.isNotBlank()) put("spx", p.spiderX)
            if (p.allowInsecure) put("allowInsecure", "1")
        }
        return "vless://${urlEncode(p.credential)}@${formatHost(p.address)}:${p.port}?${encodeQuery(params)}#${urlEncode(p.name)}"
    }

    private fun buildVmessUri(p: ServerProfileEntity): String {
        val obj = JSONObject().apply {
            put("v", "2")
            put("ps", p.name)
            put("add", p.address)
            put("port", p.port.toString())
            put("id", p.credential)
            put("aid", p.alterId.toString())
            put("scy", p.cipher)
            put("net", p.transport.value)
            put("type", p.headerType)
            put("host", p.hostHeader)
            put("path", if (p.transport == TransportType.GRPC) p.serviceName else p.path)
            put("tls", if (p.security == SecurityType.NONE) "" else p.security.value)
            put("sni", p.sni)
            put("alpn", p.alpn)
            put("fp", p.fingerprint)
        }
        val b64 = Base64.encodeToString(obj.toString().toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        return "vmess://$b64"
    }

    private fun buildTrojanUri(p: ServerProfileEntity): String {
        val params = buildMap {
            put("type", p.transport.value)
            put("security", p.security.value)
            if (p.sni.isNotBlank()) put("sni", p.sni)
            if (p.fingerprint.isNotBlank()) put("fp", p.fingerprint)
            if (p.alpn.isNotBlank()) put("alpn", p.alpn)
            if (p.hostHeader.isNotBlank()) put("host", p.hostHeader)
            if (p.path.isNotBlank()) put("path", p.path)
            if (p.serviceName.isNotBlank()) put("serviceName", p.serviceName)
            if (p.allowInsecure) put("allowInsecure", "1")
        }
        return "trojan://${urlEncode(p.credential)}@${formatHost(p.address)}:${p.port}?${encodeQuery(params)}#${urlEncode(p.name)}"
    }

    private fun buildHysteria2Uri(p: ServerProfileEntity): String {
        val params = buildMap {
            if (p.sni.isNotBlank()) put("sni", p.sni)
            if (p.allowInsecure) put("insecure", "1")
            if (p.obfsType.isNotBlank()) put("obfs", p.obfsType)
            if (p.obfsPassword.isNotBlank()) put("obfs-password", p.obfsPassword)
            if (p.portHopping.isNotBlank()) put("mport", p.portHopping)
            if (p.upMbps > 0) put("upmbps", p.upMbps.toString())
            if (p.downMbps > 0) put("downmbps", p.downMbps.toString())
        }
        return "hysteria2://${urlEncode(p.credential)}@${formatHost(p.address)}:${p.port}?${encodeQuery(params)}#${urlEncode(p.name)}"
    }

    private fun mapTransport(value: String?): TransportType {
        return when (value?.lowercase()) {
            "ws", "websocket" -> TransportType.WS
            "grpc" -> TransportType.GRPC
            "httpupgrade" -> TransportType.HTTPUPGRADE
            "xhttp", "splithttp" -> TransportType.XHTTP
            "quic" -> TransportType.QUIC
            else -> TransportType.TCP
        }
    }

    private fun mapSecurity(value: String?): SecurityType {
        return when (value?.lowercase()) {
            "reality" -> SecurityType.REALITY
            "none", "" -> SecurityType.NONE
            else -> SecurityType.TLS
        }
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&")
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null
                else urlDecode(pair.substring(0, idx)) to urlDecode(pair.substring(idx + 1))
            }.toMap()
    }

    private fun encodeQuery(params: Map<String, String>): String {
        return params.entries.joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }
    }

    private fun formatHost(host: String): String =
        if (host.contains(":") && !host.startsWith("[")) "[$host]" else host

    private fun urlDecode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun urlEncode(value: String): String =
        runCatching { URLEncoder.encode(value, "UTF-8").replace("+", "%20") }.getOrDefault(value)

    private fun decodeBase64Safe(encoded: String): String? {
        val cleaned = encoded.replace('-', '+').replace('_', '/').replace("\n", "").replace("\r", "")
        val padded = cleaned.padEnd((cleaned.length + 3) / 4 * 4, '=')
        return runCatching {
            val bytes = Base64.decode(padded, Base64.DEFAULT)
            String(bytes, StandardCharsets.UTF_8)
        }.getOrNull()
    }
}
