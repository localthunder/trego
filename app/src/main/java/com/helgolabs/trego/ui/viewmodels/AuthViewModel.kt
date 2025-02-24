package com.helgolabs.trego.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helgolabs.trego.data.local.dataClasses.AuthResponse
import com.helgolabs.trego.data.local.dataClasses.LoginRequest
import com.helgolabs.trego.data.local.dataClasses.RegisterRequest
import com.helgolabs.trego.data.repositories.InstitutionRepository
import com.helgolabs.trego.data.repositories.UserRepository
import com.helgolabs.trego.utils.AuthUtils
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                // Get login result
                val result = userRepository.loginUser(context, loginRequest)

                // If login successful, store token and wait for completion
                result.onSuccess { authResponse ->
                    authResponse.token?.let { token ->
                        // Store token and wait for completion
                        withContext(dispatchers.io) {
                            TokenManager.saveAccessToken(context, token)
                            AuthUtils.storeLoginState(context, token)
                            // Add delay to ensure token is propagated
                            delay(100)
                        }
                    }
                }

                // Only set result after token is stored
                _authResult.value = result

            } catch (e: Exception) {
                _authResult.value = Result.failure(e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun requestPasswordReset(email: String) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                val result = userRepository.requestPasswordReset(email)
                _authResult.value = result
            } catch (e: Exception) {
                _authResult.value = Result.failure(e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun resetPassword(token: String, newPassword: String) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                val result = userRepository.resetPassword(token, newPassword)
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