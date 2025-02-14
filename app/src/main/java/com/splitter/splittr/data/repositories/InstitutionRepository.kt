package com.splitter.splittr.data.repositories

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.Color
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.InstitutionDao
import com.splitter.splittr.data.local.dataClasses.RequisitionRequest
import com.splitter.splittr.data.local.dataClasses.RequisitionResponseWithRedirect
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.model.Institution
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.GradientBorderUtils
import com.splitter.splittr.utils.InstitutionLogoManager
import downloadAndSaveImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class InstitutionRepository(
    private val institutionDao: InstitutionDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context
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

    suspend fun createRequisitionAndGetLink(
        institutionId: String,
        baseUrl: String = "splittr://bankaccounts"
    ): RequisitionResponseWithRedirect = withContext(dispatchers.io) {
        val requisitionRequest = RequisitionRequest(
            baseUrl = baseUrl,
            institutionId = institutionId,
            reference = "ref_${System.currentTimeMillis()}",
            userLanguage = "EN"
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
            if (logoUrl == null) return@withContext Result.failure(Exception("No logo URL available"))

            val logoFilename = "${institutionId}.png"
            val file = downloadAndSaveImage(context, logoUrl, logoFilename)
                ?: return@withContext Result.failure(Exception("Failed to download logo"))

            val bitmap = BitmapFactory.decodeFile(file.path)
                ?: return@withContext Result.failure(Exception("Failed to decode logo"))

            val dominantColors = GradientBorderUtils.getDominantColors(bitmap).map { Color(it) }
            val logoInfo =
                InstitutionLogoManager.LogoInfo(
                    file = file,
                    bitmap = bitmap,
                    dominantColors = dominantColors
                )

            // Update institution with local logo path
            institutionDao.getInstitutionById(institutionId)?.let { entity ->
                institutionDao.updateInstitution(entity.copy(localLogoPath = file.absolutePath))
            }

            Result.success(logoInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLocalInstitutionLogo(institutionId: String): InstitutionLogoManager.LogoInfo? = withContext(dispatchers.io) {
        val institution = institutionDao.getInstitutionById(institutionId) ?: return@withContext null
        val localPath = institution.localLogoPath ?: return@withContext null

        val file = File(localPath)
        if (!file.exists()) return@withContext null

        val bitmap = BitmapFactory.decodeFile(file.path) ?: return@withContext null
        val dominantColors = GradientBorderUtils.getDominantColors(bitmap).map { Color(it) }

        InstitutionLogoManager.LogoInfo(
            file = file,
            bitmap = bitmap,
            dominantColors = dominantColors
        )
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
                        institutionDao.updateInstitution(serverInstitution.toEntity())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InstitutionRepository", "Failed to sync institutions", e)
            throw e
        }
    }
}
