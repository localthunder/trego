package com.helgolabs.trego.data.local.dao

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.helgolabs.trego.TestApplication
import com.helgolabs.trego.data.local.AppDatabase
import com.helgolabs.trego.data.local.entities.GroupEntity
import com.helgolabs.trego.data.local.entities.PaymentEntity
import com.helgolabs.trego.data.local.entities.PaymentSplitEntity
import com.helgolabs.trego.data.local.entities.UserEntity
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
import java.sql.Timestamp

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class PaymentSplitDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var paymentDao: PaymentDao
    private lateinit var paymentSplitDao: PaymentSplitDao
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
        paymentSplitDao = database.paymentSplitDao()

        // Insert required UserEntity and GroupEntity
        val user = UserEntity(
            userId = 1,
            serverId = 1,
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
            updatedAt = "2024-08-01",
            localImagePath = "img",
            imageLastModified = "2024-09-12"
        )
        val group2 = GroupEntity(
            id = 2,
            name = "Test Group2",
            createdAt = "2024-08-01",
            description = "Test Group Description",
            groupImg = "AAAAAAAAAA",
            inviteLink = "inteasdv",
            updatedAt = "2024-08-01",
            localImagePath = "img",
            imageLastModified = "2024-09-12"
        )

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

        runBlocking {
            userDao.insertUser(user)
            groupDao.insertGroup(group1)
            groupDao.insertGroup(group2)
            paymentDao.insertPayment(payment)

        }
    }

    @After
    fun teardown() {
        // Close the database after tests
        database.close()
    }

    @Test
    fun insertAndUpdatePaymentSplit() = runBlocking {
        val timestampNow = Timestamp(System.currentTimeMillis()).toString()

        val paymentSplit = PaymentSplitEntity(
            id = 1,
            serverId = 1,
            paymentId = 1,
            userId = 1,
            amount = 50.0,
            createdBy = 1,
            updatedBy = 1,
            createdAt = timestampNow,
            updatedAt = timestampNow,
            currency = "USD",
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_SYNC
        )

        // Insert PaymentSplit
        val insertResult = paymentSplitDao.insertPaymentSplit(paymentSplit)
        assertTrue(insertResult > 0)

        // Update PaymentSplit
        val updatedPaymentSplit = paymentSplit.copy(amount = 75.0, syncStatus = SyncStatus.SYNCED)
        paymentSplitDao.updatePaymentSplit(updatedPaymentSplit)

        // Verify Insert
        val splits = paymentSplitDao.getPaymentSplitsByPayment(1).first()
        assertEquals(1, splits.size)
        assertEquals(75.0, splits[0].amount, 0.01) // Use a delta value like 0.01
        assertEquals(SyncStatus.SYNCED, splits[0].syncStatus)
    }


    @Test
    fun getUnsyncedPaymentSplits() = runBlocking {
        val timestampNow = Timestamp(System.currentTimeMillis()).toString()

        // Insert an unsynced PaymentSplit
        val unsyncedPaymentSplit = PaymentSplitEntity(
            id = 1,
            serverId = 1,
            paymentId = 1,
            userId = 1,
            amount = 100.0,
            createdBy = 1,
            updatedBy = 1,
            createdAt = timestampNow,
            updatedAt = timestampNow,
            currency = "EUR",
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_SYNC
        )
        paymentSplitDao.insertPaymentSplit(unsyncedPaymentSplit)

        // Insert a synced PaymentSplit
        val syncedPaymentSplit = PaymentSplitEntity(
            id = 2,  // Ensure this ID is different from unsyncedPaymentSplit
            serverId = 2,
            paymentId = 1,
            userId = 1,
            amount = 150.0,
            createdBy = 1,
            updatedBy = 1,
            createdAt = timestampNow,
            updatedAt = timestampNow,
            currency = "EUR",
            deletedAt = null,
            syncStatus = SyncStatus.SYNCED
        )
        paymentSplitDao.insertPaymentSplit(syncedPaymentSplit)

        // Fetch unsynced splits
        val unsyncedSplits = paymentSplitDao.getUnsyncedPaymentSplits().first()

        // Check if unsynced splits contain the unsynced split
        assertTrue(unsyncedSplits.any { it.id == 1 })  // ID of the unsynced split
        assertTrue(unsyncedSplits.none { it.id == 2 })  // ID of the synced split
    }


    @Test
    fun updatePaymentSplitSyncStatus() = runBlocking {
        val timestampNow = Timestamp(System.currentTimeMillis()).toString()

        // Insert a PaymentSplit with id = 1
        val paymentSplit = PaymentSplitEntity(
            id = 1,
            serverId = 1,
            paymentId = 1,
            userId = 1,
            amount = 200.0,
            createdBy = 1,
            updatedBy = 1,
            createdAt = timestampNow,
            updatedAt = timestampNow,
            currency = "GBP",
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_SYNC
        )
        paymentSplitDao.insertPaymentSplit(paymentSplit)

        // Update Sync Status with the correct id
        paymentSplitDao.updatePaymentSplitSyncStatus(1, SyncStatus.SYNCED)

        // Verify Sync Status Update
        val splits = paymentSplitDao.getPaymentSplitsByPayment(1).first()
        assertTrue(splits.isNotEmpty()) // Ensure the list is not empty
        assertEquals(SyncStatus.SYNCED.name, splits[0].syncStatus.name)
    }
}
