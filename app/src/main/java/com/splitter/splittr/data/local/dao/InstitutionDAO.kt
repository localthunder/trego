package com.splitter.splittr.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.splitter.splittr.data.local.entities.InstitutionEntity
import com.splitter.splittr.data.model.Institution

@Dao
interface InstitutionDao {

    @Insert
    suspend fun insert(institutionEntity: InstitutionEntity)

    @Update
    suspend fun updateInstitution(institutionEntity: InstitutionEntity)

    @Query("SELECT * FROM institutions")
    suspend fun getAllInstitutions(): List<InstitutionEntity>

    @Query("SELECT * FROM institutions WHERE id = :id")
    suspend fun getInstitutionById(id: String): InstitutionEntity?

}
