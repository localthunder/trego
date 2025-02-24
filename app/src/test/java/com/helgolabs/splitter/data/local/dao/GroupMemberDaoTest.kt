package com.helgolabs.trego.data.local.dao

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.helgolabs.trego.TestApplication
import com.helgolabs.trego.data.local.AppDatabase
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.data.local.entities.GroupEntity
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class GroupMemberDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var groupMemberDao: GroupMemberDao
    private lateinit var userDao: UserDao
    private lateinit var groupDao: GroupDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        groupMemberDao = database.groupMemberDao()
        userDao = database.userDao()
        groupDao = database.groupDao()

        // Insert required UserEntity and GroupEntity before running tests
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
        val group = GroupEntity(
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

        runBlocking {
            userDao.insertUser(user)
            groupDao.insertGroup(group)
        }
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetGroupMembers() = runBlocking {
        // Insert group members
        val groupMember1 = GroupMemberEntity(
            groupId = 1,
            userId = 1,
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            removedAt = null,
            syncStatus = SyncStatus.PENDING_SYNC
        )
        val groupMember2 = GroupMemberEntity(
            groupId = 1,
            userId = 1,
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            removedAt = null,
            syncStatus = SyncStatus.SYNCED
        )
        groupMemberDao.insertGroupMember(groupMember1)
        groupMemberDao.insertGroupMember(groupMember2)

        // Retrieve and verify group members
        val members = groupMemberDao.getMembersOfGroup(1).first()
        assertNotNull(members)
        assertEquals(2, members.size)
        assertTrue(members.any { it.userId == 1 })
    }

    @Test
    fun removeGroupMember() = runBlocking {
        // Insert group member
        val groupMember = GroupMemberEntity(
            groupId = 1,
            userId = 1,
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            removedAt = null,
            syncStatus = SyncStatus.PENDING_SYNC
        )
        groupMemberDao.insertGroupMember(groupMember)

        // Remove the group member
        groupMemberDao.removeGroupMember(1)

        // Verify the group member was removed
        val members = groupMemberDao.getMembersOfGroup(1).first()
        assertTrue(members.isEmpty())
    }

    @Test
    fun getUnsyncedGroupMembers() = runBlocking {
        // Insert group members with different sync statuses
        val syncedMember = GroupMemberEntity(
            groupId = 1,
            userId = 1,
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            removedAt = null,
            syncStatus = SyncStatus.SYNCED
        )
        val unsyncedMember = GroupMemberEntity(
            groupId = 1,
            userId = 1,
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            removedAt = null,
            syncStatus = SyncStatus.PENDING_SYNC
        )
        groupMemberDao.insertGroupMember(syncedMember)
        groupMemberDao.insertGroupMember(unsyncedMember)

        // Retrieve and verify unsynced group members
        val unsyncedMembers = groupMemberDao.getUnsyncedGroupMembers().first()
        assertNotNull(unsyncedMembers)
        assertEquals(1, unsyncedMembers.size)
        assertEquals(1, unsyncedMembers[0].userId)
    }

    @Test
    fun updateGroupMemberSyncStatus() = runBlocking {
        // Insert group member
        val groupMember = GroupMemberEntity(
            groupId = 1,
            userId = 1,
            createdAt = "2024-08-01",
            updatedAt = "2024-08-01",
            removedAt = null,
            syncStatus = SyncStatus.PENDING_SYNC
        )
        groupMemberDao.insertGroupMember(groupMember)

        // Update sync status
        groupMemberDao.updateGroupMemberSyncStatus(1, SyncStatus.SYNCED)

        // Verify the update
        val updatedMember = groupMemberDao.getMembersOfGroup(1).first().find { it.userId == 1 }
        assertNotNull(updatedMember)
        assertEquals(SyncStatus.PENDING_SYNC, updatedMember?.syncStatus)
    }
}
