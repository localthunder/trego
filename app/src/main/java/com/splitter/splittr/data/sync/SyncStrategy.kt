package com.splitter.splittr.data.sync

// SyncStrategy.kt
sealed class SyncStrategy {
    object FullSync : SyncStrategy()
    data class IncrementalSync(val since: Long) : SyncStrategy()
    data class EtagSync(val etag: String) : SyncStrategy()
}