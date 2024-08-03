package com.splitter.splitter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.splitter.splitter.data.local.entities.RequisitionEntity

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
}
