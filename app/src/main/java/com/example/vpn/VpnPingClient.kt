package com.example.vpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class VpnPingClient(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val bindMutex = Mutex()
    private val idleJobLock = Any()
    private val requestSeq = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Long>>()

    @Volatile
    private var serviceMessenger: Messenger? = null

    @Volatile
    private var activeConnection: ServiceConnection? = null

    @Volatile
    private var connectingDeferred: CompletableDeferred<Messenger?>? = null

    private var idleUnbindJob: Job? = null

    private val replyMessenger = Messenger(
        object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (msg.what == XrayVpnService.MSG_PING_RESULT) {
                    val requestId = msg.arg1
                    val latency = msg.data.getLong(XrayVpnService.EXTRA_PING_LATENCY_MS, -1L)
                    pending.remove(requestId)?.complete(latency)
                    scheduleIdleUnbind()
                }
            }
        }
    )

    suspend fun measureDelay(configJson: String, host: String, port: Int): Long = withContext(Dispatchers.IO) {
        val remote = getOrBindMessenger()
        if (remote != null) {
            val requestId = requestSeq.getAndIncrement()
            val deferred = CompletableDeferred<Long>()
            cancelIdleUnbind()
            pending[requestId] = deferred

            val sent = runCatching {
                val message = Message.obtain(null, XrayVpnService.MSG_PING_REQUEST, requestId, 0).apply {
                    replyTo = replyMessenger
                    data = Bundle().apply {
                        putString(XrayVpnService.EXTRA_PING_CONFIG, configJson)
                        putString(XrayVpnService.EXTRA_PING_HOST, host)
                        putInt(XrayVpnService.EXTRA_PING_PORT, port)
                    }
                }
                remote.send(message)
                true
            }.getOrDefault(false)

            if (sent) {
                val result = withTimeoutOrNull(8_500L) { deferred.await() }
                pending.remove(requestId)
                scheduleIdleUnbind()
                if (result != null) {
                    return@withContext result
                }
            } else {
                pending.remove(requestId)
                scheduleIdleUnbind()
            }
        }

        // Pure JVM socket fallback when service binding is unavailable (e.g. Robolectric unit tests)
        measureTcpPing(host, port)
    }

    private suspend fun getOrBindMessenger(): Messenger? {
        serviceMessenger?.let { return it }

        val waiter = bindMutex.withLock {
            serviceMessenger?.let { return it }
            connectingDeferred?.let { return@withLock it }

            val deferred = CompletableDeferred<Messenger?>()
            connectingDeferred = deferred

            val intent = Intent(appContext, XrayVpnService::class.java).apply {
                action = XrayVpnService.ACTION_PING_SERVICE
            }
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val messenger = service?.let { Messenger(it) }
                    if (activeConnection === this) {
                        serviceMessenger = messenger
                        if (connectingDeferred === deferred) {
                            connectingDeferred = null
                        }
                    }
                    deferred.complete(messenger)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    if (activeConnection === this) {
                        serviceMessenger = null
                        activeConnection = null
                        if (connectingDeferred === deferred) {
                            connectingDeferred = null
                        }
                    }
                    deferred.complete(null)
                    failAllPending()
                }

                override fun onBindingDied(name: ComponentName?) {
                    if (activeConnection === this) {
                        serviceMessenger = null
                        activeConnection = null
                        if (connectingDeferred === deferred) {
                            connectingDeferred = null
                        }
                    }
                    deferred.complete(null)
                    failAllPending()
                }
            }

            activeConnection = conn
            val bound = runCatching {
                appContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)

            if (!bound) {
                activeConnection = null
                connectingDeferred = null
                deferred.complete(null)
            }
            deferred
        }

        val resolved = withTimeoutOrNull(2_500L) { waiter.await() }
        if (resolved == null) {
            bindMutex.withLock {
                if (connectingDeferred === waiter) {
                    connectingDeferred = null
                    serviceMessenger = null
                    activeConnection?.let { conn ->
                        runCatching { appContext.unbindService(conn) }
                    }
                    activeConnection = null
                    waiter.complete(null)
                }
            }
        }
        return resolved
    }

    private fun cancelIdleUnbind() {
        synchronized(idleJobLock) {
            idleUnbindJob?.cancel()
            idleUnbindJob = null
        }
    }

    private fun scheduleIdleUnbind() {
        if (pending.isNotEmpty()) return
        synchronized(idleJobLock) {
            if (pending.isEmpty()) {
                idleUnbindJob?.cancel()
                idleUnbindJob = scope.launch {
                    // Brief grace window lets batched "Ping all" calls reuse the binder, then immediately
                    // releases :vpn so the background process does not linger when VPN is off.
                    delay(500L)
                    if (pending.isEmpty()) {
                        unbind()
                    }
                }
            }
        }
    }

    private suspend fun unbind() {
        bindMutex.withLock {
            if (pending.isNotEmpty()) return
            activeConnection?.let { conn ->
                runCatching { appContext.unbindService(conn) }
            }
            activeConnection = null
            serviceMessenger = null
            connectingDeferred = null
        }
    }

    private fun failAllPending() {
        val entries = pending.entries.toList()
        entries.forEach { (id, deferred) ->
            if (pending.remove(id, deferred)) {
                deferred.complete(-1L)
            }
        }
    }

    private fun measureTcpPing(host: String, port: Int): Long {
        if (host.isBlank() || port <= 0) return -1L
        val start = System.currentTimeMillis()
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3000)
            }
            (System.currentTimeMillis() - start).coerceAtLeast(1L)
        }.getOrDefault(-1L)
    }
}
