package com.helgolabs.trego.data.cache

import android.content.Context
import android.util.Log
import com.helgolabs.trego.data.extensions.toJson
import com.helgolabs.trego.data.local.dao.CachedTransactionDao
import com.helgolabs.trego.data.local.dataClasses.RateLimitInfo
import com.helgolabs.trego.data.local.entities.CachedTransactionEntity
import com.helgolabs.trego.data.model.Transaction
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class TransactionCacheManager(
    private val context: Context,
    private val cachedTransactionDao: CachedTransactionDao
) {
    companion object {
        private const val TAG = "TransactionCacheManager"
        private const val CACHE_DURATION_HOURS = 23L // Slightly less than 24h to be safe
        private const val MAX_API_CALLS_PER_DAY = 4
        private const val RATE_LIMIT_KEY = "transaction_api_calls"
        private const val LAST_API_CALL_KEY = "last_api_call_timestamp"
        private const val FIRST_API_CALL_KEY = "first_api_call_timestamp" // New key for tracking first call
        private const val COOLDOWN_MINUTES = 30L
        private const val PREFERENCES_NAME = "transaction_cache_prefs"
        private val LOW_ACTIVITY_HOURS = 0..6 // Midnight to 6 AM
        private const val REFRESH_PRIORITY_THRESHOLD = 50.0
    }

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun shouldRefreshCache(userId: Int): Boolean {
        // Check cooldown period first
        val lastApiCallTime = preferences.getLong(LAST_API_CALL_KEY, 0)
        val timeSinceLastCall = Duration.between(
            Instant.ofEpochMilli(lastApiCallTime),
            Instant.now()
        )

        if (timeSinceLastCall.toMinutes() < COOLDOWN_MINUTES) {
            Log.d(TAG, "In cooldown period (${COOLDOWN_MINUTES - timeSinceLastCall.toMinutes()} minutes remaining)")
            return false
        }

        val currentTime = System.currentTimeMillis()
        val lastFetchTimestamp = cachedTransactionDao.getLastFetchTimestamp(userId) ?: 0
        val timeSinceLastFetch = Duration.between(
            Instant.ofEpochMilli(lastFetchTimestamp),
            Instant.ofEpochMilli(currentTime)
        )

        val currentApiCalls = getApiCallsToday()

        // If we're near the API reset time and have used all calls, refresh
        if (currentApiCalls >= MAX_API_CALLS_PER_DAY) {
            val timeUntilReset = getTimeUntilApiReset()
            if (timeUntilReset.toMinutes() < 30) {
                Log.d(TAG, "Near API reset time, proceeding with refresh")
                return true
            }
            return false
        }

        // Reserve last API call for manual refresh
        if (currentApiCalls == MAX_API_CALLS_PER_DAY - 1) {
            Log.d(TAG, "Only one API call remaining, reserving for manual refresh")
            return false
        }

        // Calculate refresh priority
        val refreshPriority = calculateRefreshPriority(
            timeSinceLastFetch = timeSinceLastFetch,
            remainingApiCalls = MAX_API_CALLS_PER_DAY - currentApiCalls,
            userId = userId
        )

        Log.d(TAG, "Refresh priority calculated: $refreshPriority")
        return refreshPriority > REFRESH_PRIORITY_THRESHOLD
    }

    private suspend fun calculateRefreshPriority(
        timeSinceLastFetch: Duration,
        remainingApiCalls: Int,
        userId: Int
    ): Double {
        var priority = 0.0

        // Base priority from cache age (50% of total weight)
        priority += (timeSinceLastFetch.toHours().toDouble() / CACHE_DURATION_HOURS) * 50

        // Time of day factor (30% of total weight)
        val currentHour = LocalDateTime.now().hour
        val isLowActivityPeriod = currentHour in LOW_ACTIVITY_HOURS

        if (!isLowActivityPeriod) {
            priority += 30  // Higher priority during active hours
        }

        // User activity factor (20% of total weight)
        val recentUserActivity = getRecentUserActivity(userId)
        if (recentUserActivity) {
            priority += 20  // Higher priority for active users
        }

        Log.d(TAG, """
            Priority Breakdown:
            - Cache Age Priority: ${(timeSinceLastFetch.toHours().toDouble() / CACHE_DURATION_HOURS) * 50}
            - Time of Day Priority: ${if (!isLowActivityPeriod) 30 else 0}
            - User Activity Priority: ${if (recentUserActivity) 20 else 0}
            - Total Priority: $priority
        """.trimIndent())

        return priority
    }

    /**
     * Get detailed information about the current rate limit status
     */
    fun getRateLimitInfo(): RateLimitInfo {
        val remainingCalls = getRemainingApiCalls()
        val cooldownMinutesRemaining = getCooldownTimeRemaining()
        val timeUntilReset = getTimeUntilApiReset()

        return RateLimitInfo(
            remainingCalls = remainingCalls,
            maxCalls = MAX_API_CALLS_PER_DAY,
            cooldownMinutesRemaining = cooldownMinutesRemaining,
            timeUntilReset = timeUntilReset
        )
    }

    /**
     * Get time until API call counter resets (24 hours from first call)
     */
    fun getTimeUntilApiReset(): Duration {
        val firstCallTimestamp = preferences.getLong(FIRST_API_CALL_KEY, 0)

        // If no calls yet made today, return zero duration
        if (firstCallTimestamp == 0L) {
            return Duration.ZERO
        }

        // Calculate reset time (24 hours after first call)
        val resetTime = Instant.ofEpochMilli(firstCallTimestamp).plus(24, ChronoUnit.HOURS)
        return Duration.between(Instant.now(), resetTime)
    }

    private suspend fun getRecentUserActivity(userId: Int): Boolean {
        val lastActionTimestamp = preferences.getLong("last_user_action_$userId", 0)
        return Duration.between(
            Instant.ofEpochMilli(lastActionTimestamp),
            Instant.now()
        ).toHours() < 1
    }

    fun updateUserActivity(userId: Int) {
        preferences.edit().putLong(
            "last_user_action_$userId",
            System.currentTimeMillis()
        ).apply()
    }

    fun forceRefresh(userId: Int): Boolean {
        // Allow refresh if manually requested, regardless of cooldown
        val currentApiCalls = getApiCallsToday()
        return currentApiCalls < MAX_API_CALLS_PER_DAY ||
                getTimeUntilApiReset().toMinutes() < 30
    }

    /**
     * Cache transactions and increment API call count
     */
    suspend fun cacheTransactions(userId: Int, transactions: List<Transaction>) {
        val currentTime = System.currentTimeMillis()
        val expiryTime = currentTime + Duration.ofHours(CACHE_DURATION_HOURS).toMillis()

        val cachedEntities = transactions.map { transaction ->
            CachedTransactionEntity(
                transactionId = transaction.transactionId,
                userId = userId,
                transactionData = transaction.toJson(),
                fetchTimestamp = currentTime,
                expiryTimestamp = expiryTime
            )
        }

        cachedTransactionDao.insertCachedTransactions(cachedEntities)
        incrementApiCallCount()

        // Update last API call timestamp
        preferences.edit().putLong(LAST_API_CALL_KEY, currentTime).apply()
        Log.d(TAG, "Cached ${transactions.size} transactions for user $userId")
    }

    /**
     * Cache account-specific transactions without affecting the rate limit
     */
    suspend fun cacheAccountTransactions(userId: Int, transactions: List<Transaction>, isAccountSpecific: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        val expiryTime = currentTime + Duration.ofHours(CACHE_DURATION_HOURS).toMillis()

        val cachedEntities = transactions.map { transaction ->
            CachedTransactionEntity(
                transactionId = transaction.transactionId,
                userId = userId,
                transactionData = transaction.toJson(),
                fetchTimestamp = currentTime,
                expiryTimestamp = expiryTime
            )
        }

        cachedTransactionDao.insertCachedTransactions(cachedEntities)

        // Only increment API call count if it's not an account-specific fetch
        if (!isAccountSpecific) {
            incrementApiCallCount()
        }

        // Always update last API call timestamp for cooldown purposes
        preferences.edit().putLong(LAST_API_CALL_KEY, currentTime).apply()
        Log.d(TAG, "Cached ${transactions.size} transactions for user $userId" +
                if (isAccountSpecific) " (account-specific fetch, no rate limit impact)" else "")
    }

    /**
     * Get the number of API calls made today in the rolling 24-hour window
     */
    private fun getApiCallsToday(): Int {
        val firstCallTimestamp = preferences.getLong(FIRST_API_CALL_KEY, 0)
        val currentTime = System.currentTimeMillis()

        // If first call is more than 24 hours ago or no calls yet, reset counter
        if (firstCallTimestamp == 0L ||
            Duration.between(
                Instant.ofEpochMilli(firstCallTimestamp),
                Instant.ofEpochMilli(currentTime)
            ).toHours() >= 24) {

            preferences.edit()
                .putLong(FIRST_API_CALL_KEY, 0)
                .putInt(RATE_LIMIT_KEY, 0)
                .apply()
            return 0
        }

        return preferences.getInt(RATE_LIMIT_KEY, 0)
    }

    /**
     * Increment the API call count and update first call timestamp if needed
     */
    private fun incrementApiCallCount() {
        val currentCount = getApiCallsToday()
        val currentTime = System.currentTimeMillis()
        val firstCallTimestamp = preferences.getLong(FIRST_API_CALL_KEY, 0)

        // If this is the first call in the window, set the first call timestamp
        if (currentCount == 0 || firstCallTimestamp == 0L) {
            preferences.edit()
                .putLong(FIRST_API_CALL_KEY, currentTime)
                .putInt(RATE_LIMIT_KEY, 1)
                .apply()
            Log.d(TAG, "First API call of the window at: ${Instant.ofEpochMilli(currentTime)}")
        } else {
            preferences.edit()
                .putInt(RATE_LIMIT_KEY, currentCount + 1)
                .apply()
            Log.d(TAG, "API call count incremented to: ${currentCount + 1}")
        }
    }

    suspend fun clearExpiredCache() {
        cachedTransactionDao.clearExpiredTransactions()
    }

    suspend fun forceRefreshCache(userId: Int) {
        if (getApiCallsToday() < MAX_API_CALLS_PER_DAY) {
            cachedTransactionDao.clearUserTransactions(userId)
            // The actual refresh will be triggered by the repository
        }
    }

    fun getRemainingApiCalls(): Int {
        return MAX_API_CALLS_PER_DAY - getApiCallsToday()
    }

    fun getCooldownTimeRemaining(): Long {
        val lastApiCallTime = preferences.getLong(LAST_API_CALL_KEY, 0)
        val timeSinceLastCall = Duration.between(
            Instant.ofEpochMilli(lastApiCallTime),
            Instant.now()
        )
        return maxOf(0L, COOLDOWN_MINUTES - timeSinceLastCall.toMinutes())
    }

    /**
     * Check if refresh is allowed for specific account transactions
     * This is always allowed regardless of rate limit for newly added accounts
     */
    fun isAccountSpecificRefreshAllowed(): Boolean {
        // Account-specific refreshes don't count toward the rate limit
        // but we still enforce the cooldown period to prevent excessive calls
        return getCooldownTimeRemaining() == 0L
    }
}