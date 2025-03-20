package com.helgolabs.trego.data.network

import android.content.Context
import android.util.Log
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.helgolabs.trego.utils.TokenManager
import com.helgolabs.trego.utils.createGson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // private const val BASE_URL = "http://10.0.2.2:3000/"
    private const val BASE_URL = "http://192.168.68.62:3000/"
    private const val TIMEOUT_DURATION = 60L
    private const val TAG = "RetrofitClient"

    // Create a separate instance for token refresh that doesn't go through auth interceptor
    private val refreshClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                setLevel(HttpLoggingInterceptor.Level.BODY)
            })
            .connectTimeout(TIMEOUT_DURATION, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_DURATION, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_DURATION, TimeUnit.SECONDS)
            .build()
    }

    private val refreshRetrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(refreshClient)
            .addConverterFactory(GsonConverterFactory.create(createGson()))
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .build()
    }

    private val refreshService by lazy {
        refreshRetrofit.create(ApiService::class.java)
    }

    // Synchronization to prevent multiple refresh attempts
    private val refreshTokenLock = Any()
    private var isRefreshing = false
    private var lastRefreshTime = 0L

    fun getClient(context: Context): OkHttpClient {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)

        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()

            // Skip token handling for authentication endpoints
            val pathSegments = originalRequest.url.pathSegments
            val isAuthEndpoint = pathSegments.size >= 2 &&
                    pathSegments[0] == "api" &&
                    pathSegments[1] == "auth" &&
                    (pathSegments.getOrNull(2) == "login" ||
                            pathSegments.getOrNull(2) == "register")

            // For auth endpoints, just add Content-Type and proceed
            if (isAuthEndpoint) {
                val authRequest = originalRequest.newBuilder()
                    .header("Content-Type", "application/json")
                    .build()
                return@Interceptor chain.proceed(authRequest)
            }

            // For all other endpoints, add the access token
            val accessToken = TokenManager.getAccessToken(context)

            // Build request with current token
            val requestBuilder = originalRequest.newBuilder()
                .header("Content-Type", "application/json")

            if (accessToken != null) {
                requestBuilder.header("JWT-Authorization", "Bearer $accessToken")
            }

            val initialRequest = requestBuilder.build()
            var response = chain.proceed(initialRequest)

            // If we get a 401 and the response indicates token expiration
            if (response.code == 401 && !isRefreshing) {
                Log.d(TAG, "Received 401, attempting token refresh")
                response.close() // Close the response to avoid leaks

                val newToken = synchronized(refreshTokenLock) {
                    if (isRefreshing) {
                        // Another thread is already refreshing, wait for it
                        try {
                            (refreshTokenLock as Object).wait(5000) // Wait up to 5 seconds
                            TokenManager.getAccessToken(context) // Get the potentially refreshed token
                        } catch (e: Exception) {
                            Log.e(TAG, "Error waiting for token refresh", e)
                            null
                        }
                    } else {
                        isRefreshing = true
                        try {
                            val refreshToken = TokenManager.getRefreshToken(context)
                            Log.d(TAG, "Retrieved refresh token: ${refreshToken?.take(15)}...")

                            if (refreshToken != null) {
                                // Run token refresh on a separate dispatcher
                                Log.d(TAG, "Calling /auth/refresh-token endpoint")
                                val result = runBlocking(Dispatchers.IO) {
                                    try {
                                        refreshService.refreshToken(mapOf("refreshToken" to refreshToken))
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error during refresh call", e)
                                        null
                                    }
                                }

                                // Verify the response format and save tokens
                                if (result?.success == true && result.token != null) {
                                    Log.d(TAG, "Refresh SUCCESS - new token: ${result.token.take(15)}...")
                                    TokenManager.saveAccessToken(context, result.token)
                                    if (result.refreshToken != null) {
                                        TokenManager.saveRefreshToken(context, result.refreshToken)
                                    }
                                    lastRefreshTime = System.currentTimeMillis()
                                    result.token
                                } else {
                                    Log.e(TAG, "Refresh failed: ${result?.message ?: "No response"}")
                                    null
                                }
                            } else {
                                Log.e(TAG, "No refresh token available!")
                                null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception during refresh", e)
                            null
                        } finally {
                            isRefreshing = false
                            (refreshTokenLock as Object).notifyAll() // Notify waiting threads
                        }
                    }
                }

                // If we got a new token, retry the request
                if (newToken != null) {
                    val newRequest = originalRequest.newBuilder()
                        .header("Content-Type", "application/json")
                        .header("JWT-Authorization", "Bearer $newToken")
                        .build()
                    return@Interceptor chain.proceed(newRequest)
                }
            }

            response
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(TIMEOUT_DURATION, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_DURATION, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_DURATION, TimeUnit.SECONDS)
            .build()
    }

    fun getInstance(context: Context): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(getClient(context))
            .addConverterFactory(GsonConverterFactory.create(createGson()))
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .build()
    }
}