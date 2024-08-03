package com.splitter.splitter.data.extensions

import com.splitter.splitter.data.local.entities.RequisitionEntity
import com.splitter.splitter.model.Requisition

fun Requisition.toEntity(): RequisitionEntity {
    return RequisitionEntity(
        requisitionId = this.requisitionId,
        userId = this.userId,
        institutionId = this.institutionId,
        reference = this.reference,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}

fun RequisitionEntity.toModel(): Requisition {
    return Requisition(
        requisitionId = this.requisitionId,
        userId = this.userId,
        institutionId = this.institutionId,
        reference = this.reference,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}
