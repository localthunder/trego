package com.helgolabs.trego.data.extensions

import com.helgolabs.trego.data.local.entities.InstitutionEntity
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.model.Institution
import java.time.Instant
import java.time.format.DateTimeFormatter

fun Institution.toEntity(): InstitutionEntity {
    val currentTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
    return InstitutionEntity(
        id = this.id,
        name = this.name,
        bic = this.bic,
        transactionTotalDays = this.transactionTotalDays,
        countries = this.countries,
        logo = this.logo,
        localLogoPath = null,
        createdAt = this.createdAt ?: currentTime,
        updatedAt = currentTime,
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
