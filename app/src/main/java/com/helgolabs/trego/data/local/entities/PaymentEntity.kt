package com.helgolabs.trego.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.helgolabs.trego.data.sync.SyncStatus
import java.sql.Timestamp

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["paid_by_user_id"],
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
        Index("group_id"),
        Index("paid_by_user_id"),
        Index("transaction_id"),
        Index(value = ["server_id"], unique = true),
        Index("created_by"),
        Index("updated_by")
    ]
)

data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "server_id") val serverId: Int? = null,
    @ColumnInfo(name = "group_id") val groupId: Int,
    @ColumnInfo(name = "paid_by_user_id") val paidByUserId: Int,
    @ColumnInfo(name = "transaction_id") val transactionId: String?,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "payment_date") val paymentDate: String,
    @ColumnInfo(name = "created_by") val createdBy: Int,
    @ColumnInfo(name = "updated_by") val updatedBy: Int,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "split_mode") val splitMode: String,
    @ColumnInfo(name = "institution_id") val institutionId: String?,
    @ColumnInfo(name = "payment_type") val paymentType: String,
    @ColumnInfo(name = "currency") val currency: String?,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "sync_status") var syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
)