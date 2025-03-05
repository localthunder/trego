package com.helgolabs.trego.data.repositories

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.Color
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dao.InstitutionDao
import com.helgolabs.trego.data.local.dataClasses.RequisitionRequest
import com.helgolabs.trego.data.local.dataClasses.RequisitionResponseWithRedirect
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.model.Institution
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.GradientBorderUtils
import com.helgolabs.trego.utils.InstitutionLogoManager
import downloadAndSaveImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class InstitutionRepository(
    private val institutionDao: InstitutionDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context,
    private val logoManager: InstitutionLogoManager
) {

    suspend fun insert(institution: Institution) {
        institutionDao.insert(institution.toEntity())
    }

    /**
     * Get a flow of institutions that emits local data immediately and refreshes from network
     */
    fun getInstitutionsStream(): Flow<List<Institution>> {
        return institutionDao.getInstitutionsStream()
            .map { institutions -> institutions.map { it.toModel() } }
            .onStart { refreshInstitutions("GB") }
    }

    /**
     * Get institutions with offline-first approach
     */
    suspend fun getAllInstitutions(): List<Institution> = withContext(dispatchers.io) {
        val localInstitutions = institutionDao.getAllInstitutions().map { it.toModel() }

        // Trigger a refresh in the background
        launch {
            refreshInstitutions("GB")
        }

        return@withContext localInstitutions
    }

    /**
     * Refresh institutions from the API
     */
    suspend fun refreshInstitutions(country: String) {
        withContext(dispatchers.io) {
            try {
                val apiInstitutions = apiService.getInstitutions(country)

                institutionDao.runInTransaction {
                    apiInstitutions.forEach { apiInstitution ->
                        val localInstitution = institutionDao.getInstitutionById(apiInstitution.id)
                        if (localInstitution == null) {
                            institutionDao.insert(apiInstitution.toEntity())
                        } else {
                            val updatedInstitution = apiInstitution.toEntity().copy(
                                localLogoPath = localInstitution.localLogoPath
                            )
                            institutionDao.updateInstitution(updatedInstitution)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("InstitutionRepository", "Error refreshing institutions from API", e)
                // The flow will continue to emit the latest local data regardless of this error
            }
        }
    }

    suspend fun getInstitutionById(id: String): Institution? {
        return institutionDao.getInstitutionById(id)?.toModel()
    }

    suspend fun createRequisition(requisitionRequest: RequisitionRequest): RequisitionResponseWithRedirect = withContext(dispatchers.io) {
        apiService.createRequisition(requisitionRequest)
    }

    suspend fun createRequisitionAndGetLink(
        institutionId: String,
        baseUrl: String = "trego://bankaccounts",
        returnRoute: String = "home"
    ): RequisitionResponseWithRedirect = withContext(dispatchers.io) {
        val requisitionRequest = RequisitionRequest(
            baseUrl = baseUrl,
            institutionId = institutionId,
            userLanguage = "EN",
            returnRoute = returnRoute
        )
        createRequisition(requisitionRequest)
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

    suspend fun downloadAndSaveInstitutionLogo(institutionId: String): Result<InstitutionLogoManager.LogoInfo> = withContext(dispatchers.io) {
        try {
            val logoUrl = getInstitutionLogoUrl(institutionId)
            if (logoUrl == null) {
                return@withContext Result.failure(Exception("No logo URL available"))
            }

            // Use the logo manager to fetch and process the image
            val logoInfo = logoManager.getOrFetchLogo(institutionId, logoUrl)
                ?: return@withContext Result.failure(Exception("Failed to fetch logo"))

            // Update institution with local logo path
            institutionDao.getInstitutionById(institutionId)?.let { entity ->
                institutionDao.updateInstitution(entity.copy(localLogoPath = logoInfo.file.absolutePath))
            }

            Result.success(logoInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLocalInstitutionLogo(institutionId: String): InstitutionLogoManager.LogoInfo? = withContext(dispatchers.io) {
        try {
            val institution = institutionDao.getInstitutionById(institutionId) ?: return@withContext null

            // Use the logo manager to get the logo
            return@withContext logoManager.getOrFetchLogo(institutionId, null)
        } catch (e: Exception) {
            Log.e("InstitutionRepository", "Error getting local logo", e)
            return@withContext null
        }
    }

    suspend fun syncInstitutions(country: String) = withContext(dispatchers.io) {
        try {
            val serverInstitutions = apiService.getInstitutions(country)

            // Update local database in a single transaction
            institutionDao.runInTransaction {
                serverInstitutions.forEach { serverInstitution ->
                    val localInstitution = institutionDao.getInstitutionById(serverInstitution.id)
                    if (localInstitution == null) {
                        institutionDao.insert(serverInstitution.toEntity())
                    } else {
                        // Preserve local logo path
                        val updatedInstitution = serverInstitution.toEntity().copy(
                            localLogoPath = localInstitution.localLogoPath
                        )
                        institutionDao.updateInstitution(updatedInstitution)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InstitutionRepository", "Failed to sync institutions", e)
            throw e
        }
    }
}
