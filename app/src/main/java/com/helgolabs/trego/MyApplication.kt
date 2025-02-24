package com.helgolabs.trego

import BankAccountViewModel
import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.firebase.FirebaseApp
import com.helgolabs.trego.data.cache.TransactionCacheManager
import com.helgolabs.trego.data.local.AppDatabase
import com.helgolabs.trego.data.repositories.BankAccountRepository
import com.helgolabs.trego.data.repositories.GroupRepository
import com.helgolabs.trego.data.repositories.InstitutionRepository
import com.helgolabs.trego.data.repositories.PaymentRepository
import com.helgolabs.trego.data.repositories.RequisitionRepository
import com.helgolabs.trego.data.repositories.TransactionRepository
import com.helgolabs.trego.data.repositories.UserRepository
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.network.RetrofitClient
import com.helgolabs.trego.data.repositories.NotificationRepository
import com.helgolabs.trego.data.repositories.PaymentSplitRepository
import com.helgolabs.trego.data.sync.GroupSyncManager
import com.helgolabs.trego.data.sync.SyncManagerProvider
import com.helgolabs.trego.data.sync.SyncWorker
import com.helgolabs.trego.data.sync.SyncWorkerFactory
import com.helgolabs.trego.data.sync.managers.BankAccountSyncManager
import com.helgolabs.trego.data.sync.managers.GroupMemberSyncManager
import com.helgolabs.trego.data.sync.managers.RequisitionSyncManager
import com.helgolabs.trego.data.sync.managers.TransactionSyncManager
import com.helgolabs.trego.data.sync.managers.UserSyncManager
import com.helgolabs.trego.ui.viewmodels.AppViewModelFactory
import com.helgolabs.trego.ui.viewmodels.AuthViewModel
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
import com.helgolabs.trego.ui.viewmodels.InstitutionViewModel
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel
import com.helgolabs.trego.ui.viewmodels.TransactionViewModel
import com.helgolabs.trego.ui.viewmodels.UserViewModel
import com.helgolabs.trego.utils.AppCoroutineDispatchers
import com.helgolabs.trego.utils.EntityServerConverter
import com.helgolabs.trego.utils.InstitutionLogoManager
import com.helgolabs.trego.utils.NetworkUtils
import com.helgolabs.trego.utils.NetworkUtils.hasNetworkCapabilities
import com.helgolabs.trego.utils.NetworkUtils.isOnline
import com.helgolabs.trego.utils.PlayStoreReviewManager
import com.helgolabs.trego.utils.SyncUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
import com.helgolabs.trego.workers.CacheCleanupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MyApplication : Application(), Configuration.Provider {
    lateinit var viewModelFactory: AppViewModelFactory
    lateinit var reviewManager: PlayStoreReviewManager
        private set

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val apiService: ApiService by lazy { RetrofitClient.getInstance(this).create(ApiService::class.java) }
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val entityServerConverter by lazy { EntityServerConverter(this) }

    val transactionCacheManager: TransactionCacheManager by lazy {
        TransactionCacheManager(
            context = this,
            cachedTransactionDao = database.cachedTransactionDao()
        )
    }

    val syncManagerProvider by lazy {
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
    val notificationRepository: NotificationRepository by lazy { syncManagerProvider.provideNotificationRepository() }

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
            transactionRepository,
        )

        NetworkUtils.initialize(this, apiService)

        reviewManager = PlayStoreReviewManager(applicationContext)
        FirebaseApp.initializeApp(this)

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

            CacheCleanupWorker.schedule(this)

            // Only request sync if userId is valid
            val userId = getUserIdFromPreferences(this)
            if (userId != null && userId != -1) {
                if (hasNetworkCapabilities()) {
                    SyncWorker.requestSync(this)
                }
            } else {
                android.util.Log.d("MyApplication", "Sync not requested as user id is null or -1")
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
                BankAccountViewModel::class.java to { BankAccountViewModel(bankAccountRepository, transactionRepository, dispatchers)},
                InstitutionViewModel::class.java to { InstitutionViewModel(institutionRepository, dispatchers)},
                PaymentsViewModel::class.java to { PaymentsViewModel(paymentRepository, paymentSplitRepository, groupRepository, transactionRepository, institutionRepository, userRepository,this) },
                TransactionViewModel::class.java to { TransactionViewModel(transactionRepository, dispatchers, this)},
                UserViewModel::class.java to { UserViewModel(userRepository, dispatchers, this)}
            )
        )
    }

    // Helper method to check if the app is running in test mode
    private fun isTestMode(): Boolean {
        return "true" == System.getProperty("robolectric.enabled") || "true" == System.getProperty("androidx.test.platform.app.InstrumentationRegistry")
    }
}
