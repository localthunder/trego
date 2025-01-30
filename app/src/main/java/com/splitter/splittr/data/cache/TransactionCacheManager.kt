package com.splitter.splittr.data.cache

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toJson
import com.splitter.splittr.data.local.dao.CachedTransactionDao
import com.splitter.splittr.data.local.entities.CachedTransactionEntity
import com.splitter.splittr.data.model.Transaction
import com.splitter.splittr.utils.DateUtils
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
        private const val PREFERENCES_NAME = "transaction_cache_prefs"
        private val LOW_ACTIVITY_HOURS = 0..6 // Midnight to 6 AM
        private const val REFRESH_PRIORITY_THRESHOLD = 50.0
    }

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun shouldRefreshCache(userId: Int): Boolean {
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

    private fun getTimeUntilApiReset(): Duration {
        val now = LocalDateTime.now()
        val tomorrow = now.plusDays(1).truncatedTo(ChronoUnit.DAYS)
        return Duration.between(now, tomorrow)
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
        val currentApiCalls = getApiCallsToday()
        return currentApiCalls < MAX_API_CALLS_PER_DAY ||
                getTimeUntilApiReset().toMinutes() < 30
    }

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
        Log.d(TAG, "Cached ${transactions.size} transactions for user $userId")
    }

    private fun getApiCallsToday(): Int {
        val currentDate = DateUtils.getCurrentDate()
        val lastCallDate = preferences.getString("last_call_date", "")

        if (lastCallDate != currentDate) {
            // Reset counter for new day
            preferences.edit()
                .putString("last_call_date", currentDate)
                .putInt(RATE_LIMIT_KEY, 0)
                .apply()
            return 0
        }

        return preferences.getInt(RATE_LIMIT_KEY, 0)
    }

    private fun incrementApiCallCount() {
        val currentDate = DateUtils.getCurrentDate()
        val currentCount = getApiCallsToday()

        preferences.edit()
            .putString("last_call_date", currentDate)
            .putInt(RATE_LIMIT_KEY, currentCount + 1)
            .apply()
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
}