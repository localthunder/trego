package com.splitter.splittr.data.local.dataClasses

data class SettlingInstruction(
    val from: String,
    val to: String,
    val amount: Double,
    val currency: String
)

// Data class to group instructions by currency
data class CurrencySettlingInstructions(
    val currency: String,
    val instructions: List<SettlingInstruction>
)