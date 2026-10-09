package com.raman.emergencyrelay.relay

import com.raman.emergencyrelay.model.EmergencyMessage

sealed class RoutingDecision {

    data object DropDuplicate : RoutingDecision()

    data object DropExpired : RoutingDecision()

    data object DeliverToBackend : RoutingDecision()

    data object ForwardToPeer : RoutingDecision()

    data object StoreForLater : RoutingDecision()
}

class RoutingPolicy {

    fun decide(
        message: EmergencyMessage,
        alreadySeen: Boolean,
        backendAvailable: Boolean
    ): RoutingDecision {

        if (alreadySeen) {
            return RoutingDecision.DropDuplicate
        }

        if (!message.canForward()) {
            return if (backendAvailable) {
                RoutingDecision.DeliverToBackend
            } else {
                RoutingDecision.DropExpired
            }
        }

        if (backendAvailable) {
            return RoutingDecision.DeliverToBackend
        }

        return RoutingDecision.ForwardToPeer
    }
}