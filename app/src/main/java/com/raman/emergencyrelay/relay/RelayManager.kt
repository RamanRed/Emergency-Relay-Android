package com.raman.emergencyrelay.relay

import android.content.Context
import com.raman.emergencyrelay.NearbyConnectionManager
import com.raman.emergencyrelay.backend.BackendClient
import com.raman.emergencyrelay.backend.BackendResult
import com.raman.emergencyrelay.model.EmergencyMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Orchestrates direct-first, relay-second delivery.
 *
 * State (the seen-set and the pending queue) lives in a disk-backed
 * [PersistentMessageStore], so messages survive the app being killed
 * or the phone rebooting. [retryPending] re-attempts everything still
 * on disk, and is fired on several triggers:
 *
 *   - app start / Nearby attach
 *   - a new peer connecting (via MainActivity -> NearbyConnectionManager)
 *   - a validated internet connection returning (ConnectivityMonitor)
 *   - a periodic timer while anything is queued
 */
class RelayManager(
    context: Context,
    backendBaseUrl: String
) {

    private val backendClient =
        BackendClient(backendBaseUrl)

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /*
     * Disk-backed store. This is what makes the system a
     * delay-tolerant relay rather than a best-effort in-memory
     * forwarder that loses everything on process death.
     */
    private val store =
        PersistentMessageStore(context)

    private var nearbyManager: NearbyConnectionManager? = null

    init {
        startPeriodicRetry()
    }

    /*
     * Connect relay logic with Nearby Connections and immediately
     * retry anything that was still pending from a previous run.
     */
    fun attachNearbyManager(
        manager: NearbyConnectionManager
    ) {
        nearbyManager = manager
        println("[Relay] Nearby manager attached")
        retryPending()
    }

    /*
     * Called when this phone creates a new emergency.
     *
     * The message is persisted BEFORE the network attempt, so a
     * crash or kill during the HTTP call cannot lose the user's
     * own SOS.
     */
    fun submitEmergency(
        message: EmergencyMessage,
        onResult: (BackendResult) -> Unit
    ) {
        store.markSeen(message.messageId)
        store.storePending(message)

        println("[Relay] New emergency: ${message.messageId}")

        scope.launch {
            val result = backendClient.sendMessage(message)
            when (result) {
                is BackendResult.Delivered,
                is BackendResult.Duplicate -> {
                    println("[Relay] Direct delivery done: ${message.messageId}")
                    store.removePending(message.messageId)
                    onResult(result)
                }

                is BackendResult.Failed -> {
                    println("[Relay] Direct delivery failed: ${result.reason}")
                    // Stays pending on disk; try an opportunistic hop.
                    forwardToNearby(message)
                    onResult(result)
                }
            }
        }
    }

    /*
     * Called when an emergency arrives from another phone.
     */
    fun receiveFromPeer(
        message: EmergencyMessage,
        backendAvailable: Boolean,
        onResult: (BackendResult) -> Unit
    ) {
        // Duplicate check and seen-marking are one atomic step.
        if (!store.markSeenIfNew(message.messageId)) {
            println("[Relay] Duplicate ignored: ${message.messageId}")
            onResult(BackendResult.Duplicate(message.messageId))
            return
        }

        println("[Relay] Received emergency from peer: ${message.messageId}")

        // Persist up front for durability, then try to deliver.
        store.storePending(message)

        scope.launch {
            val result = backendClient.sendMessage(message)
            when (result) {
                is BackendResult.Delivered,
                is BackendResult.Duplicate -> {
                    println("[Relay] Peer message delivered: ${message.messageId}")
                    store.removePending(message.messageId)
                    onResult(result)
                }

                is BackendResult.Failed -> {
                    println("[Relay] Gateway delivery failed: ${result.reason}")
                    // Backend unavailable: keep stored, try another peer.
                    forwardToNearby(message)
                    onResult(result)
                }
            }
        }
    }

    /*
     * Forward a message to currently connected peers.
     * Decrements TTL and increments hop count on the forwarded copy;
     * the stored original is left intact for future retries.
     */
    private fun forwardToNearby(
        message: EmergencyMessage
    ) {
        val forwarded = message.forward()

        if (forwarded == null) {
            println("[Relay] TTL expired, not forwarding: ${message.messageId}")
            return
        }

        val manager = nearbyManager

        if (manager == null || manager.getConnectedEndpointCount() == 0) {
            println(
                "[Relay] No relay path yet, message stays pending: " +
                        message.messageId
            )
            return
        }

        println(
            "[Relay] Forwarding ${forwarded.messageId} " +
                    "hop=${forwarded.hopCount} ttl=${forwarded.ttl}"
        )

        manager.sendMessage(forwarded)
    }

    /*
     * Retry everything still pending on disk.
     *
     * For each pending message: try the backend first; if that
     * fails, try a nearby relay. This is what actually delivers
     * messages that were queued before a restart or while offline.
     */
    fun retryPending() {
        scope.launch {
            val messages = store.getPendingMessages()
            if (messages.isEmpty()) return@launch

            println("[Relay] Retrying ${messages.size} pending message(s)")

            messages.forEach { message ->
                launch {
                    val result = backendClient.sendMessage(message)
                    when (result) {
                        is BackendResult.Delivered,
                        is BackendResult.Duplicate ->
                            store.removePending(message.messageId)

                        is BackendResult.Failed ->
                            forwardToNearby(message)
                    }
                }
            }
        }
    }

    /*
     * Background safety net: while anything is queued, retry on a
     * fixed cadence even if no other trigger fires.
     */
    private fun startPeriodicRetry() {
        scope.launch {
            while (isActive) {
                delay(RETRY_INTERVAL_MS)
                if (store.pendingCount() > 0) {
                    retryPending()
                }
            }
        }
    }

    fun getPendingMessageCount(): Int = store.pendingCount()

    fun hasSeen(messageId: String): Boolean = store.hasSeen(messageId)

    fun markDelivered(messageId: String) {
        store.removePending(messageId)
        println("[Relay] Message marked delivered: $messageId")
    }

    /*
     * Stop background relay work. The disk store is untouched, so a
     * later launch resumes exactly where this one left off.
     */
    fun shutdown() {
        scope.cancel()
        println("[Relay] Relay manager shutdown")
    }

    companion object {
        private const val RETRY_INTERVAL_MS = 30_000L
    }
}
