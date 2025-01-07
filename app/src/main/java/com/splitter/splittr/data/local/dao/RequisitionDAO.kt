package com.splitter.splittr.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.splitter.splittr.data.local.entities.PaymentSplitEntity
import com.splitter.splittr.data.local.entities.RequisitionEntity
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.utils.DateUtils
import kotlinx.coroutines.flow.Flow

@Dao
interface RequisitionDao {

    @Insert
    suspend fun insert(requisition: RequisitionEntity)

    @Query("SELECT * FROM requisitions")
    suspend fun getAllRequisitions(): List<RequisitionEntity>

    @Query("SELECT * FROM requisitions WHERE requisition_id = :requisitionId")
    suspend fun getRequisitionById(requisitionId: String): RequisitionEntity?

    @Query("DELETE FROM requisitions WHERE requisition_id = :requisitionId")
    suspend fun deleteRequisitionById(requisitionId: String)

    @Query("SELECT * FROM requisitions WHERE reference = :reference")
    suspend fun getRequisitionByReference(reference: String): RequisitionEntity?

    @Query("SELECT * FROM requisitions WHERE sync_status != 'SYNCED'")
    fun getUnsyncedRequisitions(): Flow<List<RequisitionEntity>>

    @Update
    suspend fun updateRequisitionDirect(requisition: RequisitionEntity)

    @Transaction
    suspend fun updateRequisition(requisition: RequisitionEntity) {
        // Create a copy of the requisition with the current timestamp
        val updatedRequisition = requisition.copy(
            updatedAt = DateUtils.getCurrentTimestamp(),
            syncStatus = SyncStatus.PENDING_SYNC
        )
        updateRequisitionDirect(updatedRequisition)
    }

    @Query("UPDATE requisitions SET sync_status = :status, updated_at = CURRENT_TIMESTAMP WHERE requisition_id = :requisitionId")
    suspend fun updateRequisitionSyncStatus(requisitionId: String, status: SyncStatus)
}

