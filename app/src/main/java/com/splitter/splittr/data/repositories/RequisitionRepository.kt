package com.splitter.splittr.data.repositories

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.RequisitionDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.model.Requisition
import com.splitter.splittr.data.sync.SyncableRepository
import com.splitter.splittr.data.sync.managers.RequisitionSyncManager
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class RequisitionRepository(
    private val requisitionDao: RequisitionDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context,
    private val syncMetadataDao: SyncMetadataDao,
    private val requisitionSyncManager: RequisitionSyncManager
) : SyncableRepository {

    override val entityType = "requisitions"
    override val syncPriority = 2  // Sync before bank accounts but after groups

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
    override suspend fun sync(): Unit = withContext(dispatchers.io) {
        try {
            Log.d(TAG, "Starting requisition sync")
            requisitionSyncManager.performSync()
            Log.d(TAG, "Requisition sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during requisition sync", e)
            throw e
        }
    }
    companion object {
        private const val TAG = "RequisitionRepository"
    }
}
