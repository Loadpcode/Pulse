package com.xraypulse.app.data.xray

import com.xraypulse.app.data.local.RoutingRuleEntity
import com.xraypulse.app.data.local.ServerProfileEntity
import com.xraypulse.app.model.AppSettings
import com.xraypulse.app.model.RoutingPreset
import com.xraypulse.app.model.RuleType
import com.xraypulse.app.model.SecurityType
import com.xraypulse.app.model.TransportType
import com.xraypulse.app.model.VpnProtocol
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigBuilder {

    fun buildFullConfig(
        profile: ServerProfileEntity,
        settings: AppSettings,
        customRules: List<RoutingRuleEntity>
    ): String {
        val root = JSONObject()

        root.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })

        root.put("stats", JSONObject())

        root.put("policy", JSONObject().apply {
            put("levels", JSONObject().apply {
                put("0", JSONObject().apply {
                    put("statsUserUplink", true)
                    put("statsUserDownlink", true)
                })
            })
            put("system", JSONObject().apply {
                put("statsOutboundUplink", true)
                put("statsOutboundDownlink", true)
            })
        })

        root.put("dns", JSONObject().apply {
            put("queryStrategy", if (settings.enableIpv6) "UseIP" else "UseIPv4")
            put("servers", JSONArray().apply {
                put(settings.primaryDns.ifBlank { "1.1.1.1" })
                put(settings.secondaryDns.ifBlank { "8.8.8.8" })
            })
        })

        root.put("inbounds", JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "socks")
                put("port", settings.socksPort)
                put("listen", "127.0.0.1")
                put("protocol", "socks")
                put("settings", JSONObject().apply {
                    put("auth", "noauth")
                    put("udp", true)
                    put("userLevel", 8)
                })
                put("sniffing", JSONObject().apply {
                    put("enabled", true)
                    put("destOverride", JSONArray(listOf("http", "tls", "quic")))
                    put("routeOnly", false)
                })
            })
            put(JSONObject().apply {
                put("tag", "http")
                put("port", settings.httpPort)
                put("listen", "127.0.0.1")
                put("protocol", "http")
                put("settings", JSONObject().apply {
                    put("userLevel", 8)
                })
                put("sniffing", JSONObject().apply {
                    put("enabled", true)
                    put("destOverride", JSONArray(listOf("http", "tls", "quic")))
                    put("routeOnly", false)
                })
            })
        })

        root.put("outbounds", JSONArray().apply {
            put(buildProxyOutbound(profile, settings))
            put(JSONObject().apply {
                put("tag", "direct")
                put("protocol", "freedom")
            })
            if (settings.enableFragment) {
                put(buildFragmentOutbound(settings))
            }
            put(JSONObject().apply {
                put("tag", "block")
                put("protocol", "blackhole")
            })
        })

        root.put("routing", buildRouting(settings.routingPreset, customRules, settings.bypassLan))

        return root.toString(2)
    }

    fun buildLatencyTestConfig(
        profile: ServerProfileEntity,
        settings: AppSettings
    ): String {
        val root = JSONObject()
        root.put("log", JSONObject().apply { put("loglevel", "none") })
        root.put("outbounds", JSONArray().apply {
            put(buildProxyOutbound(profile, settings))
            if (settings.enableFragment) {
                put(buildFragmentOutbound(settings))
            }
        })
        return root.toString()
    }

    private fun buildFragmentOutbound(settings: AppSettings): JSONObject {
        return JSONObject().apply {
            put("tag", "fragment")
            put("protocol", "freedom")
            put("settings", JSONObject().apply {
                put("fragment", JSONObject().apply {
                    put("packets", settings.fragmentPackets.ifBlank { "tlshello" })
                    put("length", settings.fragmentLength.ifBlank { "10-20" })
                    put("interval", settings.fragmentInterval.ifBlank { "10-20" })
                })
            })
        }
    }

    private fun buildProxyOutbound(
        profile: ServerProfileEntity,
        settings: AppSettings
    ): JSONObject {
        val outbound = JSONObject()
        outbound.put("tag", "proxy")

        when (profile.protocol) {
            VpnProtocol.VLESS -> {
                outbound.put("protocol", "vless")
                outbound.put("settings", JSONObject().apply {
                    put("vnext", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", profile.address)
                            put("port", profile.port)
                            put("users", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("id", profile.credential)
                                    put("encryption", profile.encryption.ifBlank { "none" })
                                    if (profile.flow.isNotBlank()) {
                                        put("flow", profile.flow)
                                    }
                                    put("level", 8)
                                })
                            })
                        })
                    })
                })
                outbound.put("streamSettings", buildStandardStreamSettings(profile, settings))
            }

            VpnProtocol.VMESS -> {
                outbound.put("protocol", "vmess")
                outbound.put("settings", JSONObject().apply {
                    put("vnext", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", profile.address)
                            put("port", profile.port)
                            put("users", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("id", profile.credential)
                                    put("alterId", profile.alterId)
                                    put("security", profile.cipher.ifBlank { "auto" })
                                    put("level", 8)
                                })
                            })
                        })
                    })
                })
                outbound.put("streamSettings", buildStandardStreamSettings(profile, settings))
            }

            VpnProtocol.TROJAN -> {
                outbound.put("protocol", "trojan")
                outbound.put("settings", JSONObject().apply {
                    put("servers", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", profile.address)
                            put("port", profile.port)
                            put("password", profile.credential)
                            put("level", 8)
                        })
                    })
                })
                outbound.put("streamSettings", buildStandardStreamSettings(profile, settings))
            }

            VpnProtocol.HYSTERIA2 -> {
                outbound.put("protocol", "hysteria")
                outbound.put("settings", JSONObject().apply {
                    put("version", 2)
                    put("address", profile.address)
                    put("port", profile.port)
                })
                outbound.put("streamSettings", buildHysteria2StreamSettings(profile, settings))
            }
        }

        val skipMux = profile.protocol == VpnProtocol.HYSTERIA2 ||
            profile.flow.contains("vision", ignoreCase = true)
        if (settings.enableMux && !skipMux) {
            outbound.put("mux", JSONObject().apply {
                put("enabled", true)
                put("concurrency", settings.muxConcurrency.coerceIn(1, 128))
                if (settings.muxXudpConcurrency > 0) {
                    put("xudpConcurrency", settings.muxXudpConcurrency.coerceIn(1, 128))
                }
            })
        }

        return outbound
    }

    private fun buildStandardStreamSettings(
        profile: ServerProfileEntity,
        settings: AppSettings
    ): JSONObject {
        val stream = JSONObject()
        stream.put("network", profile.transport.value)
        stream.put("security", profile.security.value)
        if (settings.enableFragment && profile.transport != TransportType.QUIC) {
            stream.put("sockopt", JSONObject().apply {
                put("dialerProxy", "fragment")
            })
        }

        val effectiveSni = profile.sni.ifBlank {
            profile.hostHeader.ifBlank { profile.address }
        }
        val allowInsecure = profile.allowInsecure || settings.allowInsecureGlobal

        when (profile.security) {
            SecurityType.TLS -> {
                stream.put("tlsSettings", JSONObject().apply {
                    put("serverName", effectiveSni)
                    put("allowInsecure", allowInsecure)
                    if (profile.fingerprint.isNotBlank()) {
                        put("fingerprint", profile.fingerprint)
                    }
                    if (profile.alpn.isNotBlank()) {
                        put("alpn", JSONArray(profile.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }))
                    }
                })
            }

            SecurityType.REALITY -> {
                stream.put("realitySettings", JSONObject().apply {
                    put("serverName", effectiveSni)
                    put("fingerprint", profile.fingerprint.ifBlank { "chrome" })
                    put("publicKey", profile.publicKey)
                    put("shortId", profile.shortId)
                    if (profile.spiderX.isNotBlank()) {
                        put("spiderX", profile.spiderX)
                    }
                })
            }

            SecurityType.NONE -> Unit
        }

        when (profile.transport) {
            TransportType.TCP -> {
                if (profile.headerType.equals("http", ignoreCase = true)) {
                    stream.put("tcpSettings", JSONObject().apply {
                        put("header", JSONObject().apply {
                            put("type", "http")
                            if (profile.hostHeader.isNotBlank() || profile.path.isNotBlank()) {
                                put("request", JSONObject().apply {
                                    put("path", JSONArray(listOf(profile.path.ifBlank { "/" })))
                                    if (profile.hostHeader.isNotBlank()) {
                                        put("headers", JSONObject().apply {
                                            put("Host", JSONArray(listOf(profile.hostHeader)))
                                        })
                                    }
                                })
                            }
                        })
                    })
                }
            }

            TransportType.WS -> {
                stream.put("wsSettings", JSONObject().apply {
                    put("path", profile.path.ifBlank { "/" })
                    if (profile.hostHeader.isNotBlank()) {
                        put("headers", JSONObject().apply {
                            put("Host", profile.hostHeader)
                        })
                    }
                })
            }

            TransportType.GRPC -> {
                stream.put("grpcSettings", JSONObject().apply {
                    put("serviceName", profile.serviceName.ifBlank { profile.path })
                    put("multiMode", profile.grpcMode.equals("multi", ignoreCase = true))
                })
            }

            TransportType.HTTPUPGRADE -> {
                stream.put("httpupgradeSettings", JSONObject().apply {
                    put("path", profile.path.ifBlank { "/" })
                    if (profile.hostHeader.isNotBlank()) {
                        put("host", profile.hostHeader)
                    }
                })
            }

            TransportType.XHTTP -> {
                stream.put("xhttpSettings", JSONObject().apply {
                    put("path", profile.path.ifBlank { "/" })
                    if (profile.hostHeader.isNotBlank()) {
                        put("host", profile.hostHeader)
                    }
                })
            }

            TransportType.QUIC -> {
                stream.put("quicSettings", JSONObject().apply {
                    put("security", "none")
                    put("key", "")
                })
            }
        }

        return stream
    }

    private fun buildHysteria2StreamSettings(
        profile: ServerProfileEntity,
        settings: AppSettings
    ): JSONObject {
        val stream = JSONObject()
        stream.put("network", "hysteria")
        stream.put("security", "tls")

        stream.put("hysteriaSettings", JSONObject().apply {
            put("version", 2)
            put("auth", profile.credential)
            if (profile.upMbps > 0) put("up", "${profile.upMbps} mbps")
            if (profile.downMbps > 0) put("down", "${profile.downMbps} mbps")
            if (profile.portHopping.isNotBlank()) {
                put("udphop", JSONObject().apply {
                    put("port", profile.portHopping)
                    put("interval", 30)
                })
            }
        })

        val effectiveSni = profile.sni.ifBlank { profile.address }
        val allowInsecure = profile.allowInsecure || settings.allowInsecureGlobal
        stream.put("tlsSettings", JSONObject().apply {
            put("serverName", effectiveSni)
            put("allowInsecure", allowInsecure)
            val alpnList = if (profile.alpn.isNotBlank()) {
                profile.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                listOf("h3")
            }
            put("alpn", JSONArray(alpnList))
        })

        if (profile.obfsType.equals("salamander", ignoreCase = true) && profile.obfsPassword.isNotBlank()) {
            stream.put("finalmask", JSONObject().apply {
                put("udp", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "salamander")
                        put("settings", JSONObject().apply {
                            put("password", profile.obfsPassword)
                        })
                    })
                })
            })
        }

        return stream
    }

    private fun buildRouting(
        preset: RoutingPreset,
        customRules: List<RoutingRuleEntity>,
        bypassLan: Boolean = true
    ): JSONObject {
        val rules = JSONArray()

        // Always apply enabled user custom rules first (highest priority)
        val activeRules = customRules.filter { it.enabled && it.pattern.isNotBlank() }
        for (rule in activeRules) {
            rules.put(JSONObject().apply {
                put("type", "field")
                when (rule.type) {
                    RuleType.DOMAIN -> put("domain", JSONArray(listOf(rule.pattern.trim())))
                    RuleType.IP_CIDR -> put("ip", JSONArray(listOf(rule.pattern.trim())))
                }
                put("outboundTag", rule.action.outboundTag)
            })
        }

        if (bypassLan) {
            rules.put(JSONObject().apply {
                put("type", "field")
                put("ip", JSONArray(listOf("geoip:private")))
                put("outboundTag", "direct")
            })
        }

        when (preset) {
            RoutingPreset.GLOBAL, RoutingPreset.BYPASS_LAN, RoutingPreset.CUSTOM -> Unit
            RoutingPreset.BYPASS_RU -> {
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray(listOf("geoip:ru")))
                    put("outboundTag", "direct")
                })
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("domain", JSONArray(listOf("geosite:category-ru", "regexp:.*\\.ru$")))
                    put("outboundTag", "direct")
                })
            }
        }

        return JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", rules)
        }
    }
}
