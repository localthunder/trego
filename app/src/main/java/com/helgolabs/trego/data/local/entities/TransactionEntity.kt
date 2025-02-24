package com.helgolabs.trego.data.local.entities

import androidx.room.*
import com.google.gson.annotations.SerializedName
import com.helgolabs.trego.data.local.dataClasses.TransactionStatus
import com.helgolabs.trego.data.sync.SyncStatus

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BankAccountEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InstitutionEntity::class,
            parentColumns = ["id"],
            childColumns = ["institution_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("user_id"),
        Index("account_id"),
        Index("institution_id"),
        Index(value = ["server_id"], unique = true)
    ]
)
data class TransactionEntity(
    @PrimaryKey @ColumnInfo(name = "transaction_id") val transactionId: String,
    @SerializedName("server_id") @ColumnInfo(name = "server_id") val serverId: String? = null,
    @ColumnInfo(name = "user_id") val userId: Int?,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "account_id") val accountId: String?,
    @ColumnInfo(name = "currency") val currency: String?,
    @ColumnInfo(name = "booking_date") val bookingDate: String?,
    @ColumnInfo(name = "value_date") val valueDate: String?,
    @ColumnInfo(name = "booking_date_time") val bookingDateTime: String?,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "creditor_name") val creditorName: String?,
    @ColumnInfo(name = "creditor_account_bban") val creditorAccountBban: String?,
    @ColumnInfo(name = "debtor_name") val debtorName: String?,
    @ColumnInfo(name = "remittance_information_unstructured") val remittanceInformationUnstructured: String?,
    @ColumnInfo(name = "proprietary_bank_transaction_code") val proprietaryBankTransactionCode: String?,
    @ColumnInfo(name = "internal_transaction_id") val internalTransactionId: String?,
    @ColumnInfo(name = "institution_name") val institutionName: String?,
    @ColumnInfo(name = "institution_id") val institutionId: String?,
    @ColumnInfo(name = "sync_status") var syncStatus: SyncStatus = SyncStatus.PENDING_SYNC,
    @ColumnInfo(name = "transaction_status") val transactionStatus: TransactionStatus? = TransactionStatus.BOOKED

)