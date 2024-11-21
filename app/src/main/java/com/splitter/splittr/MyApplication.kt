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
import com.splitter.splittr.data.sync.SyncManagerProvider
import com.splitter.splittr.data.sync.SyncWorker
import com.splitter.splittr.data.sync.SyncWorkerFactory
import com.splitter.splittr.data.sync.managers.BankAccountSyncManager
import com.splitter.splittr.data.sync.managers.GroupMemberSyncManager
import com.splitter.splittr.data.sync.managers.RequisitionSyncManager
import com.splitter.splittr.data.sync.managers.TransactionSyncManager
import com.splitter.splittr.data.sync.managers.UserSyncManager
import com.splitter.splittr.ui.viewmodels.AppViewModelFactory
import com.splitter.splittr.ui.viewmodels.AuthViewModel
import com.splitter.splittr.ui.viewmodels.GroupViewModel
import com.splitter.splittr.ui.viewmodels.InstitutionViewModel
import com.splitter.splittr.ui.viewmodels.PaymentsViewModel
import com.splitter.splittr.ui.viewmodels.TransactionViewModel
import com.splitter.splittr.ui.viewmodels.UserViewModel
import com.splitter.splittr.utils.AppCoroutineDispatchers
import com.splitter.splittr.utils.InstitutionLogoManager
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.NetworkUtils.isOnline
import com.splitter.splittr.utils.SyncUtils

class MyApplication : Application(), Configuration.Provider {
    lateinit var viewModelFactory: AppViewModelFactory

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val apiService: ApiService by lazy { RetrofitClient.getInstance(this).create(ApiService::class.java) }

    private val syncManagerProvider by lazy {
        SyncManagerProvider(
            context = this,
            apiService = apiService,
            database = database,
            dispatchers = dispatchers
        )
    }

    val bankAccountRepository: BankAccountRepository by lazy { syncManagerProvider.provideBankAccountRepository() }
    val groupRepository: GroupRepository by lazy { syncManagerProvider.provideGroupRepository() }
    val institutionRepository: InstitutionRepository by lazy { syncManagerProvider.provideInstitutionRepository() }
    val paymentRepository: PaymentRepository by lazy { syncManagerProvider.providePaymentRepository() }
    val paymentSplitRepository: PaymentSplitRepository by lazy { syncManagerProvider.providePaymentSplitRepository() }
    val requisitionRepository: RequisitionRepository by lazy { syncManagerProvider.provideRequisitionRepository() }
    val transactionRepository: TransactionRepository by lazy { syncManagerProvider.provideTransactionRepository() }
    val userRepository: UserRepository by lazy { syncManagerProvider.provideUserRepository() }

    val institutionLogoManager: InstitutionLogoManager by lazy {
        InstitutionLogoManager(applicationContext)
    }

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
                PaymentsViewModel::class.java to { PaymentsViewModel(paymentRepository, paymentSplitRepository, groupRepository, transactionRepository, institutionRepository, userRepository,this) },
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
