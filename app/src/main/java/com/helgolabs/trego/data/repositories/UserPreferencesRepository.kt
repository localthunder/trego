package com.helgolabs.trego.data.repositories

import android.content.Context
import android.util.Log
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dao.UserPreferenceDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.dataClasses.PreferenceKeys
import com.helgolabs.trego.data.local.entities.UserPreferenceEntity
import com.helgolabs.trego.data.model.UserPreference
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.sync.SyncableRepository
import com.helgolabs.trego.data.sync.managers.UserPreferencesSyncManager
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.NetworkUtils
import com.helgolabs.trego.utils.ServerIdUtil.getServerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

class UserPreferencesRepository(
    private val userPreferenceDao: UserPreferenceDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val syncMetadataDao: SyncMetadataDao,
    private val userPreferencesSyncManager: UserPreferencesSyncManager,
    private val context: Context
) : SyncableRepository {

    override val entityType = "user_preferences"
    override val syncPriority = 2 // Lower priority than users

    // Theme preference methods
    suspend fun setThemeMode(userId: Int, themeMode: String) = withContext(dispatchers.io) {
        val timestamp = DateUtils.getCurrentTimestamp()
        val preference = UserPreferenceEntity(
            userId = userId,
            preferenceKey = PreferenceKeys.THEME_MODE,
            preferenceValue = themeMode,
            updatedAt = timestamp,
            syncStatus = SyncStatus.PENDING_SYNC
        )

        try {
            // Insert the preference into the local database
            val insertId = userPreferenceDao.insertPreference(preference)

            // If online, try to sync immediately
            if (NetworkUtils.isOnline()) {
                val serverUserId = getServerId(userId, "users", context)
                    ?: return@withContext  // If we can't get server ID, just keep local entry

                // Create value wrapper for PUT requests
                val valueWrapper = mapOf("value" to themeMode)

                try {
                    // First try to see if this preference already exists on the server
                    val checkResponse = apiService.getUserPreference(PreferenceKeys.THEME_MODE)

                    if (checkResponse.success && checkResponse.data != null) {
                        // If it exists, update it
                        Log.d("UserPrefsRepository", "Preference exists on server, updating")
                        val updateResponse = apiService.updateUserPreference(
                            PreferenceKeys.THEME_MODE,
                            valueWrapper
                        )

                        if (updateResponse.isSuccessful && updateResponse.body()?.success == true) {
                            // Update local entry with server ID
                            val updatedPreference = preference.copy(
                                id = insertId.toInt(),
                                serverId = checkResponse.data.id,
                                syncStatus = SyncStatus.SYNCED
                            )
                            userPreferenceDao.updatePreference(updatedPreference)
                            Log.d("UserPrefsRepository", "Updated local entry with server ID: ${checkResponse.data.id}")
                        }
                    }
                } catch (e: Exception) {
                    Log.d("UserPrefsRepository", "Error checking if preference exists: ${e.message}")
                }

                // If check fails or preference doesn't exist, try to create it
                if (preference.serverId == null) {
                    try {
                        // Convert to server model
                        val serverPreference = UserPreference(
                            userId = serverUserId,
                            preferenceKey = preference.preferenceKey,
                            preferenceValue = preference.preferenceValue,
                            updatedAt = preference.updatedAt
                        )

                        // Create on server
                        val createResponse = apiService.createUserPreference(serverPreference)

                        if (createResponse.isSuccessful && createResponse.body()?.success == true) {
                            // Get server ID from response
                            @Suppress("UNCHECKED_CAST")
                            val data = createResponse.body()?.data as? Map<String, Any>
                            val serverId = data?.get("id") as? Int

                            if (serverId != null) {
                                // Update local entry with server ID
                                val updatedPreference = preference.copy(
                                    id = insertId.toInt(),
                                    serverId = serverId,
                                    syncStatus = SyncStatus.SYNCED
                                )
                                userPreferenceDao.updatePreference(updatedPreference)
                                Log.d("UserPrefsRepository", "Created on server and updated local entry with server ID: $serverId")
                            }
                        } else if (createResponse.code() == 400 &&
                            createResponse.body()?.message?.contains("already exists") == true) {
                            // If it already exists (race condition), try to get it and update local entry
                            try {
                                val getResponse = apiService.getUserPreference(PreferenceKeys.THEME_MODE)

                                if (getResponse.success && getResponse.data != null) {
                                    val updatedPreference = preference.copy(
                                        id = insertId.toInt(),
                                        serverId = getResponse.data.id,
                                        syncStatus = SyncStatus.SYNCED
                                    )
                                    userPreferenceDao.updatePreference(updatedPreference)
                                    Log.d("UserPrefsRepository", "Updated local entry with server ID after conflict: ${getResponse.data.id}")
                                }
                            } catch (e: Exception) {
                                Log.e("UserPrefsRepository", "Error fetching server ID after conflict: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("UserPrefsRepository", "Error creating preference on server: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UserPrefsRepository", "Error setting theme mode", e)
            throw e
        }
    }

    suspend fun getThemeMode(userId: Int): String = withContext(dispatchers.io) {
        try {
            // Try to refresh from server if online
            if (NetworkUtils.isOnline()) {
                try {
                    val serverUserId = getServerId(userId, "users", context) ?: 0
                    refreshUserPreferences(serverUserId)
                } catch (e: Exception) {
                    Log.w("UserPrefsRepository", "Failed to refresh preferences from server", e)
                    // Continue with local data if refresh fails
                }
            }

            // Return local data
            userPreferenceDao.getPreferenceValue(userId, PreferenceKeys.THEME_MODE)
                ?: PreferenceKeys.ThemeMode.SYSTEM // Default value
        } catch (e: Exception) {
            Log.e("UserPrefsRepository", "Error getting theme mode", e)
            PreferenceKeys.ThemeMode.SYSTEM // Fall back to default on error
        }
    }

    fun getThemeModeFlow(userId: Int): Flow<String> {
        return userPreferenceDao.getPreferenceFlow(userId, PreferenceKeys.THEME_MODE)
            .map { it?.preferenceValue ?: PreferenceKeys.ThemeMode.SYSTEM }
    }

    // Notification preference methods
    suspend fun setNotificationsEnabled(userId: Int, enabled: Boolean) = withContext(dispatchers.io) {
        val timestamp = DateUtils.getCurrentTimestamp()
        val value = if (enabled) PreferenceKeys.BooleanValue.TRUE else PreferenceKeys.BooleanValue.FALSE
        val preference = UserPreferenceEntity(
            userId = userId,
            preferenceKey = PreferenceKeys.NOTIFICATIONS_ENABLED,
            preferenceValue = value,
            updatedAt = timestamp,
            syncStatus = if (NetworkUtils.isOnline()) SyncStatus.PENDING_SYNC else SyncStatus.LOCAL_ONLY
        )

        try {
            // Insert the preference into the local database
            val insertId = userPreferenceDao.insertPreference(preference)

            // If online, try to sync immediately
            if (NetworkUtils.isOnline()) {
                val serverUserId = getServerId(userId, "users", context)
                    ?: return@withContext  // If we can't get server ID, just keep local entry

                // Create value wrapper for PUT requests
                val valueWrapper = mapOf("value" to value)

                try {
                    // First try to see if this preference already exists on the server
                    val checkResponse = apiService.getUserPreference(PreferenceKeys.NOTIFICATIONS_ENABLED)

                    if (checkResponse.success && checkResponse.data != null) {
                        // If it exists, update it
                        Log.d("UserPrefsRepository", "Notifications preference exists on server, updating")
                        val updateResponse = apiService.updateUserPreference(
                            PreferenceKeys.NOTIFICATIONS_ENABLED,
                            valueWrapper
                        )

                        if (updateResponse.isSuccessful && updateResponse.body()?.success == true) {
                            // Update local entry with server ID
                            val updatedPreference = preference.copy(
                                id = insertId.toInt(),
                                serverId = checkResponse.data.id,
                                syncStatus = SyncStatus.SYNCED
                            )
                            userPreferenceDao.updatePreference(updatedPreference)
                            Log.d("UserPrefsRepository", "Updated local notifications preference with server ID: ${checkResponse.data.id}")
                        }
                    }
                } catch (e: Exception) {
                    Log.d("UserPrefsRepository", "Error checking if notifications preference exists: ${e.message}")
                }

                // If check fails or preference doesn't exist, try to create it
                if (preference.serverId == null) {
                    try {
                        // Convert to server model
                        val serverPreference = UserPreference(
                            userId = serverUserId,
                            preferenceKey = preference.preferenceKey,
                            preferenceValue = preference.preferenceValue,
                            updatedAt = preference.updatedAt
                        )

                        // Create on server
                        val createResponse = apiService.createUserPreference(serverPreference)

                        if (createResponse.isSuccessful && createResponse.body()?.success == true) {
                            // Get server ID from response
                            @Suppress("UNCHECKED_CAST")
                            val data = createResponse.body()?.data as? Map<String, Any>
                            val serverId = data?.get("id") as? Int

                            if (serverId != null) {
                                // Update local entry with server ID
                                val updatedPreference = preference.copy(
                                    id = insertId.toInt(),
                                    serverId = serverId,
                                    syncStatus = SyncStatus.SYNCED
                                )
                                userPreferenceDao.updatePreference(updatedPreference)
                                Log.d("UserPrefsRepository", "Created notifications preference on server and updated local entry with server ID: $serverId")
                            }
                        } else if (createResponse.code() == 400 &&
                            createResponse.body()?.message?.contains("already exists") == true) {
                            // If it already exists (race condition), try to get it and update local entry
                            try {
                                val getResponse = apiService.getUserPreference(PreferenceKeys.NOTIFICATIONS_ENABLED)

                                if (getResponse.success && getResponse.data != null) {
                                    val updatedPreference = preference.copy(
                                        id = insertId.toInt(),
                                        serverId = getResponse.data.id,
                                        syncStatus = SyncStatus.SYNCED
                                    )
                                    userPreferenceDao.updatePreference(updatedPreference)
                                    Log.d("UserPrefsRepository", "Updated local notifications preference with server ID after conflict: ${getResponse.data.id}")
                                }
                            } catch (e: Exception) {
                                Log.e("UserPrefsRepository", "Error fetching server ID after conflict: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("UserPrefsRepository", "Error creating notifications preference on server: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UserPrefsRepository", "Error setting notifications preference", e)
            throw e
        }
    }

    suspend fun areNotificationsEnabled(userId: Int): Boolean = withContext(dispatchers.io) {
        try {
            // Try to refresh from server if online
            if (NetworkUtils.isOnline()) {
                try {
                    val serverUserId = getServerId(userId, "users", context) ?: 0
                    refreshUserPreferences(serverUserId)
                } catch (e: Exception) {
                    Log.w("UserPrefsRepository", "Failed to refresh preferences from server", e)
                    // Continue with local data if refresh fails
                }
            }

            // Return local data
            userPreferenceDao.getPreferenceValue(userId, PreferenceKeys.NOTIFICATIONS_ENABLED)
                ?.equals(PreferenceKeys.BooleanValue.TRUE, ignoreCase = true)
                ?: true // Default to true
        } catch (e: Exception) {
            Log.e("UserPrefsRepository", "Error getting notifications preference", e)
            true // Fall back to default on error
        }
    }

    fun areNotificationsEnabledFlow(userId: Int): Flow<Boolean> {
        return userPreferenceDao.getPreferenceFlow(userId, PreferenceKeys.NOTIFICATIONS_ENABLED)
            .map { it?.preferenceValue?.equals(PreferenceKeys.BooleanValue.TRUE, ignoreCase = true) ?: true }
    }

    // Generic methods to get/set any preference
    suspend fun setPreference(userId: Int, key: String, value: String) = withContext(dispatchers.io) {
        val timestamp = DateUtils.getCurrentTimestamp()
        val preference = UserPreferenceEntity(
            userId = userId,
            preferenceKey = key,
            preferenceValue = value,
            updatedAt = timestamp,
            syncStatus = if (NetworkUtils.isOnline()) SyncStatus.PENDING_SYNC else SyncStatus.LOCAL_ONLY
        )

        try {
            userPreferenceDao.upsertPreference(preference)

            // If online, try to sync immediately
            if (NetworkUtils.isOnline()) {
                val serverUserId = getServerId(userId, "users", context) ?: 0
                val serverPreference = preference.copy(userId = serverUserId).toModel()
                syncPreference(serverPreference)
            }
        } catch (e: Exception) {
            Log.e("UserPrefsRepository", "Error setting preference: $key", e)
            throw e
        }
    }

    suspend fun getPreferenceValue(userId: Int, key: String, defaultValue: String? = null): String? =
        withContext(dispatchers.io) {
            try {
                // Try to refresh from server if online
                if (NetworkUtils.isOnline()) {
                    try {
                        val serverUserId = getServerId(userId, "users", context) ?: 0
                        refreshUserPreferences(serverUserId)
                    } catch (e: Exception) {
                        Log.w("UserPrefsRepository", "Failed to refresh preferences from server", e)
                        // Continue with local data if refresh fails
                    }
                }

                // Return local data
                userPreferenceDao.getPreferenceValue(userId, key) ?: defaultValue
            } catch (e: Exception) {
                Log.e("UserPrefsRepository", "Error getting preference: $key", e)
                defaultValue
            }
        }

    fun getPreferenceValueFlow(userId: Int, key: String, defaultValue: String? = null): Flow<String?> {
        return userPreferenceDao.getPreferenceFlow(userId, key)
            .map { it?.preferenceValue ?: defaultValue }
    }

    suspend fun deletePreference(userId: Int, key: String) = withContext(dispatchers.io) {
        try {
            userPreferenceDao.deletePreference(userId, key)

            // If online, sync the deletion to the server
            if (NetworkUtils.isOnline()) {
                try {
                    val serverUserId = getServerId(userId, "users", context) ?: 0
                    apiService.deleteUserPreference(key)
                } catch (e: Exception) {
                    Log.e("UserPrefsRepository", "Error deleting preference on server: $key", e)
                    // Mark for deletion later if server sync fails
                    markForDeletion(userId, key)
                }
            } else {
                // Mark for deletion later
                markForDeletion(userId, key)
            }
        } catch (e: Exception) {
            Log.e("UserPrefsRepository", "Error deleting preference: $key", e)
            throw e
        }
    }

    private suspend fun markForDeletion(userId: Int, key: String) {
        val timestamp = DateUtils.getCurrentTimestamp()
        val preference = userPreferenceDao.getPreference(userId, key) ?: return
        val preferenceMarkedForDeletion = preference.copy(
            updatedAt = timestamp,
            syncStatus = SyncStatus.LOCALLY_DELETED
        )

        try {
            userPreferenceDao.upsertPreference(preferenceMarkedForDeletion)

            // If online, try to sync immediately
            if (NetworkUtils.isOnline()) {
                val serverUserId = getServerId(userId, "users", context) ?: 0
                val serverPreference = preferenceMarkedForDeletion.copy(userId = serverUserId).toModel()
                syncPreference(serverPreference)
            }
        } catch (e: Exception) {
            Log.e("UserPrefsRepository", "Error marking preference as locally deleted", e)
            throw e
        }
    }

    suspend fun deleteAllPreferences(userId: Int) = withContext(dispatchers.io) {
        try {
            userPreferenceDao.deleteAllPreferencesForUser(userId)

            // If online, sync the deletion to the server
            if (NetworkUtils.isOnline()) {
                try {
                    apiService.deleteAllUserPreferences()
                } catch (e: Exception) {
                    Log.e("UserPrefsRepository", "Error deleting all preferences on server", e)
                    // Mark for batch deletion later
                    markAllForDeletion(userId)
                }
            } else {
                // Mark for batch deletion later
                markAllForDeletion(userId)
            }
        } catch (e: Exception) {
            Log.e("UserPrefsRepository", "Error deleting all preferences", e)
            throw e
        }
    }

    private suspend fun markAllForDeletion(userId: Int) {
        // Implementation for marking all user preferences for deletion
    }

    // Sync-related methods
    private suspend fun syncPreference(preference: UserPreference) {
        try {
            val jsonObject = mapOf("value" to preference.preferenceValue)
            // Send the preference to the server
            val response = apiService.updateUserPreference(
                key = preference.preferenceKey,
                value = jsonObject
            )

            // Update the local record to reflect successful sync
            if (response.isSuccessful && response.body()?.success == true) {
                val updatedPreference = preference.toEntity().copy(syncStatus = SyncStatus.SYNCED)
                userPreferenceDao.updatePreference(updatedPreference)
            }
        } catch (e: Exception) {
            Log.e("UserPrefsRepository", "Error syncing preference to server", e)
            // Keep as PENDING_SYNC to try again later
        }
    }

    suspend fun refreshUserPreferences(userId: Int) = withContext(dispatchers.io) {
        try {
            // Get all preferences from the server
            val response = apiService.getUserPreferences()

            // Update local database with server data if successful
            if (response.success && response.data != null) {
                response.data.forEach { prefDto ->
                    val localPref = UserPreferenceEntity(
                        userId = userId,
                        preferenceKey = prefDto.preferenceKey,
                        preferenceValue = prefDto.preferenceValue,
                        updatedAt = prefDto.updatedAt ?: DateUtils.getCurrentTimestamp(),
                        syncStatus = SyncStatus.SYNCED
                    )
                    userPreferenceDao.upsertPreference(localPref)
                }
            }
        } catch (e: Exception) {
            Log.e("UserPrefsRepository", "Error refreshing preferences from server", e)
            throw e
        }
    }

    override suspend fun sync(): Unit = withContext(dispatchers.io) {
        try {
            Log.d(TAG, "Starting user preferences sync")
            userPreferencesSyncManager.performSync()
            Log.d(TAG, "User preferences sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during user sync", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "UserPreferencesRepository"
    }
}