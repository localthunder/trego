package com.splitter.splitter.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "accounts")
data class BankAccountEntity(
    @PrimaryKey
    @SerializedName("account_id")
    @ColumnInfo(name = "account_id")
    val accountId: String,

    @SerializedName("requisition_id")
    @ColumnInfo(name = "requisition_id")
    val requisitionId: String,

    @SerializedName("user_id")
    @ColumnInfo(name = "user_id")
    val userId: Int,

    @SerializedName("iban")
    @ColumnInfo(name = "iban")
    val iban: String?,

    @SerializedName("institution_id")
    @ColumnInfo(name = "institution_id")
    val institutionId: String,

    @SerializedName("currency")
    @ColumnInfo(name = "currency")
    val currency: String?,

    @SerializedName("owner_name")
    @ColumnInfo(name = "owner_name")
    val ownerName: String?,

    @SerializedName("name")
    @ColumnInfo(name = "name")
    val name: String?,

    @SerializedName("product")
    @ColumnInfo(name = "product")
    val product: String?,

    @SerializedName("cash_account_type")
    @ColumnInfo(name = "cash_account_type")
    val cashAccountType: String?,

    @SerializedName("created_at")
    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)

