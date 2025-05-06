package com.helgolabs.trego.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helgolabs.trego.data.local.dataClasses.PreferenceKeys
import com.helgolabs.trego.data.repositories.UserPreferencesRepository
import com.helgolabs.trego.ui.theme.ThemeManager
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class UserPreferencesViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context
) : ViewModel() {

    // StateFlows for UI updates
    private val _themeMode = MutableStateFlow(PreferenceKeys.ThemeMode.SYSTEM)
    val themeMode: StateFlow<String> = _themeMode

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Initialize the ViewModel by loading current preferences
    init {
        loadPreferences()
    }

    fun loadPreferences() {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                val userId = getUserIdFromPreferences(context)
                if (userId != null) {
                    // Load theme mode
                    userPreferencesRepository.getThemeModeFlow(userId).firstOrNull()?.let {
                        _themeMode.value = it
                    }

                    // Load notifications setting
                    userPreferencesRepository.areNotificationsEnabledFlow(userId).firstOrNull()?.let {
                        _notificationsEnabled.value = it
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to load preferences: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                val userId = getUserIdFromPreferences(context)
                    ?: throw Exception("No user logged in")

                userPreferencesRepository.setThemeMode(userId, mode)
                _themeMode.value = mode

                // Update the ThemeManager to trigger app-wide recomposition
                ThemeManager.setThemeMode(mode)
            } catch (e: Exception) {
                _error.value = "Failed to set theme mode: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                val userId = getUserIdFromPreferences(context)
                    ?: throw Exception("No user logged in")

                userPreferencesRepository.setNotificationsEnabled(userId, enabled)
                _notificationsEnabled.value = enabled
            } catch (e: Exception) {
                _error.value = "Failed to set notifications preference: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun toggleThemeMode() {
        val newMode = when (themeMode.value) {
            PreferenceKeys.ThemeMode.LIGHT -> PreferenceKeys.ThemeMode.DARK
            PreferenceKeys.ThemeMode.DARK -> PreferenceKeys.ThemeMode.LIGHT
            else -> PreferenceKeys.ThemeMode.LIGHT
        }
        setThemeMode(newMode)
    }

    fun toggleNotifications() {
        setNotificationsEnabled(!notificationsEnabled.value)
    }

    fun clearError() {
        _error.value = null
    }
}