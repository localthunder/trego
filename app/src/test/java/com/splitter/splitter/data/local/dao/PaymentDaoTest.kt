package com.splitter.splittr.data.local.dao

import androidx.room.Room
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
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider

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
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        userDao = database.userDao()
        paymentDao = database.paymentDao()
        groupDao = database.groupDao()

        runBlocking {
            setupTestData()
        }
    }

    private suspend fun setupTestData() {
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

        val groups = listOf(
            GroupEntity(
                id = 1,
                name = "Test Group",
                createdAt = "2024-08-01",
                description = "Test Group Description",
                groupImg = "AAAAAAAAAA",
                inviteLink = "inteasdv",
                updatedAt = "2024-08-01",
                localImagePath = "imgpath",
                imageLastModified = "2024-08-11"
            ),
            GroupEntity(
                id = 2,
                name = "Test Group2",
                createdAt = "2024-08-01",
                description = "Test Group Description",
                groupImg = "AAAAAAAAAA",
                inviteLink = "inteasdv2",
                updatedAt = "2024-08-01",
                localImagePath = "imgpath",
                imageLastModified = "2024-08-11"
            )
        )

        userDao.insertUser(user)
        groups.forEach { groupDao.insertGroup(it) }
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createTestPayment(
        id: Int,
        groupId: Int = 1,
        status: SyncStatus = SyncStatus.PENDING_SYNC
    ) = PaymentEntity(
        id = id,
        groupId = groupId,
        paidByUserId = 1,
        transactionId = "txn$id",
        amount = 100.0,
        description = "Test payment",
        notes = "Notes",
        paymentDate = "2024-08-24",
        createdBy = 1,
        updatedBy = 1,
        createdAt = System.currentTimeMillis().toString(),
        updatedAt = System.currentTimeMillis().toString(),
        splitMode = "even",
        institutionName = "Test Institution",
        paymentType = "credit",
        currency = "USD",
        deletedAt = null,
        syncStatus = status
    )

    @Test
    fun testInsertPaymentDirect() = runBlocking {
        val payment = createTestPayment(1)
        val rowId = paymentDao.insertPayment(payment)

        assertTrue(rowId > 0)
        val retrievedPayment = paymentDao.getPaymentById(payment.id).first()
        assertNotNull(retrievedPayment)
        assertEquals(payment.id, retrievedPayment?.id)
    }

    @Test
    fun testInsertOrUpdatePaymentWithTransaction() = runBlocking {
        val payment = createTestPayment(1)
        paymentDao.insertOrUpdatePayment(payment)

        val retrievedPayment = paymentDao.getPaymentById(payment.id).first()
        assertNotNull(retrievedPayment)
        assertEquals(SyncStatus.PENDING_SYNC, retrievedPayment?.syncStatus)
        assertTrue(retrievedPayment?.updatedAt?.toLong() ?: 0 >= payment.updatedAt.toLong())
    }

    @Test
    fun testInsertOrUpdatePaymentsWithTransaction() = runBlocking {
        val payments = listOf(
            createTestPayment(1, 1),
            createTestPayment(2, 2)
        )

        paymentDao.insertOrUpdatePayments(payments)

        // Check Group 1
        val paymentsGroup1 = paymentDao.getPaymentsByGroup(1).first()
        assertTrue(paymentsGroup1.any { it.id == 1 })
        assertTrue(paymentsGroup1.all { it.syncStatus == SyncStatus.PENDING_SYNC })

        // Check Group 2
        val paymentsGroup2 = paymentDao.getPaymentsByGroup(2).first()
        assertTrue(paymentsGroup2.any { it.id == 2 })
        assertTrue(paymentsGroup2.all { it.syncStatus == SyncStatus.PENDING_SYNC })
    }

    @Test
    fun testGetPaymentByTransactionId() = runBlocking {
        val payment = createTestPayment(1)
        paymentDao.insertPayment(payment)

        val retrieved = paymentDao.getPaymentByTransactionId(payment.transactionId!!).first()
        assertNotNull(retrieved)
        assertEquals(payment.transactionId, retrieved?.transactionId)
    }

    @Test
    fun testGetNonArchivedPaymentsByGroup() = runBlocking {
        val payment1 = createTestPayment(1)
        val payment2 = createTestPayment(2)

        paymentDao.insertPayment(payment1)
        paymentDao.insertPayment(payment2)
        paymentDao.archivePayment(payment2.id, "2024-08-24")

        val nonArchived = paymentDao.getNonArchivedPaymentsByGroup(1)
        assertEquals(1, nonArchived.size)
        assertEquals(payment1.id, nonArchived.first().id)
    }

    @Test
    fun testUpdatePaymentWithTransaction() = runBlocking {
        val payment = createTestPayment(1)
        paymentDao.insertPayment(payment)

        val updatedPayment = payment.copy(amount = 150.0)
        paymentDao.updatePayment(updatedPayment)

        val retrieved = paymentDao.getPaymentById(payment.id).first()
        assertNotNull(retrieved)
        assertEquals(150.0, retrieved?.amount)
        assertEquals(SyncStatus.PENDING_SYNC, retrieved?.syncStatus)
        assertTrue(retrieved?.updatedAt?.toLong() ?: 0 > payment.updatedAt.toLong())
    }

    @Test
    fun testTransactionHandling() = runBlocking {
        val result = paymentDao.runInTransaction {
            val payment = createTestPayment(1)
            val rowId = paymentDao.insertPayment(payment)
            paymentDao.updatePaymentSyncStatus(payment.id, SyncStatus.SYNCED)
            rowId
        }

        assertTrue(result > 0)
        val payment = paymentDao.getPaymentById(1).first()
        assertNotNull(payment)
        assertEquals(SyncStatus.SYNCED, payment?.syncStatus)
    }

    @Test
    fun testArchiveAndRestorePayment() = runBlocking {
        val payment = createTestPayment(1)
        paymentDao.insertPayment(payment)

        val archiveTime = "2024-08-24 10:00:00"
        paymentDao.archivePayment(payment.id, archiveTime)

        var archived = paymentDao.getPaymentById(payment.id).first()
        assertNotNull(archived?.deletedAt)

        paymentDao.restorePayment(payment.id)
        archived = paymentDao.getPaymentById(payment.id).first()
        assertNull(archived?.deletedAt)
    }

    @Test
    fun testGetUnsyncedPayments() = runBlocking {
        val syncedPayment = createTestPayment(1, status = SyncStatus.SYNCED)
        val unsyncedPayment = createTestPayment(2, status = SyncStatus.SYNC_FAILED)

        paymentDao.insertPayment(syncedPayment)
        paymentDao.insertPayment(unsyncedPayment)

        val unsynced = paymentDao.getUnsyncedPayments().first()
        assertEquals(1, unsynced.size)
        assertEquals(unsyncedPayment.id, unsynced.first().id)
    }
}