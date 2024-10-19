package com.splitter.splittr.data.local.repositories

import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.InstitutionDao
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.network.RequisitionRequest
import com.splitter.splittr.data.network.RequisitionResponseWithRedirect
import com.splitter.splittr.model.Institution
import com.splitter.splittr.utils.CoroutineDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstitutionRepository(
    private val institutionDao: InstitutionDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers
) {

    suspend fun insert(institution: Institution) {
        institutionDao.insert(institution.toEntity())
    }

    suspend fun getAllInstitutions(): List<Institution> = withContext(Dispatchers.IO) {
        val localInstitutions = institutionDao.getAllInstitutions().map { it.toModel() }

        try {
            // Fetch institutions from the API
            val apiInstitutions = apiService.getInstitutions("GB") // Assuming "GB" for United Kingdom, adjust as needed

            // Update local database with API data
            apiInstitutions.forEach { apiInstitution ->
                val localInstitution = institutionDao.getInstitutionById(apiInstitution.id)
                if (localInstitution == null) {
                    // Insert new institution
                    institutionDao.insert(apiInstitution.toEntity())
                } else {
                    // Update existing institution
                    institutionDao.updateInstitution(apiInstitution.toEntity())
                }
            }

            // Return the updated list from the database
            institutionDao.getAllInstitutions().map { it.toModel() }
        } catch (e: Exception) {
            Log.e("InstitutionRepository", "Error fetching institutions from API", e)
            // If API call fails, return local data
            localInstitutions
        }
    }

    suspend fun getInstitutionById(id: String): Institution? {
        return institutionDao.getInstitutionById(id)?.toModel()
    }

    suspend fun createRequisition(requisitionRequest: RequisitionRequest): RequisitionResponseWithRedirect = withContext(dispatchers.io) {
        apiService.createRequisition(requisitionRequest)
    }

    suspend fun getInstitutionLogoUrl(institutionId: String): String? = withContext(dispatchers.io) {
        val institution = institutionDao.getInstitutionById(institutionId)
        return@withContext institution?.logo ?: fetchLogoUrlFromApi(institutionId)
    }

    private suspend fun fetchLogoUrlFromApi(institutionId: String): String? {
        return try {
            val apiInstitution = apiService.getInstitutionById(institutionId)

            // Update the institution in the local database
            institutionDao.updateInstitution(apiInstitution.toEntity())

            // Return the logo URL
            apiInstitution.logo
        } catch (e: Exception) {
            Log.e("InstitutionRepository", "Error fetching institution logo from API", e)
            null
        }
    }

    suspend fun syncInstitutions() = withContext(dispatchers.io) {
        // Sync local unsynced institutions to the server
//        institutionDao.getUnsyncedInstitutions().forEach { institutionEntity ->
        institutionDao.getAllInstitutions().forEach { institutionEntity ->
            try {
                institutionDao.insert(institutionEntity)
//                institutionDao.updateInstitutionSyncStatus(institutionEntity.id, SyncStatus.SYNCED.name)
            } catch (e: Exception) {
//                institutionDao.updateInstitutionSyncStatus(institutionEntity.id, SyncStatus.SYNC_FAILED.name)
                Log.e("SyncInstitutions", "Failed to sync institution ${institutionEntity.id}", e)
            }
        }

        // Sync all local institutions with the server (including creation of new institutions on the server)
        institutionDao.getAllInstitutions().forEach { institutionEntity ->
            try {
                // Check if the institution exists on the server
                val serverInstitution = try {
                    apiService.getInstitutionById(institutionEntity.id)
                } catch (e: Exception) {
                    null // Institution doesn't exist on the server
                }

                if (serverInstitution == null) {
                    // Institution doesn't exist on the server, so create it
                    apiService.insertInstitution(institutionEntity.toModel())
                } else {
                    // Institution exists, so update it
//                    apiService.updateInstitution(institutionEntity.id, institutionEntity.toModel())
                }
//                institutionDao.updateInstitutionSyncStatus(institutionEntity.id, SyncStatus.SYNCED.name)
            } catch (e: Exception) {
                Log.e("ReverseSyncInstitutions", "Failed to sync institution ${institutionEntity.id}", e)
//                institutionDao.updateInstitutionSyncStatus(institutionEntity.id, SyncStatus.SYNC_FAILED.name)
            }
        }

        // Fetch all institutions from the server and update local database
        // MUST CHANGE GB HERE!! This is only needed for rebuilding the server database after the crash
        try {
            val serverInstitutions = apiService.getInstitutions("GB")
            serverInstitutions.forEach { serverInstitution ->
                val localInstitution = institutionDao.getInstitutionById(serverInstitution.id)
                if (localInstitution == null) {
                    // New institution from server, insert it locally
                    institutionDao.insert(serverInstitution.toEntity())
                } else {
                    // Update existing institution
                    institutionDao.updateInstitution(serverInstitution.toEntity())
                }
            }
        } catch (e: Exception) {
            Log.e("FetchServerInstitutions", "Failed to fetch institutions from server", e)
        }
    }
}
