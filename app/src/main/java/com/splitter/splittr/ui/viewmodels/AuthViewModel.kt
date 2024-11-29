package com.splitter.splittr.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitter.splittr.data.local.dataClasses.AuthResponse
import com.splitter.splittr.data.local.dataClasses.LoginRequest
import com.splitter.splittr.data.repositories.InstitutionRepository
import com.splitter.splittr.data.repositories.UserRepository
import com.splitter.splittr.ui.screens.RegisterRequest
import com.splitter.splittr.utils.CoroutineDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val userRepository: UserRepository,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {

    private val _authResult = MutableStateFlow<Result<AuthResponse>?>(null)
    val authResult: StateFlow<Result<AuthResponse>?> = _authResult

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun register(registerRequest: RegisterRequest) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                val result = userRepository.registerUser(registerRequest)
                _authResult.value = result
            } catch (e: Exception) {
                _authResult.value = Result.failure(e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun login(context: Context, loginRequest: LoginRequest) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                val result = userRepository.loginUser(context, loginRequest)
                _authResult.value = result
            } catch (e: Exception) {
                _authResult.value = Result.failure(e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearAuthResult() {
        _authResult.value = null
    }
}