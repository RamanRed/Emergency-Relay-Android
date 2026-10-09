package com.raman.emergencyrelay.relay

import android.content.Context
import com.raman.emergencyrelay.model.EmergencyMessage
import com.raman.emergencyrelay.nearby.NearbyPayload
import java.io.File

/**
 * Disk-backed store for the relay queue and the seen-message set.
 *
 * Why this exists:
 * A delay-tolerant relay must not lose messages when the app is
 * killed or the phone reboots. This store persists:
 *   - pending messages -> one file per message under files/relay/pending/<id>.msg
 *   - seen message ids -> files/relay/seen.ids (first-seen time + id per line)
 *
 * Messages are serialized with the SAME codec used on the wire
 * (NearbyPayload), so disk and network share one format.
 *
 * Loading happens on a BACKGROUND thread at startup so it never
 * blocks the UI / main thread. Any method called before loading
 * finishes blocks only the caller (expected to be a background
 * coroutine), via ensureLoaded().
 */
class PersistentMessageStore(
    context: Context
) {

    private val lock = Any()

    private val rootDir =
        File(context.applicationContext.filesDir, "relay")
            .apply { mkdirs() }

    private val pendingDir =
        File(rootDir, "pending")
            .apply { mkdirs() }

    private val seenFile =
        File(rootDir, "seen.ids")

    private val pending = LinkedHashMap<String, EmergencyMessage>()
    private val seen = LinkedHashMap<String, Long>()   // id -> first seen (ms)

    private var appendsSinceCompaction = 0

    @Volatile
    private var loaded = false

    companion object {
        const val MAX_PENDING = 200
        const val MAX_SEEN = 5000
        const val SEEN_MAX_AGE_MS = 24L * 60 * 60 * 1000   // 24 hours
        const val COMPACT_EVERY = 200
    }

    init {
        // Load off the main thread. ensureLoaded() guarantees any
        // early caller still sees a fully loaded store.
        Thread { ensureLoaded() }.start()
    }

    /*
     * Load the store from disk exactly once, under the main lock so
     * no reader sees half-loaded maps.
     */
    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            loadSeen()
            loadPending()
            loaded = true
            println(
                "[Store] Loaded ${pending.size} pending, " +
                        "${seen.size} seen ids from disk"
            )
        }
    }

    // ---------------------------------------------------------------
    // SEEN SET
    // ---------------------------------------------------------------

    /** Returns true if this id was NOT seen before (newly added). */
    fun markSeenIfNew(messageId: String): Boolean = synchronized(lock) {
        ensureLoaded()
        if (seen.containsKey(messageId)) return false
        val now = System.currentTimeMillis()
        seen[messageId] = now
        appendSeen(messageId, now)
        enforceSeenCap()
        true
    }

    fun markSeen(messageId: String) {
        markSeenIfNew(messageId)
    }

    fun hasSeen(messageId: String): Boolean = synchronized(lock) {
        ensureLoaded()
        seen.containsKey(messageId)
    }

    fun seenCount(): Int = synchronized(lock) {
        ensureLoaded()
        seen.size
    }

    // ---------------------------------------------------------------
    // PENDING QUEUE
    // ---------------------------------------------------------------

    fun storePending(message: EmergencyMessage) = synchronized(lock) {
        ensureLoaded()
        pending[message.messageId] = message
        writePendingFile(message)
        enforcePendingCap()
    }

    fun removePending(messageId: String) = synchronized(lock) {
        ensureLoaded()
        pending.remove(messageId)
        pendingFile(messageId).delete()
    }

    fun getPendingMessages(): List<EmergencyMessage> = synchronized(lock) {
        ensureLoaded()
        pending.values.toList()
    }

    fun pendingCount(): Int = synchronized(lock) {
        ensureLoaded()
        pending.size
    }

    fun clear() = synchronized(lock) {
        ensureLoaded()
        pending.clear()
        seen.clear()
        pendingDir.listFiles()?.forEach { it.delete() }
        seenFile.delete()
        appendsSinceCompaction = 0
    }

    // ---------------------------------------------------------------
    // LOADING
    // ---------------------------------------------------------------

    private fun loadPending() {
        val files = pendingDir.listFiles() ?: return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (!file.name.endsWith(".msg")) return@forEach
            try {
                val message = NearbyPayload.decode(file.readBytes())
                pending[message.messageId] = message
            } catch (_: Exception) {
                file.delete()
            }
        }
    }

    private fun loadSeen() {
        if (!seenFile.exists()) return
        val now = System.currentTimeMillis()
        try {
            seenFile.readLines().forEach { line ->
                val comma = line.indexOf(',')
                if (comma <= 0) return@forEach
                val ts = line.substring(0, comma).toLongOrNull()
                    ?: return@forEach
                val id = line.substring(comma + 1)
                if (now - ts <= SEEN_MAX_AGE_MS) {
                    seen[id] = ts
                }
            }
        } catch (_: Exception) {
            seenFile.delete()
            seen.clear()
        }
        enforceSeenCap()
        compactSeen()
    }

    // ---------------------------------------------------------------
    // SEEN FILE WRITES
    // ---------------------------------------------------------------

    private fun appendSeen(id: String, ts: Long) {
        try {
            seenFile.appendText("$ts,$id\n")
        } catch (_: Exception) {
            // In-memory set still correct; ignore.
        }
        if (++appendsSinceCompaction >= COMPACT_EVERY) {
            compactSeen()
        }
    }

    private fun compactSeen() {
        try {
            val tmp = File(rootDir, "seen.tmp")
            tmp.bufferedWriter().use { w ->
                seen.forEach { (id, ts) -> w.write("$ts,$id\n") }
            }
            tmp.renameTo(seenFile)
            appendsSinceCompaction = 0
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun enforceSeenCap() {
        val now = System.currentTimeMillis()
        val expired = seen.filterValues { now - it > SEEN_MAX_AGE_MS }.keys
        expired.forEach { seen.remove(it) }
        while (seen.size > MAX_SEEN) {
            val oldest = seen.keys.firstOrNull() ?: break
            seen.remove(oldest)
        }
    }

    // ---------------------------------------------------------------
    // PENDING FILE WRITES
    // ---------------------------------------------------------------

    private fun writePendingFile(message: EmergencyMessage) {
        try {
            val target = pendingFile(message.messageId)
            val tmp = File(pendingDir, safeName(message.messageId) + ".tmp")
            tmp.writeBytes(NearbyPayload.encode(message))
            tmp.renameTo(target)
        } catch (_: Exception) {
            // Best effort; in-memory copy is still queued.
        }
    }

    private fun enforcePendingCap() {
        if (pending.size <= MAX_PENDING) return
        val iterator = pending.keys.iterator()
        while (pending.size > MAX_PENDING && iterator.hasNext()) {
            val id = iterator.next()
            iterator.remove()
            pendingFile(id).delete()
        }
    }

    private fun pendingFile(messageId: String) =
        File(pendingDir, safeName(messageId) + ".msg")

    private fun safeName(messageId: String) =
        messageId.replace(Regex("[^A-Za-z0-9_-]"), "_")
}
