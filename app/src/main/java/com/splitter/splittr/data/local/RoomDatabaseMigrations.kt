package com.splitter.splittr.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

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
    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE institutions ADD COLUMN localLogoPath TEXT")
        }
    }
    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create new table
            database.execSQL("""
            CREATE TABLE payments_temp (
                id INTEGER PRIMARY KEY NOT NULL,
                group_id INTEGER NOT NULL,
                paid_by_user_id INTEGER NOT NULL,
                transaction_id TEXT,
                institution_id TEXT,
                amount REAL NOT NULL,
                description TEXT,
                notes TEXT,
                payment_date TEXT NOT NULL,
                currency TEXT,
                split_mode TEXT NOT NULL,
                payment_type TEXT NOT NULL,
                created_by INTEGER NOT NULL,
                updated_by INTEGER NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                deleted_at TEXT
            )
        """)

            // Copy existing data except institution_name
            database.execSQL("""
            INSERT INTO payments_temp (
                id, group_id, paid_by_user_id, transaction_id,
                amount, description, notes, payment_date, currency,
                split_mode, payment_type, created_by, updated_by,
                created_at, updated_at, deleted_at
            )
            SELECT 
                id, group_id, paid_by_user_id, transaction_id,
                amount, description, notes, payment_date, currency,
                split_mode, payment_type, created_by, updated_by,
                created_at, updated_at, deleted_at
            FROM payments
        """)

            // Drop old table and rename new one
            database.execSQL("DROP TABLE payments")
            database.execSQL("ALTER TABLE payments_temp RENAME TO payments")
        }
    }
    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Drop existing indices if they exist
            database.execSQL("DROP INDEX IF EXISTS index_payments_group_id")
            database.execSQL("DROP INDEX IF EXISTS index_payments_paid_by_user_id")
            database.execSQL("DROP INDEX IF EXISTS index_payments_transaction_id")
            database.execSQL("DROP INDEX IF EXISTS index_payments_server_id")

            // Create new table with all required columns and constraints
            database.execSQL("""
            CREATE TABLE payments_temp (
                id INTEGER PRIMARY KEY NOT NULL,
                server_id INTEGER,
                group_id INTEGER NOT NULL,
                paid_by_user_id INTEGER NOT NULL,
                transaction_id TEXT,
                institution_id TEXT,
                amount REAL NOT NULL,
                description TEXT,
                notes TEXT,
                payment_date TEXT NOT NULL,
                currency TEXT,
                split_mode TEXT NOT NULL,
                payment_type TEXT NOT NULL,
                created_by INTEGER NOT NULL,
                updated_by INTEGER NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                deleted_at TEXT,
                sync_status TEXT NOT NULL DEFAULT 'PENDING_SYNC',
                FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE ON UPDATE NO ACTION,
                FOREIGN KEY (paid_by_user_id) REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE NO ACTION,
                FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE NO ACTION,
                FOREIGN KEY (updated_by) REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE NO ACTION
            )
        """)

            // Copy data with default sync_status
            database.execSQL("""
            INSERT INTO payments_temp SELECT
                id, NULL as server_id, group_id, paid_by_user_id, transaction_id,
                institution_id, amount, description, notes, payment_date,
                currency, split_mode, payment_type, created_by, updated_by,
                created_at, updated_at, deleted_at, 'PENDING_SYNC' as sync_status
            FROM payments
        """)

            // Drop old table and rename new one
            database.execSQL("DROP TABLE payments")
            database.execSQL("ALTER TABLE payments_temp RENAME TO payments")

            // Recreate indices
            database.execSQL("CREATE INDEX index_payments_group_id ON payments(group_id)")
            database.execSQL("CREATE INDEX index_payments_paid_by_user_id ON payments(paid_by_user_id)")
            database.execSQL("CREATE INDEX index_payments_transaction_id ON payments(transaction_id)")
            database.execSQL("CREATE INDEX index_payments_server_id ON payments(server_id)")
        }
    }
}
