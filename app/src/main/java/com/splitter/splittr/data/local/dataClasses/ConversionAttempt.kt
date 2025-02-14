package com.splitter.splittr.data.local.dataClasses

data class ConversionAttempt(
    val paymentId: Int,
    val fromCurrency: String,
    val toCurrency: String,
    val originalAmount: Double,
    val convertedAmount: Double? = null,
    val exchangeRate: Double? = null,
    val error: String? = null
) {
    val isSuccessful: Boolean
        get() = error == null && convertedAmount != null && exchangeRate != null
}