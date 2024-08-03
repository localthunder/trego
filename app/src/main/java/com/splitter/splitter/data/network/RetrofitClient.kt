package com.splitter.splitter.data.network

import android.content.Context
import com.splitter.splitter.utils.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "http://192.168.0.18:3000"
    private const val TIMEOUT_DURATION = 60L // Set timeout duration in seconds

    private fun getClient(context: Context): OkHttpClient {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)

        val headerInterceptor = Interceptor { chain ->
            val original = chain.request()
            val accessToken = TokenManager.getAccessToken(context)
            val requestBuilder = original.newBuilder()
                .header("Content-Type", "application/json")

            if (accessToken != null) {
                requestBuilder.header("JWT-Authorization", "Bearer $accessToken")
            }

            val request = requestBuilder.build()
            val response = chain.proceed(request)

            if (response.code == 401) {
                // Token is expired, try to refresh it
                val newAccessToken = refreshToken(context)
                if (newAccessToken != null) {
                    val newRequest = original.newBuilder()
                        .header("Content-Type", "application/json")
                        .header("JWT-Authorization", "Bearer $newAccessToken")
                        .build()
                    return@Interceptor chain.proceed(newRequest)
                }
            }

            response
        }

        return OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            .addInterceptor(logging)
            .connectTimeout(TIMEOUT_DURATION, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_DURATION, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_DURATION, TimeUnit.SECONDS)
            .build()
    }

    private fun refreshToken(context: Context): String? {
        val refreshToken = TokenManager.getRefreshToken(context) ?: return null

        val refreshService = getInstance(context).create(ApiService::class.java)
        val call = refreshService.refreshToken(mapOf("refreshToken" to refreshToken))
        val response = call.execute()

        return if (response.isSuccessful) {
            val newAccessToken = response.body()?.token
            val newRefreshToken = response.body()?.refreshToken
            if (newAccessToken != null) {
                TokenManager.saveAccessToken(context, newAccessToken)
            }
            if (newRefreshToken != null) {
                TokenManager.saveRefreshToken(context, newRefreshToken)
            }
            newAccessToken
        } else {
            null
        }
    }

    fun getInstance(context: Context): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(getClient(context))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
