package com.helgolabs.trego.data.local.dataClasses

import com.helgolabs.trego.utils.DateUtils

data class CurrencyConversionResult(
    val originalAmount: Double,
    val originalCurrency: String,
    val convertedAmount: Double,
    val targetCurrency: String,
    val exchangeRate: Double,
    val timestamp: String = DateUtils.getCurrentTimestamp()
)