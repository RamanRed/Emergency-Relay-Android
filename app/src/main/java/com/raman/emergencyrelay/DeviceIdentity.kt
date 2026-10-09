package com.raman.emergencyrelay

import android.content.Context
import java.util.UUID

/**
 * Stable, random, per-install device identity.
 *
 * Replaces Build.MODEL, which every phone of the same model shares
 * (breaking deduplication, rate limiting, and the connection
 * tie-break). This id is unique per install, persists across
 * launches, and is not tied to hardware, so it does not expose the
 * device.
 */
object DeviceIdentity {

    private const val PREFS = "device_identity"
    private const val KEY_NODE_ID = "node_id"

    @Volatile
    private var cached: String? = null

    fun nodeId(context: Context): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            var id = prefs.getString(KEY_NODE_ID, null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_NODE_ID, id).apply()
            }
            cached = id
            return id
        }
    }
}
