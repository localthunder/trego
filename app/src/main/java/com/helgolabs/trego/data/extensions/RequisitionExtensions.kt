package com.helgolabs.trego.data.extensions

import com.helgolabs.trego.data.local.entities.RequisitionEntity
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.model.Requisition

fun Requisition.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING_SYNC): RequisitionEntity {
    return RequisitionEntity(
        requisitionId = this.requisitionId,
        userId = this.userId,
        institutionId = this.institutionId,
        reference = this.reference,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        syncStatus = syncStatus
    )
}

fun RequisitionEntity.toModel(): Requisition {
    return Requisition(
        requisitionId = this.requisitionId,
        userId = this.userId,
        institutionId = this.institutionId,
        reference = this.reference,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt ?: ""
    )
}
