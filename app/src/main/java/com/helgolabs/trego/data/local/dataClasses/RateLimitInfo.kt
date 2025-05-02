package com.helgolabs.trego.data.local.dataClasses

data class RateLimitInfo(
    val remainingCalls: Int,
    val maxCalls: Int = 4, // Default to 4 calls per day
    val cooldownMinutesRemaining: Long = 0,
    val timeUntilReset: java.time.Duration? = null
)