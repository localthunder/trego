package com.splitter.splittr.data.extensions

import com.splitter.splittr.data.local.entities.InstitutionEntity
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.model.Institution
import java.time.Instant
import java.time.format.DateTimeFormatter

fun Institution.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING_SYNC): InstitutionEntity {
    val currentTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
    return InstitutionEntity(
        id = this.id,
        name = this.name,
        bic = this.bic,
        transactionTotalDays = this.transactionTotalDays,
        countries = this.countries,
        logo = this.logo,
        createdAt = this.createdAt ?: currentTime,
        updatedAt = currentTime,
//        syncStatus = syncStatus
    )
}

fun InstitutionEntity.toModel(): Institution {
    return Institution(
        id = this.id,
        name = this.name,
        bic = this.bic,
        transactionTotalDays = this.transactionTotalDays,
        countries = this.countries ?: emptyList(),
        logo = this.logo,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}
