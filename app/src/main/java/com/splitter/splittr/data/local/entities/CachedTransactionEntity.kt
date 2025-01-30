package com.splitter.splittr.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_transactions")
data class CachedTransactionEntity(
    @PrimaryKey val transactionId: String,
    val userId: Int,
    val transactionData: String, // JSON string of Transaction
    val fetchTimestamp: Long,
    val expiryTimestamp: Long
)