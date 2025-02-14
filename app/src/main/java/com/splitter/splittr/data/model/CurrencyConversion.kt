package com.splitter.splittr.data.model

import com.google.gson.annotations.SerializedName

data class CurrencyConversion(
    val id: Int = 0,
    @SerializedName("payment_id") val paymentId: Int,
    @SerializedName("original_currency") val originalCurrency: String,
    @SerializedName("original_amount") val originalAmount: Double,
    @SerializedName("final_currency") val finalCurrency: String,
    @SerializedName("final_amount") val finalAmount: Double,
    @SerializedName("exchange_rate") val exchangeRate: Double,
    val source: String,
    @SerializedName("created_by") val createdBy: Int,
    @SerializedName("updated_by") val updatedBy: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)