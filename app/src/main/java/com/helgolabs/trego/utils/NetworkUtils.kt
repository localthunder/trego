package com.helgolabs.trego.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.helgolabs.trego.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object NetworkUtils {
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var apiService: ApiService
    private const val SERVER_TIMEOUT = 3000L // 3 seconds timeout

    // Cache the health check result
    private var lastHealthCheckTime = 0L
    private var lastHealthCheckResult = false
    private const val HEALTH_CHECK_CACHE_DURATION = 30000L // 30 seconds

    // Add a mutex to prevent multiple simultaneous health checks
    private val healthCheckMutex = Mutex()

    // Track if a health check is in progress
    private var healthCheckInProgress = false

    fun initialize(context: Context, apiService: ApiService) {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        this.apiService = apiService

        // Set initial health check values
        lastHealthCheckTime = 0L
        lastHealthCheckResult = false
    }

    fun hasNetworkCapabilities(): Boolean {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
    }

    private fun shouldPerformHealthCheck(): Boolean {
        val now = System.currentTimeMillis()
        return now - lastHealthCheckTime > HEALTH_CHECK_CACHE_DURATION
    }

    // Update our base isOnline function with mutex protection
    suspend fun isOnline(): Boolean {
        // First check basic connectivity
        if (!hasNetworkCapabilities()) {
            return false
        }

        // Fast path: use cached result if available and not stale
        if (!shouldPerformHealthCheck()) {
            return lastHealthCheckResult
        }

        // Use mutex to prevent multiple simultaneous health checks
        return healthCheckMutex.withLock {
            // Double-check after acquiring lock (another thread might have updated the cache)
            if (!shouldPerformHealthCheck()) {
                return@withLock lastHealthCheckResult
            }

            // Mark that a health check is in progress
            healthCheckInProgress = true

            try {
                Log.d("NetworkUtils", "Performing server health check")
                withTimeout(SERVER_TIMEOUT) {
                    try {
                        apiService.healthCheck()
                        // Cache successful result
                        lastHealthCheckTime = System.currentTimeMillis()
                        lastHealthCheckResult = true
                        true
                    } catch (e: Exception) {
                        Log.e("NetworkUtils", "Server health check failed", e)
                        lastHealthCheckTime = System.currentTimeMillis()
                        lastHealthCheckResult = false
                        false
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("NetworkUtils", "Server health check timed out")
                lastHealthCheckTime = System.currentTimeMillis()
                lastHealthCheckResult = false
                false
            } catch (e: Exception) {
                Log.e("NetworkUtils", "Error checking server status", e)
                lastHealthCheckTime = System.currentTimeMillis()
                lastHealthCheckResult = false
                false
            } finally {
                healthCheckInProgress = false
            }
        }
    }

    // Add a function for forced health check when needed
    suspend fun forceHealthCheck(): Boolean {
        lastHealthCheckTime = 0 // Reset cache
        return isOnline()
    }

    // Update observeNetworkState to use cached health check
    fun observeNetworkState(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Only check basic connectivity here
                trySend(hasNetworkCapabilities())
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Initial state - use cached result if available
        trySend(if (shouldPerformHealthCheck()) isOnline() else lastHealthCheckResult)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}