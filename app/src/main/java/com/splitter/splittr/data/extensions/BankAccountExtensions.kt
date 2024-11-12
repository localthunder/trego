package com.splitter.splittr.data.extensions

import com.splitter.splittr.data.local.entities.BankAccountEntity
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.model.BankAccount

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
        needsReauthentication = this.needsReauthentication
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