package com.helgolabs.trego.data.local.dataClasses

data class NotificationResponse(
    val success: Boolean,
    val notificationsSent: Int,
    val message: String? = null,
    val error: String? = null
)