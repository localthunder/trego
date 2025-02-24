package com.helgolabs.trego.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.helgolabs.trego.data.sync.SyncStatus

@Entity(tableName = "institutions")
data class InstitutionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val bic: String?,
    val transactionTotalDays: String?,
    val countries: List<String>?,
    val logo: String?,
    val localLogoPath: String?, // Local file path
    val createdAt: String,
    val updatedAt: String,
//    val syncStatus: SyncStatus = SyncStatus.PENDING_SYNC

)
