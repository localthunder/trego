package com.splitter.splitter.data.local.repositories

import com.splitter.splitter.data.extensions.toEntity
import com.splitter.splitter.data.extensions.toModel
import com.splitter.splitter.data.local.dao.InstitutionDao
import com.splitter.splitter.model.Institution

class InstitutionRepository(private val institutionDao: InstitutionDao) {

    suspend fun insert(institution: Institution) {
        institutionDao.insert(institution.toEntity())
    }

    suspend fun getAllInstitutions(): List<Institution> {
        return institutionDao.getAllInstitutions().map { it.toModel() }
    }

    suspend fun getInstitutionById(id: String): Institution? {
        return institutionDao.getInstitutionById(id)?.toModel()
    }

    suspend fun deleteInstitutionById(id: String) {
        institutionDao.deleteInstitutionById(id)
    }
}
