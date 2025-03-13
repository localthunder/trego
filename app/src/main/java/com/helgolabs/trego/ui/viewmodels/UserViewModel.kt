package com.helgolabs.trego.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.entities.UserEntity
import com.helgolabs.trego.data.repositories.UserRepository
import com.helgolabs.trego.data.model.User
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserViewModel(
    private val userRepository: UserRepository,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context
) : ViewModel() {
    private val _user = MutableStateFlow<UserEntity?>(null)
    val user: StateFlow<UserEntity?> = _user

    private val _users = MutableStateFlow<List<UserEntity>>(emptyList())
    val users: StateFlow<List<UserEntity>> = _users

    private val _loading = MutableStateFlow<Boolean>(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _passwordChangeResult = MutableStateFlow<Result<Unit>?>(null)
    val passwordChangeResult: StateFlow<Result<Unit>?> = _passwordChangeResult

    private val _resetToken = MutableStateFlow<Result<String>?>(null)
    val resetToken: StateFlow<Result<String>?> = _resetToken

    private val _usernameUpdateResult = MutableStateFlow<Result<Unit>?>(null)
    val usernameUpdateResult: StateFlow<Result<Unit>?> = _usernameUpdateResult

    fun loadUser(userId: Int) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = (true)
            try {
                userRepository.getUserById(userId).collect { userEntity ->
                    userEntity?.let {
                        _user.value = (it)
                    }
                }
            } catch (e: Exception) {
                _error.value = ("Failed to load user: ${e.message}")
            } finally {
                _loading.value = (false)
            }
        }
    }

    fun loadUsers(userIds: List<Int>) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = (true)
            try {
                userRepository.getUsersByIds(userIds).collect { userEntities ->
                    _users.value = (userEntities)
                }
            } catch (e: Exception) {
                _error.value = ("Failed to load users: ${e.message}")
            } finally {
                _loading.value = (false)
            }
        }
    }

    fun refreshUser(userId: Int) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = (true)
            try {
                userRepository.refreshUser(userId)
                loadUser(userId) // Reload the user after refreshing
            } catch (e: Exception) {
                _error.value = ("Failed to refresh user: ${e.message}")
            } finally {
                _loading.value = (false)
            }
        }
    }

    fun refreshToken(context: Context, refreshToken: String) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = (true)
            try {
                val result = userRepository.refreshToken(context, refreshToken)
                if (result.isSuccess) {
                    // Handle successful token refresh
                    // You might want to store the new token or update the user session
                } else {
                    _error.value = ("Failed to refresh token: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _error.value = ("Failed to refresh token: ${e.message}")
            } finally {
                _loading.value = (false)
            }
        }
    }

    suspend fun createProvisionalUser(username: String, email: String?, inviteLater: Boolean, groupId: Int): Result<Int> {
        _loading.value = true
        return try {
            // Simply delegate to repository and return the result
            userRepository.createProvisionalUser(username, email, inviteLater, groupId)
        } catch (e: Exception) {
            Log.e("UserViewModel", "Failed to create provisional user", e)
            _error.value = "Failed to create provisional user: ${e.message}"
            Result.failure(e)
        } finally {
            _loading.value = false
        }
    }

    suspend fun getUserByServerId(serverId: Int): Result<User> = withContext(dispatchers.io) {
        try {
            val user = userRepository.getUserByServerId(serverId)?.toModel()
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun initiatePasswordChange() {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                val result = userRepository.initiatePasswordChange()
                _resetToken.value = result
            } catch (e: Exception) {
                _resetToken.value = Result.failure(e)
                _error.value = "Failed to initiate password change: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearPasswordChangeResult() {
        _passwordChangeResult.value = null
        _resetToken.value = null
    }

    fun updateUsername(newUsername: String) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                val userId = getUserIdFromPreferences(context)
                    ?: throw Exception("No user logged in")

                val result = userRepository.updateUsername(userId, newUsername)
                _usernameUpdateResult.value = result

                // Reload user data if update was successful
                if (result.isSuccess) {
                    loadUser(userId)
                }
            } catch (e: Exception) {
                _usernameUpdateResult.value = Result.failure(e)
                _error.value = "Failed to update username: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearUsernameUpdateResult() {
        _usernameUpdateResult.value = null
    }

    fun syncUsers() {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = (true)
            try {
                userRepository.sync()
                // You might want to reload the users after syncing
            } catch (e: Exception) {
                _error.value = ("Failed to sync users: ${e.message}")
            } finally {
                _loading.value = (false)
            }
        }
    }
}