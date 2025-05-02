package com.helgolabs.trego.data.local.dataClasses

import com.helgolabs.trego.data.model.Transaction

data class TransactionsApiResponse(
    val transactions: List<Transaction>,
    val accountsNeedingReauthentication: List<AccountReauthState>
)