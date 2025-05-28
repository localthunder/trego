package com.helgolabs.trego.data.sync.managers

import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dao.CurrencyConversionDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.dao.UserDao
import com.helgolabs.trego.data.local.dataClasses.CurrencyConversionData
import com.helgolabs.trego.data.local.entities.CurrencyConversionEntity
import com.helgolabs.trego.data.model.CurrencyConversion
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.OptimizedSyncManager
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first

class CurrencyConversionSyncManager(
    private val currencyConversionDao: CurrencyConversionDao,
    private val userDao: UserDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<CurrencyConversionEntity, CurrencyConversionData>(syncMetadataDao, dispatchers) {

    override val entityType = "currency_conversions"
    override val batchSize = 50

    val myApplication = context.applicationContext as MyApplication

    // Override parent methods to provide CurrencyConversion-specific anti-loop protection
    override fun shouldSyncEntity(entity: CurrencyConversionEntity): Boolean {
        // Skip entities that are already synced and recently updated
        return when (entity.syncStatus) {
            SyncStatus.SYNCED -> false // Skip recently synced conversions
            SyncStatus.LOCALLY_DELETED -> true // Allow deleted items to sync (for server cleanup)
            else -> true
        }
    }

    override fun getEntityTimestamp(entity: CurrencyConversionEntity): Long {
        return try {
            DateUtils.parseTimestamp(entity.updatedAt).toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    override fun getEntitySyncStatus(entity: CurrencyConversionEntity): SyncStatus {
        return entity.syncStatus
    }

    override suspend fun getLocalChanges(): List<CurrencyConversionEntity> =
        currencyConversionDao.getUnsyncedConversions().first()

    override suspend fun syncToServer(entity: CurrencyConversionEntity): Result<CurrencyConversionEntity> {
        return try {
            Log.d(TAG, "Syncing currency conversion to server")

            // Get the local entity to check server ID
            val localEntity = currencyConversionDao.getConversionById(entity.id).first()
                ?: return Result.failure(Exception("Local entity not found"))

            Log.d(TAG, """
                Syncing currency conversion:
                Local ID: ${entity.id}
                Server ID: ${localEntity.serverId}
                Is new conversion: ${localEntity.serverId == null}
            """.trimIndent())

            // Handle deleted records
            if (localEntity.deletedAt != null) {
                if (localEntity.serverId != null) {
                    try {
                        // Delete on server
                        Log.d(TAG, "Deleting currency conversion on server: ${localEntity.serverId}")
                        apiService.deleteCurrencyConversion(localEntity.serverId)

                        // Update local sync status to LOCALLY_DELETED
                        currencyConversionDao.runInTransaction {
                            currencyConversionDao.updateSyncStatus(entity.id, SyncStatus.LOCALLY_DELETED)
                        }

                        return Result.success(entity)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting currency conversion on server", e)
                        currencyConversionDao.updateSyncStatus(entity.id, SyncStatus.SYNC_FAILED)
                        return Result.failure(e)
                    }
                } else {
                    // If record was deleted locally before ever being synced to server,
                    // just mark it as LOCALLY_DELETED
                    currencyConversionDao.runInTransaction {
                        currencyConversionDao.updateSyncStatus(entity.id, SyncStatus.LOCALLY_DELETED)
                    }
                    return Result.success(entity)
                }
            }

            val serverModel = myApplication.entityServerConverter
                .convertCurrencyConversionToServer(localEntity)
                .getOrThrow()

            // Get server response and wrap it in CurrencyConversionData
            val serverConversion = if (localEntity.serverId == null) {
                Log.d(TAG, "Creating new currency conversion on server")
                val response = apiService.createCurrencyConversion(serverModel)
                CurrencyConversionData(
                    currencyConversion = response,
                    payment = null,  // These could be populated if needed
                    group = null
                )
            } else {
                Log.d(TAG, "Updating existing currency conversion on server: ${localEntity.serverId}")
                val response = apiService.updateCurrencyConversion(localEntity.serverId, serverModel)
                CurrencyConversionData(
                    currencyConversion = response,
                    payment = null,
                    group = null
                )
            }

            // Convert server response back to local entity
            myApplication.entityServerConverter
                .convertCurrencyConversionFromServer(serverConversion, localEntity)
                .onSuccess { updatedLocalEntity ->
                    currencyConversionDao.runInTransaction {
                        currencyConversionDao.updateConversion(updatedLocalEntity)
                        currencyConversionDao.updateSyncStatus(entity.id, SyncStatus.SYNCED)
                    }
                }

            Result.success(entity)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing currency conversion to server", e)
            currencyConversionDao.updateSyncStatus(entity.id, SyncStatus.SYNC_FAILED)
            Result.failure(e)
        }
    }

    override suspend fun getServerChanges(since: Long): List<CurrencyConversionData> {
        Log.d(TAG, "Fetching currency conversions since $since")
        return apiService.getCurrencyConversionsSince(since).data
    }

    override suspend fun applyServerChange(serverData: CurrencyConversionData) {
        currencyConversionDao.runInTransaction {
            // Convert server conversion to local entity
            val existingEntity = currencyConversionDao.getConversionByServerId(serverData.currencyConversion.id)
            val localConversion = myApplication.entityServerConverter
                .convertCurrencyConversionFromServer(serverData, existingEntity)
                .getOrNull() ?: throw Exception("Failed to convert server currency conversion")

            when {
                localConversion.id == 0 -> {
                    Log.d(TAG, "Inserting new currency conversion from server: ${serverData.currencyConversion.id}")
                    currencyConversionDao.insertConversion(localConversion.copy(syncStatus = SyncStatus.SYNCED))
                }
                DateUtils.isUpdateNeeded(
                    serverData.currencyConversion.updatedAt,
                    localConversion.updatedAt,
                    "CurrencyConversion-${serverData.currencyConversion.id}"
                ) -> {
                    Log.d(TAG, "Updating existing currency conversion from server: ${serverData.currencyConversion.id}")
                    currencyConversionDao.updateConversion(localConversion.copy(syncStatus = SyncStatus.SYNCED))
                }
                else -> {
                    Log.d(TAG, "Local currency conversion ${serverData.currencyConversion.id} is up to date")
                }
            }
        }
    }

    companion object {
        private const val TAG = "CurrencyConversionSyncManager"
    }
}