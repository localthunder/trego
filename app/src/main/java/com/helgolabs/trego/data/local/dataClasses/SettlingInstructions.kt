package com.helgolabs.trego.data.local.dataClasses

data class SettlingInstruction(
    val fromId: Int,
    val toId: Int,
    val fromName: String,
    val toName: String,
    val amount: Double,
    val currency: String,
    val groupId: Int
)

// Data class to group instructions by currency
data class CurrencySettlingInstructions(
    val currency: String,
    val instructions: List<SettlingInstruction>
)