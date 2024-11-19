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
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add the new image_last_modified column to groups table
            database.execSQL("""
            ALTER TABLE groups 
            ADD COLUMN image_last_modified TEXT
        """)
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create temporary table with new schema
            database.execSQL("""
            CREATE TABLE IF NOT EXISTS users_temp (
                user_id INTEGER PRIMARY KEY NOT NULL,
                server_id INTEGER,
                username TEXT NOT NULL,
                email TEXT NOT NULL DEFAULT '',
                password_hash TEXT,
                google_id TEXT,
                apple_id TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                default_currency TEXT NOT NULL DEFAULT 'GBP',
                last_login_date TEXT,
                sync_status TEXT NOT NULL DEFAULT 'PENDING_SYNC'
            )
        """)

            // Copy data from old table to new table, converting lastLoginDate from Long to String
            database.execSQL("""
            INSERT INTO users_temp 
            SELECT 
                user_id,
                server_id,
                username,
                email,
                password_hash,
                google_id,
                apple_id,
                created_at,
                updated_at,
                default_currency,
                CASE 
                    WHEN last_login_date IS NULL THEN NULL
                    ELSE CAST(last_login_date AS TEXT)
                END,
                sync_status
            FROM users
        """)

            // Drop old table
            database.execSQL("DROP TABLE users")

            // Rename temporary table to original name
            database.execSQL("ALTER TABLE users_temp RENAME TO users")

            // Recreate indices
            database.execSQL("CREATE INDEX IF NOT EXISTS index_users_server_id ON users(server_id)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_email ON users(email)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_username ON users(username)")
        }
    }
}
