package com.splitter.splittr.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.splitter.splittr.data.local.DatabaseMigrations.MIGRATION_11_12
import com.splitter.splittr.data.local.DatabaseMigrations.MIGRATION_12_13
import com.splitter.splittr.data.local.DatabaseMigrations.MIGRATION_13_14
import com.splitter.splittr.data.local.converters.Converters
import com.splitter.splittr.data.local.dao.*
import com.splitter.splittr.data.local.entities.*
import com.splitter.splittr.data.sync.SyncMetadata

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
        SyncMetadata:: class],

    version = 14,
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
                    .addMigrations(
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
