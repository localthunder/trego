package com.helgolabs.trego.data.network

import android.util.Log
import com.helgolabs.trego.data.local.entities.CurrencyConversionEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ExchangeRateService {
    private val TAG = "ExchangeRateService"
    private val ecbApi = ECBExchangeRateApi
    private val exchangeRatesApi = createExchangeRatesApi()
    private val rateCache = ExchangeRateCache()

    companion object {
        private const val EXCHANGE_RATES_API_KEY = "YOUR_API_KEY" // Move to secure storage
        private const val EXCHANGE_RATES_BASE_URL = "https://v6.exchangerate-api.com/v6/"
        private const val CACHE_DURATION_HOURS = 24L
    }

    data class DateRangeRate(
        val date: LocalDate,
        val rate: Double
    )

    data class AverageRateResult(
        val averageRate: Double,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val numberOfDataPoints: Int,
        val minRate: Double,
        val maxRate: Double
    )

    // Response data classes
    data class ExchangeRateApiResponse(
        val result: String,
        val documentation: String,
        val terms_of_use: String,
        val time_last_update_unix: Long,
        val time_last_update_utc: String,
        val time_next_update_unix: Long,
        val time_next_update_utc: String,
        val base_code: String,
        val target_code: String,
        val conversion_rate: Double,
        val conversion_rates: Map<String, Double>
    )

    // API Interface
    interface ExchangeRatesApiService {
        @GET("{api_key}/latest/{base}")
        suspend fun getLatestRates(
            @Path("api_key") apiKey: String = EXCHANGE_RATES_API_KEY,
            @Path("base") base: String
        ): ExchangeRateApiResponse

        @GET("{api_key}/history/{base}/{date}")
        suspend fun getHistoricalRates(
            @Path("api_key") apiKey: String = EXCHANGE_RATES_API_KEY,
            @Path("base") base: String,
            @Path("date") date: String
        ): ExchangeRateApiResponse
    }

    private fun createExchangeRatesApi(): ExchangeRatesApiService {
        return Retrofit.Builder()
            .baseUrl(EXCHANGE_RATES_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExchangeRatesApiService::class.java)
    }

    /**
     * Gets the exchange rate between two currencies for a specific date
     */
    suspend fun getExchangeRate(
        fromCurrency: String,
        toCurrency: String,
        date: LocalDate? = null
    ): Result<Double> = coroutineScope {
        try {
            // Check cache first
            val cacheKey = "${fromCurrency}_${toCurrency}_${date ?: "latest"}"
            rateCache.get(cacheKey)?.let {
                return@coroutineScope Result.success(it)
            }

            // Try ECB first
            val ecbResult = async { getEcbRate(fromCurrency, toCurrency, date) }
            val rate = ecbResult.await().getOrNull()

            if (rate != null) {
                rateCache.put(cacheKey, rate)
                return@coroutineScope Result.success(rate)
            }

            // Fallback to Exchange Rates API
            val exchangeRatesResult = async {
                getExchangeRatesApiRate(fromCurrency, toCurrency, date)
            }
            val fallbackRate = exchangeRatesResult.await().getOrNull()
                ?: return@coroutineScope Result.failure(
                    Exception("Could not get exchange rate from any provider")
                )

            rateCache.put(cacheKey, fallbackRate)
            Result.success(fallbackRate)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting exchange rate", e)
            Result.failure(e)
        }
    }

    private suspend fun getEcbRate(
        fromCurrency: String,
        toCurrency: String,
        date: LocalDate?
    ): Result<Double> {
        try {
            val rates = if (date == null) {
                ecbApi.getCurrentExchangeRates()
            } else {
                ecbApi.getHistoricalExchangeRates(date)
            }.getOrNull() ?: return Result.failure(Exception("No rates available from ECB"))

            // ECB uses EUR as base, so we need to handle conversion
            val fromRate = if (fromCurrency == "EUR") 1.0 else rates[fromCurrency]
            val toRate = if (toCurrency == "EUR") 1.0 else rates[toCurrency]

            if (fromRate == null || toRate == null) {
                return Result.failure(Exception("Currency not supported by ECB"))
            }

            // Calculate cross-rate
            return Result.success(toRate / fromRate)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting ECB rate", e)
            return Result.failure(e)
        }
    }

    private suspend fun getExchangeRatesApiRate(
        fromCurrency: String,
        toCurrency: String,
        date: LocalDate?
    ): Result<Double> {
        try {
            val response = if (date == null) {
                exchangeRatesApi.getLatestRates(base = fromCurrency)
            } else {
                exchangeRatesApi.getHistoricalRates(
                    base = fromCurrency,
                    date = date.toString()
                )
            }

            val rate = response.conversion_rates[toCurrency]
                ?: return Result.failure(Exception("Currency not supported"))

            return Result.success(rate)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Exchange Rates API rate", e)
            return Result.failure(e)
        }
    }

    /**
     * Simple in-memory cache for exchange rates
     */
    private class ExchangeRateCache {
        private val cache = mutableMapOf<String, CachedRate>()

        private data class CachedRate(
            val rate: Double,
            val timestamp: LocalDate
        )

        fun get(key: String): Double? {
            val cached = cache[key] ?: return null

            // Check if cache is still valid
            if (ChronoUnit.HOURS.between(
                    cached.timestamp,
                    LocalDate.now()
                ) > CACHE_DURATION_HOURS) {
                cache.remove(key)
                return null
            }

            return cached.rate
        }

        fun put(key: String, rate: Double) {
            cache[key] = CachedRate(rate, LocalDate.now())
        }

        fun clear() {
            cache.clear()
        }
    }

    /**
     * Creates a CurrencyConversionEntity from an exchange rate
     */
    fun createConversionEntity(
        paymentId: Int,
        fromCurrency: String,
        fromAmount: Double,
        toCurrency: String,
        exchangeRate: Double,
        createdBy: Int,
        updatedBy: Int
    ): CurrencyConversionEntity {
        val finalAmount = fromAmount * exchangeRate

        return CurrencyConversionEntity(
            paymentId = paymentId,
            originalCurrency = fromCurrency,
            originalAmount = fromAmount,
            finalCurrency = toCurrency,
            finalAmount = finalAmount,
            exchangeRate = exchangeRate,
            source = "ECB/ExchangeRatesAPI",
            createdBy = createdBy,
            updatedBy = updatedBy,
            createdAt = LocalDate.now().toString(),
            updatedAt = LocalDate.now().toString()
        )
    }
}