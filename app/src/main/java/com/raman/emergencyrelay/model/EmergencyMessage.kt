package com.raman.emergencyrelay.model

import java.util.UUID

enum class EmergencyCode(val code: Int) {
    MEDICAL(1),
    FIRE(2),
    POLICE(3),
    ACCIDENT(4),
    TRAPPED(5),
    MISSING_PERSON(6),
    GENERAL_SOS(7);

    companion object {
        fun fromCode(code: Int): EmergencyCode? {
            return entries.find { it.code == code }
        }
    }
}

enum class LocationSource(val code: Int) {
    UNKNOWN(0),
    ORIGIN(1),
    RELAY_DERIVED(2);

    companion object {
        fun fromCode(code: Int): LocationSource? {
            return entries.find { it.code == code }
        }
    }
}

data class EmergencyMessage(
    val messageId: String = UUID.randomUUID().toString(),
    val sourceId: String,
    val emergencyCode: EmergencyCode,
    val severity: Int,
    val timestamp: Long,
    val originLatitude: Double? = null,
    val originLongitude: Double? = null,
    val locationSource: LocationSource = LocationSource.UNKNOWN,
    val fallbackLatitude: Double? = null,
    val fallbackLongitude: Double? = null,
    val ttl: Int = DEFAULT_TTL,
    val hopCount: Int = 0
) {

    companion object {
        const val DEFAULT_TTL = 5

        const val MIN_SEVERITY = 1
        const val MAX_SEVERITY = 5

        const val MIN_LATITUDE = -90.0
        const val MAX_LATITUDE = 90.0

        const val MIN_LONGITUDE = -180.0
        const val MAX_LONGITUDE = 180.0
    }

    init {
        require(sourceId.isNotBlank()) {
            "sourceId must not be empty"
        }

        require(severity in MIN_SEVERITY..MAX_SEVERITY) {
            "severity must be between $MIN_SEVERITY and $MAX_SEVERITY"
        }

        require(ttl >= 0) {
            "ttl cannot be negative"
        }

        require(hopCount >= 0) {
            "hopCount cannot be negative"
        }

        validateCoordinatePair(
            originLatitude,
            originLongitude,
            "origin"
        )

        validateCoordinatePair(
            fallbackLatitude,
            fallbackLongitude,
            "fallback"
        )

        if (locationSource == LocationSource.ORIGIN) {
            require(
                originLatitude != null &&
                        originLongitude != null
            ) {
                "ORIGIN location source requires origin coordinates"
            }
        }

        if (locationSource == LocationSource.RELAY_DERIVED) {
            require(
                fallbackLatitude != null &&
                        fallbackLongitude != null
            ) {
                "RELAY_DERIVED location source requires fallback coordinates"
            }
        }
    }

    private fun validateCoordinatePair(
        latitude: Double?,
        longitude: Double?,
        locationName: String
    ) {
        require(
            (latitude == null) == (longitude == null)
        ) {
            "$locationName latitude and longitude must both be provided or both be null"
        }

        if (latitude == null || longitude == null) {
            return
        }

        require(
            latitude >= MIN_LATITUDE &&
                    latitude <= MAX_LATITUDE
        ) {
            "Invalid $locationName latitude: $latitude"
        }

        require(
            longitude >= MIN_LONGITUDE &&
                    longitude <= MAX_LONGITUDE
        ) {
            "Invalid $locationName longitude: $longitude"
        }
    }

    fun addFallbackLocation(
        latitude: Double,
        longitude: Double
    ): EmergencyMessage {

        if (locationSource != LocationSource.UNKNOWN) {
            return this
        }

        if (fallbackLatitude != null || fallbackLongitude != null) {
            return this
        }

        return copy(
            locationSource = LocationSource.RELAY_DERIVED,
            fallbackLatitude = latitude,
            fallbackLongitude = longitude
        )
    }

    fun getEffectiveLatitude(): Double? {
        return when (locationSource) {
            LocationSource.ORIGIN -> originLatitude
            LocationSource.RELAY_DERIVED -> fallbackLatitude
            LocationSource.UNKNOWN -> null
        }
    }

    fun getEffectiveLongitude(): Double? {
        return when (locationSource) {
            LocationSource.ORIGIN -> originLongitude
            LocationSource.RELAY_DERIVED -> fallbackLongitude
            LocationSource.UNKNOWN -> null
        }
    }

    fun getEffectiveLocationSource(): String {
        return when (locationSource) {
            LocationSource.ORIGIN -> "ORIGIN"
            LocationSource.RELAY_DERIVED -> "RELAY_APPROXIMATE"
            LocationSource.UNKNOWN -> "UNKNOWN"
        }
    }

    fun canForward(): Boolean {
        return ttl > 0
    }

    fun forward(): EmergencyMessage? {
        if (!canForward()) {
            return null
        }

        return copy(
            ttl = ttl - 1,
            hopCount = hopCount + 1
        )
    }
}