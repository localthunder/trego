package com.helgolabs.trego.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.helgolabs.trego.data.local.DatabaseMigrations.MIGRATION_11_12
import com.helgolabs.trego.data.local.DatabaseMigrations.MIGRATION_12_13
import com.helgolabs.trego.data.local.DatabaseMigrations.MIGRATION_13_14
import com.helgolabs.trego.data.local.DatabaseMigrations.MIGRATION_14_15
import com.helgolabs.trego.data.local.DatabaseMigrations.MIGRATION_15_16
import com.helgolabs.trego.data.local.DatabaseMigrations.MIGRATION_16_17
import com.helgolabs.trego.data.local.DatabaseMigrations.MIGRATION_17_18
import com.helgolabs.trego.data.local.DatabaseMigrations.MIGRATION_18_19
import com.helgolabs.trego.data.local.DatabaseMigrations.MIGRATION_19_20
import com.helgolabs.trego.data.local.DatabaseMigrations.MIGRATION_20_21
import com.helgolabs.trego.data.local.DatabaseMigrations.MIGRATION_21_22
import com.helgolabs.trego.data.local.DatabaseMigrations.MIGRATION_22_23
import com.helgolabs.trego.data.local.converters.Converters
import com.helgolabs.trego.data.local.dao.*
import com.helgolabs.trego.data.local.entities.*
import com.helgolabs.trego.data.sync.SyncMetadata

@Database(
    entities = [
        BankAccountEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        InstitutionEntity::class,
        PaymentEntity::class,
        PaymentSplitEntity::class,
        RequisitionEntity::class,
        TransactionEntity::class,
        UserEntity::class,
        SyncMetadata:: class,
        CachedTransactionEntity:: class,
        CurrencyConversionEntity:: class,
        UserGroupArchiveEntity::class,
        DeviceTokenEntity::class,
        GroupDefaultSplitEntity::class],

    version = 63,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bankAccountDao(): BankAccountDao
    abstract fun groupDao(): GroupDao
    abstract fun groupMemberDao(): GroupMemberDao
    abstract fun institutionDao(): InstitutionDao
    abstract fun paymentDao(): PaymentDao
    abstract fun paymentSplitDao(): PaymentSplitDao
    abstract fun requisitionDao(): RequisitionDao
    abstract fun transactionDao(): TransactionDao
    abstract fun userDao(): UserDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun cachedTransactionDao(): CachedTransactionDao
    abstract fun currencyConversionDao(): CurrencyConversionDao
    abstract fun userGroupArchivesDao(): UserGroupArchiveDao
    abstract fun deviceTokenDao(): DeviceTokenDao
    abstract fun groupDefaultSplitDao(): GroupDefaultSplitDao



    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
//                    .addMigrations(
//                        MIGRATION_11_12,
//                        MIGRATION_12_13,
//                        MIGRATION_13_14,
//                        MIGRATION_14_15,
//                        MIGRATION_15_16,
//                        MIGRATION_16_17,
//                        MIGRATION_17_18,
//                        MIGRATION_18_19,
//                        MIGRATION_19_20,
//                        MIGRATION_20_21,
//                        MIGRATION_21_22,
//                        MIGRATION_22_23
//                    )
                    .fallbackToDestructiveMigration() // REMOVE BEFORE PRODUCTION
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
