package com.helgolabs.trego.data.local.dataClasses

data class PaymentSyncResponse(
    val data: List<PaymentWithSplits>,
    val timestamp: Long
)