package com.raman.emergencyrelay.backend

import com.raman.emergencyrelay.model.EmergencyMessage
import com.raman.emergencyrelay.nearby.NearbyPayload
import java.net.HttpURLConnection
import java.net.URL

sealed class BackendResult {

    data class Delivered(
        val messageId: String
    ) : BackendResult()

    data class Duplicate(
        val messageId: String
    ) : BackendResult()

    data class Failed(
        val messageId: String,
        val reason: String
    ) : BackendResult()
}

class BackendClient(
    backendBaseUrl: String
) {

    // Normalise so a trailing slash can't produce '//messages' (a 404).
    private val baseUrl = backendBaseUrl.trimEnd('/')

    fun sendMessage(
        message: EmergencyMessage
    ): BackendResult {

        val messageId = message.messageId

        try {

            val url = URL(
                "$baseUrl/messages"
            )

            val connection =
                url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val requestBody =
                NearbyPayload.encode(message)

            connection.outputStream.use { output ->
                output.write(requestBody)
                output.flush()
            }

            val responseCode =
                connection.responseCode

            val responseBody =
                try {
                    connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }
                } catch (exception: Exception) {
                    connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: ""
                }

            connection.disconnect()

            return when (responseCode) {

                HttpURLConnection.HTTP_OK -> {

                    if (
                        responseBody.contains(
                            "\"status\":\"received\""
                        )
                    ) {

                        BackendResult.Delivered(
                            messageId
                        )

                    } else if (
                        responseBody.contains(
                            "\"status\":\"duplicate\""
                        )
                    ) {

                        BackendResult.Duplicate(
                            messageId
                        )

                    } else {

                        BackendResult.Failed(
                            messageId,
                            "Unexpected backend response: $responseBody"
                        )
                    }
                }

                else -> {

                    BackendResult.Failed(
                        messageId,
                        "Backend returned HTTP $responseCode"
                    )
                }
            }

        } catch (exception: Exception) {

            return BackendResult.Failed(
                messageId,
                exception.message
                    ?: "Unknown backend error"
            )
        }
    }
}