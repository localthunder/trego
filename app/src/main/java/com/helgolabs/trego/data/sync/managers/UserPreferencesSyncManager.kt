package com.helgolabs.trego.data.sync.managers

import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.dao.UserPreferenceDao
import com.helgolabs.trego.data.local.entities.UserPreferenceEntity
import com.helgolabs.trego.data.model.UserPreference
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.OptimizedSyncManager
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.ServerIdUtil.getServerId
import com.helgolabs.trego.utils.getUserIdFromPreferences

class UserPreferencesSyncManager(
    private val userPreferenceDao: UserPreferenceDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<UserPreferenceEntity, UserPreference>(syncMetadataDao, dispatchers) {

    override val entityType = "user_preferences"
    override val batchSize = 50

    val myApplication = context.applicationContext as MyApplication

    override suspend fun getLocalChanges(): List<UserPreferenceEntity> =
        userPreferenceDao.getPendingSyncPreferences()

    override suspend fun syncToServer(entity: UserPreferenceEntity): Result<UserPreferenceEntity> {
        return try {
            Log.d(TAG, "Syncing user preference to server: ${entity.preferenceKey} for user ${entity.userId}")

            // Get the local entity to check server ID
            val localEntity = userPreferenceDao.getPreference(entity.userId, entity.preferenceKey)
                ?: return Result.failure(Exception("Local entity not found"))

            Log.d(TAG, """
            Syncing preference:
            User ID: ${entity.userId}
            Key: ${entity.preferenceKey}
            Value: ${entity.preferenceValue}
            Server ID: ${localEntity.serverId}
            Sync Status: ${localEntity.syncStatus}
            Is new preference: ${localEntity.serverId == null}
        """.trimIndent())

            // Get the server user ID
            val serverUserId = getServerId(entity.userId, "users", context)
                ?: return Result.failure(Exception("No server ID found for user ${entity.userId}"))

            // Create a JSON object with a "value" property for PUT requests
            val jsonObject = mapOf("value" to entity.preferenceValue)

            // First check if the preference already exists on the server
            // This helps handle cases where local db thinks it's new but it already exists on server
            try {
                val checkResponse = apiService.getUserPreference(entity.preferenceKey)

                if (checkResponse.success && checkResponse.data != null) {
                    // Preference exists on server, just update it regardless of local sync status
                    Log.d(TAG, "Preference already exists on server (ID: ${checkResponse.data.id}), updating via PUT")

                    val updateResponse = apiService.updateUserPreference(
                        entity.preferenceKey,
                        jsonObject
                    )

                    if (!updateResponse.isSuccessful || updateResponse.body()?.success != true) {
                        Log.e(TAG, "Server responded with error on update: ${updateResponse.message()}")
                        userPreferenceDao.updateSyncStatus(entity.id, SyncStatus.SYNC_FAILED)
                        return Result.failure(Exception("Server sync failed: ${updateResponse.message()}"))
                    }

                    // Update local entity with server info and synced status
                    val updatedEntity = localEntity.copy(
                        serverId = checkResponse.data.id,
                        syncStatus = SyncStatus.SYNCED
                    )
                    userPreferenceDao.updatePreference(updatedEntity)
                    Log.d(TAG, "Successfully updated preference on server and updated local entity with server ID: ${checkResponse.data.id}")
                    return Result.success(updatedEntity)
                }
            } catch (e: Exception) {
                // If we can't check or it doesn't exist, we'll proceed with normal flow
                Log.d(TAG, "Error checking if preference exists on server: ${e.message}")
            }

            // Proceed with normal create/update flow based on server ID
            val response = if (localEntity.serverId == null) {
                // Convert local entity to server DTO
                val serverPreferenceDto = myApplication.entityServerConverter.convertUserPreferenceToServer(localEntity).getOrThrow()

                // Try to create new preference
                Log.d(TAG, "Attempting to create new preference on server for user ID: $serverUserId")
                apiService.createUserPreference(serverPreferenceDto)
            } else {
                // Update existing preference
                Log.d(TAG, "Updating existing preference on server with ID: ${localEntity.serverId}")
                apiService.updateUserPreference(
                    entity.preferenceKey,
                    jsonObject
                )
            }

            // Handle the response
            if (response.isSuccessful && response.body()?.success == true) {
                // Success case
                val updatedEntity = if (localEntity.serverId == null && response.body()?.data is Map<*, *>) {
                    // Extract server ID for new preferences
                    @Suppress("UNCHECKED_CAST")
                    val data = response.body()?.data as? Map<String, Any>
                    val serverId = data?.get("id") as? Int

                    if (serverId != null) {
                        localEntity.copy(serverId = serverId, syncStatus = SyncStatus.SYNCED)
                    } else {
                        localEntity.copy(syncStatus = SyncStatus.SYNCED)
                    }
                } else {
                    localEntity.copy(syncStatus = SyncStatus.SYNCED)
                }

                // Update local database with synced status
                userPreferenceDao.updatePreference(updatedEntity)
                Log.d(TAG, "Successfully synced preference to server")
                Result.success(updatedEntity)
            } else if (response.code() == 400 && response.body()?.message?.contains("already exists") == true) {
                // Handle "already exists" error by fetching and then updating
                Log.d(TAG, "Server says preference already exists, fetching it and then updating")

                try {
                    // Get the existing preference to get its ID
                    val getResponse = apiService.getUserPreference(entity.preferenceKey)

                    if (getResponse.success && getResponse.data != null) {
                        // Now update it
                        val updateResponse = apiService.updateUserPreference(
                            entity.preferenceKey,
                            jsonObject
                        )

                        if (updateResponse.isSuccessful && updateResponse.body()?.success == true) {
                            // Update succeeded, update local entity with server ID
                            val updatedEntity = localEntity.copy(
                                serverId = getResponse.data.id,
                                syncStatus = SyncStatus.SYNCED
                            )
                            userPreferenceDao.updatePreference(updatedEntity)
                            Log.d(TAG, "Successfully updated existing preference and updated local entity with server ID: ${getResponse.data.id}")
                            return Result.success(updatedEntity)
                        } else {
                            // Update failed
                            Log.e(TAG, "Server responded with error when updating existing preference: ${updateResponse.message()}")
                            userPreferenceDao.updateSyncStatus(entity.id, SyncStatus.SYNC_FAILED)
                            return Result.failure(Exception("Server sync failed: ${updateResponse.message()}"))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling 'already exists' case: ${e.message}")
                    userPreferenceDao.updateSyncStatus(entity.id, SyncStatus.SYNC_FAILED)
                    return Result.failure(e)
                }

                // If we get here, something went wrong in the error handling
                Log.e(TAG, "Failed to resolve 'already exists' error gracefully")
                userPreferenceDao.updateSyncStatus(entity.id, SyncStatus.SYNC_FAILED)
                return Result.failure(Exception("Failed to resolve preference conflict"))
            } else {
                // Other error
                Log.e(TAG, "Server responded with error: ${response.message()}, code: ${response.code()}")
                userPreferenceDao.updateSyncStatus(entity.id, SyncStatus.SYNC_FAILED)
                return Result.failure(Exception("Server sync failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing preference to server", e)
            try {
                userPreferenceDao.updateSyncStatus(entity.id, SyncStatus.SYNC_FAILED)
            } catch (dbException: Exception) {
                Log.e(TAG, "Failed to update sync status in DB", dbException)
            }
            Result.failure(e)
        }
    }

    override suspend fun getServerChanges(since: Long): List<UserPreference> {
        val userId = getUserIdFromPreferences(context)
            ?: throw IllegalStateException("User ID not found")

        // Get the server ID from the local user ID
        val serverUserId = getServerId(userId, "users", context)
            ?: throw IllegalStateException("No server ID found for user $userId")

        Log.d(TAG, "Fetching preferences changes since $since")

        val response = apiService.getUserPreferencesSince(since)
        return if (response.success) {
            response.data
        } else {
            emptyList()
        }
    }

    override suspend fun applyServerChange(serverEntity: UserPreference) {
        userPreferenceDao.runInTransaction {
            val currentUserId = getUserIdFromPreferences(context) ?: 0

            // Convert server DTO to local entity
            val localEntity = myApplication.entityServerConverter.convertUserPreferenceFromServer(
                serverEntity,
                userPreferenceDao.getPreference(currentUserId, serverEntity.preferenceKey)
            ).getOrNull() ?: throw Exception("Failed to convert server preference")

            if (serverEntity.id != null) {
                // Check if we already have this preference locally
                val existingPreference = userPreferenceDao.getPreference(localEntity.userId, localEntity.preferenceKey)

                if (existingPreference == null) {
                    Log.d(TAG, "Inserting new preference from server: ${serverEntity.preferenceKey}")
                    userPreferenceDao.insertPreference(localEntity.copy(syncStatus = SyncStatus.SYNCED))
                } else if (DateUtils.isUpdateNeeded(
                        serverEntity.updatedAt ?: "",
                        existingPreference.updatedAt,
                        "Preference-${serverEntity.userId}-${serverEntity.preferenceKey}"
                    )) {
                    Log.d(TAG, "Updating existing preference from server: ${serverEntity.preferenceKey}")
                    userPreferenceDao.updatePreference(localEntity.copy(syncStatus = SyncStatus.SYNCED))
                } else {
                    Log.d(TAG, "Local preference ${serverEntity.preferenceKey} is up to date")
                }
            }
        }
    }

    companion object {
        private const val TAG = "UserPrefSyncManager"
    }
}