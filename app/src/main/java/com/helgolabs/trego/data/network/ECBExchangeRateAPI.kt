package com.helgolabs.trego.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

object ECBExchangeRateApi {
    private const val TAG = "ECBExchangeRateApi"

    // Base URLs for different ECB exchange rate feeds
    private const val CURRENT_RATES_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"
    private const val HISTORICAL_RATES_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.xml"
    private const val HISTORICAL_90_DAYS_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist-90d.xml"

    /**
     * Fetches the current exchange rates from ECB
     * @return Map of currency codes to exchange rates (base currency is EUR)
     */
    suspend fun getCurrentExchangeRates(): Result<Map<String, Double>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = fetchXmlFromUrl(CURRENT_RATES_URL)
                val rates = parseExchangeRates(response)
                if (rates.isEmpty()) {
                    Result.failure(Exception("No exchange rates found in the response"))
                } else {
                    Result.success(rates)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching current exchange rates", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Fetches historical exchange rates for a specific date
     * @param date The date for which to fetch exchange rates
     * @return Map of currency codes to exchange rates for the specified date (base currency is EUR)
     */
    suspend fun getHistoricalExchangeRates(date: LocalDate): Result<Map<String, Double>> {
        return withContext(Dispatchers.IO) {
            try {
                // Use 90-day history if date is within last 90 days, otherwise use full history
                val isWithin90Days = date.isAfter(LocalDate.now().minusDays(90))
                val url = if (isWithin90Days) HISTORICAL_90_DAYS_URL else HISTORICAL_RATES_URL

                val response = fetchXmlFromUrl(url)
                val rates = parseHistoricalExchangeRates(response, date)
                if (rates.isEmpty()) {
                    Result.failure(Exception("No exchange rates found for date $date"))
                } else {
                    Result.success(rates)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching historical exchange rates for date $date", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Fetches historical exchange rates for a date range
     * @param startDate Start date of the range
     * @param endDate End date of the range
     * @return Map of dates to exchange rate maps
     */
    suspend fun getHistoricalExchangeRatesRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<Map<LocalDate, Map<String, Double>>> {
        return withContext(Dispatchers.IO) {
            try {
                // Use 90-day history if entire range is within last 90 days, otherwise use full history
                val isWithin90Days = startDate.isAfter(LocalDate.now().minusDays(90))
                val url = if (isWithin90Days) HISTORICAL_90_DAYS_URL else HISTORICAL_RATES_URL

                val response = fetchXmlFromUrl(url)
                val rates = parseHistoricalExchangeRatesRange(response, startDate, endDate)
                if (rates.isEmpty()) {
                    Result.failure(Exception("No exchange rates found for date range $startDate to $endDate"))
                } else {
                    Result.success(rates)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching historical exchange rates for range $startDate to $endDate", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun fetchXmlFromUrl(urlString: String): String {
        return withContext(Dispatchers.IO) {  // Move network call to IO dispatcher
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                response
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching XML from $urlString", e)
                throw e
            }
        }
    }

    private fun parseExchangeRates(xml: String): Map<String, Double> {
        val rates = mutableMapOf<String, Double>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(StringReader(xml).toInputSource())

            val cubeElements = document.getElementsByTagName("Cube")
            for (i in 0 until cubeElements.length) {
                val element = cubeElements.item(i) as Element
                val currency = element.getAttribute("currency")
                val rate = element.getAttribute("rate")
                if (currency.isNotEmpty() && rate.isNotEmpty()) {
                    rates[currency] = rate.toDouble()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing exchange rates XML", e)
        }
        return rates
    }

    private fun parseHistoricalExchangeRates(xml: String, date: LocalDate): Map<String, Double> {
        val dateStr = date.format(DateTimeFormatter.ISO_DATE)
        val rates = mutableMapOf<String, Double>()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(StringReader(xml).toInputSource())

            val timeElements = document.getElementsByTagName("Cube").let { nodeList ->
                (0 until nodeList.length)
                    .map { nodeList.item(it) as Element }
                    .filter { it.getAttribute("time") == dateStr }
            }

            if (timeElements.isNotEmpty()) {
                val rateNodes = (timeElements[0] as Element).getElementsByTagName("Cube")
                for (i in 0 until rateNodes.length) {
                    val element = rateNodes.item(i) as Element
                    val currency = element.getAttribute("currency")
                    val rate = element.getAttribute("rate")
                    if (currency.isNotEmpty() && rate.isNotEmpty()) {
                        rates[currency] = rate.toDouble()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing historical exchange rates XML", e)
        }
        return rates
    }

    private fun parseHistoricalExchangeRatesRange(
        xml: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<LocalDate, Map<String, Double>> {
        val ratesByDate = mutableMapOf<LocalDate, Map<String, Double>>()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(StringReader(xml).toInputSource())

            val timeElements = document.getElementsByTagName("Cube")
            for (i in 0 until timeElements.length) {
                val element = timeElements.item(i) as Element
                val timeStr = element.getAttribute("time")
                if (timeStr.isNotEmpty()) {
                    val date = LocalDate.parse(timeStr)
                    if (date in startDate..endDate) {
                        val rateNodes = element.getElementsByTagName("Cube")
                        val rates = mutableMapOf<String, Double>()
                        for (j in 0 until rateNodes.length) {
                            val rateElement = rateNodes.item(j) as Element
                            val currency = rateElement.getAttribute("currency")
                            val rate = rateElement.getAttribute("rate")
                            if (currency.isNotEmpty() && rate.isNotEmpty()) {
                                rates[currency] = rate.toDouble()
                            }
                        }
                        if (rates.isNotEmpty()) {
                            ratesByDate[date] = rates
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing historical exchange rates range XML", e)
        }
        return ratesByDate
    }

    private fun StringReader.toInputSource() = org.xml.sax.InputSource(this)
}