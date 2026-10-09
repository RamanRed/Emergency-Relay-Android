package com.raman.emergencyrelay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.raman.emergencyrelay.backend.BackendResult
import com.raman.emergencyrelay.model.EmergencyCode
import com.raman.emergencyrelay.model.EmergencyMessage
import com.raman.emergencyrelay.model.LocationSource
import com.raman.emergencyrelay.net.ConnectivityMonitor
import com.raman.emergencyrelay.relay.RelayManager
import com.raman.emergencyrelay.ui.theme.EmergencyRelayTheme
import com.raman.emergencyrelay.ui.theme.EmergencyScreen
import java.util.UUID

class MainActivity : ComponentActivity() {

    /*
     * Hosted backend. For a physical phone this must be an HTTPS
     * endpoint (the emulator could use http://10.0.2.2:8000).
     */
    private val backendBaseUrl =
        "https://emergency-connect-tan.vercel.app"

    private lateinit var relayManager: RelayManager
    private lateinit var nearbyManager: NearbyConnectionManager
    private lateinit var connectivityMonitor: ConnectivityMonitor

    private var relayEnabled by mutableStateOf(false)

    /*
     * How many messages are queued on disk, waiting for a path.
     * Shown to the user so an SOS never looks silently stuck.
     */
    private var pendingCount by mutableStateOf(0)

    private var status by mutableStateOf(
        "Ready to send an emergency signal"
    )

    /*
     * Messages received from nearby phones, shown in the Received tab.
     */
    private var receivedMessages by mutableStateOf(
        listOf<EmergencyMessage>()
    )

    private var waitingForRelayPermission = false

    /*
     * Android Nearby permission launcher
     */
    private val nearbyPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val allGranted =
                permissions.values.all { it }

            if (
                allGranted &&
                waitingForRelayPermission
            ) {

                waitingForRelayPermission = false

                enableRelayAfterPermission()

            } else {

                waitingForRelayPermission = false

                status =
                    "Nearby permission is required for relay communication"
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        /*
         * -----------------------------------------
         * RELAY MANAGER
         * -----------------------------------------
         */

        relayManager =
            RelayManager(
                context = this,
                backendBaseUrl = backendBaseUrl
            )

        /*
         * -----------------------------------------
         * NEARBY CONNECTION MANAGER
         * -----------------------------------------
         *
         * onMessageReceived: an emergency arrived from a peer.
         * onPeerConnected:   a new relay path appeared, so flush
         *                    anything that is still queued.
         */

        nearbyManager =
            NearbyConnectionManager(
                context = this,

                onMessageReceived = { message ->

                    runOnUiThread {

                        if (
                            receivedMessages.none {
                                it.messageId ==
                                        message.messageId
                            }
                        ) {

                            receivedMessages =
                                receivedMessages + message
                        }

                        status =
                            "Emergency received from nearby device"
                    }

                    relayManager.receiveFromPeer(

                        message = message,

                        backendAvailable = true,

                        onResult = { result ->

                            runOnUiThread {

                                when (result) {

                                    is BackendResult.Delivered ->
                                        status =
                                            "Emergency forwarded to backend"

                                    is BackendResult.Duplicate ->
                                        status =
                                            "Duplicate emergency ignored"

                                    is BackendResult.Failed ->
                                        status =
                                            "Backend unavailable — message queued"
                                }

                                refreshPending()
                            }
                        }
                    )
                },

                onPeerConnected = {
                    relayManager.retryPending()
                    runOnUiThread { refreshPending() }
                }
            )

        /*
         * Give RelayManager access to Nearby Connections.
         * This also retries anything left pending from a prior run.
         */
        relayManager.attachNearbyManager(
            nearbyManager
        )

        /*
         * -----------------------------------------
         * CONNECTIVITY MONITOR
         * -----------------------------------------
         *
         * When a validated internet path returns, flush the queue.
         */

        connectivityMonitor =
            ConnectivityMonitor(
                context = this,
                onInternetAvailable = {
                    relayManager.retryPending()
                    runOnUiThread { refreshPending() }
                }
            )

        connectivityMonitor.start()

        refreshPending()

        /*
         * -----------------------------------------
         * UI
         * -----------------------------------------
         */

        setContent {

            EmergencyRelayTheme {

                EmergencyScreen(

                    status = status,

                    relayEnabled = relayEnabled,

                    pendingCount = pendingCount,

                    receivedMessages = receivedMessages,

                    onSendEmergency = {
                            emergencyCode,
                            severity ->

                        sendEmergency(
                            emergencyCode = emergencyCode,
                            severity = severity
                        )
                    },

                    onRelayToggle = { enabled ->

                        if (enabled) {

                            requestRelayAccess()

                        } else {

                            disableRelay()
                        }
                    },

                    onCall = { number ->
                        callNumber(number)
                    }
                )
            }
        }
    }

    /*
     * =========================================
     * SEND EMERGENCY
     * =========================================
     */

    private fun sendEmergency(
        emergencyCode: EmergencyCode,
        severity: Int
    ) {

        val message =
            EmergencyMessage(

                messageId =
                    UUID.randomUUID().toString(),

                /*
                 * Stable per-install id, not the phone model, so
                 * two phones of the same model are distinguishable.
                 */
                sourceId =
                    DeviceIdentity.nodeId(this@MainActivity),

                emergencyCode =
                    emergencyCode,

                severity =
                    severity,

                timestamp =
                    System.currentTimeMillis(),

                /*
                 * Location is not collected by the app yet.
                 */
                originLatitude =
                    null,

                originLongitude =
                    null,

                locationSource =
                    LocationSource.UNKNOWN,

                fallbackLatitude =
                    null,

                fallbackLongitude =
                    null,

                ttl =
                    EmergencyMessage.DEFAULT_TTL,

                hopCount =
                    0
            )

        status =
            "Sending emergency..."

        relayManager.submitEmergency(

            message = message,

            onResult = { result ->

                runOnUiThread {

                    when (result) {

                        is BackendResult.Delivered ->
                            status =
                                "Emergency delivered to backend"

                        is BackendResult.Duplicate ->
                            status =
                                "Emergency already received"

                        is BackendResult.Failed -> {

                            status =
                                "No direct connection — searching for relay"

                            startSenderDiscovery()
                        }
                    }

                    refreshPending()
                }
            }
        )
    }

    /*
     * =========================================
     * OUTBOUND NEARBY DISCOVERY
     * =========================================
     */

    private fun startSenderDiscovery() {

        if (!hasNearbyPermissions()) {

            status =
                "Nearby access required for offline relay"

            return
        }

        nearbyManager.startDiscovery()

        status =
            "Searching for a nearby relay phone..."
    }

    /*
     * =========================================
     * PENDING COUNT
     * =========================================
     */

    private fun refreshPending() {
        pendingCount = relayManager.getPendingMessageCount()
    }

    /*
     * Open the phone dialer with an emergency number pre-filled.
     * Uses ACTION_DIAL, so it needs no calling permission and the
     * user still presses the call button themselves.
     */
    private fun callNumber(number: String) {
        try {
            startActivity(
                Intent(
                    Intent.ACTION_DIAL,
                    Uri.parse("tel:$number")
                )
            )
        } catch (e: Exception) {
            status = "Could not open the dialer for $number"
        }
    }

    /*
     * =========================================
     * RELAY CONSENT + PERMISSION
     * =========================================
     */

    private fun requestRelayAccess() {

        if (hasNearbyPermissions()) {

            enableRelayAfterPermission()

            return
        }

        waitingForRelayPermission = true

        nearbyPermissionLauncher.launch(
            nearbyPermissions()
        )
    }

    private fun enableRelayAfterPermission() {

        relayEnabled = true

        nearbyManager.setRelayEnabled(
            true
        )

        status =
            "Relay Mode ON — looking for nearby devices"
    }

    private fun disableRelay() {

        relayEnabled = false

        nearbyManager.setRelayEnabled(
            false
        )

        status =
            "Relay Mode OFF"
    }

    /*
     * =========================================
     * CHECK NEARBY PERMISSIONS
     * =========================================
     */

    private fun hasNearbyPermissions(): Boolean {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.S
        ) {
            return true
        }

        return nearbyPermissions().all { permission ->

            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun nearbyPermissions(): Array<String> {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            arrayOf(

                Manifest.permission.BLUETOOTH_SCAN,

                Manifest.permission.BLUETOOTH_CONNECT,

                Manifest.permission.BLUETOOTH_ADVERTISE
            )

        } else {

            emptyArray()
        }
    }

    /*
     * =========================================
     * CLEANUP
     * =========================================
     */

    override fun onDestroy() {

        connectivityMonitor.stop()

        nearbyManager.stopAdvertising()

        nearbyManager.stopDiscovery()

        nearbyManager.stopAllEndpoints()

        relayManager.shutdown()

        super.onDestroy()
    }
}
