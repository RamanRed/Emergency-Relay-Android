package com.raman.emergencyrelay.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * Notifies when the phone gains a *validated* internet connection, so
 * the relay can flush its queued messages the moment a usable path
 * appears — not merely when a network is connected but captive or
 * without real internet.
 *
 * Requires the ACCESS_NETWORK_STATE permission (manifest).
 */
class ConnectivityMonitor(
    context: Context,
    private val onInternetAvailable: () -> Unit
) {

    private val connectivityManager =
        context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager

    private val callback =
        object : ConnectivityManager.NetworkCallback() {

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                val validated = capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )
                if (validated) {
                    onInternetAvailable()
                }
            }
        }

    fun start() {
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            // Ignore; the queue still retries on other triggers.
        }
    }

    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            // Not registered; ignore.
        }
    }
}
