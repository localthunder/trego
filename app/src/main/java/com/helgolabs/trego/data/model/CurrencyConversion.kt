package com.helgolabs.trego.data.model

import com.google.gson.annotations.SerializedName

data class CurrencyConversion(
    val id: Int = 0,
    @SerializedName("paymentId") val paymentId: Int,
    @SerializedName("originalCurrency") val originalCurrency: String,
    @SerializedName("originalAmount") val originalAmount: Double,
    @SerializedName("finalCurrency") val finalCurrency: String,
    @SerializedName("finalAmount") val finalAmount: Double,
    @SerializedName("exchangeRate") val exchangeRate: Double,
    val source: String,
    @SerializedName("createdBy") val createdBy: Int,
    @SerializedName("updatedBy") val updatedBy: Int,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
)