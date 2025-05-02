package com.helgolabs.trego.data.local.dataClasses

import java.time.Duration

data class RefreshMessage(
    val type: RefreshMessageType,
    val message: String,
    val duration: Duration? = null
)

enum class RefreshMessageType {
    SUCCESS, WARNING, ERROR, INFO
}