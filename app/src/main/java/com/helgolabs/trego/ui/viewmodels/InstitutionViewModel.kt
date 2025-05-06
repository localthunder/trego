package com.helgolabs.trego.ui.viewmodels

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helgolabs.trego.data.repositories.InstitutionRepository
import com.helgolabs.trego.data.local.dataClasses.RequisitionRequest
import com.helgolabs.trego.data.local.dataClasses.RequisitionResponseWithRedirect
import com.helgolabs.trego.data.model.Institution
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.InstitutionLogoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class InstitutionViewModel(
    private val institutionRepository: InstitutionRepository,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {

    private val _institutions = MutableStateFlow<List<Institution>>(emptyList())
    val institutions: StateFlow<List<Institution>> = _institutions

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _institutionLogoUrl = MutableStateFlow<String?>(null)
    val institutionLogoUrl: StateFlow<String?> = _institutionLogoUrl

    private val _requisitionLink = MutableStateFlow<Result<RequisitionResponseWithRedirect>?>(null)
    val requisitionLink: StateFlow<Result<RequisitionResponseWithRedirect>?> = _requisitionLink.asStateFlow()

    private val _logoInfo = MutableStateFlow<Map<String, InstitutionLogoManager.LogoInfo>>(emptyMap())
    val logoInfo = _logoInfo.asStateFlow()

    private val activeLogoFetches = mutableSetOf<String>()
    private val fetchMutex = Mutex()


    init {
        // Use a simpler approach - just fetch the data once on initialization
        fetchInstitutions()
    }

    private fun fetchInstitutions() {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                val fetchedInstitutions = institutionRepository.getAllInstitutions()
                _institutions.value = fetchedInstitutions
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to fetch institutions: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun syncInstitutions(country: String) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                institutionRepository.syncInstitutions(country)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to fetch institutions: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    suspend fun createRequisition(requisitionRequest: RequisitionRequest): Result<RequisitionResponseWithRedirect> {
        return try {
            val response = institutionRepository.createRequisition(requisitionRequest)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createRequisitionLink(
        institutionId: String,
        currentRoute: String,
        baseUrl: String = "trego://bankaccounts"
    ) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val encodedRoute = Uri.encode(currentRoute)
                val baseUrlWithReturn = "$baseUrl?returnRoute=$encodedRoute"

                val result = institutionRepository.createRequisitionAndGetLink(
                    institutionId = institutionId,
                    baseUrl = baseUrlWithReturn
                )
                _requisitionLink.value = Result.success(result)
            } catch (e: Exception) {
                Log.e("InstitutionViewModel", "Error creating requisition", e)
                _requisitionLink.value = Result.failure(e)
                _error.value = "Failed to create requisition: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearRequisitionLink() {
        _requisitionLink.value = null
    }

    fun loadInstitutionLogo(institutionId: String) {
        viewModelScope.launch {
            if (!fetchMutex.withLock { activeLogoFetches.add(institutionId) }) {
                return@launch
            }

            try {
                // First try to get logo from local storage
                val localLogo = institutionRepository.getLocalInstitutionLogo(institutionId)
                if (localLogo != null) {
                    _logoInfo.update { current -> current + (institutionId to localLogo) }
                    return@launch
                }

                // If no local logo, download from server
                val result = institutionRepository.downloadAndSaveInstitutionLogo(institutionId)
                result.onSuccess { info ->
                    _logoInfo.update { current -> current + (institutionId to info) }
                }.onFailure { e ->
                    _error.value = "Failed to load logo: ${e.message}"
                }
            } finally {
                fetchMutex.withLock {
                    activeLogoFetches.remove(institutionId)
                }
            }
        }
    }

    suspend fun getInstitutionLogoUrl(institutionId: String): String? {
        Log.d("InstitutionViewModel", "Fetching logo URL for $institutionId")
        return withContext(dispatchers.io) {
            try {
                val logoUrl = institutionRepository.getInstitutionLogoUrl(institutionId)
                _institutionLogoUrl.value = logoUrl
                Log.d("InstitutionViewModel", "Got logo URL: $logoUrl for $institutionId")
                logoUrl
            } catch (e: Exception) {
                Log.e("InstitutionViewModel", "Failed to get logo URL for $institutionId", e)
                _institutionLogoUrl.value = null
                null
            }
        }
    }
}