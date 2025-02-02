package com.splitter.splittr.utils

import android.util.Log
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object DateUtils {
    private const val TAG = "DateUtils"

    // Standard formats
    private val STANDARD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSX")
    private val DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DISPLAY_FORMAT_CURRENT_YEAR = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val DISPLAY_FORMAT_FULL = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    // All supported input formats
    private val PARSABLE_FORMATS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"), // ISO 8601 with millis and timezone
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),     // ISO 8601 with timezone
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX"),   // ISO 8601 with millis and offset
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),        // ISO 8601 without timezone
        STANDARD_FORMAT,                                             // Our standard format
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,                      // Standard ISO format
        DateTimeFormatter.ISO_INSTANT,                               // Standard ISO instant
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),          // Simple datetime
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),      // Simple datetime with millis
        DATE_ONLY_FORMAT                                            // Date only
    )

    /**
     * Formats a date string for display, showing only month and day if current year
     */
    fun formatForDisplay(dateStr: String): String {
        for (formatter in PARSABLE_FORMATS) {
            try {
                val parsedDate = when {
                    formatter.toString().contains("XXX") -> {
                        ZonedDateTime.parse(dateStr, formatter)
                    }
                    dateStr.contains("T") || dateStr.contains(" ") -> {
                        LocalDateTime.parse(dateStr, formatter)
                            .atZone(ZoneId.systemDefault())
                    }
                    else -> {
                        LocalDate.parse(dateStr, formatter)
                            .atStartOfDay(ZoneId.systemDefault())
                    }
                }

                return if (parsedDate.year == Year.now().value) {
                    DISPLAY_FORMAT_CURRENT_YEAR.format(parsedDate)
                } else {
                    DISPLAY_FORMAT_FULL.format(parsedDate)
                }
            } catch (e: DateTimeParseException) {
                continue
            }
        }

        // Try parsing as Unix timestamp
        try {
            val instant = Instant.ofEpochMilli(dateStr.toLong())
            val zonedDateTime = instant.atZone(ZoneId.systemDefault())
            return if (zonedDateTime.year == Year.now().value) {
                DISPLAY_FORMAT_CURRENT_YEAR.format(zonedDateTime)
            } else {
                DISPLAY_FORMAT_FULL.format(zonedDateTime)
            }
        } catch (e: NumberFormatException) {
            // Not a numeric timestamp
        }

        Log.w(TAG, "Unable to parse date for display: $dateStr")
        return "N/A"
    }

    /**
     * Formats a date to our standard storage format (ISO 8601)
     */
    fun formatToStorageFormat(dateStr: String): String {
        return try {
            // If it's already in ISO format, just return it
            if (dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"))) {
                return dateStr
            }

            // Try parsing as Unix timestamp first
            try {
                val instant = Instant.ofEpochMilli(dateStr.toLong())
                return instant.toString()
            } catch (e: NumberFormatException) {
                // Not a numeric timestamp, continue with string parsing
            }

            // Try each format
            val instant = parseTimestamp(dateStr)
            instant.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to format date: $dateStr, using current time")
            Instant.now().toString()
        }
    }

    /**
     * Parses any supported timestamp format to Instant
     */
    fun parseTimestamp(timestamp: String): Instant {
        for (formatter in PARSABLE_FORMATS) {
            try {
                return when {
                    formatter.toString().contains("XXX") -> {
                        ZonedDateTime.parse(timestamp, formatter).toInstant()
                    }
                    timestamp.contains("T") || timestamp.contains(" ") -> {
                        LocalDateTime.parse(timestamp, formatter)
                            .atZone(ZoneOffset.UTC)
                            .toInstant()
                    }
                    else -> {
                        LocalDate.parse(timestamp, formatter)
                            .atStartOfDay(ZoneOffset.UTC)
                            .toInstant()
                    }
                }
            } catch (e: DateTimeParseException) {
                continue
            }
        }

        throw IllegalArgumentException("Unable to parse timestamp: $timestamp")
    }

    /**
     * Gets current timestamp in ISO 8601 format
     */
    fun getCurrentTimestamp(): String = Instant.now().toString()

    /**
     * Gets current date in yyyy-MM-dd format
     */
    fun getCurrentDate(): String = LocalDate.now().format(DATE_ONLY_FORMAT)

    /**
     * Converts any supported timestamp format to ISO 8601
     */
    fun standardizeTimestamp(
        timestamp: String?,
        useCurrentTimeForNull: Boolean = true
    ): String {
        return when {
            timestamp == null || timestamp.isBlank() -> {
                if (useCurrentTimeForNull) getCurrentTimestamp()
                else throw IllegalArgumentException("Timestamp cannot be null or blank")
            }
            else -> formatToStorageFormat(timestamp)
        }
    }

    /**
     * Compares two timestamps to determine if an update is needed
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
                    Server timestamp: $serverTimestamp
                    Local timestamp: $localTimestamp
                    Needs update: $needsUpdate
                """.trimIndent())
            }

            needsUpdate
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing timestamps: $e")
            false
        }
    }

    /**
     * Gets a future timestamp
     */
    fun getFutureTimestamp(seconds: Long): String =
        Instant.now().plusSeconds(seconds).toString()

    /**
     * Gets a past timestamp
     */
    fun getPastTimestamp(seconds: Long): String =
        Instant.now().minusSeconds(seconds).toString()

    /**
     * Validates if a timestamp string is in any supported format
     */
    fun isValidTimestamp(timestamp: String): Boolean {
        return try {
            parseTimestamp(timestamp)
            true
        } catch (e: Exception) {
            false
        }
    }
}