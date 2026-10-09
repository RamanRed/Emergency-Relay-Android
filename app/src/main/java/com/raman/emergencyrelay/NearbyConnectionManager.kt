package com.raman.emergencyrelay

import android.content.Context
import android.os.Build
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.raman.emergencyrelay.model.EmergencyMessage
import com.raman.emergencyrelay.nearby.NearbyPayload

class NearbyConnectionManager(
    private val context: Context,
    private val onMessageReceived: (EmergencyMessage) -> Unit,
    private val onPeerConnected: () -> Unit = {}
) {

    companion object {

        private const val SERVICE_ID =
            "com.raman.emergencyrelay"

        private val STRATEGY =
            Strategy.P2P_CLUSTER
    }

    private val connectionsClient =
        Nearby.getConnectionsClient(context)

    /*
     * Stable, unique id for this install. Used as the advertised
     * endpoint name and for the connection tie-break below.
     */
    private val nodeId =
        DeviceIdentity.nodeId(context)

    private val connectedEndpoints =
        mutableSetOf<String>()

    private val pendingEndpoints =
        mutableSetOf<String>()

    /*
     * Connections initiated by this phone.
     */
    private val outgoingEndpoints =
        mutableSetOf<String>()

    private val endpointNames =
        mutableMapOf<String, String>()

    /*
     * Explicit application-level relay consent.
     */
    private var relayEnabled = false

    private val payloadCallback =
        object : PayloadCallback() {

            override fun onPayloadReceived(
                endpointId: String,
                payload: Payload
            ) {

                if (
                    payload.type != Payload.Type.BYTES
                ) {

                    println(
                        "[Nearby] Unsupported payload type"
                    )

                    return
                }

                val bytes =
                    payload.asBytes()
                        ?: return

                try {

                    val message =
                        NearbyPayload.decode(bytes)

                    println(
                        "[Nearby] Message received: " +
                                message.messageId
                    )

                    /*
                     * Only explicitly participating relay
                     * devices process incoming messages.
                     */
                    if (!relayEnabled) {

                        println(
                            "[Nearby] Relay Mode OFF. " +
                                    "Ignoring incoming message."
                        )

                        return
                    }

                    onMessageReceived(message)

                } catch (exception: Exception) {

                    println(
                        "[Nearby] Decode failed: " +
                                exception.message
                    )
                }
            }

            override fun onPayloadTransferUpdate(
                endpointId: String,
                update: PayloadTransferUpdate
            ) {

                println(
                    "[Nearby] Payload transfer: " +
                            "$endpointId status=${update.status}"
                )
            }
        }

    private val connectionLifecycleCallback =
        object : ConnectionLifecycleCallback() {

            override fun onConnectionInitiated(
                endpointId: String,
                connectionInfo: ConnectionInfo
            ) {

                println(
                    "[Nearby] Connection initiated: " +
                            endpointId
                )

                endpointNames[
                    endpointId
                ] = connectionInfo.endpointName

                pendingEndpoints.add(
                    endpointId
                )

                /*
                 * Accept if:
                 *
                 * 1. This phone has explicitly enabled Relay Mode.
                 * OR
                 *
                 * 2. This phone itself initiated the connection
                 *    to a discovered relay.
                 */
                val shouldAccept =
                    relayEnabled ||
                            outgoingEndpoints.contains(
                                endpointId
                            )

                if (shouldAccept) {

                    acceptConnection(
                        endpointId
                    )

                } else {

                    rejectConnection(
                        endpointId
                    )
                }
            }

            override fun onConnectionResult(
                endpointId: String,
                result: ConnectionResolution
            ) {

                pendingEndpoints.remove(
                    endpointId
                )

                outgoingEndpoints.remove(
                    endpointId
                )

                if (result.status.isSuccess) {

                    connectedEndpoints.add(
                        endpointId
                    )

                    println(
                        "[Nearby] Connection established: " +
                                endpointId
                    )

                    /*
                     * A new relay path just appeared: ask the relay
                     * logic to flush anything that is still pending.
                     */
                    onPeerConnected()

                } else {

                    connectedEndpoints.remove(
                        endpointId
                    )

                    println(
                        "[Nearby] Connection failed: " +
                                endpointId
                    )
                }
            }

            override fun onDisconnected(
                endpointId: String
            ) {

                connectedEndpoints.remove(
                    endpointId
                )

                pendingEndpoints.remove(
                    endpointId
                )

                outgoingEndpoints.remove(
                    endpointId
                )

                endpointNames.remove(
                    endpointId
                )

                println(
                    "[Nearby] Disconnected: " +
                            endpointId
                )
            }
        }

    private val endpointDiscoveryCallback =
        object : EndpointDiscoveryCallback() {

            override fun onEndpointFound(
                endpointId: String,
                info: DiscoveredEndpointInfo
            ) {

                println(
                    "[Nearby] Endpoint found: " +
                            "${info.endpointName}"
                )

                endpointNames[
                    endpointId
                ] = info.endpointName

                if (
                    endpointId in connectedEndpoints ||
                    endpointId in pendingEndpoints
                ) {
                    return
                }

                /*
                 * We can discover relay devices even when
                 * our own Relay Mode is OFF.
                 *
                 * This is required for sending an SOS.
                 *
                 * Connection tie-break (prevents both phones from
                 * calling requestConnection at the same time, which
                 * causes connection collisions):
                 *
                 *  - If our Relay Mode is OFF we are a sender only;
                 *    the peer cannot discover us, so we MUST initiate.
                 *  - If both sides are advertising, only the side
                 *    with the smaller nodeId initiates; the other
                 *    waits and accepts.
                 */
                val remoteName = info.endpointName
                val shouldInitiate =
                    !relayEnabled || nodeId < remoteName

                if (!shouldInitiate) {
                    println(
                        "[Nearby] Waiting for peer to initiate: " +
                                endpointId
                    )
                    return
                }

                pendingEndpoints.add(
                    endpointId
                )

                outgoingEndpoints.add(
                    endpointId
                )

                requestConnection(
                    endpointId
                )
            }

            override fun onEndpointLost(
                endpointId: String
            ) {

                pendingEndpoints.remove(
                    endpointId
                )

                outgoingEndpoints.remove(
                    endpointId
                )

                println(
                    "[Nearby] Endpoint lost: " +
                            endpointId
                )
            }
        }

    /*
     * Enable or disable explicit relay participation.
     */
    fun setRelayEnabled(
        enabled: Boolean
    ) {

        relayEnabled = enabled

        println(
            "[Nearby] Relay Mode = $enabled"
        )

        if (enabled) {

            startAdvertising(
                deviceName = nodeId
            )

            startDiscovery()

        } else {

            stopAdvertising()
            stopDiscovery()

            connectionsClient.stopAllEndpoints()

            connectedEndpoints.clear()
            pendingEndpoints.clear()
            outgoingEndpoints.clear()
            endpointNames.clear()
        }
    }

    /*
     * Advertise this phone as a relay.
     *
     * This should only be called after explicit
     * Relay Mode consent.
     */
    fun startAdvertising(
        deviceName: String
    ) {

        val options =
            AdvertisingOptions.Builder()
                .setStrategy(STRATEGY)
                .build()

        connectionsClient
            .startAdvertising(
                deviceName,
                SERVICE_ID,
                connectionLifecycleCallback,
                options
            )
            .addOnSuccessListener {

                println(
                    "[Nearby] Advertising started"
                )
            }
            .addOnFailureListener { error ->

                println(
                    "[Nearby] Advertising failed: " +
                            error.message
                )
            }
    }

    /*
     * Search for nearby participating devices.
     */
    fun startDiscovery() {

        val options =
            DiscoveryOptions.Builder()
                .setStrategy(STRATEGY)
                .build()

        connectionsClient
            .startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                options
            )
            .addOnSuccessListener {

                println(
                    "[Nearby] Discovery started"
                )
            }
            .addOnFailureListener { error ->

                println(
                    "[Nearby] Discovery failed: " +
                            error.message
                )
            }
    }

    private fun requestConnection(
        endpointId: String
    ) {

        println(
            "[Nearby] Requesting connection: " +
                    endpointId
        )

        connectionsClient
            .requestConnection(
                nodeId,
                endpointId,
                connectionLifecycleCallback
            )
            .addOnSuccessListener {

                println(
                    "[Nearby] Connection request sent"
                )
            }
            .addOnFailureListener { error ->

                pendingEndpoints.remove(
                    endpointId
                )

                outgoingEndpoints.remove(
                    endpointId
                )

                println(
                    "[Nearby] Connection request failed: " +
                            error.message
                )
            }
    }

    private fun acceptConnection(
        endpointId: String
    ) {

        println(
            "[Nearby] Accepting connection: " +
                    endpointId
        )

        connectionsClient
            .acceptConnection(
                endpointId,
                payloadCallback
            )
            .addOnSuccessListener {

                println(
                    "[Nearby] Connection accepted"
                )
            }
            .addOnFailureListener { error ->

                println(
                    "[Nearby] Accept failed: " +
                            error.message
                )
            }
    }

    private fun rejectConnection(
        endpointId: String
    ) {

        println(
            "[Nearby] Rejecting connection: " +
                    endpointId
        )

        connectionsClient
            .rejectConnection(endpointId)
            .addOnSuccessListener {

                pendingEndpoints.remove(
                    endpointId
                )

            }
            .addOnFailureListener { error ->

                println(
                    "[Nearby] Reject failed: " +
                            error.message
                )
            }
    }

    fun sendMessage(
        message: EmergencyMessage
    ) {

        if (connectedEndpoints.isEmpty()) {

            println(
                "[Nearby] No connected endpoints"
            )

            return
        }

        val payload =
            Payload.fromBytes(
                NearbyPayload.encode(message)
            )

        val endpoints =
            connectedEndpoints.toList()

        println(
            "[Nearby] Sending ${message.messageId} " +
                    "to ${endpoints.size} endpoint(s)"
        )

        connectionsClient
            .sendPayload(
                endpoints,
                payload
            )
            .addOnSuccessListener {

                println(
                    "[Nearby] Message sent: " +
                            message.messageId
                )
            }
            .addOnFailureListener { error ->

                println(
                    "[Nearby] Send failed: " +
                            error.message
                )
            }
    }

    fun getConnectedEndpointCount(): Int {
        return connectedEndpoints.size
    }

    fun isRelayEnabled(): Boolean {
        return relayEnabled
    }

    fun stopAdvertising() {

        connectionsClient.stopAdvertising()

        println(
            "[Nearby] Advertising stopped"
        )
    }

    fun stopDiscovery() {

        connectionsClient.stopDiscovery()

        println(
            "[Nearby] Discovery stopped"
        )
    }

    fun stopAllEndpoints() {

        connectionsClient.stopAllEndpoints()

        connectedEndpoints.clear()
        pendingEndpoints.clear()
        outgoingEndpoints.clear()
        endpointNames.clear()

        println(
            "[Nearby] All endpoints stopped"
        )
    }
}