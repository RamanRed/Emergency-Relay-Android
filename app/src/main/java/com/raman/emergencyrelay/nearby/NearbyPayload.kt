package com.raman.emergencyrelay.nearby

import com.raman.emergencyrelay.model.EmergencyCode
import com.raman.emergencyrelay.model.EmergencyMessage
import com.raman.emergencyrelay.model.LocationSource
import org.json.JSONObject

object NearbyPayload {

    private const val FIELD_MESSAGE_ID = "message_id"
    private const val FIELD_SOURCE_ID = "source_id"
    private const val FIELD_EMERGENCY_CODE = "emergency_code"
    private const val FIELD_SEVERITY = "severity"
    private const val FIELD_TIMESTAMP = "timestamp"

    private const val FIELD_ORIGIN_LAT = "origin_lat"
    private const val FIELD_ORIGIN_LON = "origin_lon"

    private const val FIELD_LOCATION_SOURCE = "location_source"

    private const val FIELD_FALLBACK_LAT = "fallback_lat"
    private const val FIELD_FALLBACK_LON = "fallback_lon"

    private const val FIELD_TTL = "ttl"
    private const val FIELD_HOP_COUNT = "hop_count"

    fun encode(message: EmergencyMessage): ByteArray {

        val json = JSONObject()

        json.put(
            FIELD_MESSAGE_ID,
            message.messageId
        )

        json.put(
            FIELD_SOURCE_ID,
            message.sourceId
        )

        json.put(
            FIELD_EMERGENCY_CODE,
            message.emergencyCode.code
        )

        json.put(
            FIELD_SEVERITY,
            message.severity
        )

        json.put(
            FIELD_TIMESTAMP,
            message.timestamp
        )

        putNullableDouble(
            json,
            FIELD_ORIGIN_LAT,
            message.originLatitude
        )

        putNullableDouble(
            json,
            FIELD_ORIGIN_LON,
            message.originLongitude
        )

        json.put(
            FIELD_LOCATION_SOURCE,
            message.locationSource.code
        )

        putNullableDouble(
            json,
            FIELD_FALLBACK_LAT,
            message.fallbackLatitude
        )

        putNullableDouble(
            json,
            FIELD_FALLBACK_LON,
            message.fallbackLongitude
        )

        json.put(
            FIELD_TTL,
            message.ttl
        )

        json.put(
            FIELD_HOP_COUNT,
            message.hopCount
        )

        return json.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(data: ByteArray): EmergencyMessage {

        val jsonString = data.toString(Charsets.UTF_8)

        val json = JSONObject(jsonString)

        val emergencyCodeValue =
            json.getInt(FIELD_EMERGENCY_CODE)

        val emergencyCode =
            EmergencyCode.fromCode(emergencyCodeValue)
                ?: throw IllegalArgumentException(
                    "Invalid emergency code: $emergencyCodeValue"
                )

        val locationSourceValue =
            json.getInt(FIELD_LOCATION_SOURCE)

        val locationSource =
            LocationSource.fromCode(locationSourceValue)
                ?: throw IllegalArgumentException(
                    "Invalid location source: $locationSourceValue"
                )

        return EmergencyMessage(
            messageId = json.getString(FIELD_MESSAGE_ID),

            sourceId = json.getString(FIELD_SOURCE_ID),

            emergencyCode = emergencyCode,

            severity = json.getInt(FIELD_SEVERITY),

            timestamp = json.getLong(FIELD_TIMESTAMP),

            originLatitude = getNullableDouble(
                json,
                FIELD_ORIGIN_LAT
            ),

            originLongitude = getNullableDouble(
                json,
                FIELD_ORIGIN_LON
            ),

            locationSource = locationSource,

            fallbackLatitude = getNullableDouble(
                json,
                FIELD_FALLBACK_LAT
            ),

            fallbackLongitude = getNullableDouble(
                json,
                FIELD_FALLBACK_LON
            ),

            ttl = json.getInt(FIELD_TTL),

            hopCount = json.getInt(FIELD_HOP_COUNT)
        )
    }

    private fun putNullableDouble(
        json: JSONObject,
        key: String,
        value: Double?
    ) {
        if (value == null) {
            json.put(key, JSONObject.NULL)
        } else {
            json.put(key, value)
        }
    }

    private fun getNullableDouble(
        json: JSONObject,
        key: String
    ): Double? {

        if (
            !json.has(key) ||
            json.isNull(key)
        ) {
            return null
        }

        return json.getDouble(key)
    }
}