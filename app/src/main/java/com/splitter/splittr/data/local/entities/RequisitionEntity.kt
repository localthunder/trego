package com.splitter.splittr.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import com.splitter.splittr.data.sync.SyncStatus

@Entity(
    tableName = "requisitions",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InstitutionEntity::class,
            parentColumns = ["id"],
            childColumns = ["institution_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("user_id"),
        Index("institution_id"),
        Index("server_id")
    ]
)
data class RequisitionEntity(
    @PrimaryKey @ColumnInfo(name = "requisition_id") val requisitionId: String,
    @ColumnInfo(name = "server_id") val serverId: String? = null,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "institution_id") val institutionId: String?,
    @ColumnInfo(name = "reference") val reference: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "sync_status") var syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
)