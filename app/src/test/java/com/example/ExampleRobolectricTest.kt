package com.example

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SettingsDataStore
import com.example.model.ButtonShapeParams
import com.example.model.ConnectButtonShape
import com.example.model.VpnStatus
import com.example.ui.components.isAndroidTv
import com.example.ui.components.toComposeShape
import com.example.vpn.VpnSessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("XRay Pulse", appName)
  }

  @Test
  fun `main activity launches and handles connection error state without crashing`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    assertNotNull(controller.get())

    VpnSessionManager.setError("Connection failed")
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals(VpnStatus.ERROR, VpnSessionManager.state.value.status)

    VpnSessionManager.setDisconnected()
    shadowOf(Looper.getMainLooper()).idle()
    controller.pause().stop().destroy()
  }

  @Test
  @Config(sdk = [36], qualifiers = "w1280dp-h800dp-land")
  fun `main activity launches cleanly in tablet landscape layout`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    assertNotNull(controller.get())
    controller.pause().stop().destroy()
  }

  @Test
  @Config(sdk = [36], qualifiers = "w960dp-h540dp-television-notouch")
  fun `main activity detects TV mode and launches cleanly`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val activity = controller.get()
    assertNotNull(activity)
    assertTrue(activity.isAndroidTv())
    controller.pause().stop().destroy()
  }

  @Test
  fun `phone configuration is not falsely detected as TV`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertFalse(context.isAndroidTv())
  }

  @Test
  fun `vector xr splash and launcher drawables inflate cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertNotNull(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground))
    assertNotNull(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_monochrome))
    assertNotNull(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_splash_xr))
    assertNotNull(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.tv_banner))
  }

  @Test
  fun `all material3 button shapes resolve and cache outlines across frames`() {
    val size = androidx.compose.ui.geometry.Size(148f, 148f)
    val density = androidx.compose.ui.unit.Density(1f)
    ConnectButtonShape.entries.forEach { shape ->
      val composeShape = shape.toComposeShape()
      assertNotNull(composeShape)
      val firstOutline = composeShape.createOutline(
        size = size,
        layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
        density = density
      )
      val secondOutline = composeShape.createOutline(
        size = size,
        layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
        density = density
      )
      assertNotNull(firstOutline)
      assertSame(firstOutline, secondOutline)
    }
  }

  @Test
  fun `visual settings persist in datastore`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = SettingsDataStore(context)

    store.setDynamicColorEnabled(false)
    store.setCustomAccentColor(0xFFFB7185L)
    store.setCustomBackgroundColor(0xFF0F172AL)
    store.setConnectingRotationSpeed(90)
    store.setConnectedRotationSpeed(180)
    store.setConnectButtonShape(ConnectButtonShape.COOKIE_6)
    val customParams = ButtonShapeParams(
      squirclePower = 6.2f,
      cookie6Depth = 0.14f,
      pillWidth = 0.88f,
      pillHeight = 0.50f
    )
    store.setButtonShapeParams(customParams)

    val saved = store.settingsFlow.first()
    assertEquals(false, saved.dynamicColorEnabled)
    assertEquals(0xFFFB7185L, saved.customAccentColorArgb)
    assertEquals(0xFF0F172AL, saved.customBackgroundColorArgb)
    assertEquals(90, saved.connectingRotationSpeed)
    assertEquals(180, saved.connectedRotationSpeed)
    assertEquals(ConnectButtonShape.COOKIE_6, saved.connectButtonShape)
    assertEquals(6.2f, saved.buttonShapeParams.squirclePower, 0.001f)
    assertEquals(0.14f, saved.buttonShapeParams.cookie6Depth, 0.001f)
    assertEquals(0.88f, saved.buttonShapeParams.pillWidth, 0.001f)

    // Custom parameters should still build valid cached outlines for all 10 M3 shapes
    assertEquals(10, ConnectButtonShape.entries.size)
    ConnectButtonShape.entries.forEach { shape ->
      val outline = shape.toComposeShape(customParams).createOutline(
        size = androidx.compose.ui.geometry.Size(120f, 120f),
        layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
        density = androidx.compose.ui.unit.Density(1f)
      )
      assertNotNull(outline)
    }
  }

  @Test
  fun `vpn error increments errorNonce and persists until cleared`() {
    VpnSessionManager.clearError()
    VpnSessionManager.setError("Handshake timeout")
    val first = VpnSessionManager.state.value
    assertEquals(VpnStatus.ERROR, first.status)
    assertEquals("Handshake timeout", first.errorMessage)

    VpnSessionManager.setError("Handshake timeout")
    val second = VpnSessionManager.state.value
    assertEquals(VpnStatus.ERROR, second.status)
    assertEquals(true, second.errorNonce > first.errorNonce)

    VpnSessionManager.clearError()
    assertEquals(VpnStatus.DISCONNECTED, VpnSessionManager.state.value.status)
    assertEquals(null, VpnSessionManager.state.value.errorMessage)
  }

  @Test
  fun `quick settings tile drawable inflates cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_qs_xr)
    assertNotNull(drawable)
  }

  @Test
  fun `lastConnectedProfileId persists and clears in datastore`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = SettingsDataStore(context)

    store.setLastConnectedProfileId(42L)
    assertEquals(42L, store.settingsFlow.first().lastConnectedProfileId)

    store.setLastConnectedProfileId(null)
    assertEquals(null, store.settingsFlow.first().lastConnectedProfileId)
  }

  @Test
  fun `quick settings tile service is declared with bind permission and initializes cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val component = android.content.ComponentName(context, com.example.vpn.XrayQuickSettingsTileService::class.java)
    val serviceInfo = context.packageManager.getServiceInfo(component, android.content.pm.PackageManager.GET_META_DATA)
    assertEquals(android.Manifest.permission.BIND_QUICK_SETTINGS_TILE, serviceInfo.permission)

    val controller = Robolectric.buildService(com.example.vpn.XrayQuickSettingsTileService::class.java).create()
    val service = controller.get()
    assertNotNull(service)
    service.onStartListening()
    shadowOf(Looper.getMainLooper()).idle()
    service.onStopListening()
  }

  @Test
  fun `server profile sortOrder persists across updateSortOrders and observeAll`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.example.data.local.AppDatabase::class.java
    )
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.serverProfileDao()
      val id1 = dao.insert(
        com.example.data.local.ServerProfileEntity(
          name = "First",
          protocol = com.example.model.VpnProtocol.VLESS,
          address = "one.example.com",
          port = 443,
          credential = "11111111-1111-1111-1111-111111111111",
          sortOrder = 0
        )
      )
      val id2 = dao.insert(
        com.example.data.local.ServerProfileEntity(
          name = "Second",
          protocol = com.example.model.VpnProtocol.VLESS,
          address = "two.example.com",
          port = 443,
          credential = "22222222-2222-2222-2222-222222222222",
          sortOrder = 1
        )
      )
      val id3 = dao.insert(
        com.example.data.local.ServerProfileEntity(
          name = "Third",
          protocol = com.example.model.VpnProtocol.VLESS,
          address = "three.example.com",
          port = 443,
          credential = "33333333-3333-3333-3333-333333333333",
          sortOrder = 2
        )
      )

      dao.updateSortOrders(listOf(id3, id1, id2))
      val ordered = dao.observeAll().first()
      assertEquals(listOf(id3, id1, id2), ordered.map { it.id })
      assertEquals(listOf(0, 1, 2), ordered.map { it.sortOrder })
    } finally {
      db.close()
    }
  }

  @Test
  fun `database migration 1 to 2 adds sortOrder column safely`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbFile = context.getDatabasePath("migration_test.db")
    dbFile.delete()
    val sqlite = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    sqlite.execSQL(
      """
      CREATE TABLE IF NOT EXISTS server_profiles (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        name TEXT NOT NULL,
        protocol TEXT NOT NULL,
        address TEXT NOT NULL,
        port INTEGER NOT NULL,
        credential TEXT NOT NULL,
        flow TEXT NOT NULL,
        encryption TEXT NOT NULL,
        alterId INTEGER NOT NULL,
        cipher TEXT NOT NULL,
        transport TEXT NOT NULL,
        hostHeader TEXT NOT NULL,
        path TEXT NOT NULL,
        serviceName TEXT NOT NULL,
        grpcMode TEXT NOT NULL,
        headerType TEXT NOT NULL,
        security TEXT NOT NULL,
        sni TEXT NOT NULL,
        fingerprint TEXT NOT NULL,
        alpn TEXT NOT NULL,
        publicKey TEXT NOT NULL,
        shortId TEXT NOT NULL,
        spiderX TEXT NOT NULL,
        allowInsecure INTEGER NOT NULL,
        upMbps INTEGER NOT NULL,
        downMbps INTEGER NOT NULL,
        obfsType TEXT NOT NULL,
        obfsPassword TEXT NOT NULL,
        portHopping TEXT NOT NULL,
        subscriptionId INTEGER,
        latencyMs INTEGER,
        updatedAt INTEGER NOT NULL
      )
      """.trimIndent()
    )
    sqlite.execSQL(
      """
      CREATE TABLE IF NOT EXISTS subscriptions (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        name TEXT NOT NULL,
        url TEXT NOT NULL,
        lastUpdatedAt INTEGER NOT NULL,
        uploadBytes INTEGER NOT NULL,
        downloadBytes INTEGER NOT NULL,
        totalBytes INTEGER NOT NULL,
        expireTimestampSec INTEGER NOT NULL
      )
      """.trimIndent()
    )
    sqlite.execSQL(
      """
      CREATE TABLE IF NOT EXISTS routing_rules (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        type TEXT NOT NULL,
        pattern TEXT NOT NULL,
        action TEXT NOT NULL,
        enabled INTEGER NOT NULL,
        note TEXT NOT NULL
      )
      """.trimIndent()
    )
    sqlite.execSQL(
      """
      INSERT INTO server_profiles (
        name, protocol, address, port, credential, flow, encryption, alterId, cipher,
        transport, hostHeader, path, serviceName, grpcMode, headerType, security, sni,
        fingerprint, alpn, publicKey, shortId, spiderX, allowInsecure, upMbps, downMbps,
        obfsType, obfsPassword, portHopping, subscriptionId, latencyMs, updatedAt
      ) VALUES (
        'Legacy', 'VLESS', 'legacy.example.com', 443, '11111111-1111-1111-1111-111111111111',
        '', 'none', 0, 'auto', 'TCP', '', '', '', 'gun', 'none', 'TLS', '', 'chrome',
        '', '', '', '', 0, 50, 100, '', '', '', NULL, NULL, 1000
      )
      """.trimIndent()
    )
    sqlite.execSQL("PRAGMA user_version = 1")
    sqlite.close()

    val roomDb = androidx.room.Room.databaseBuilder(
      context,
      com.example.data.local.AppDatabase::class.java,
      "migration_test.db"
    )
      .addMigrations(com.example.data.local.AppDatabase.MIGRATION_1_2)
      .allowMainThreadQueries()
      .build()
    try {
      val list = roomDb.serverProfileDao().let { dao ->
        kotlinx.coroutines.runBlocking { dao.getAllOnce() }
      }
      assertEquals(1, list.size)
      assertEquals("Legacy", list.first().name)
      assertEquals(0, list.first().sortOrder)
    } finally {
      roomDb.close()
      dbFile.delete()
    }
  }

  @Test
  fun `auto update launch and schedule flags persist in datastore`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = SettingsDataStore(context)

    store.setAutoUpdateSettings(
      enabled = true,
      onLaunch = true,
      bySchedule = false,
      intervalHours = 6
    )
    val launchOnly = store.settingsFlow.first()
    assertTrue(launchOnly.autoUpdateSubscriptions)
    assertTrue(launchOnly.autoUpdateOnLaunch)
    assertFalse(launchOnly.autoUpdateBySchedule)
    assertEquals(6, launchOnly.autoUpdateIntervalHours)

    store.setAutoUpdateSettings(
      enabled = true,
      onLaunch = true,
      bySchedule = true,
      intervalHours = 24
    )
    val bothModes = store.settingsFlow.first()
    assertTrue(bothModes.autoUpdateSubscriptions)
    assertTrue(bothModes.autoUpdateOnLaunch)
    assertTrue(bothModes.autoUpdateBySchedule)
    assertEquals(24, bothModes.autoUpdateIntervalHours)
  }

  @Test
  fun `updating subscription timestamp shifts next scheduled auto-update delay`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.example.data.local.AppDatabase::class.java
    )
      .allowMainThreadQueries()
      .build()
    try {
      val repo = com.example.data.repository.ProfileRepository(
        profileDao = db.serverProfileDao(),
        subscriptionDao = db.subscriptionDao(),
        settingsDataStore = SettingsDataStore(context),
        vpnPingClient = com.example.vpn.VpnPingClient(context)
      )
      val now = 1_700_000_000_000L
      val twelveHoursMs = 12 * 3_600_000L
      val subId = db.subscriptionDao().insert(
        com.example.data.local.SubscriptionEntity(
          name = "Main Sub",
          url = "https://example.com/sub",
          lastUpdatedAt = now - twelveHoursMs - 60_000L
        )
      )

      // Due immediately before launch refresh
      assertEquals(0L, repo.nextAutoUpdateDelayMs(intervalHours = 12, nowMillis = now))

      // Simulate launch refresh completing at `now - 1_000L`, which shifts the timer forward
      val stored = db.subscriptionDao().getById(subId)!!
      db.subscriptionDao().update(stored.copy(lastUpdatedAt = now - 1_000L))

      val shiftedDelay = repo.nextAutoUpdateDelayMs(intervalHours = 12, nowMillis = now)
      assertEquals(twelveHoursMs - 1_000L, shiftedDelay)
    } finally {
      db.close()
    }
  }

  @Test
  fun `share utils builds ACTION_SEND chooser and extracts incoming share payloads`() {
    val vlessUri = "vless://11111111-1111-1111-1111-111111111111@example.com:443?type=tcp&security=tls#TestNode"
    val parsed = com.example.data.parser.ShareUriParser.parseSingle(vlessUri)!!
    val exported = com.example.data.parser.ShareUriParser.toShareUri(parsed)
    assertTrue(exported.startsWith("vless://"))
    assertTrue(exported.contains("TestNode"))

    val chooser = com.example.ui.components.ShareUtils.createShareTextIntent(
      text = exported,
      chooserTitle = "Share via",
      subject = parsed.name
    )
    assertEquals(android.content.Intent.ACTION_CHOOSER, chooser.action)
    @Suppress("DEPRECATION")
    val inner = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
    assertEquals(android.content.Intent.ACTION_SEND, inner.action)
    assertEquals("text/plain", inner.type)
    assertEquals(exported, inner.getStringExtra(android.content.Intent.EXTRA_TEXT))
    assertEquals("TestNode", inner.getStringExtra(android.content.Intent.EXTRA_SUBJECT))

    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(android.content.Intent.EXTRA_TEXT, "  $vlessUri  ")
    }
    assertEquals(vlessUri, com.example.ui.components.ShareUtils.extractIncomingShareText(sendIntent))
    com.example.ui.components.ShareUtils.clearIncomingSharePayload(sendIntent)
    assertEquals(null, com.example.ui.components.ShareUtils.extractIncomingShareText(sendIntent))

    val viewIntent = android.content.Intent(
      android.content.Intent.ACTION_VIEW,
      android.net.Uri.parse("hy2://pass@host.example:443?sni=host.example#HyNode")
    )
    assertEquals(
      "hy2://pass@host.example:443?sni=host.example#HyNode",
      com.example.ui.components.ShareUtils.extractIncomingShareText(viewIntent)
    )

    val unsupportedViewIntent = android.content.Intent(
      android.content.Intent.ACTION_VIEW,
      android.net.Uri.parse("https://example.com/page")
    )
    assertEquals(null, com.example.ui.components.ShareUtils.extractIncomingShareText(unsupportedViewIntent))
  }

  @Test
  fun `main activity consumes incoming share intent cleanly and profileinstaller is bundled`() {
    assertNotNull(Class.forName("androidx.profileinstaller.ProfileInstaller"))
    assertNotNull(Class.forName("androidx.profileinstaller.ProfileInstallReceiver"))

    val shareUri = "vless://22222222-2222-2222-2222-222222222222@share.example.com:443?security=tls#SharedNode"
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(android.content.Intent.EXTRA_TEXT, shareUri)
    }
    val controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
    shadowOf(Looper.getMainLooper()).idle()
    assertNotNull(controller.get())
    assertEquals(null, com.example.ui.components.ShareUtils.extractIncomingShareText(controller.get().intent))
    controller.pause().stop().destroy()
  }

  @Test
  fun `share uri parser extracts embedded uris from noisy messenger text and share utils caps oversized text`() {
    val sharedMessage = """
      My server config:
      Here it is -> vless://33333333-3333-3333-3333-333333333333@edge.example.com:443?type=tcp&security=tls#EdgeNode, thanks!
      Backup: hy2://pass@hy.example.com:8443?sni=hy.example.com#HyBackup
    """.trimIndent()

    val parsed = com.example.data.parser.ShareUriParser.parseBulk(sharedMessage)
    assertEquals(2, parsed.size)
    assertEquals("EdgeNode", parsed[0].name)
    assertEquals("HyBackup", parsed[1].name)

    val longLogs = (1..4000).joinToString("\n") { idx -> "Line $idx: xray handshake ok" }
    val cappedTail = com.example.ui.components.ShareUtils.capShareText(
      text = longLogs,
      maxChars = 1000,
      preferTail = true
    )
    assertTrue(cappedTail.length <= 1000)
    assertTrue(cappedTail.contains("Line 4000: xray handshake ok"))

    val cappedHead = com.example.ui.components.ShareUtils.capShareText(
      text = longLogs,
      maxChars = 1000,
      preferTail = false
    )
    assertTrue(cappedHead.length <= 1000)
    assertTrue(cappedHead.contains("Line 1: xray handshake ok"))
  }

  @Test
  fun `selector pill betweenness curve is zero on options and peaks smoothly mid-transition`() {
    assertEquals(0f, com.example.ui.components.computeSelectorPillBetweenness(0f), 0.001f)
    assertEquals(0f, com.example.ui.components.computeSelectorPillBetweenness(1f), 0.001f)
    assertEquals(0f, com.example.ui.components.computeSelectorPillBetweenness(2f), 0.001f)

    // Exact midpoint between two options peaks at 1.0
    assertEquals(1f, com.example.ui.components.computeSelectorPillBetweenness(0.5f), 0.001f)
    assertEquals(1f, com.example.ui.components.computeSelectorPillBetweenness(1.5f), 0.001f)

    // Quarter position lies smoothly in (0, 1)
    val quarter = com.example.ui.components.computeSelectorPillBetweenness(0.25f)
    assertEquals(0.5f, quarter, 0.001f)
  }

  @Test
  fun `syncing one subscription atomically replaces its rows and preserves selection on another subscription`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.example.data.local.AppDatabase::class.java
    )
      .allowMainThreadQueries()
      .build()
    try {
      val store = SettingsDataStore(context)
      val repo = com.example.data.repository.ProfileRepository(
        profileDao = db.serverProfileDao(),
        subscriptionDao = db.subscriptionDao(),
        settingsDataStore = store,
        vpnPingClient = com.example.vpn.VpnPingClient(context)
      )

      val subAId = db.subscriptionDao().insert(
        com.example.data.local.SubscriptionEntity(name = "Sub A", url = "https://example.com/a")
      )
      val subBId = db.subscriptionDao().insert(
        com.example.data.local.SubscriptionEntity(name = "Sub B", url = "https://example.com/b")
      )

      val aNode = com.example.data.local.ServerProfileEntity(
        name = "A-1",
        protocol = com.example.model.VpnProtocol.VLESS,
        address = "a1.example.com",
        port = 443,
        credential = "11111111-1111-1111-1111-111111111111",
        subscriptionId = subAId
      )
      val bNode = com.example.data.local.ServerProfileEntity(
        name = "B-1",
        protocol = com.example.model.VpnProtocol.VLESS,
        address = "b1.example.com",
        port = 443,
        credential = "22222222-2222-2222-2222-222222222222",
        subscriptionId = subBId
      )

      val aInsertedId = db.serverProfileDao().insert(aNode)
      val bInsertedId = db.serverProfileDao().insert(bNode)
      store.setSelectedProfileId(bInsertedId)

      // Sync Sub A with updated node list; Sub B's selected server MUST stay selected
      val aNew = aNode.copy(id = 0L, name = "A-2", address = "a2.example.com")
      val count = repo.applySyncedSubscriptionResult(
        subscriptionId = subAId,
        fallbackName = "Sub A",
        profiles = listOf(aNew)
      )
      assertEquals(1, count)
      assertEquals(bInsertedId, store.settingsFlow.first().selectedProfileId)

      val allProfiles = db.serverProfileDao().getAllOnce()
      assertTrue(allProfiles.none { it.id == aInsertedId })
      assertTrue(allProfiles.any { it.address == "a2.example.com" })

      // If Sub A is deleted before a slow sync commits, applySyncedSubscriptionResult must abort cleanly
      repo.deleteSubscription(subAId)
      val orphanedCount = repo.applySyncedSubscriptionResult(
        subscriptionId = subAId,
        fallbackName = "Sub A",
        profiles = listOf(aNode.copy(id = 0L, address = "orphan.example.com"))
      )
      assertEquals(0, orphanedCount)
      assertTrue(db.serverProfileDao().getAllOnce().none { it.address == "orphan.example.com" })
    } finally {
      db.close()
    }
  }

  @Test
  fun `vpn session manager setDisconnected clears stale errorMessage`() {
    VpnSessionManager.setError("Network unreachable")
    assertEquals(VpnStatus.ERROR, VpnSessionManager.state.value.status)
    assertEquals("Network unreachable", VpnSessionManager.state.value.errorMessage)

    VpnSessionManager.setDisconnected()
    assertEquals(VpnStatus.DISCONNECTED, VpnSessionManager.state.value.status)
    assertEquals(null, VpnSessionManager.state.value.errorMessage)
  }
}
