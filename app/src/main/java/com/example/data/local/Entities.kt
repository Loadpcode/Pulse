package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.RuleAction
import com.example.model.RuleType
import com.example.model.SecurityType
import com.example.model.TransportType
import com.example.model.VpnProtocol

@Entity(tableName = "server_profiles")
data class ServerProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: VpnProtocol,
    val address: String,
    val port: Int,
    // Credentials (UUID for VLESS/VMess, password/auth for Trojan/Hysteria2)
    val credential: String,
    val flow: String = "",
    val encryption: String = "none",
    val alterId: Int = 0,
    val cipher: String = "auto",
    // Transport
    val transport: TransportType = TransportType.TCP,
    val hostHeader: String = "",
    val path: String = "",
    val serviceName: String = "",
    val grpcMode: String = "gun",
    val headerType: String = "none",
    // Security
    val security: SecurityType = SecurityType.TLS,
    val sni: String = "",
    val fingerprint: String = "chrome",
    val alpn: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val allowInsecure: Boolean = false,
    // Hysteria2 specific
    val upMbps: Int = 50,
    val downMbps: Int = 100,
    val obfsType: String = "",
    val obfsPassword: String = "",
    val portHopping: String = "",
    // Metadata
    val subscriptionId: Long? = null,
    val sortOrder: Int = 0,
    val latencyMs: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val expireTimestampSec: Long = 0L
)

@Entity(tableName = "routing_rules")
data class RoutingRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: RuleType,
    val pattern: String,
    val action: RuleAction,
    val enabled: Boolean = true,
    val note: String = ""
)
