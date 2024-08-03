package com.splitter.splitter.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "requisitions")
data class RequisitionEntity(
    @PrimaryKey
    @ColumnInfo(name = "requisition_id")
    val requisitionId: String?,

    @ColumnInfo(name = "user_id")
    val userId: Int?,

    @ColumnInfo(name = "institution_id")
    val institutionId: String?,

    @ColumnInfo(name = "reference")
    val reference: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: String?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String?
)
