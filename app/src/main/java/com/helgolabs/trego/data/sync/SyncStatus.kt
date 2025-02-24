package com.helgolabs.trego.data.sync

enum class SyncStatus {
    SYNCED,
    PENDING_SYNC,
    SYNC_FAILED,
    LOCALLY_DELETED
}