package com.splitter.splittr.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Migration logic here
            database.execSQL("ALTER TABLE groups ADD COLUMN is_sync_pending INTEGER DEFAULT 0 NOT NULL")
            database.execSQL("ALTER TABLE group_members ADD COLUMN is_sync_pending INTEGER DEFAULT 0 NOT NULL")
        }
    }
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE accounts ADD COLUMN needsReauthentication INTEGER NOT NULL DEFAULT 0")
        }
    }
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE groups ADD COLUMN local_image_path TEXT"
            )
        }
    }
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_metadata (
                entityType TEXT PRIMARY KEY NOT NULL,
                lastSyncTimestamp INTEGER NOT NULL,
                lastEtag TEXT,
                syncStatus TEXT NOT NULL,
                updateCount INTEGER NOT NULL DEFAULT 0,
                lastSyncResult TEXT
            )
        """)
        }
    }
}
