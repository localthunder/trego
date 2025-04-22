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
    suspend fun getExchangeRate(fromCurrency: String, toCurrency: String, date: LocalDate? = null): Result<Double> {
        try {
            // Use provided date or current date
            val requestDate = date ?: LocalDate.now()

            Log.d(TAG, "Getting exchange rate from $fromCurrency to $toCurrency for date $requestDate")

            // First try: Try to get rates for the specific date
            val ratesResult = if (requestDate == LocalDate.now()) {
                // For current date, try current rates first
                ECBExchangeRateApi.getCurrentExchangeRates()
            } else {
                // For historical dates, use historical API
                ECBExchangeRateApi.getHistoricalExchangeRates(requestDate)
            }

            // If the specific date fails or returns empty rates (e.g., weekend or holiday)
            if (ratesResult.isFailure || ratesResult.getOrNull().isNullOrEmpty()) {
                Log.d(TAG, "No rates available for $requestDate, falling back to recent rates")

                // Second try: For today's date, fall back to historical data which has the most recent
                val fallbackResult = if (requestDate == LocalDate.now()) {
                    // Use 90-day history to get the most recent rates
                    val historicalRates = ECBExchangeRateApi.getHistoricalExchangeRates(
                        requestDate.minusDays(7)  // Look back a week to ensure we get some rates
                    ).getOrNull()

                    // Find the most recent date with available rates
                    if (historicalRates != null) {
                        Result.success(historicalRates)
                    } else {
                        Result.failure(Exception("No recent exchange rates available"))
                    }
                } else {
                    // For historical dates, try neighboring dates (previous business day)
                    var searchDate = requestDate.minusDays(1)
                    var foundRates: Map<String, Double>? = null

                    // Try up to 5 previous days to find rates
                    for (i in 0 until 5) {
                        val prevDayRates = ECBExchangeRateApi.getHistoricalExchangeRates(searchDate).getOrNull()
                        if (!prevDayRates.isNullOrEmpty()) {
                            foundRates = prevDayRates
                            break
                        }
                        searchDate = searchDate.minusDays(1)
                    }

                    if (foundRates != null) {
                        Result.success(foundRates)
                    } else {
                        Result.failure(Exception("No exchange rates found for date range around $requestDate"))
                    }
                }

                if (fallbackResult.isFailure) {
                    return Result.failure(fallbackResult.exceptionOrNull()
                        ?: Exception("Failed to get exchange rates"))
                }

                // Use the fallback rates
                return calculateRate(fromCurrency, toCurrency, fallbackResult.getOrNull()!!)
            }

            // Use the rates from the specific date
            return calculateRate(fromCurrency, toCurrency, ratesResult.getOrNull()!!)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting exchange rate", e)
            return Result.failure(e)
        }
    }

    private fun calculateRate(fromCurrency: String, toCurrency: String, rates: Map<String, Double>): Result<Double> {
        // Both currencies are the same, rate is 1:1
        if (fromCurrency == toCurrency) {
            return Result.success(1.0)
        }

        // Handle EUR as base currency (ECB provides rates with EUR as base)
        if (fromCurrency == "EUR" && rates.containsKey(toCurrency)) {
            return Result.success(rates[toCurrency]!!)
        }

        if (toCurrency == "EUR" && rates.containsKey(fromCurrency)) {
            return Result.success(1.0 / rates[fromCurrency]!!)
        }

        // Cross-rate calculation: convert through EUR
        if (rates.containsKey(fromCurrency) && rates.containsKey(toCurrency)) {
            val fromToEur = 1.0 / rates[fromCurrency]!!
            val eurToTarget = rates[toCurrency]!!
            return Result.success(fromToEur * eurToTarget)
        }

        return Result.failure(Exception("Cannot calculate exchange rate from $fromCurrency to $toCurrency"))
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