package com.helgolabs.trego.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PlayStoreReviewManager(private val context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val reviewManager: ReviewManager = ReviewManagerFactory.create(context)
    private var cachedReviewInfo: ReviewInfo? = null

    companion object {
        private const val PREFS_NAME = "ReviewManagerPrefs"
        private const val KEY_LAST_REVIEW_REQUEST = "lastReviewRequest"
        private const val KEY_REVIEW_COUNT = "reviewCount"
        private const val DAYS_BETWEEN_REVIEWS = 30L // Minimum days between review requests
        private const val MAX_REVIEW_REQUESTS = 3 // Maximum number of review requests
    }

    suspend fun maybeAskForReview(activity: Activity) {
        try {
            if (!shouldShowReview()) {
                Log.d("ReviewManager", "Review conditions not met")
                return
            }

            // Get or create ReviewInfo object
            val reviewInfo = cachedReviewInfo ?: requestReviewFlow().also {
                cachedReviewInfo = it
            }

            // Launch review flow
            launchReviewFlow(activity, reviewInfo)

            // Update review request timestamp and count
            updateReviewMetadata()

            Log.d("ReviewManager", "Review flow completed successfully")
        } catch (e: Exception) {
            Log.e("ReviewManager", "Error launching review flow", e)
        }
    }

    private suspend fun requestReviewFlow(): ReviewInfo = suspendCancellableCoroutine { continuation ->
        reviewManager.requestReviewFlow()
            .addOnSuccessListener { reviewInfo ->
                continuation.resume(reviewInfo)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }

    private suspend fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo) = suspendCancellableCoroutine { continuation ->
        reviewManager.launchReviewFlow(activity, reviewInfo)
            .addOnSuccessListener {
                continuation.resume(Unit)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }

    private fun shouldShowReview(): Boolean {
        val lastReviewTimestamp = sharedPreferences.getLong(KEY_LAST_REVIEW_REQUEST, 0)
        val reviewCount = sharedPreferences.getInt(KEY_REVIEW_COUNT, 0)
        val currentTime = System.currentTimeMillis()

        // Check if we've shown too many reviews
        if (reviewCount >= MAX_REVIEW_REQUESTS) {
            return false
        }

        // Check if enough time has passed since last review
        val daysSinceLastReview = TimeUnit.MILLISECONDS.toDays(currentTime - lastReviewTimestamp)
        return daysSinceLastReview >= DAYS_BETWEEN_REVIEWS
    }

    private fun updateReviewMetadata() {
        sharedPreferences.edit().apply {
            putLong(KEY_LAST_REVIEW_REQUEST, System.currentTimeMillis())
            putInt(KEY_REVIEW_COUNT, sharedPreferences.getInt(KEY_REVIEW_COUNT, 0) + 1)
            apply()
        }
    }
}