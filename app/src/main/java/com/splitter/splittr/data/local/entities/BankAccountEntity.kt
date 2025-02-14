package com.splitter.splittr.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.splitter.splittr.data.sync.SyncStatus

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RequisitionEntity::class,
            parentColumns = ["requisition_id"],
            childColumns = ["requisition_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("user_id"),
        Index("requisition_id"),
        Index("institution_id")
    ]
)
data class BankAccountEntity(
    @PrimaryKey @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "server_id") val serverId: String? = null,
    @ColumnInfo(name = "requisition_id") val requisitionId: String,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "iban") val iban: String?,
    @ColumnInfo(name = "institution_id") val institutionId: String,
    @ColumnInfo(name = "currency") val currency: String?,
    @ColumnInfo(name = "owner_name") val ownerName: String?,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "product") val product: String?,
    @ColumnInfo(name = "cash_account_type") val cashAccountType: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "sync_status") var syncStatus: SyncStatus = SyncStatus.PENDING_SYNC,
    val needsReauthentication: Boolean = false,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?
)