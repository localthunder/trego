package com.splitter.splittr.data.extensions

import com.splitter.splittr.data.local.entities.RequisitionEntity
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.model.Requisition

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
        updatedAt = this.updatedAt
    )
}
