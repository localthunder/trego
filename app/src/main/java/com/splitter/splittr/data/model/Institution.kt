package com.splitter.splittr.data.model

import com.google.gson.annotations.SerializedName

data class Institution(
    val id: String,
    val name: String,
    val bic: String?,
    @SerializedName("transaction_total_days")
    val transactionTotalDays: String?,
    val countries: List<String>,
    val logo: String?,
    val createdAt: String,
    val updatedAt: String,
)
