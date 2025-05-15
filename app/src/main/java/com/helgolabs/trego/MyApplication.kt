package com.helgolabs.trego

import BankAccountViewModel
import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.helgolabs.trego.data.cache.TransactionCacheManager
import com.helgolabs.trego.data.calculators.DefaultSplitCalculator
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
import com.helgolabs.trego.data.repositories.UserPreferencesRepository
import com.helgolabs.trego.data.sync.SyncManagerProvider
import com.helgolabs.trego.data.sync.SyncWorker
import com.helgolabs.trego.data.sync.SyncWorkerFactory
import com.helgolabs.trego.ui.viewmodels.AppViewModelFactory
import com.helgolabs.trego.ui.viewmodels.AuthViewModel
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
import com.helgolabs.trego.ui.viewmodels.InstitutionViewModel
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel
import com.helgolabs.trego.ui.viewmodels.TransactionViewModel
import com.helgolabs.trego.ui.viewmodels.UserPreferencesViewModel
import com.helgolabs.trego.ui.viewmodels.UserViewModel
import com.helgolabs.trego.utils.AppCoroutineDispatchers
import com.helgolabs.trego.utils.AuthManager
import com.helgolabs.trego.utils.AuthUtils
import com.helgolabs.trego.utils.ColorSchemeCache
import com.helgolabs.trego.utils.EntityServerConverter
import com.helgolabs.trego.utils.NetworkUtils
import com.helgolabs.trego.utils.NetworkUtils.hasNetworkCapabilities
import com.helgolabs.trego.utils.PlayStoreReviewManager
import com.helgolabs.trego.utils.SyncUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
import com.helgolabs.trego.workers.CacheCleanupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyApplication : Application(), Configuration.Provider {
    lateinit var viewModelFactory: AppViewModelFactory
    lateinit var reviewManager: PlayStoreReviewManager
        private set

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val apiService: ApiService by lazy { RetrofitClient.getInstance(this).create(ApiService::class.java) }
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val entityServerConverter by lazy { EntityServerConverter(this) }
    val persistentBackgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
    val userPreferencesRepository: UserPreferencesRepository by lazy { syncManagerProvider.provideUserPreferencesRepository() }
    val splitCalculator by lazy { DefaultSplitCalculator() }

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
            userPreferencesRepository
        )

        NetworkUtils.initialize(this, apiService)

        reviewManager = PlayStoreReviewManager(applicationContext)
        FirebaseApp.initializeApp(this)

        applicationScope.launch {
            ColorSchemeCache.initialize(this@MyApplication)
        }

        // Clear invalid states on startup
        validateAuthenticationState()

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

            // Only request sync if userId is valid - check AFTER validation
            val userId = getUserIdFromPreferences(this)
            if (userId != null && userId != -1 && isUserAuthenticated()) {
                if (hasNetworkCapabilities()) {
                    SyncWorker.requestSync(this)
                }
            } else {
                android.util.Log.d("MyApplication", "Sync not requested - user not authenticated or invalid ID")
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
                BankAccountViewModel::class.java to { BankAccountViewModel(bankAccountRepository, transactionRepository, dispatchers, this)},
                InstitutionViewModel::class.java to { InstitutionViewModel(institutionRepository, dispatchers)},
                PaymentsViewModel::class.java to { PaymentsViewModel(paymentRepository, paymentSplitRepository, groupRepository, transactionRepository, institutionRepository, userRepository, splitCalculator,this) },
                TransactionViewModel::class.java to { TransactionViewModel(transactionRepository, paymentRepository, dispatchers, this)},
                UserViewModel::class.java to { UserViewModel(userRepository, dispatchers, this)},
                UserPreferencesViewModel::class.java to { UserPreferencesViewModel(userPreferencesRepository, dispatchers, this)}
            )
        )
    }

    // Helper method to check if the app is running in test mode
    private fun isTestMode(): Boolean {
        return "true" == System.getProperty("robolectric.enabled") || "true" == System.getProperty("androidx.test.platform.app.InstrumentationRegistry")
    }

    private fun validateAuthenticationState() {
        try {
            val userId = getUserIdFromPreferences(this)
            val token = AuthUtils.getLoginState(this)

            // If we have a user ID but no valid token, clear everything
            if (userId != null && userId != -1 && token.isNullOrBlank()) {
                Log.w(TAG, "Invalid state detected: User ID exists but no valid token")
                AuthManager.logout(this)
            }

            // If we have a token but no user ID, clear everything
            if (!token.isNullOrBlank() && (userId == null || userId == -1)) {
                Log.w(TAG, "Invalid state detected: Token exists but no valid user ID")
                AuthManager.logout(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating authentication state", e)
            // On any error, clear state to be safe
            AuthManager.logout(this)
        }
    }

    private fun isUserAuthenticated(): Boolean {
        val token = AuthUtils.getLoginState(this)
        return !token.isNullOrBlank()
    }

    fun registerCurrentFcmToken() {
        applicationScope.launch {
            try {
                // Get the current FCM token
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                        return@addOnCompleteListener
                    }

                    // Get new FCM registration token
                    val token = task.result
                    Log.d(TAG, "Current FCM token: $token")

                    // Register it if we have a user
                    val userId = getUserIdFromPreferences(this@MyApplication)
                    if (userId != null && token != null) {
                        applicationScope.launch {
                            try {
                                notificationRepository.registerDeviceToken(token, userId)
                                Log.d(TAG, "FCM token registered successfully")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to register FCM token", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting FCM token", e)
            }
        }
    }

    companion object {
        private const val TAG = "MyApplication"
    }
}
