package com.helgolabs.trego.data.local.dao

import androidx.room.*
import com.helgolabs.trego.data.local.entities.CurrencyConversionEntity
import com.helgolabs.trego.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyConversionDao {

    @Transaction
    suspend fun runInTransaction(block: suspend () -> Unit) {
        block()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversion(conversion: CurrencyConversionEntity): Long

    @Update
    suspend fun updateConversion(conversion: CurrencyConversionEntity)

    @Query("SELECT * FROM currency_conversions WHERE id = :id")
    fun getConversionById(id: Int): Flow<CurrencyConversionEntity?>

    @Query("SELECT * FROM currency_conversions WHERE id = :id")
    fun getConversionByIdSync(id: Int): CurrencyConversionEntity?


    @Query("SELECT * FROM currency_conversions WHERE server_id = :serverId")
    suspend fun getConversionByServerId(serverId: Int): CurrencyConversionEntity?

    @Query("SELECT * FROM currency_conversions WHERE payment_id = :paymentId ORDER BY created_at DESC")
    fun getConversionsByPayment(paymentId: Int): Flow<List<CurrencyConversionEntity>>

    @Query("SELECT * FROM currency_conversions WHERE sync_status NOT IN ('SYNCED', 'LOCALLY_DELETED')")
    fun getUnsyncedConversions(): Flow<List<CurrencyConversionEntity>>

    @Query("""
        SELECT * FROM currency_conversions 
        WHERE payment_id = :paymentId 
        AND deleted_at IS NULL 
        ORDER BY created_at DESC 
        LIMIT 1
    """)
    suspend fun getLatestConversionForPayment(paymentId: Int): CurrencyConversionEntity?

    @Query("""
        SELECT * FROM currency_conversions 
        WHERE original_currency = :fromCurrency 
        AND final_currency = :toCurrency 
        AND deleted_at IS NULL 
        ORDER BY created_at DESC 
        LIMIT 1
    """)
    suspend fun getLatestConversionRate(fromCurrency: String, toCurrency: String): CurrencyConversionEntity?

    @Query("UPDATE currency_conversions SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, status: SyncStatus)


    @Query("""
        UPDATE currency_conversions 
        SET deleted_at = :timestamp, 
            sync_status = :syncStatus 
        WHERE payment_id = :paymentId
    """)
    suspend fun markPaymentConversionsAsDeleted(
        paymentId: Int,
        timestamp: String,
        syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
    )

    @Query("""
        SELECT DISTINCT cc.* 
        FROM currency_conversions cc
        JOIN payments p ON cc.payment_id = p.id
        WHERE p.group_id = :groupId 
        AND cc.deleted_at IS NULL
        ORDER BY cc.created_at DESC
    """)
    fun getConversionsByGroup(groupId: Int): Flow<List<CurrencyConversionEntity>>

    @Query("""
        SELECT * FROM currency_conversions 
        WHERE sync_status IN (:statuses) 
        AND deleted_at IS NULL
        ORDER BY created_at ASC
    """)
    suspend fun getConversionsByStatus(
        statuses: List<SyncStatus> = listOf(
            SyncStatus.PENDING_SYNC,
            SyncStatus.SYNC_FAILED
        )
    ): List<CurrencyConversionEntity>

    @Query("""
        SELECT * FROM currency_conversions 
        WHERE updated_at > :timestamp 
        AND deleted_at IS NULL
        ORDER BY updated_at ASC
    """)
    fun getConversionsUpdatedSince(timestamp: String): Flow<List<CurrencyConversionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConversion(conversion: CurrencyConversionEntity): Long

    @Query("SELECT COUNT(*) FROM currency_conversions WHERE payment_id = :paymentId AND deleted_at IS NULL")
    suspend fun getConversionCountForPayment(paymentId: Int): Int
}