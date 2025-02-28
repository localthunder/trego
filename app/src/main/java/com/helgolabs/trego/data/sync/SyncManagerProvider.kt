package com.helgolabs.trego.data.sync

import android.content.Context
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.cache.TransactionCacheManager
import com.helgolabs.trego.data.calculators.DefaultSplitCalculator
import com.helgolabs.trego.data.calculators.SplitCalculator
import com.helgolabs.trego.data.local.AppDatabase
import com.helgolabs.trego.data.managers.CurrencyConversionManager
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.repositories.BankAccountRepository
import com.helgolabs.trego.data.repositories.GroupRepository
import com.helgolabs.trego.data.repositories.InstitutionRepository
import com.helgolabs.trego.data.repositories.NotificationRepository
import com.helgolabs.trego.data.repositories.PaymentRepository
import com.helgolabs.trego.data.repositories.PaymentSplitRepository
import com.helgolabs.trego.data.repositories.RequisitionRepository
import com.helgolabs.trego.data.repositories.TransactionRepository
import com.helgolabs.trego.data.repositories.UserRepository
import com.helgolabs.trego.data.sync.managers.BankAccountSyncManager
import com.helgolabs.trego.data.sync.managers.CurrencyConversionSyncManager
import com.helgolabs.trego.data.sync.managers.GroupDefaultSplitSyncManager
import com.helgolabs.trego.data.sync.managers.GroupMemberSyncManager
import com.helgolabs.trego.data.sync.managers.PaymentSyncManager
import com.helgolabs.trego.data.sync.managers.RequisitionSyncManager
import com.helgolabs.trego.data.sync.managers.TransactionSyncManager
import com.helgolabs.trego.data.sync.managers.UserGroupArchiveSyncManager
import com.helgolabs.trego.data.sync.managers.UserSyncManager
import com.helgolabs.trego.utils.AppCoroutineDispatchers
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.EntityServerConverter

class SyncManagerProvider(
    private val context: Context,
    private val apiService: ApiService,
    private val database: AppDatabase,
    private val dispatchers: CoroutineDispatchers = AppCoroutineDispatchers()
) {
    private val syncMetadataDao = database.syncMetadataDao()
    val transactionCacheManager = TransactionCacheManager(context, database.cachedTransactionDao())


    // Sync Managers
    val bankAccountSyncManager by lazy {
        BankAccountSyncManager(
            bankAccountDao = database.bankAccountDao(),
            userDao = database.userDao(),
            apiService = apiService,
            syncMetadataDao = syncMetadataDao,
            dispatchers = dispatchers,
            context = context
        )
    }

    val groupMemberSyncManager by lazy {
        GroupMemberSyncManager(
            groupMemberDao = database.groupMemberDao(),
            groupDao = database.groupDao(),
            userDao = database.userDao(),
            apiService = apiService,
            syncMetadataDao = syncMetadataDao,
            dispatchers = dispatchers,
            context = context
        )
    }

    val groupSyncManager by lazy {
        GroupSyncManager(
            groupDao = database.groupDao(),
            userDao = database.userDao(),
            apiService = apiService,
            syncMetadataDao = syncMetadataDao,
            dispatchers = dispatchers,
            context = context,
            groupMemberSyncManager = groupMemberSyncManager
        )
    }

    val paymentSyncManager by lazy {
        PaymentSyncManager(
            paymentDao = database.paymentDao(),
            paymentSplitDao = database.paymentSplitDao(),
            userDao = database.userDao(),
            apiService = apiService,
            syncMetadataDao = syncMetadataDao,
            dispatchers = dispatchers,
            context = context
        )
    }

    val requisitionSyncManager by lazy {
        RequisitionSyncManager(
            requisitionDao = database.requisitionDao(),
            userDao = database.userDao(),
            apiService = apiService,
            syncMetadataDao = syncMetadataDao,
            dispatchers = dispatchers,
            context = context
        )
    }

    val transactionSyncManager by lazy {
        TransactionSyncManager(
            transactionDao = database.transactionDao(),
            bankAccountDao = database.bankAccountDao(),
            userDao = database.userDao(),
            transactionCacheManager = transactionCacheManager,
            apiService = apiService,
            syncMetadataDao = syncMetadataDao,
            dispatchers = dispatchers,
            context = context,
        )
    }

    val userSyncManager by lazy {
        UserSyncManager(
            userDao = database.userDao(),
            apiService = apiService,
            syncMetadataDao = syncMetadataDao,
            dispatchers = dispatchers,
            context = context
        )
    }

    val userGroupArchiveSyncManager by lazy {
        UserGroupArchiveSyncManager(
            userGroupArchiveDao = database.userGroupArchivesDao(),
            userDao = database.userDao(),
            groupDao = database.groupDao(),
            apiService = apiService,
            syncMetadataDao = syncMetadataDao,
            dispatchers = dispatchers,
            context = context
        )
    }

    val currencyConversionSyncManager by lazy {
        CurrencyConversionSyncManager(
            currencyConversionDao = database.currencyConversionDao(),
            userDao = database.userDao(),
            apiService = apiService,
            syncMetadataDao = syncMetadataDao,
            dispatchers = dispatchers,
            context = context
        )
    }

    val groupDefaultSplitSyncManager by lazy {
        GroupDefaultSplitSyncManager(
            groupDefaultSplitDao = database.groupDefaultSplitDao(),
            userDao = database.userDao(),
            apiService = apiService,
            syncMetadataDao = syncMetadataDao,
            dispatchers = dispatchers,
            context = context
        )
    }

    val currencyConversionManager by lazy {
        CurrencyConversionManager(
            apiService = apiService,
            currencyConversionDao = database.currencyConversionDao(),
            paymentDao = database.paymentDao(),
            paymentSplitDao = database.paymentSplitDao(),
            splitCalculator = DefaultSplitCalculator(),
            entityServerConverter = EntityServerConverter(context),
            dispatchers = dispatchers
        )
    }

    // Repository Providers
    fun provideBankAccountRepository() = BankAccountRepository(
        bankAccountDao = database.bankAccountDao(),
        requisitionDao = database.requisitionDao(),
        apiService = apiService,
        dispatchers = dispatchers,
        syncMetadataDao = syncMetadataDao,
        context = context,
        bankAccountSyncManager = bankAccountSyncManager,
        requisitionSyncManager = requisitionSyncManager
    )

    fun provideGroupRepository() = GroupRepository(
        groupDao = database.groupDao(),
        groupMemberDao = database.groupMemberDao(),
        userDao = database.userDao(),
        paymentDao = database.paymentDao(),
        paymentSplitDao = database.paymentSplitDao(),
        syncMetadataDao = syncMetadataDao,
        userGroupArchiveDao = database.userGroupArchivesDao(),
        groupDefaultSplitDao = database.groupDefaultSplitDao(),
        apiService = apiService,
        context = context,
        dispatchers = dispatchers,
        groupSyncManager = groupSyncManager,
        groupMemberSyncManager = groupMemberSyncManager,
        userGroupArchiveSyncManager = userGroupArchiveSyncManager,
        groupDefaultSplitSyncManager = groupDefaultSplitSyncManager
    )

    fun provideInstitutionRepository() = InstitutionRepository(
        institutionDao = database.institutionDao(),
        apiService = apiService,
        dispatchers = dispatchers,
        context = context
    )
    fun providePaymentRepository() = PaymentRepository(
        paymentDao = database.paymentDao(),
        paymentSplitDao = database.paymentSplitDao(),
        groupDao = database.groupDao(),
        groupMemberDao = database.groupMemberDao(),
        transactionDao = database.transactionDao(),
        currencyConversionDao = database.currencyConversionDao(),
        apiService = apiService,
        dispatchers = dispatchers,
        context = context,
        syncMetadataDao = syncMetadataDao,
        paymentSyncManager = paymentSyncManager,
        currencyConversionSyncManager = currencyConversionSyncManager,
        splitCalculator = DefaultSplitCalculator()
    )

    fun providePaymentSplitRepository() = PaymentSplitRepository(
        paymentSplitDao = database.paymentSplitDao(),
        paymentDao = database.paymentDao(),
        groupDao = database.groupDao(),
        apiService = apiService,
        dispatchers = dispatchers,
        context = context
    )

    fun provideRequisitionRepository() = RequisitionRepository(
        requisitionDao = database.requisitionDao(),
        apiService = apiService,
        dispatchers = dispatchers,
        context = context,
        syncMetadataDao = syncMetadataDao,
        requisitionSyncManager = requisitionSyncManager
    )

    fun provideTransactionRepository() = TransactionRepository(
        transactionDao = database.transactionDao(),
        bankAccountDao = database.bankAccountDao(),
        apiService = apiService,
        dispatchers = dispatchers,
        context = context,
        syncMetadataDao = syncMetadataDao,
        transactionSyncManager = transactionSyncManager,
        cachedTransactionDao = database.cachedTransactionDao(),
        cacheManager = (context.applicationContext as MyApplication).transactionCacheManager
    )

    fun provideUserRepository() = UserRepository(
        userDao = database.userDao(),
        apiService = apiService,
        dispatchers = dispatchers,
        syncMetadataDao = syncMetadataDao,
        groupMemberDao = database.groupMemberDao(),
        groupDao = database.groupDao(),
        paymentDao = database.paymentDao(),
        paymentSplitDao = database.paymentSplitDao(),
        userSyncManager = userSyncManager,
        context = context
    )

    fun provideNotificationRepository() = NotificationRepository(
        deviceTokenDao = database.deviceTokenDao(),
        apiService = apiService,
        dispatchers = dispatchers,
        context = context
    )
}