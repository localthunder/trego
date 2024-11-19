package com.splitter.splittr.utils

import android.util.Log
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object DateUtils {
    private const val TAG = "DateUtils"

    // Standard format for storing timestamps: "2024-05-20 04:02:40.143-07"
    private val STANDARD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSX")

    private val PARSABLE_FORMATS = listOf(
        STANDARD_FORMAT,                                      // "2024-05-20 04:02:40.143-07"
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,               // "2024-05-20T04:02:40.143-07:00"
        DateTimeFormatter.ISO_INSTANT,                        // "2024-05-20T04:02:40.143Z"
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),   // "2024-05-20 04:02:40"
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS") // "2024-05-20 04:02:40.143"
    )

    /**
     * Compares two timestamps to determine if an update is needed.
     * Returns true if serverTimestamp is more recent than localTimestamp.
     */
    fun isUpdateNeeded(
        serverTimestamp: String?,
        localTimestamp: String?,
        entityId: String? = null
    ): Boolean {
        if (serverTimestamp == null || localTimestamp == null) return false

        return try {
            val serverInstant = parseTimestamp(serverTimestamp)
            val localInstant = parseTimestamp(localTimestamp)
            val needsUpdate = serverInstant.isAfter(localInstant)

            if (entityId != null) {
                Log.d(TAG, """
                    Timestamp comparison for entity $entityId:
                    Server timestamp: $serverTimestamp (${formatTimestamp(serverInstant)})
                    Local timestamp: $localTimestamp (${formatTimestamp(localInstant)})
                    Needs update: $needsUpdate
                """.trimIndent())
            }

            needsUpdate
        } catch (e: Exception) {
            Log.e(TAG, """
                Error comparing timestamps:
                Server timestamp: $serverTimestamp
                Local timestamp: $localTimestamp
                Entity ID: $entityId
                Error: ${e.message}
            """.trimIndent())
            false
        }
    }

    /**
     * Parses a timestamp string into an Instant object.
     * Handles multiple timestamp formats.
     */
    fun parseTimestamp(timestamp: String): Instant {
        // First try parsing as milliseconds since epoch
        try {
            val millis = timestamp.toLong()
            return Instant.ofEpochMilli(millis)
        } catch (e: NumberFormatException) {
            // Not a numeric timestamp, continue with string parsing
        }

        // Try each format in sequence
        for (formatter in PARSABLE_FORMATS) {
            try {
                return when (formatter) {
                    STANDARD_FORMAT,
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME -> {
                        // These formats include timezone information
                        ZonedDateTime.parse(timestamp, formatter).toInstant()
                    }
                    DateTimeFormatter.ISO_INSTANT -> {
                        Instant.from(formatter.parse(timestamp))
                    }
                    else -> {
                        // For formats without timezone, assume UTC
                        LocalDateTime.parse(timestamp, formatter)
                            .atZone(ZoneOffset.UTC)
                            .toInstant()
                    }
                }
            } catch (e: DateTimeParseException) {
                continue // Try next format
            }
        }

        throw IllegalArgumentException("Unable to parse timestamp: $timestamp")
    }

    /**
     * Formats an Instant to the standard format: "2024-05-20 04:02:40.143-07"
     */
    fun formatTimestamp(instant: Instant): String {
        return STANDARD_FORMAT.format(instant.atZone(ZoneId.systemDefault()))
    }

    /**
     * Converts any supported timestamp format to our standard format
     */
    fun standardizeTimestamp(
        timestamp: String?,
        useCurrentTimeForNull: Boolean = false
    ): String {
        return when {
            timestamp == null -> {
                if (useCurrentTimeForNull) {
                    getCurrentTimestamp()
                } else {
                    getCurrentTimestamp() // Let's default to current time instead of throwing
                }
            }
            timestamp.isBlank() -> getCurrentTimestamp()
            else -> try {
                formatTimestamp(parseTimestamp(timestamp))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse timestamp: $timestamp, using current time")
                getCurrentTimestamp()
            }
        }
    }

    /**
     * Gets current timestamp in standard format
     */
    fun getCurrentTimestamp(): String {
        return formatTimestamp(Instant.now())
    }

    /**
     * Validates if a timestamp string is in the correct format
     */
    fun isValidTimestamp(timestamp: String): Boolean {
        return try {
            parseTimestamp(timestamp)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets a timestamp for a future time
     * @param seconds Number of seconds in the future
     */
    fun getFutureTimestamp(seconds: Long): String {
        return formatTimestamp(Instant.now().plusSeconds(seconds))
    }

    /**
     * Gets a timestamp for a past time
     * @param seconds Number of seconds in the past
     */
    fun getPastTimestamp(seconds: Long): String {
        return formatTimestamp(Instant.now().minusSeconds(seconds))
    }
}