package com.raman.emergencyrelay.relay

import com.raman.emergencyrelay.model.EmergencyMessage
import java.util.concurrent.ConcurrentHashMap

class MessageStore {

    private val messages =
        ConcurrentHashMap<String, EmergencyMessage>()

    private val pendingMessages =
        ConcurrentHashMap<String, EmergencyMessage>()

    fun hasSeen(messageId: String): Boolean {
        return messages.containsKey(messageId)
    }

    fun markSeen(message: EmergencyMessage) {
        messages[message.messageId] = message
    }

    fun getMessage(
        messageId: String
    ): EmergencyMessage? {
        return messages[messageId]
    }

    fun storePending(
        message: EmergencyMessage
    ) {
        messages[message.messageId] = message
        pendingMessages[message.messageId] = message
    }

    fun getPendingMessages(): List<EmergencyMessage> {
        return pendingMessages.values.toList()
    }

    fun getPendingMessage(
        messageId: String
    ): EmergencyMessage? {
        return pendingMessages[messageId]
    }

    fun removePending(
        messageId: String
    ) {
        pendingMessages.remove(messageId)
    }

    fun clear() {
        messages.clear()
        pendingMessages.clear()
    }

    fun size(): Int {
        return messages.size
    }

    fun pendingSize(): Int {
        return pendingMessages.size
    }
}