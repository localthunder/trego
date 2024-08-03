package com.splitter.splitter.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.splitter.splitter.data.local.converters.Converters
import java.sql.Timestamp

@Entity(tableName = "payments")
@TypeConverters(Converters::class) // Include converters for complex types
data class PaymentEntity(
    @PrimaryKey val id: Int,

    @ColumnInfo(name = "group_id")
    val groupId: Int,

    @ColumnInfo(name = "paid_by_user_id")
    val paidByUserId: Int,

    @ColumnInfo(name = "transaction_id")
    val transactionId: String?,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "payment_date")
    val paymentDate: String,

    @ColumnInfo(name = "created_by")
    val createdBy: Int,

    @ColumnInfo(name = "updated_by")
    val updatedBy: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String,

    @ColumnInfo(name = "split_mode")
    val splitMode: String,

    @ColumnInfo(name = "institution_name")
    val institutionName: String?,

    @ColumnInfo(name = "payment_type")
    val paymentType: String,

    @ColumnInfo(name = "currency")
    val currency: String?,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Timestamp? // Ensure this matches the data type in your backend model
)
