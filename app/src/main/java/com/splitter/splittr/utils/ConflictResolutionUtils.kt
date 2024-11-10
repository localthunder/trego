package com.splitter.splittr.utils

import android.util.Log
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Define an interface for entities with timestamps
interface TimestampedEntity {
    val updatedAt: String
}

// Utility object for conflict resolution
object ConflictResolver {
    /**
     * Resolves conflicts between local and server entities based on updatedAt timestamp
     * @param local The local version of the entity
     * @param server The server version of the entity
     * @param defaultToServer Whether to default to server version if timestamps can't be compared
     * @return The entity that should be kept
     */
    fun <T : TimestampedEntity> resolve(
        local: T,
        server: T,
        defaultToServer: Boolean = true
    ): ConflictResolution<T> {
        return try {
            val serverTimestamp = server.updatedAt.toInstant()
            val localTimestamp = local.updatedAt.toInstant()

            when {
                serverTimestamp > localTimestamp -> ConflictResolution.ServerWins(server)
                localTimestamp > serverTimestamp -> ConflictResolution.LocalWins(local)
                else -> if (defaultToServer) {
                    ConflictResolution.ServerWins(server)
                } else {
                    ConflictResolution.LocalWins(local)
                }
            }
        } catch (e: Exception) {
            Log.e("ConflictResolver", "Error comparing timestamps", e)
            if (defaultToServer) {
                ConflictResolution.ServerWins(server)
            } else {
                ConflictResolution.LocalWins(local)
            }
        }
    }

    /**
     * Converts a timestamp string to Instant
     * Supports multiple common timestamp formats
     */
    private fun String.toInstant(): Instant {
        return try {
            // Try parsing as ISO 8601
            Instant.parse(this)
        } catch (e: Exception) {
            try {
                // Try parsing as milliseconds since epoch
                Instant.ofEpochMilli(this.toLong())
            } catch (e: Exception) {
                try {
                    // Try parsing common datetime format
                    val formatter = DateTimeFormatter.ofPattern(
                        "yyyy-MM-dd HH:mm:ss.SSS"
                    )
                    LocalDateTime.parse(this, formatter)
                        .atZone(ZoneOffset.UTC)
                        .toInstant()
                } catch (e: Exception) {
                    throw IllegalArgumentException("Unable to parse timestamp: $this", e)
                }
            }
        }
    }
}

// Sealed class to represent resolution result
sealed class ConflictResolution<out T : TimestampedEntity> {
    data class ServerWins<T : TimestampedEntity>(val entity: T) : ConflictResolution<T>()
    data class LocalWins<T : TimestampedEntity>(val entity: T) : ConflictResolution<T>()
}