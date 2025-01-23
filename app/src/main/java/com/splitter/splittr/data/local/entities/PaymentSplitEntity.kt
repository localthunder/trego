// file: PaymentSplitEntity.kt
package com.splitter.splittr.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverters
import com.splitter.splittr.data.local.converters.Converters
import com.splitter.splittr.data.sync.SyncStatus
import java.sql.Timestamp

@Entity(
    tableName = "payment_splits",
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
            childColumns = ["user_id"],
            onDelete = ForeignKey.RESTRICT
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
        Index("user_id"),
        Index(value = ["server_id"], unique = true),
        Index(value = ["payment_id", "user_id"], unique = true)
    ]
)
@TypeConverters(Converters::class)
data class PaymentSplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "server_id") val serverId: Int? = null,
    @ColumnInfo(name = "payment_id") val paymentId: Int,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "created_by") val createdBy: Int,
    @ColumnInfo(name = "updated_by") val updatedBy: Int,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "sync_status") var syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
)