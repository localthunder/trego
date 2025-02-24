package com.helgolabs.trego.data.extensions

import com.helgolabs.trego.data.local.entities.BankAccountEntity
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.model.BankAccount

fun BankAccount.toEntity(syncStatus: SyncStatus): BankAccountEntity {
    return BankAccountEntity(
        accountId = this.accountId,
        serverId = this.accountId,
        requisitionId = this.requisitionId,
        userId = this.userId,
        iban = this.iban,
        institutionId = this.institutionId,
        currency = this.currency,
        ownerName = this.ownerName,
        name = this.name,
        product = this.product,
        cashAccountType = this.cashAccountType,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        syncStatus = syncStatus,
        needsReauthentication = this.needsReauthentication,
        deletedAt = null
    )
}

fun BankAccountEntity.toModel(): BankAccount {
    return BankAccount(
        accountId = this.accountId,
        requisitionId = this.requisitionId,
        userId = this.userId,
        iban = this.iban,
        institutionId = this.institutionId,
        currency = this.currency,
        ownerName = this.ownerName,
        name = this.name,
        product = this.product,
        cashAccountType = this.cashAccountType,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        needsReauthentication = this.needsReauthentication
    )
}