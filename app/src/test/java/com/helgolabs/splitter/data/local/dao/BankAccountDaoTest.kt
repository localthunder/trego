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
class BankAccountDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var bankAccountDao: BankAccountDao
    private lateinit var userDao: UserDao
    private lateinit var requisitionDao: RequisitionDao
    private lateinit var institutionDao: InstitutionDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        bankAccountDao = database.bankAccountDao()
        userDao = database.userDao()
        requisitionDao = database.requisitionDao()
        institutionDao = database.institutionDao() // Add InstitutionDao reference if it exists

        // Insert InstitutionEntity
        val institution = InstitutionEntity(
            id = "institution123",
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

        // Insert RequisitionEntity with valid foreign key references
        val requisition = RequisitionEntity(
            requisitionId = "requisition_001",
            serverId = "server001",
            userId = 1, // Ensure this userId exists
            institutionId = "institution123", // Ensure this institutionId exists
            reference = "Reference",
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01"
        )

        runBlocking {
            requisitionDao.insert(requisition)
        }
    }


    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetUserAccounts() = runBlocking {
        val account = BankAccountEntity(
            accountId = "123",
            serverId = "server123",
            requisitionId = "requisition_001",
            userId = 1,
            iban = "DE89370400440532013000",
            institutionId = "institution123",
            currency = "USD",
            ownerName = "John Doe",
            name = "Main Account",
            product = "Checking",
            cashAccountType = "Personal",
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            syncStatus = SyncStatus.PENDING_SYNC
        )
        bankAccountDao.insertBankAccount(account)

        val accounts = bankAccountDao.getUserAccounts(1).first()
        assertNotNull(accounts)
        assertTrue(accounts.isNotEmpty())
        assertEquals("123", accounts[0].accountId)
        assertEquals("server123", accounts[0].serverId)
        assertEquals("requisition_001", accounts[0].requisitionId)
        assertEquals("DE89370400440532013000", accounts[0].iban)
        assertEquals("institution123", accounts[0].institutionId)
        assertEquals("USD", accounts[0].currency)
        assertEquals("John Doe", accounts[0].ownerName)
        assertEquals("Main Account", accounts[0].name)
        assertEquals("Checking", accounts[0].product)
        assertEquals("Personal", accounts[0].cashAccountType)
    }

    @Test
    fun insertAndGetBankAccountsByRequisition() = runBlocking {
        val account = BankAccountEntity(
            accountId = "123",
            serverId = "server123",
            requisitionId = "requisition_001",
            userId = 1,
            iban = "DE89370400440532013000",
            institutionId = "institution123",
            currency = "USD",
            ownerName = "John Doe",
            name = "Main Account",
            product = "Checking",
            cashAccountType = "Personal",
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            syncStatus = SyncStatus.PENDING_SYNC
        )
        bankAccountDao.insertBankAccount(account)

        val accounts = bankAccountDao.getBankAccountsByRequisition("requisition_001").first()
        assertNotNull(accounts)
        assertTrue(accounts.isNotEmpty())
        assertEquals("123", accounts[0].accountId)
        assertEquals("server123", accounts[0].serverId)
        assertEquals("requisition_001", accounts[0].requisitionId)
        assertEquals("DE89370400440532013000", accounts[0].iban)
        assertEquals("institution123", accounts[0].institutionId)
        assertEquals("USD", accounts[0].currency)
        assertEquals("John Doe", accounts[0].ownerName)
        assertEquals("Main Account", accounts[0].name)
        assertEquals("Checking", accounts[0].product)
        assertEquals("Personal", accounts[0].cashAccountType)
    }

    @Test
    fun updateBankAccountSyncStatus() = runBlocking {
        val account = BankAccountEntity(
            accountId = "123",
            serverId = "server123",
            requisitionId = "requisition_001",
            userId = 1,
            iban = "DE89370400440532013000",
            institutionId = "institution123",
            currency = "USD",
            ownerName = "John Doe",
            name = "Main Account",
            product = "Checking",
            cashAccountType = "Personal",
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            syncStatus = SyncStatus.PENDING_SYNC
        )
        bankAccountDao.insertBankAccount(account)

        bankAccountDao.updateBankAccountSyncStatus("123", SyncStatus.SYNCED)

        val updatedAccount = bankAccountDao.getUserAccounts(1).first().find { it.accountId == "123" }
        assertNotNull(updatedAccount)
        assertEquals(SyncStatus.SYNCED, updatedAccount?.syncStatus)
    }

    @Test
    fun getUnsyncedBankAccounts() = runBlocking {
        val unsyncedAccount = BankAccountEntity(
            accountId = "123",
            serverId = "server123",
            requisitionId = "requisition_001",
            userId = 1,
            iban = "DE89370400440532013000",
            institutionId = "institution123",
            currency = "USD",
            ownerName = "John Doe",
            name = "Main Account",
            product = "Checking",
            cashAccountType = "Personal",
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            syncStatus = SyncStatus.PENDING_SYNC
        )
        bankAccountDao.insertBankAccount(unsyncedAccount)

        val unsyncedAccounts = bankAccountDao.getUnsyncedBankAccounts().first()
        assertTrue(unsyncedAccounts.isNotEmpty())
        assertEquals("123", unsyncedAccounts[0].accountId)
        assertEquals(SyncStatus.PENDING_SYNC, unsyncedAccounts[0].syncStatus)
    }
}
