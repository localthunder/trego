package com.splitter.splittr.data.local.dao

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.splitter.splittr.TestApplication
import com.splitter.splittr.data.local.AppDatabase
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
class UserDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var userDao: UserDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        userDao = database.userDao()

        // Insert initial data
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
    fun insertUser() = runBlocking {
        val user = UserEntity(
            userId = 2,
            serverId = 4,
            username = "anotheruser",
            email = "anotheruser@example.com",
            passwordHash = "anotherhashedpassword",
            googleId = "googleid",
            appleId = "appleid",
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            defaultCurrency = "EUR",
            lastLoginDate = "2024-08-01"
        )

        userDao.insertUser(user)

        // Verify Insert
        val retrievedUser = userDao.getUserById(2).first()
        assertEquals(user, retrievedUser)
    }

    @Test
    fun updateUser() = runBlocking {
        val updatedUser = UserEntity(
            userId = 1,
            serverId = 5,
            username = "updateduser",
            email = "updateduser@example.com",
            passwordHash = "newhashedpassword",
            googleId = "newgoogleid",
            appleId = "newappleid",
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            defaultCurrency = "USD",
            syncStatus = SyncStatus.SYNCED,
            lastLoginDate = "2024-08-01"
        )

        userDao.updateUser(updatedUser)

        // Verify Update
        val retrievedUser = userDao.getUserById(1).first()
        assertEquals(updatedUser, retrievedUser)
    }

    @Test
    fun deleteUser() = runBlocking {
        val user = UserEntity(
            userId = 3,
            serverId = 6,
            username = "userToDelete",
            email = "userToDelete@example.com",
            passwordHash = "passwordToDelete",
            googleId = null,
            appleId = null,
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            defaultCurrency = "USD",
            lastLoginDate = "2024-08-01"
        )

        userDao.insertUser(user)
        userDao.deleteUser(user)

        // Verify Deletion
        val retrievedUser = userDao.getUserById(3).first()
        assertNull(retrievedUser)
    }

    @Test
    fun getUserByEmail() = runBlocking {
        val user = UserEntity(
            userId = 4,
            serverId = 7,
            username = "emailuser",
            email = "emailuser@example.com",
            passwordHash = "emailhashedpassword",
            googleId = null,
            appleId = null,
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            defaultCurrency = "USD",
            lastLoginDate = "2024-08-01"
        )

        userDao.insertUser(user)

        val retrievedUser = userDao.getUserByEmail("emailuser@example.com").first()
        assertEquals(user, retrievedUser)
    }

    @Test
    fun getUserByUsername() = runBlocking {
        val user = UserEntity(
            userId = 5,
            serverId = 9,
            username = "uniqueusername",
            email = "uniqueuser@example.com",
            passwordHash = "usernamehashedpassword",
            googleId = null,
            appleId = null,
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            defaultCurrency = "USD",
            lastLoginDate = "2024-08-01"
        )

        userDao.insertUser(user)

        val retrievedUser = userDao.getUserByUsername("uniqueusername").first()
        assertEquals(user, retrievedUser)
    }

    @Test
    fun getUnsyncedUsers() = runBlocking {
        val syncedUser = UserEntity(
            userId = 6,
            serverId = 8,
            username = "synceduser",
            email = "synceduser@example.com",
            passwordHash = "syncedpassword",
            googleId = null,
            appleId = null,
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            defaultCurrency = "USD",
            syncStatus = SyncStatus.SYNCED,
            lastLoginDate = "2024-08-01"
        )

        val unsyncedUser = UserEntity(
            userId = 7,
            serverId = 13,
            username = "unsynceduser",
            email = "unsynceduser@example.com",
            passwordHash = "unsyncedpassword",
            googleId = null,
            appleId = null,
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            defaultCurrency = "USD",
            syncStatus = SyncStatus.PENDING_SYNC,
            lastLoginDate = "2024-08-01"
        )

        userDao.insertUser(syncedUser)
        userDao.insertUser(unsyncedUser)

        val unsyncedUsers = userDao.getUnsyncedUsers().first()
        assertTrue(unsyncedUsers.any { it.userId == 7 })
        assertFalse(unsyncedUsers.any { it.userId == 6 })
    }

    @Test
    fun updateUserSyncStatus() = runBlocking {
        val user = UserEntity(
            userId = 8,
            serverId = 33,
            username = "syncstatususer",
            email = "syncstatususer@example.com",
            passwordHash = "syncstatuspassword",
            googleId = null,
            appleId = null,
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            defaultCurrency = "USD",
            syncStatus = SyncStatus.PENDING_SYNC,
            lastLoginDate = "2024-08-01"
        )

        userDao.insertUser(user)

        // Verify Initial Sync Status
        val retrievedUser = userDao.getUserById(8).first()
        assertEquals(SyncStatus.PENDING_SYNC, retrievedUser?.syncStatus)

        // Update Sync Status
        userDao.updateUserSyncStatus(8, SyncStatus.SYNCED)

        // Verify Updated Sync Status
        val updatedUser = userDao.getUserById(8).first()
        assertEquals(SyncStatus.SYNCED, updatedUser?.syncStatus)
    }
}
