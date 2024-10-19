package com.splitter.splittr.data.extensions

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Helper function to convert from ISO 8601 string to Unix timestamp
val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

fun String?.toUnixTimestamp(): Long? {
    return if (this.isNullOrBlank() || this == "null") {
        null // or return a default value like 0L
    } else {
        try {
            LocalDateTime.parse(this, isoFormatter)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            null // Handle parsing error
        }
    }
}

fun Long.toIsoString(): String {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
        .format(DateTimeFormatter.ISO_DATE_TIME)
}