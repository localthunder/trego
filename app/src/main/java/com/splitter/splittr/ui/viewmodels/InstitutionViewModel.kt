package com.splitter.splittr.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitter.splittr.data.local.repositories.InstitutionRepository
import com.splitter.splittr.data.network.RequisitionRequest
import com.splitter.splittr.data.network.RequisitionResponseWithRedirect
import com.splitter.splittr.model.Institution
import com.splitter.splittr.utils.CoroutineDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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