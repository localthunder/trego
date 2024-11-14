package com.splitter.splittr.data.sync

import android.content.Context
import com.splitter.splittr.data.local.AppDatabase
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.repositories.BankAccountRepository
import com.splitter.splittr.data.repositories.GroupRepository
import com.splitter.splittr.data.repositories.InstitutionRepository
import com.splitter.splittr.data.repositories.PaymentRepository
import com.splitter.splittr.data.repositories.PaymentSplitRepository
import com.splitter.splittr.data.repositories.RequisitionRepository
import com.splitter.splittr.data.repositories.TransactionRepository
import com.splitter.splittr.data.repositories.UserRepository
import com.splitter.splittr.data.sync.managers.BankAccountSyncManager
import com.splitter.splittr.data.sync.managers.GroupMemberSyncManager
import com.splitter.splittr.data.sync.managers.PaymentSyncManager
import com.splitter.splittr.data.sync.managers.RequisitionSyncManager
import com.splitter.splittr.data.sync.managers.TransactionSyncManager
import com.splitter.splittr.data.sync.managers.UserSyncManager
import com.splitter.splittr.utils.AppCoroutineDispatchers
import com.splitter.splittr.utils.CoroutineDispatchers
class SyncManagerProvider(
    private val context: Context,
    private val apiService: ApiService,
    private val database: AppDatabase,
    private val dispatchers: CoroutineDispatchers = AppCoroutineDispatchers()
) {
    private val syncMetadataDao = database.syncMetadataDao()

    // Sync Managers
    val bankAccountSyncManager by lazy {
        BankAccountSyncManager(
            bankAccountDao = database.bankAccountDao(),
            apiService = apiService,
            syncMetadataDao = syncMetadataDao,
            dispatchers = dispatchers,
            context = context
        )
    }

    val groupMemberSyncManager by lazy {
        GroupMemberSyncManager(
            groupMemberDao = database.groupMemberDao(),
            apiService = apiService,
            syncMetadataDao = syncMetadataDao,
            dispatchers = dispatchers,
            context = context
        )
    }

    val groupSyncManager by lazy {
        GroupSyncManager(
            groupDao = database.groupDao(),
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
            apiService = apiService,
            syncMetadataDao = syncMetadataDao,
            dispatchers = dispatchers,
            context = context
        )
    }

    val requisitionSyncManager by lazy {
        RequisitionSyncManager(
            requisitionDao = database.requisitionDao(),
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
            apiService = apiService,
            syncMetadataDao = syncMetadataDao,
            dispatchers = dispatchers,
            context = context
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
        apiService = apiService,
        context = context,
        dispatchers = dispatchers,
        groupSyncManager = groupSyncManager
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
        apiService = apiService,
        dispatchers = dispatchers,
        context = context,
        syncMetadataDao = syncMetadataDao,
        paymentSyncManager = paymentSyncManager
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
        transactionSyncManager = transactionSyncManager
    )

    fun provideUserRepository() = UserRepository(
        userDao = database.userDao(),
        apiService = apiService,
        dispatchers = dispatchers,
        syncMetadataDao = syncMetadataDao,
        userSyncManager = userSyncManager
    )
}