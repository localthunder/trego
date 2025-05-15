package com.helgolabs.trego.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helgolabs.trego.MyApplication
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
import retrofit2.HttpException
import java.net.HttpURLConnection

class AuthViewModel(
    private val userRepository: UserRepository,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {

    private val _authResult = MutableStateFlow<Result<AuthResponse>?>(null)
    val authResult: StateFlow<Result<AuthResponse>?> = _authResult

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun register(context: Context, registerRequest: RegisterRequest) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                val result = userRepository.registerUser(registerRequest)
                _authResult.value = result

                // If registration successful, register FCM token
                result.onSuccess { authResponse ->
                    // Store token and auth state
                    authResponse.token?.let { token ->
                        withContext(dispatchers.io) {
                            TokenManager.saveAccessToken(context, token)
                            AuthUtils.storeLoginState(context, token)
                            delay(100)
                        }

                        // Register FCM token after successful registration
                        (context.applicationContext as? MyApplication)?.registerCurrentFcmToken()
                    }
                }
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

            // Get login result
            val result = userRepository.loginUser(context, loginRequest)

            // Transform the result to handle rate limiting
            val transformedResult = result.fold(
                onSuccess = { authResponse ->
                    // If login successful, store token and wait for completion
                    authResponse.token?.let { token ->
                        withContext(dispatchers.io) {
                            TokenManager.saveAccessToken(context, token)
                            AuthUtils.storeLoginState(context, token)
                            // Add delay to ensure token is propagated
                            delay(100)
                        }

                        // Register FCM token after successful login
                        (context.applicationContext as? MyApplication)?.registerCurrentFcmToken()
                    }
                    Result.success(authResponse)
                },
                onFailure = { exception ->
                    // Handle rate limiting specifically
                    val errorMessage = when {
                        exception.message?.contains("429") == true -> {
                            // Return a user-friendly message for rate limiting
                            "Too many login attempts. Please wait 1 minute before trying again."
                        }
                        exception.message?.contains("401") == true -> {
                            "Invalid email or password"
                        }
                        exception is HttpException && exception.code() == 429 -> {
                            "Too many login attempts. Please wait 1 minute before trying again."
                        }
                        exception is HttpException && exception.code() == HttpURLConnection.HTTP_UNAUTHORIZED -> {
                            "Invalid email or password"
                        }
                        else -> exception.message ?: "An unexpected error occurred"
                    }

                    // Create a custom exception with the user-friendly message
                    Result.failure(Exception(errorMessage))
                }
            )

            _authResult.value = transformedResult
            _loading.value = false
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