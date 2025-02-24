package com.helgolabs.trego.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.helgolabs.trego.data.sync.SyncStatus
import java.sql.Timestamp

@Entity(
    tableName = "currency_conversions",
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["payment_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["created_by"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["updated_by"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("payment_id"),
        Index(value = ["server_id"], unique = true),
        Index("created_by"),
        Index("updated_by")
    ]
)
data class CurrencyConversionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "server_id") val serverId: Int? = null,
    @ColumnInfo(name = "payment_id") val paymentId: Int,
    @ColumnInfo(name = "original_currency") val originalCurrency: String,
    @ColumnInfo(name = "original_amount") val originalAmount: Double,
    @ColumnInfo(name = "final_currency") val finalCurrency: String,
    @ColumnInfo(name = "final_amount") val finalAmount: Double,
    @ColumnInfo(name = "exchange_rate") val exchangeRate: Double,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "created_by") val createdBy: Int,
    @ColumnInfo(name = "updated_by") val updatedBy: Int,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "sync_status") var syncStatus: SyncStatus = SyncStatus.PENDING_SYNC,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null
)