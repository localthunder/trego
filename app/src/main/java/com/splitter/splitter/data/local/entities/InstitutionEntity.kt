package com.splitter.splitter.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "institutions")
data class InstitutionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val bic: String?,
    val transactionTotalDays: String?,
    val countries: List<String>?, // Room does not support List types out of the box
    val logo: String?,
    val createdAt: String,
    val updatedAt: String
)
