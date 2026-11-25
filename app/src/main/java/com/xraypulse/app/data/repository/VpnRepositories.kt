package com.xraypulse.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.xraypulse.app.data.local.RoutingRuleDao
import com.xraypulse.app.data.local.RoutingRuleEntity
import com.xraypulse.app.data.local.ServerProfileDao
import com.xraypulse.app.data.local.ServerProfileEntity
import com.xraypulse.app.data.local.SettingsDataStore
import com.xraypulse.app.data.local.SubscriptionDao
import com.xraypulse.app.data.local.SubscriptionEntity
import com.xraypulse.app.data.parser.ShareUriParser
import com.xraypulse.app.data.xray.XrayConfigBuilder
import com.xraypulse.app.model.AppLanguage
import com.xraypulse.app.model.AppSettings
import com.xraypulse.app.model.AppThemeMode
import com.xraypulse.app.model.InstalledAppInfo
import com.xraypulse.app.model.SplitTunnelMode
import com.xraypulse.app.vpn.VpnPingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ProfileRepository(
    private val profileDao: ServerProfileDao,
    private val subscriptionDao: SubscriptionDao,
    private val settingsDataStore: SettingsDataStore,
    private val vpnPingClient: VpnPingClient
) {
    private val syncMutex = Mutex()
    private val dbMutationMutex = Mutex()
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    val profilesFlow: Flow<List<ServerProfileEntity>> = profileDao.observeAll()
    val subscriptionsFlow: Flow<List<SubscriptionEntity>> = subscriptionDao.observeAll()

    suspend fun getProfileById(id: Long): ServerProfileEntity? = profileDao.getById(id)

    suspend fun getFirstProfileOrNull(): ServerProfileEntity? = profileDao.getAllOnce().firstOrNull()

    suspend fun importFromText(rawText: String): Int {
        val parsed = ShareUriParser.parseBulk(rawText, subscriptionId = null)
        if (parsed.isEmpty()) return 0
        val existingLocal = profileDao.getAllOnce().filter { it.subscriptionId == null }
        val baseOrder = (existingLocal.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val orderedParsed = parsed.mapIndexed { index, profile ->
            profile.copy(sortOrder = baseOrder + index)
        }
        val insertedIds = profileDao.insertAll(orderedParsed)
        if (insertedIds.isNotEmpty()) {
            settingsDataStore.setSelectedProfileId(insertedIds.first())
        }
        return insertedIds.size
    }

    suspend fun reorderProfiles(orderedIds: List<Long>) {
        if (orderedIds.isEmpty()) return
        profileDao.updateSortOrders(orderedIds)
    }

    suspend fun addAndSyncSubscription(name: String, url: String): Result<Int> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            runCatching {
                val cleanUrl = url.trim()
                require(cleanUrl.startsWith("https://", ignoreCase = true)) {
                    "Subscription URL must start with https://"
                }
                val displayName = name.trim().ifBlank {
                    runCatching { java.net.URI(cleanUrl).host ?: "Subscription" }.getOrDefault("Subscription")
                }
                val subId = subscriptionDao.insert(SubscriptionEntity(name = displayName, url = cleanUrl))
                syncSubscriptionInternal(subId, cleanUrl, displayName, selectIfAdded = true).getOrThrow()
            }
        }
    }

    suspend fun purgeLegacyTestSubscriptions() = withContext(Dispatchers.IO) {
        dbMutationMutex.withLock {
            val subs = subscriptionDao.getAllOnce()
            val testSubs = subs.filter {
                it.url.startsWith("dev://", ignoreCase = true) ||
                    it.name == "Test Subscription"
            }
            if (testSubs.isEmpty()) return@withContext
            testSubs.forEach { sub ->
                profileDao.deleteBySubscription(sub.id)
                subscriptionDao.deleteById(sub.id)
            }
            val selected = settingsDataStore.getOnce().selectedProfileId
            if (selected != null && profileDao.getById(selected) == null) {
                settingsDataStore.setSelectedProfileId(profileDao.getAllOnce().firstOrNull()?.id)
            }
        }
    }

    suspend fun syncSubscription(subscription: SubscriptionEntity): Result<Int> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            syncSubscriptionInternal(subscription.id, subscription.url, subscription.name, selectIfAdded = false)
        }
    }

    suspend fun syncAllAutoSubscriptions(): Int = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            var updatedSubs = 0
            subscriptionDao.getAllOnce().forEach { sub ->
                if (sub.url.startsWith("https://", ignoreCase = true)) {
                    syncSubscriptionInternal(sub.id, sub.url, sub.name).onSuccess {
                        updatedSubs++
                    }
                }
            }
            updatedSubs
        }
    }

    suspend fun syncDueSubscriptions(
        intervalHours: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Int = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val intervalMs = intervalHours.coerceIn(1, 168) * 3_600_000L
            var updatedSubs = 0
            subscriptionDao.getAllOnce().forEach { sub ->
                if (sub.url.startsWith("https://", ignoreCase = true) &&
                    (nowMillis - sub.lastUpdatedAt >= intervalMs)
                ) {
                    syncSubscriptionInternal(sub.id, sub.url, sub.name).onSuccess {
                        updatedSubs++
                    }
                }
            }
            updatedSubs
        }
    }

    suspend fun nextAutoUpdateDelayMs(
        intervalHours: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? = withContext(Dispatchers.IO) {
        val intervalMs = intervalHours.coerceIn(1, 168) * 3_600_000L
        val httpsSubs = subscriptionDao.getAllOnce().filter {
            it.url.startsWith("https://", ignoreCase = true)
        }
        if (httpsSubs.isEmpty()) return@withContext null
        httpsSubs.minOf { sub ->
            val elapsed = (nowMillis - sub.lastUpdatedAt).coerceAtLeast(0L)
            (intervalMs - elapsed).coerceAtLeast(0L)
        }
    }

    suspend fun renameSubscription(subscriptionId: Long, newName: String) {
        val clean = newName.trim()
        if (clean.isNotEmpty()) {
            subscriptionDao.renameById(subscriptionId, clean)
        }
    }

    suspend fun deleteSubscription(subscriptionId: Long) = withContext(Dispatchers.IO) {
        dbMutationMutex.withLock {
            profileDao.deleteBySubscription(subscriptionId)
            subscriptionDao.deleteById(subscriptionId)
            val settings = settingsDataStore.getOnce()
            val selected = settings.selectedProfileId
            if (selected != null && profileDao.getById(selected) == null) {
                settingsDataStore.setSelectedProfileId(profileDao.getAllOnce().firstOrNull()?.id)
            }
            val lastConnected = settings.lastConnectedProfileId
            if (lastConnected != null && profileDao.getById(lastConnected) == null) {
                settingsDataStore.setLastConnectedProfileId(null)
            }
        }
    }

    private suspend fun syncSubscriptionInternal(
        subscriptionId: Long,
        url: String,
        fallbackName: String,
        selectIfAdded: Boolean = false
    ): Result<Int> = runCatching {
        require(url.startsWith("https://", ignoreCase = true)) {
            "Subscription URL must start with https://"
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "XRayPulse/1.0 v2rayNG")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val profiles = ShareUriParser.parseBulk(body, subscriptionId = subscriptionId)
            val userInfo = parseSubscriptionUserInfo(response.header("Subscription-Userinfo"))
            val rawTitleHeader = response.header("Profile-Title")?.trim()
            val titleHeader = rawTitleHeader?.let { header ->
                if (header.startsWith("base64:", ignoreCase = true)) {
                    val encoded = header.substringAfter(":").trim()
                    runCatching {
                        val cleaned = encoded.replace('-', '+').replace('_', '/')
                        val padded = cleaned.padEnd((cleaned.length + 3) / 4 * 4, '=')
                        String(android.util.Base64.decode(padded, android.util.Base64.DEFAULT), java.nio.charset.StandardCharsets.UTF_8)
                    }.getOrNull()?.trim()
                } else {
                    header
                }
            }?.takeIf { it.isNotBlank() }

            applySyncedSubscriptionResult(
                subscriptionId = subscriptionId,
                fallbackName = fallbackName,
                profiles = profiles,
                titleHeader = titleHeader,
                uploadBytes = userInfo.upload,
                downloadBytes = userInfo.download,
                totalBytes = userInfo.total,
                expireTimestampSec = userInfo.expire,
                selectIfAdded = selectIfAdded
            )
        }
    }

    internal suspend fun applySyncedSubscriptionResult(
        subscriptionId: Long,
        fallbackName: String,
        profiles: List<ServerProfileEntity>,
        titleHeader: String? = null,
        uploadBytes: Long = 0L,
        downloadBytes: Long = 0L,
        totalBytes: Long = 0L,
        expireTimestampSec: Long = 0L,
        selectIfAdded: Boolean = false
    ): Int = dbMutationMutex.withLock {
        val existing = subscriptionDao.getById(subscriptionId) ?: return@withLock 0

        val previousProfiles = profileDao.getAllOnce().filter { it.subscriptionId == subscriptionId }
        val hasCustomOrdering = previousProfiles.any { it.sortOrder != 0 }
        val previousOrderByKey = previousProfiles.mapIndexed { idx, item ->
            profileOrderKey(item) to (if (hasCustomOrdering) item.sortOrder else idx)
        }.toMap()
        val selectedId = settingsDataStore.getOnce().selectedProfileId
        val selectedWasInThisSub = selectedId != null && previousProfiles.any { it.id == selectedId }
        val previousSelectedKey = previousProfiles.find { it.id == selectedId }?.let { profileOrderKey(it) }

        val nextBaseOrder = (previousOrderByKey.values.maxOrNull() ?: -1) + 1
        var appendCounter = 0
        val orderedProfiles = profiles.map { p ->
            val preserved = previousOrderByKey[profileOrderKey(p)]
            p.copy(sortOrder = preserved ?: (nextBaseOrder + appendCounter++))
        }

        val insertedIds = profileDao.replaceSubscriptionProfiles(subscriptionId, orderedProfiles)

        val shouldReplaceName = titleHeader != null &&
            (existing.name == fallbackName || existing.name == "Subscription")
        subscriptionDao.update(
            existing.copy(
                name = if (shouldReplaceName) titleHeader!! else existing.name,
                lastUpdatedAt = System.currentTimeMillis(),
                uploadBytes = uploadBytes,
                downloadBytes = downloadBytes,
                totalBytes = totalBytes,
                expireTimestampSec = expireTimestampSec
            )
        )

        if (selectedWasInThisSub) {
            if (insertedIds.isNotEmpty()) {
                val matchingIdx = previousSelectedKey?.let { key ->
                    orderedProfiles.indexOfFirst { profileOrderKey(it) == key }
                } ?: -1
                val nextSelectedId = if (matchingIdx in insertedIds.indices) {
                    insertedIds[matchingIdx]
                } else {
                    insertedIds.first()
                }
                settingsDataStore.setSelectedProfileId(nextSelectedId)
            } else {
                settingsDataStore.setSelectedProfileId(profileDao.getAllOnce().firstOrNull()?.id)
            }
        } else if (insertedIds.isNotEmpty()) {
            val hasValidCurrentSelection = selectedId != null && profileDao.getById(selectedId) != null
            if (!hasValidCurrentSelection || selectIfAdded) {
                settingsDataStore.setSelectedProfileId(insertedIds.first())
            }
        }
        orderedProfiles.size
    }

    private fun profileOrderKey(profile: ServerProfileEntity): String =
        "${profile.protocol.name}|${profile.address}|${profile.port}|${profile.credential}|${profile.name}"

    suspend fun testProfileLatency(profile: ServerProfileEntity): Long = withContext(Dispatchers.IO) {
        val settings = settingsDataStore.getOnce()
        val testConfig = XrayConfigBuilder.buildLatencyTestConfig(profile, settings)
        val latency = vpnPingClient.measureDelay(testConfig, profile.address, profile.port)
        profileDao.updateLatency(profile.id, latency)
        latency
    }

    private data class SubUserInfo(
        val upload: Long = 0L,
        val download: Long = 0L,
        val total: Long = 0L,
        val expire: Long = 0L
    )

    private fun parseSubscriptionUserInfo(header: String?): SubUserInfo {
        if (header.isNullOrBlank()) return SubUserInfo()
        val map = header.split(";").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null
            else part.substring(0, idx).trim().lowercase() to (part.substring(idx + 1).trim().toLongOrNull() ?: 0L)
        }.toMap()
        return SubUserInfo(
            upload = map["upload"] ?: 0L,
            download = map["download"] ?: 0L,
            total = map["total"] ?: 0L,
            expire = map["expire"] ?: 0L
        )
    }
}

class RoutingRepository(
    private val context: Context,
    private val routingRuleDao: RoutingRuleDao
) {
    suspend fun getEnabledRulesOnce(): List<RoutingRuleEntity> = routingRuleDao.getEnabledOnce()

    suspend fun loadLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        resolveInfos
            .mapNotNull { info ->
                val appInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
                val pkg = appInfo.packageName
                if (pkg == context.packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val icon = runCatching { info.loadIcon(pm) }.getOrNull()
                InstalledAppInfo(
                    packageName = pkg,
                    label = label,
                    isSystemApp = isSystem,
                    icon = icon
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

class SettingsRepository(private val dataStore: SettingsDataStore) {
    val settingsFlow: Flow<AppSettings> = dataStore.settingsFlow

    suspend fun getSettingsOnce(): AppSettings = dataStore.getOnce()
    suspend fun setSelectedProfileId(id: Long?) = dataStore.setSelectedProfileId(id)
    suspend fun setLastConnectedProfileId(id: Long?) = dataStore.setLastConnectedProfileId(id)
    suspend fun setSplitTunnelMode(mode: SplitTunnelMode) = dataStore.setSplitTunnelMode(mode)
    suspend fun toggleSplitTunnelPackage(pkg: String) = dataStore.toggleSplitTunnelPackage(pkg)
    suspend fun setThemeMode(mode: AppThemeMode) = dataStore.setThemeMode(mode)
    suspend fun setDynamicColorEnabled(enabled: Boolean) = dataStore.setDynamicColorEnabled(enabled)
    suspend fun setAppLanguage(language: AppLanguage) = dataStore.setAppLanguage(language)
    suspend fun setMarqueeEnabled(enabled: Boolean) = dataStore.setMarqueeEnabled(enabled)
    suspend fun setBypassLan(bypass: Boolean) = dataStore.setBypassLan(bypass)
    suspend fun setMtu(mtu: Int) = dataStore.setMtu(mtu)
    suspend fun setEnableIpv6(enabled: Boolean) = dataStore.setEnableIpv6(enabled)
    suspend fun setMuxSettings(enabled: Boolean, concurrency: Int, xudpConcurrency: Int) =
        dataStore.setMuxSettings(enabled, concurrency, xudpConcurrency)
    suspend fun setFragmentSettings(
        enabled: Boolean,
        packets: String,
        length: String,
        interval: String
    ) = dataStore.setFragmentSettings(enabled, packets, length, interval)
    suspend fun setAutoUpdateSettings(
        enabled: Boolean,
        onLaunch: Boolean,
        bySchedule: Boolean,
        intervalHours: Int
    ) = dataStore.setAutoUpdateSettings(enabled, onLaunch, bySchedule, intervalHours)
    suspend fun setCustomAccentColor(argb: Long?) = dataStore.setCustomAccentColor(argb)
    suspend fun setCustomBackgroundColor(argb: Long?) = dataStore.setCustomBackgroundColor(argb)
    suspend fun setConnectingRotationSpeed(degPerSec: Int) = dataStore.setConnectingRotationSpeed(degPerSec)
    suspend fun setConnectedRotationSpeed(degPerSec: Int) = dataStore.setConnectedRotationSpeed(degPerSec)
    suspend fun setConnectButtonShape(shape: com.xraypulse.app.model.ConnectButtonShape) =
        dataStore.setConnectButtonShape(shape)
    suspend fun setButtonShapeParams(params: com.xraypulse.app.model.ButtonShapeParams) =
        dataStore.setButtonShapeParams(params)
}
