package com.helgolabs.trego.data.local.dao

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.helgolabs.trego.data.local.AppDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.helgolabs.trego.TestApplication
import com.helgolabs.trego.data.local.entities.InstitutionEntity
import com.helgolabs.trego.data.local.entities.UserEntity
import com.helgolabs.trego.data.local.entities.RequisitionEntity
import com.helgolabs.trego.data.sync.SyncStatus
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
class RequisitionDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var userDao: UserDao
    private lateinit var requisitionDao: RequisitionDao
    private lateinit var institutionDao: InstitutionDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        userDao = database.userDao()
        requisitionDao = database.requisitionDao()
        institutionDao = database.institutionDao() // Add InstitutionDao reference if it exists

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
        val institution2 = InstitutionEntity(
            id = "inst002",
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
            institutionDao.insert(institution2)
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
    }


    @After
    fun teardown() {
        database.close()
    }
    @Test
    fun insertRequisition() = runBlocking {
        val requisition = RequisitionEntity(
            requisitionId = "req001",
            serverId = "server001",
            userId = 1,
            institutionId = "inst001",
            reference = "Reference 1",
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            syncStatus = SyncStatus.PENDING_SYNC
        )

        requisitionDao.insert(requisition)

        // Verify Insert
        val retrievedRequisition = requisitionDao.getRequisitionById("req001")
        assertEquals(requisition, retrievedRequisition)
    }

    @Test
    fun getAllRequisitions() = runBlocking {
        val requisition1 = RequisitionEntity(
            requisitionId = "req001",
            serverId = "server001",
            userId = 1,
            institutionId = "inst001",
            reference = "Reference 1",
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            syncStatus = SyncStatus.PENDING_SYNC
        )

        val requisition2 = RequisitionEntity(
            requisitionId = "req002",
            serverId = "server002",
            userId = 1,
            institutionId = "inst002",
            reference = "Reference 2",
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            syncStatus = SyncStatus.SYNCED
        )

        requisitionDao.insert(requisition1)
        requisitionDao.insert(requisition2)

        val allRequisitions = requisitionDao.getAllRequisitions()
        assertEquals(2, allRequisitions.size)
        assertTrue(allRequisitions.any { it.requisitionId == "req001" })
        assertTrue(allRequisitions.any { it.requisitionId == "req002" })
    }

    @Test
    fun getRequisitionById() = runBlocking {
        val requisition = RequisitionEntity(
            requisitionId = "req003",
            serverId = "server003",
            userId = 1,
            institutionId = "inst001",
            reference = "Reference 3",
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            syncStatus = SyncStatus.PENDING_SYNC
        )

        requisitionDao.insert(requisition)

        val retrievedRequisition = requisitionDao.getRequisitionById("req003")
        assertEquals(requisition, retrievedRequisition)
    }

    @Test
    fun deleteRequisitionById() = runBlocking {
        val requisition = RequisitionEntity(
            requisitionId = "req004",
            serverId = "server004",
            userId = 1,
            institutionId = "inst001",
            reference = "Reference 4",
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            syncStatus = SyncStatus.SYNCED
        )

        requisitionDao.insert(requisition)

        // Verify Insert
        val retrievedRequisitionBeforeDelete = requisitionDao.getRequisitionById("req004")
        assertEquals(requisition, retrievedRequisitionBeforeDelete)

        // Delete Requisition
        requisitionDao.deleteRequisitionById("req004")

        // Verify Deletion
        val retrievedRequisitionAfterDelete = requisitionDao.getRequisitionById("req004")
        assertNull(retrievedRequisitionAfterDelete)
    }
    @Test
    fun getRequisitionByReference() = runBlocking {
        val requisition = RequisitionEntity(
            requisitionId = "req005",
            serverId = "server005",
            userId = 1,
            institutionId = "inst001",
            reference = "UniqueReference123",
            createdAt = "2024-08-24",
            updatedAt = "2024-08-24",
            syncStatus = SyncStatus.PENDING_SYNC
        )

        requisitionDao.insert(requisition)

        // Retrieve by reference
        val retrievedRequisition = requisitionDao.getRequisitionByReference("UniqueReference123")

        // Verify retrieval
        assertNotNull(retrievedRequisition)
        assertEquals(requisition, retrievedRequisition)

        // Try to retrieve a non-existent reference
        val nonExistentRequisition = requisitionDao.getRequisitionByReference("NonExistentReference")
        assertNull(nonExistentRequisition)
    }
}