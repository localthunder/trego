package com.helgolabs.trego.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.helgolabs.trego.data.local.entities.InstitutionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstitutionDao {

    @Transaction
    open suspend fun <R> runInTransaction(block: suspend () -> R): R {
        // Room automatically handles transactions for suspend functions
        return block()
    }

    @Query("SELECT * FROM institutions ORDER BY name ASC")
    fun getInstitutionsStream(): Flow<List<InstitutionEntity>>

    // Keep existing methods
    @Query("SELECT * FROM institutions ORDER BY name ASC")
    suspend fun getAllInstitutions(): List<InstitutionEntity>

    @Query("SELECT * FROM institutions WHERE id = :id")
    suspend fun getInstitutionById(id: String): InstitutionEntity?

    @Insert
    suspend fun insert(institutionEntity: InstitutionEntity)

    @Update
    suspend fun updateInstitution(institutionEntity: InstitutionEntity)



}
