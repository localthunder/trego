package com.splitter.splitter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.splitter.splitter.data.local.entities.InstitutionEntity

@Dao
interface InstitutionDao {

    @Insert
    suspend fun insert(institution: InstitutionEntity)

    @Query("SELECT * FROM institutions")
    suspend fun getAllInstitutions(): List<InstitutionEntity>

    @Query("SELECT * FROM institutions WHERE id = :id")
    suspend fun getInstitutionById(id: String): InstitutionEntity?

    @Query("DELETE FROM institutions WHERE id = :id")
    suspend fun deleteInstitutionById(id: String)
}
