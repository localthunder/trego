package com.helgolabs.trego.data.local.dataClasses

data class UserBalanceWithCurrency(
    val userId: Int,
    val username: String,
    val balances: Map<String, Double> // Currency code to balance
)