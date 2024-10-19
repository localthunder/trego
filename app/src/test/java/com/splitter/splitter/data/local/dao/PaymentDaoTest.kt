package com.splitter.splittr.data.local.dao

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.splitter.splittr.TestApplication
import com.splitter.splittr.data.local.AppDatabase
import com.splitter.splittr.data.local.entities.GroupEntity
import com.splitter.splittr.data.local.entities.PaymentEntity
import com.splitter.splittr.data.local.entities.UserEntity
import com.splitter.splittr.data.sync.SyncStatus
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
class PaymentDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var paymentDao: PaymentDao
    private lateinit var userDao: UserDao
    private lateinit var groupDao: GroupDao

    @Before
    fun setup() {
        // Initialize the in-memory database
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        // Get DAO instances
        userDao = database.userDao()
        paymentDao = database.paymentDao()
        groupDao = database.groupDao()

        // Insert required UserEntity and GroupEntity
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
        val group1 = GroupEntity(
            id = 1,
            name = "Test Group",
            createdAt = "2024-08-01",
            description = "Test Group Description",
            groupImg = "AAAAAAAAAA",
            inviteLink = "inteasdv",
            updatedAt = "2024-08-01"
        )
        val group2 = GroupEntity(
            id = 2,
            name = "Test Group2",
            createdAt = "2024-08-01",
            description = "Test Group Description",
            groupImg = "AAAAAAAAAA",
            inviteLink = "inteasdv",
            updatedAt = "2024-08-01"
        )

        runBlocking {
            userDao.insertUser(user)
            groupDao.insertGroup(group1)
            groupDao.insertGroup(group2)

        }
    }

    @After
    fun teardown() {
        // Close the database after tests
        database.close()
    }

    @Test
    fun insertPayment() = runBlocking {
        val payment = PaymentEntity(
            id = 1,
            groupId = 1,
            paidByUserId = 1, // Match existing User ID
            transactionId = "txn001",
            amount = 100.0,
            description = "Test payment",
            notes = "Notes",
            paymentDate = "2024-08-24",
            createdBy = 1, // Match existing User ID
            updatedBy = 1, // Match existing User ID
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            splitMode = "even",
            institutionName = "Test Institution",
            paymentType = "credit",
            currency = "USD",
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_SYNC
        )
        val rowId = paymentDao.insertPayment(payment)
        assertTrue(rowId > 0)

        val retrievedPayment = paymentDao.getPaymentById(payment.id).first()
        assertNotNull(retrievedPayment)
        assertEquals(payment.id, retrievedPayment?.id)
    }

    @Test
    fun insertOrUpdatePayments() = runBlocking {
        val payment1 = PaymentEntity(
            id = 1,
            groupId = 1,
            paidByUserId = 1,
            transactionId = "txn001",
            amount = 100.0,
            description = "Test payment 1",
            notes = "Notes 1",
            paymentDate = "2024-08-24",
            createdBy = 1,
            updatedBy = 1,
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            splitMode = "even",
            institutionName = "Test Institution",
            paymentType = "credit",
            currency = "USD",
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_SYNC
        )

        val payment2 = PaymentEntity(
            id = 2,
            groupId = 2,
            paidByUserId = 1,
            transactionId = "txn002",
            amount = 200.0,
            description = "Test payment 2",
            notes = "Notes 2",
            paymentDate = "2024-08-24",
            createdBy = 1,
            updatedBy = 1,
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            splitMode = "split",
            institutionName = "Test Institution 2",
            paymentType = "debit",
            currency = "EUR",
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_SYNC
        )

        paymentDao.insertOrUpdatePayments(listOf(payment1, payment2))

        // Check Group 1
        val paymentsGroup1 = paymentDao.getPaymentsByGroup(1).first()
        println("Payments Group 1: $paymentsGroup1") // Debug statement
        assertTrue(paymentsGroup1.any { it.transactionId == "txn001" })
        assertFalse(paymentsGroup1.any { it.transactionId == "txn002" }) // Should not be present in Group 1

        // Check Group 2
        val paymentsGroup2 = paymentDao.getPaymentsByGroup(2).first()
        println("Payments Group 2: $paymentsGroup2") // Debug statement
        assertTrue(paymentsGroup2.any { it.transactionId == "txn002" })
        assertFalse(paymentsGroup2.any { it.transactionId == "txn001" }) // Should not be present in Group 2
    }




    @Test
    fun updatePayment() = runBlocking {
        val payment = PaymentEntity(
            id = 1,
            groupId = 1,
            paidByUserId = 1,
            transactionId = "txn001",
            amount = 100.0,
            description = "Test payment",
            notes = "Notes",
            paymentDate = "2024-08-24",
            createdBy = 1,
            updatedBy = 1,
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            splitMode = "even",
            institutionName = "Test Institution",
            paymentType = "credit",
            currency = "USD",
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_SYNC
        )
        val rowId = paymentDao.insertPayment(payment)
        assertTrue(rowId > 0)

        val updatedPayment = payment.copy(amount = 150.0, description = "Updated payment")
        paymentDao.updatePayment(updatedPayment)

        val retrievedPayment = paymentDao.getPaymentById(payment.id).first()
        assertNotNull(retrievedPayment)
        retrievedPayment?.amount?.let { assertEquals(150.0, it, 0.0) }
        assertEquals("Updated payment", retrievedPayment?.description)
    }

    @Test
    fun archiveAndRestorePayment() = runBlocking {
        val payment = PaymentEntity(
            id = 1,
            groupId = 1,
            paidByUserId = 1,
            transactionId = "txn001",
            amount = 100.0,
            description = "Test payment",
            notes = "Notes",
            paymentDate = "2024-08-24",
            createdBy = 1,
            updatedBy = 1,
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            splitMode = "even",
            institutionName = "Test Institution",
            paymentType = "credit",
            currency = "USD",
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_SYNC
        )
        val rowId = paymentDao.insertPayment(payment)
        assertTrue(rowId > 0)

        paymentDao.archivePayment(payment.id, "2024-08-24 10:00:00")

        var archivedPayment = paymentDao.getPaymentById(payment.id).first()
        assertNotNull(archivedPayment)
        assertNotNull(archivedPayment?.deletedAt)

        paymentDao.restorePayment(payment.id)

        archivedPayment = paymentDao.getPaymentById(payment.id).first()
        assertNotNull(archivedPayment)
        assertNull(archivedPayment?.deletedAt)
    }

    @Test
    fun getUnsyncedPayments() = runBlocking {
        val payment1 = PaymentEntity(
            id = 1,
            groupId = 1,
            paidByUserId = 1,
            transactionId = "txn001",
            amount = 100.0,
            description = "Unsynced payment",
            notes = "Notes",
            paymentDate = "2024-08-24",
            createdBy = 1,
            updatedBy = 1,
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            splitMode = "even",
            institutionName = "Test Institution",
            paymentType = "credit",
            currency = "USD",
            deletedAt = null,
            syncStatus = SyncStatus.SYNC_FAILED
        )
        val payment2 = PaymentEntity(
            id = 2,
            groupId = 1,
            paidByUserId = 1,
            transactionId = "txn002",
            amount = 200.0,
            description = "Synced payment",
            notes = "Notes",
            paymentDate = "2024-08-24",
            createdBy = 1,
            updatedBy = 1,
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            splitMode = "even",
            institutionName = "Test Institution",
            paymentType = "credit",
            currency = "USD",
            deletedAt = null,
            syncStatus = SyncStatus.SYNCED
        )

        paymentDao.insertOrUpdatePayments(listOf(payment1, payment2))

        val unsyncedPayments = paymentDao.getUnsyncedPayments().first()
        assertTrue(unsyncedPayments.any { it.transactionId == "txn001" })
        assertFalse(unsyncedPayments.any { it.transactionId == "txn002" })
    }

    @Test
    fun updatePaymentSyncStatus() = runBlocking {
        val payment = PaymentEntity(
            id = 1,
            groupId = 1,
            paidByUserId = 1,
            transactionId = "txn001",
            amount = 100.0,
            description = "Test payment",
            notes = "Notes",
            paymentDate = "2024-08-24",
            createdBy = 1,
            updatedBy = 1,
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            splitMode = "even",
            institutionName = "Test Institution",
            paymentType = "credit",
            currency = "USD",
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_SYNC
        )
        val rowId = paymentDao.insertPayment(payment)
        assertTrue(rowId > 0)

        paymentDao.updatePaymentSyncStatus(payment.id, SyncStatus.SYNCED)

        val updatedPayment = paymentDao.getPaymentById(payment.id).first()
        assertNotNull(updatedPayment)
        assertEquals("SYNCED", updatedPayment?.syncStatus?.name)
    }
}
