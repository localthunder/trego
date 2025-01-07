package com.splitter.splittr.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.splitter.splittr.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object NetworkUtils {
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var apiService: ApiService
    private const val SERVER_TIMEOUT = 3000L // 3 seconds timeout

    fun initialize(context: Context, apiService: ApiService) {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        this.apiService = apiService
    }


    // Keep a non-suspend version for basic connectivity check
    fun hasNetworkCapabilities(): Boolean {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
    }

    // Suspend function for full online check including server health
    suspend fun isOnline(): Boolean {
        if (!hasNetworkCapabilities()) return false

        return withContext(Dispatchers.IO) {
            try {
                withTimeout(SERVER_TIMEOUT) {
                    try {
                        apiService.healthCheck()
                        true
                    } catch (e: Exception) {
                        Log.e("NetworkUtils", "Server health check failed", e)
                        false
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("NetworkUtils", "Server health check timed out")
                false
            } catch (e: Exception) {
                Log.e("NetworkUtils", "Error checking server status", e)
                false
            }
        }
    }

    fun observeNetworkState(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Initial state
        trySend(isOnline())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}