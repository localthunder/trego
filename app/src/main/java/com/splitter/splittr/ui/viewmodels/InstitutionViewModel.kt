package com.splitter.splittr.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitter.splittr.data.local.repositories.InstitutionRepository
import com.splitter.splittr.data.network.RequisitionRequest
import com.splitter.splittr.data.network.RequisitionResponseWithRedirect
import com.splitter.splittr.model.Institution
import com.splitter.splittr.utils.CoroutineDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private val _logoInfo = MutableStateFlow<Map<String, InstitutionRepository.LogoInfo>>(emptyMap())
    val logoInfo: StateFlow<Map<String, InstitutionRepository.LogoInfo>> = _logoInfo.asStateFlow()

    private val _requisitionLink = MutableStateFlow<Result<RequisitionResponseWithRedirect>?>(null)
    val requisitionLink: StateFlow<Result<RequisitionResponseWithRedirect>?> = _requisitionLink.asStateFlow()



    init {
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
        baseUrl: String = "splitter://bankaccounts"
    ) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val result = institutionRepository.createRequisitionAndGetLink(
                    institutionId = institutionId,
                    baseUrl = baseUrl
                )
                _requisitionLink.value = Result.success(result)
                Log.d("InstitutionViewModel", "Requisition link created: ${result.link}") // Changed from redirectUrl to link
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
            try {
                val result = institutionRepository.downloadAndSaveInstitutionLogo(institutionId)
                result.onSuccess { info ->
                    _logoInfo.update { current ->
                        current + (institutionId to info)
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to load logo: ${e.message}"
            }
        }
    }

    fun getInstitutionLogoUrl(institutionId: String) {
        viewModelScope.launch(dispatchers.io) {
            try {
                val logoUrl = institutionRepository.getInstitutionLogoUrl(institutionId)
                _institutionLogoUrl.value = logoUrl
            } catch (e: Exception) {
                _error.value = "Failed to get institution logo URL: ${e.message}"
                _institutionLogoUrl.value = null
            }
        }
    }
}