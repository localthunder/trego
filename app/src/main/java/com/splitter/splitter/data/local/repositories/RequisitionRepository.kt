package com.splitter.splitter.data.local.repositories

import com.splitter.splitter.data.extensions.toEntity
import com.splitter.splitter.data.extensions.toModel
import com.splitter.splitter.data.local.dao.RequisitionDao
import com.splitter.splitter.model.Requisition

class RequisitionRepository(private val requisitionDao: RequisitionDao) {

    suspend fun insert(requisition: Requisition) {
        requisitionDao.insert(requisition.toEntity())
    }

    suspend fun getAllRequisitions(): List<Requisition> {
        return requisitionDao.getAllRequisitions().map { it.toModel() }
    }

    suspend fun getRequisitionById(requisitionId: String): Requisition? {
        return requisitionDao.getRequisitionById(requisitionId)?.toModel()
    }

    suspend fun deleteRequisitionById(requisitionId: String) {
        requisitionDao.deleteRequisitionById(requisitionId)
    }
}
