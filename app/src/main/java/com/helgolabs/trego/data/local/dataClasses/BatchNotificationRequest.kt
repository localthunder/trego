package com.helgolabs.trego.data.local.dataClasses

data class BatchNotificationRequest(
    val groupId: Int,
    val userId: Int,
    val targetCurrency: String,
    val successfulConversions: Int,
    val totalAttempted: Int,
    val userName: String,
    val groupName: String
)