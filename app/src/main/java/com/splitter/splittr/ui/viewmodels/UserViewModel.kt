package com.splitter.splittr.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.repositories.UserRepository
import com.splitter.splittr.model.User
import com.splitter.splittr.utils.CoroutineDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserViewModel(
    private val userRepository: UserRepository,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    private val _loading = MutableStateFlow<Boolean>(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadUser(userId: Int) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = (true)
            try {
                userRepository.getUserById(userId).collect { userEntity ->
                    userEntity?.let {
                        _user.value = (it.toModel())
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
                    _users.value = (userEntities.map { it.toModel() })
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

    fun syncUsers() {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = (true)
            try {
                userRepository.syncUsers()
                // You might want to reload the users after syncing
            } catch (e: Exception) {
                _error.value = ("Failed to sync users: ${e.message}")
            } finally {
                _loading.value = (false)
            }
        }
    }
}