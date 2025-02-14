package com.splitter.splittr.data.local.dataClasses

import com.google.gson.annotations.SerializedName
import com.splitter.splittr.data.model.CurrencyConversion
import com.splitter.splittr.data.model.Group
import com.splitter.splittr.data.model.Payment

data class CurrencyConversionResponse(
    @SerializedName("timestamp")
    val timestamp: Long,
    @SerializedName("data")
    val data: List<CurrencyConversionData>
)

data class CurrencyConversionData(
    @SerializedName("currency_conversion")
    val currencyConversion: CurrencyConversion,
    @SerializedName("payment")
    val payment: Payment?,
    @SerializedName("group")
    val group: Group?
)