package com.helgolabs.trego.data.repositories

import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dao.RequisitionDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.model.Requisition
import com.helgolabs.trego.data.sync.SyncableRepository
import com.helgolabs.trego.data.sync.managers.RequisitionSyncManager
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.NetworkUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
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

    val myApplication = context.applicationContext as MyApplication

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
        Log.d(TAG, "Fetching requisition for reference: $reference")
        try {
            // First try local
            val localRequisition = requisitionDao.getRequisitionByReference(reference)?.toModel()
            if (localRequisition != null) {
                Log.d(TAG, "Found requisition in local DB")
                return localRequisition
            }

            // If not in local DB, fetch from server
            val serverRequisition = apiService.getRequisitionByReference(reference)
            Log.d(TAG, "Got requisition from server: $serverRequisition")

            if (serverRequisition != null) {
                // Convert server IDs to local IDs before saving
                myApplication.entityServerConverter.convertRequisitionFromServer(serverRequisition).fold(
                    onSuccess = { localEntity ->
                        requisitionDao.insert(localEntity)
                        Log.d(TAG, "Successfully saved converted requisition to local DB")
                        return localEntity.toModel()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to convert server requisition", error)
                        throw error
                    }
                )
            }

            return serverRequisition
        } catch (e: Exception) {
            Log.e(TAG, "Error in getRequisitionByReference", e)
            throw e
        }
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
