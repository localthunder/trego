package com.splitter.splitter.data.extensions

import com.splitter.splitter.data.local.entities.BankAccountEntity
import com.splitter.splitter.model.BankAccount

fun BankAccount.toEntity(): BankAccountEntity {
    return BankAccountEntity(
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
        updatedAt = this.updatedAt
    )
}
