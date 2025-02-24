package com.helgolabs.trego.data.local.dao

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.helgolabs.trego.data.local.AppDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.helgolabs.trego.TestApplication
import com.helgolabs.trego.data.local.entities.BankAccountEntity
import com.helgolabs.trego.data.local.entities.InstitutionEntity
import com.helgolabs.trego.data.local.entities.UserEntity
import com.helgolabs.trego.data.local.entities.RequisitionEntity
import com.helgolabs.trego.data.local.entities.TransactionEntity
import com.helgolabs.trego.data.sync.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class TransactionDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var bankAccountDao: BankAccountDao
    private lateinit var userDao: UserDao
    private lateinit var institutionDao: InstitutionDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var requisitionDao: RequisitionDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        bankAccountDao = database.bankAccountDao()
        userDao = database.userDao()
        transactionDao = database.transactionDao()
        institutionDao = database.institutionDao()
        requisitionDao = database.requisitionDao()

        // Insert InstitutionEntity
        val institution = InstitutionEntity(
            id = "inst001",
            name = "Dummy Institution",
            bic = "DUMMYBIC",
            transactionTotalDays = "30",
            countries = listOf("Country1", "Country2"),
            logo = "dummy_logo.png",
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01"
        )
        runBlocking {
            institutionDao.insert(institution)
        }

        // Insert UserEntity
        val user = UserEntity(
            userId = 1,
            serverId = 2,
            username = "testuser",
            email = "testuser@example.com",
            passwordHash = "hashedpassword",
            googleId = null,
            appleId = null,
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            defaultCurrency = "USD",
            lastLoginDate = "2024-08-01"
        )
        runBlocking {
            userDao.insertUser(user)
        }

        // Insert RequisitionEntity
        val requisition = RequisitionEntity(
            requisitionId = "requisition_001",
            serverId = "server001",
            userId = 1,
            institutionId = "inst001",
            reference = "Reference",
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01"
        )
        runBlocking {
            requisitionDao.insert(requisition)
        }

        // Insert BankAccountEntity
        val bankAccount = BankAccountEntity(
            accountId = "acc001",
            serverId = "server001",
            requisitionId = "requisition_001",
            userId = 1,
            iban = "DE89370400440532013000",
            institutionId = "inst001",
            currency = "EUR",
            ownerName = "John Doe",
            name = "John Doe Savings Account",
            product = "Savings",
            cashAccountType = "Deposit",
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            syncStatus = SyncStatus.PENDING_SYNC
        )
        runBlocking {
            bankAccountDao.insertBankAccount(bankAccount)
        }
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertTransaction() = runBlocking {
        val transaction = TransactionEntity(
            transactionId = "txn001",
            serverId = "server001",
            userId = 1,
            description = "Test transaction",
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            accountId = "acc001",
            currency = "USD",
            bookingDate = "2024-08-24",
            valueDate = "2024-08-25",
            bookingDateTime = "2024-08-24T10:00:00",
            amount = 100.0,
            creditorName = "Creditor",
            creditorAccountBban = "BBAN001",
            debtorName = "Debtor",
            remittanceInformationUnstructured = "Unstructured info",
            proprietaryBankTransactionCode = "Code001",
            internalTransactionId = "IntTxn001",
            institutionId = "inst001", // Ensuring the institution exists
            institutionName = "test bank",
            syncStatus = SyncStatus.PENDING_SYNC
        )

        transactionDao.insertTransaction(transaction)

        // Verify Insert
        val retrievedTransaction = transactionDao.getTransactionById("txn001").first()
        assertEquals(transaction, retrievedTransaction)
    }

    @Test
    fun getTransactionsByUserId() = runBlocking {
        val transaction1 = TransactionEntity(
            transactionId = "txn001",
            serverId = "server001",
            userId = 1,
            description = "Test transaction 1",
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            accountId = "acc001",
            currency = "USD",
            bookingDate = "2024-08-24",
            valueDate = "2024-08-25",
            bookingDateTime = "2024-08-24T10:00:00",
            amount = 100.0,
            creditorName = "Creditor 1",
            creditorAccountBban = "BBAN001",
            debtorName = "Debtor 1",
            remittanceInformationUnstructured = "Unstructured info 1",
            proprietaryBankTransactionCode = "Code001",
            internalTransactionId = "IntTxn001",
            institutionId = "inst001",
            institutionName = "test bank",
            syncStatus = SyncStatus.PENDING_SYNC
        )

        val transaction2 = TransactionEntity(
            transactionId = "txn002",
            serverId = "server002",
            userId = 1,
            description = "Test transaction 2",
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            accountId = "acc001", // Ensure this accountId exists
            currency = "EUR",
            bookingDate = "2024-08-25",
            valueDate = "2024-08-26",
            bookingDateTime = "2024-08-25T10:00:00",
            amount = 200.0,
            creditorName = "Creditor 2",
            creditorAccountBban = "BBAN002",
            debtorName = "Debtor 2",
            remittanceInformationUnstructured = "Unstructured info 2",
            proprietaryBankTransactionCode = "Code002",
            internalTransactionId = "IntTxn002",
            institutionId = "inst001",
            institutionName = "test bank",
            syncStatus = SyncStatus.SYNCED
        )

        transactionDao.insertTransaction(transaction1)
        transactionDao.insertTransaction(transaction2)

        val transactions = transactionDao.getTransactionsByUserId(1).first()
        assertTrue(transactions.any { it.transactionId == "txn001" })
        assertTrue(transactions.any { it.transactionId == "txn002" })
    }

    @Test
    fun getRecentTransactions() = runBlocking {
        val transaction1 = TransactionEntity(
            transactionId = "txn001",
            serverId = "server001",
            userId = 1,
            description = "Test transaction 1",
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            accountId = "acc001",
            currency = "USD",
            bookingDate = "2024-08-24",
            valueDate = "2024-08-25",
            bookingDateTime = "2024-08-24T10:00:00",
            amount = 100.0,
            creditorName = "Creditor 1",
            creditorAccountBban = "BBAN001",
            debtorName = "Debtor 1",
            remittanceInformationUnstructured = "Unstructured info 1",
            proprietaryBankTransactionCode = "Code001",
            internalTransactionId = "IntTxn001",
            institutionId = "inst001",
            institutionName = "test bank",
            syncStatus = SyncStatus.PENDING_SYNC
        )

        val transaction2 = TransactionEntity(
            transactionId = "txn002",
            serverId = "server002",
            userId = 1,
            description = "Test transaction 2",
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            accountId = "acc001", // Ensure this accountId exists
            currency = "EUR",
            bookingDate = "2024-08-25",
            valueDate = "2024-08-26",
            bookingDateTime = "2024-08-25T10:00:00",
            amount = 200.0,
            creditorName = "Creditor 2",
            creditorAccountBban = "BBAN002",
            debtorName = "Debtor 2",
            remittanceInformationUnstructured = "Unstructured info 2",
            proprietaryBankTransactionCode = "Code002",
            internalTransactionId = "IntTxn002",
            institutionId = "inst001",
            institutionName = "test bank",
            syncStatus = SyncStatus.SYNCED
        )

        transactionDao.insertTransaction(transaction1)
        transactionDao.insertTransaction(transaction2)

        val recentTransactions = transactionDao.getRecentTransactions(1, "2024-08-24").first()
        assertTrue(recentTransactions.any { it.transactionId == "txn001" })
        assertTrue(recentTransactions.any { it.transactionId == "txn002" })
    }

    @Test
    fun getNonRecentTransactions() = runBlocking {
        val transaction1 = TransactionEntity(
            transactionId = "txn003",
            serverId = "server003",
            userId = 1,
            description = "Test transaction 3",
            createdAt = "2024-08-23",
            updatedAt = "2024-08-23",
            accountId = "acc001", // Ensure this accountId exists
            currency = "USD",
            bookingDate = "2024-08-23",
            valueDate = "2024-08-24",
            bookingDateTime = "2024-08-23T10:00:00",
            amount = 300.0,
            creditorName = "Creditor 3",
            creditorAccountBban = "BBAN003",
            debtorName = "Debtor 3",
            remittanceInformationUnstructured = "Unstructured info 3",
            proprietaryBankTransactionCode = "Code003",
            internalTransactionId = "IntTxn003",
            institutionId = "inst001",
            institutionName = "test bank",
            syncStatus = SyncStatus.PENDING_SYNC
        )

        val transaction2 = TransactionEntity(
            transactionId = "txn004",
            serverId = "server004",
            userId = 1,
            description = "Test transaction 4",
            createdAt = "2024-08-22",
            updatedAt = "2024-08-22",
            accountId = "acc001", // Ensure this accountId exists
            currency = "EUR",
            bookingDate = "2024-08-22",
            valueDate = "2024-08-23",
            bookingDateTime = "2024-08-22T10:00:00",
            amount = 400.0,
            creditorName = "Creditor 4",
            creditorAccountBban = "BBAN004",
            debtorName = "Debtor 4",
            remittanceInformationUnstructured = "Unstructured info 4",
            proprietaryBankTransactionCode = "Code004",
            internalTransactionId = "IntTxn004",
            institutionId = "inst001",
            institutionName = "test bank",
            syncStatus = SyncStatus.SYNCED
        )

        transactionDao.insertTransaction(transaction1)
        transactionDao.insertTransaction(transaction2)

        val nonRecentTransactions = transactionDao.getNonRecentTransactions(1, "2024-08-24").first()
        assertTrue(nonRecentTransactions.any { it.transactionId == "txn003" })
        assertTrue(nonRecentTransactions.any { it.transactionId == "txn004" })
    }

    @Test
    fun getUnsyncedTransactions() = runBlocking {
        val transaction1 = TransactionEntity(
            transactionId = "txn005",
            serverId = "server005",
            userId = 1, // Adjust userId to match existing data
            description = "Test transaction 5",
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            accountId = "acc001", // Ensure this accountId exists
            currency = "USD",
            bookingDate = "2024-08-24",
            valueDate = "2024-08-25",
            bookingDateTime = "2024-08-24T10:00:00",
            amount = 500.0,
            creditorName = "Creditor 5",
            creditorAccountBban = "BBAN005",
            debtorName = "Debtor 5",
            remittanceInformationUnstructured = "Unstructured info 5",
            proprietaryBankTransactionCode = "Code005",
            internalTransactionId = "IntTxn005",
            institutionId = "inst001",
            institutionName = "test bank",
            syncStatus = SyncStatus.PENDING_SYNC
        )

        val transaction2 = TransactionEntity(
            transactionId = "txn006",
            serverId = "server006",
            userId = 1, // Adjust userId to match existing data
            description = "Test transaction 6",
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            accountId = "acc001", // Ensure this accountId exists
            currency = "EUR",
            bookingDate = "2024-08-24",
            valueDate = "2024-08-25",
            bookingDateTime = "2024-08-24T10:00:00",
            amount = 600.0,
            creditorName = "Creditor 6",
            creditorAccountBban = "BBAN006",
            debtorName = "Debtor 6",
            remittanceInformationUnstructured = "Unstructured info 6",
            proprietaryBankTransactionCode = "Code006",
            internalTransactionId = "IntTxn006",
            institutionId = "inst001",
            institutionName = "test bank",
            syncStatus = SyncStatus.PENDING_SYNC
        )

        transactionDao.insertTransaction(transaction1)
        transactionDao.insertTransaction(transaction2)

        val unsyncedTransactions = transactionDao.getUnsyncedTransactions().first()
        assertTrue(unsyncedTransactions.any { it.transactionId == "txn005" })
        assertTrue(unsyncedTransactions.any { it.transactionId == "txn006" })
    }

    @Test
    fun updateTransactionSyncStatus() = runBlocking {
        val transaction = TransactionEntity(
            transactionId = "txn007",
            serverId = "server007",
            userId = 1, // Ensure this userId exists
            description = "Test transaction 7",
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            accountId = "acc001", // Ensure this accountId exists
            currency = "USD",
            bookingDate = "2024-08-24",
            valueDate = "2024-08-25",
            bookingDateTime = "2024-08-24T10:00:00",
            amount = 700.0,
            creditorName = "Creditor 7",
            creditorAccountBban = "BBAN007",
            debtorName = "Debtor 7",
            remittanceInformationUnstructured = "Unstructured info 7",
            proprietaryBankTransactionCode = "Code007",
            internalTransactionId = "IntTxn007",
            institutionId = "inst001",
            institutionName = "test bank",
            syncStatus = SyncStatus.PENDING_SYNC
        )

        transactionDao.insertTransaction(transaction)

        // Verify Initial Sync Status
        val retrievedTransaction = transactionDao.getTransactionById("txn007").first()
        assertEquals(SyncStatus.PENDING_SYNC, retrievedTransaction?.syncStatus)

        // Update Sync Status
        transactionDao.updateTransactionSyncStatus("txn007", SyncStatus.SYNCED)

        // Verify Updated Sync Status
        val updatedTransaction = transactionDao.getTransactionById("txn007").first()
        assertEquals(SyncStatus.SYNCED, updatedTransaction?.syncStatus)
    }
}