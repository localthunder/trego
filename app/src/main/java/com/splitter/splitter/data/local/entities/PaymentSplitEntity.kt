// file: PaymentSplitEntity.kt
package com.splitter.splitter.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.TypeConverters
import com.splitter.splitter.data.local.converters.Converters
import java.sql.Timestamp

@Entity(tableName = "payment_splits")
@TypeConverters(Converters::class) // Include converters for complex types
data class PaymentSplitEntity(
    @PrimaryKey val id: Int,

    @ColumnInfo(name = "payment_id")
    val paymentId: Int,

    @ColumnInfo(name = "user_id")
    val userId: Int,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "created_by")
    val createdBy: Int,

    @ColumnInfo(name = "updated_by")
    val updatedBy: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String,

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Timestamp? // Ensure this matches the data type in your backend model
)
