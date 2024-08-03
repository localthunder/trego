package com.splitter.splitter.data.extensions

import com.splitter.splitter.data.local.entities.InstitutionEntity
import com.splitter.splitter.model.Institution

fun Institution.toEntity(): InstitutionEntity {
    return InstitutionEntity(
        id = this.id,
        name = this.name,
        bic = this.bic,
        transactionTotalDays = this.transactionTotalDays,
        countries = this.countries,
        logo = this.logo,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
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
