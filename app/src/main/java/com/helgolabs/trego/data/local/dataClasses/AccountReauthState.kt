package com.helgolabs.trego.data.local.dataClasses

data class AccountReauthState(
    val accountId: String,
    val institutionId: String?
)