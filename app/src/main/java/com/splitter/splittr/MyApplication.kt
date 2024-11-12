package com.splitter.splittr

import BankAccountViewModel
import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.splitter.splittr.data.local.AppDatabase
import com.splitter.splittr.data.repositories.BankAccountRepository
import com.splitter.splittr.data.repositories.GroupRepository
import com.splitter.splittr.data.repositories.InstitutionRepository
import com.splitter.splittr.data.repositories.PaymentRepository
import com.splitter.splittr.data.repositories.RequisitionRepository
import com.splitter.splittr.data.repositories.TransactionRepository
import com.splitter.splittr.data.repositories.UserRepository
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.network.RetrofitClient
import com.splitter.splittr.data.repositories.PaymentSplitRepository
import com.splitter.splittr.data.sync.GroupSyncManager
import com.splitter.splittr.data.sync.SyncWorker
import com.splitter.splittr.data.sync.SyncWorkerFactory
import com.splitter.splittr.data.sync.managers.BankAccountSyncManager
import com.splitter.splittr.data.sync.managers.GroupMemberSyncManager
import com.splitter.splittr.data.sync.managers.RequisitionSyncManager
import com.splitter.splittr.data.sync.managers.UserSyncManager
import com.splitter.splittr.ui.viewmodels.AppViewModelFactory
import com.splitter.splittr.ui.viewmodels.AuthViewModel
import com.splitter.splittr.ui.viewmodels.GroupViewModel
import com.splitter.splittr.ui.viewmodels.InstitutionViewModel
import com.splitter.splittr.ui.viewmodels.PaymentsViewModel
import com.splitter.splittr.ui.viewmodels.TransactionViewModel
import com.splitter.splittr.ui.viewmodels.UserViewModel
import com.splitter.splittr.utils.AppCoroutineDispatchers
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.NetworkUtils.isOnline
import com.splitter.splittr.utils.SyncUtils

class MyApplication : Application(), Configuration.Provider {
    lateinit var viewModelFactory: AppViewModelFactory

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }
    val apiService: ApiService by lazy {
        RetrofitClient.getInstance(this).create(ApiService::class.java)
    }

    //Initialize the sync managers
    private val bankAccountSyncManager by lazy { BankAccountSyncManager(database.bankAccountDao(),apiService, database.syncMetadataDao(), dispatchers, this)}
    private val groupMemberSyncManager by lazy { GroupMemberSyncManager(database.groupMemberDao(), apiService, database.syncMetadataDao(), dispatchers, this) }
    private val groupSyncManager by lazy { GroupSyncManager(database.groupDao(), apiService, database.syncMetadataDao(), dispatchers, this, groupMemberSyncManager) }
    private val requisitionSyncManager by lazy { RequisitionSyncManager(database.requisitionDao(),apiService, database.syncMetadataDao(), dispatchers, this)}
    private val userSyncManager by lazy { UserSyncManager(database.userDao(),apiService, database.syncMetadataDao(), dispatchers, this)}


    // Initialize repositories
    val bankAccountRepository: BankAccountRepository by lazy { BankAccountRepository(database.bankAccountDao(), database.requisitionDao(), apiService, dispatchers, database.syncMetadataDao(),this, bankAccountSyncManager, requisitionSyncManager) }
    val groupRepository: GroupRepository by lazy { GroupRepository(database.groupDao(), database.groupMemberDao(), database.userDao(), database.paymentDao(), database.paymentSplitDao(), database.syncMetadataDao(), apiService, this, dispatchers, groupSyncManager) }
    val institutionRepository: InstitutionRepository by lazy { InstitutionRepository(database.institutionDao(), apiService, dispatchers, this) }
    val paymentRepository: PaymentRepository by lazy { PaymentRepository(database.paymentDao(), database.paymentSplitDao(), database.groupDao(), apiService, dispatchers, this) }
    val paymentSplitRepository: PaymentSplitRepository by lazy { PaymentSplitRepository(database.paymentSplitDao(), database.paymentDao(), database.groupDao(), apiService, dispatchers, this) }
    val requisitionRepository: RequisitionRepository by lazy { RequisitionRepository(database.requisitionDao(), apiService, dispatchers, this, database.syncMetadataDao(), requisitionSyncManager) }
    val transactionRepository: TransactionRepository by lazy { TransactionRepository(database.transactionDao(), database.bankAccountDao(), apiService, dispatchers, this) }
    val userRepository: UserRepository by lazy { UserRepository(database.userDao(), apiService, dispatchers, database.syncMetadataDao(), userSyncManager) }

    val dispatchers = AppCoroutineDispatchers()

    // Implement the required workManagerConfiguration property
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()

        val syncWorkerFactory = SyncWorkerFactory(
            userRepository,
            paymentRepository,
            groupRepository,
            bankAccountRepository,
            paymentSplitRepository,
            institutionRepository,
            requisitionRepository,
            transactionRepository
        )

        NetworkUtils.initialize(this)


        if (isTestMode()) {
            WorkManagerTestInitHelper.initializeTestWorkManager(this)
        } else {
            // Create custom configuration
            val config = Configuration.Builder()
                .setWorkerFactory(syncWorkerFactory)
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build()

            // Initialize WorkManager manually
            WorkManager.initialize(this, config)

            if (isOnline()){
                SyncWorker.requestSync(this)
            }
        }

        setupViewModelFactory()
        SyncUtils.initialize(this)
    }


    private fun setupViewModelFactory() {
        viewModelFactory = AppViewModelFactory(
            mapOf(
                GroupViewModel::class.java to { GroupViewModel(groupRepository, userRepository, paymentRepository, this) },
                AuthViewModel::class.java to { AuthViewModel(userRepository, dispatchers)},
                BankAccountViewModel::class.java to { BankAccountViewModel(bankAccountRepository, dispatchers)},
                InstitutionViewModel::class.java to { InstitutionViewModel(institutionRepository, dispatchers)},
                PaymentsViewModel::class.java to { PaymentsViewModel(paymentRepository, paymentSplitRepository, groupRepository, this) },
                TransactionViewModel::class.java to { TransactionViewModel(transactionRepository, dispatchers)},
                UserViewModel::class.java to { UserViewModel(userRepository, dispatchers)}
            )
        )
    }

    // Helper method to check if the app is running in test mode
    private fun isTestMode(): Boolean {
        return "true" == System.getProperty("robolectric.enabled") || "true" == System.getProperty("androidx.test.platform.app.InstrumentationRegistry")
    }
}
