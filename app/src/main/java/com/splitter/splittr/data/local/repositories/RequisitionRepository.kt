package com.splitter.splittr.data.local.repositories

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.RequisitionDao
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.model.Requisition
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class RequisitionRepository(
    private val requisitionDao: RequisitionDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context

    ) {

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

    suspend fun getRequisitionByReference(reference: String): Requisition? {
        // First, try to get the requisition from the local database
        var requisition = requisitionDao.getRequisitionByReference(reference)?.toModel()

        // If not found in the database, fetch from the API
        if (requisition == null) {
            try {
                requisition = apiService.getRequisitionByReference(reference)
                // Save the fetched requisition to the local database
                requisition?.let {
                    requisitionDao.insert(it.toEntity())
                }
            } catch (e: Exception) {
                // Handle network errors
                Log.e("RequisitionRepository", "Error fetching requisition: ${e.message}")
            }
        }

        return requisition
    }
    suspend fun syncRequisitions() = withContext(dispatchers.io) {
        val userId = getUserIdFromPreferences(context)

        if (NetworkUtils.isOnline()) {
            try {
                val serverRequisitions = userId?.let { apiService.getRequisitionsByUserId(it) }
                serverRequisitions?.forEach { serverRequisition ->
                    val localRequisition = requisitionDao.getRequisitionById(serverRequisition.requisitionId)
                    if (localRequisition == null) {
                        // New requisition from server, insert it
                        requisitionDao.insert(serverRequisition.toEntity())
                    } else {
                        // Update existing requisition
                        requisitionDao.updateRequisition(serverRequisition.toEntity())
                    }
                }
                Log.d("RequisitionRepository", "Requisitions synced successfully")
            } catch (e: Exception) {
                Log.e("RequisitionRepository", "Failed to fetch requisitions from server", e)
            }
        } else {
            Log.e("RequisitionRepository", "No internet connection available for syncing requisitions")
        }
    }
}
