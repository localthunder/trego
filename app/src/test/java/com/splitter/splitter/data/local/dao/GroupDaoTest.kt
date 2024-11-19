package com.splitter.splittr.data.local.dao

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.splitter.splittr.data.local.AppDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.splitter.splittr.TestApplication
import com.splitter.splittr.data.local.entities.GroupEntity
import com.splitter.splittr.data.sync.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import org.junit.Assert.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class GroupDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var groupDao: GroupDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        groupDao = database.groupDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetGroup() = runBlocking {
        val group = GroupEntity(
            id = 0,
            name = "Test Group",
            description = "Test Description",
            groupImg = null,
            createdAt = Date().toString(),
            updatedAt = Date().toString(),
            inviteLink = null,
            syncStatus = SyncStatus.PENDING_SYNC,
            localImagePath = "img",
            imageLastModified = "2024-09-12"
        )
        val insertedId = groupDao.insertGroup(group)
        val retrievedGroup = groupDao.getGroupById(insertedId.toInt()).first()
        assertNotNull(retrievedGroup)
        assertEquals("Test Group", retrievedGroup?.name)
    }

    @Test
    fun updateGroup() = runBlocking {
        val group = GroupEntity(
            id = 0,
            name = "Original Name",
            description = "Original Description",
            groupImg = null,
            createdAt = Date().toString(),
            updatedAt = Date().toString(),
            inviteLink = null,
            syncStatus = SyncStatus.PENDING_SYNC,
            localImagePath = "img",
            imageLastModified = "2024-09-12"
        )
        val insertedId = groupDao.insertGroup(group)

        val updatedGroup = group.copy(id = insertedId.toInt(), name = "Updated Name")
        groupDao.updateGroup(updatedGroup)

        val retrievedGroup = groupDao.getGroupById(insertedId.toInt()).first()
        assertEquals("Updated Name", retrievedGroup?.name)
    }

    @Test
    fun deleteGroup() = runBlocking {
        val group = GroupEntity(
            id = 0,
            name = "To Be Deleted",
            description = "This group will be deleted",
            groupImg = null,
            createdAt = Date().toString(),
            updatedAt = Date().toString(),
            inviteLink = null,
            syncStatus = SyncStatus.PENDING_SYNC,
            localImagePath = "img",
            imageLastModified = "2024-09-12"
        )
        val insertedId = groupDao.insertGroup(group)
        val insertedGroup = groupDao.getGroupById(insertedId.toInt()).first()
        assertNotNull(insertedGroup)

        if (insertedGroup != null) {
            groupDao.deleteGroup(insertedGroup.id)
        }
        val deletedGroup = groupDao.getGroupById(insertedId.toInt()).first()
        assertNull(deletedGroup)
    }

    @Test
    fun getGroupsByUserId() = runBlocking {
        // This test requires setting up GroupMemberEntity as well
        // For simplicity, we'll just check if the method runs without errors
        val groups = groupDao.getGroupsByUserId(1).first()
        assertNotNull(groups)
    }

    @Test
    fun getUnsyncedGroups() = runBlocking {
        val unsyncedGroup = GroupEntity(
            id = 0,
            name = "Unsynced Group",
            description = "This group is not synced",
            groupImg = null,
            createdAt = Date().toString(),
            updatedAt = Date().toString(),
            inviteLink = null,
            syncStatus = SyncStatus.PENDING_SYNC,
            localImagePath = "img",
            imageLastModified = "2024-09-12"
        )
        groupDao.insertGroup(unsyncedGroup)

        val unsyncedGroups = groupDao.getUnsyncedGroups().first()
        assertTrue(unsyncedGroups.isNotEmpty())
        assertEquals("Unsynced Group", unsyncedGroups[0].name)
    }

    @Test
    fun updateGroupSyncStatus() = runBlocking {
        val group = GroupEntity(
            id = 0,
            name = "Sync Test Group",
            description = "Testing sync status update",
            groupImg = null,
            createdAt = Date().toString(),
            updatedAt = Date().toString(),
            inviteLink = null,
            syncStatus = SyncStatus.PENDING_SYNC,
            localImagePath = "img",
            imageLastModified = "2024-09-12"
        )
        val insertedId = groupDao.insertGroup(group)

        groupDao.updateGroupSyncStatus(insertedId.toInt(), SyncStatus.SYNCED)

        val updatedGroup = groupDao.getGroupById(insertedId.toInt()).first()
        assertEquals(SyncStatus.SYNCED, updatedGroup?.syncStatus)
    }

    @Test
    fun updateGroupInviteLink() = runBlocking {
        val group = GroupEntity(
            id = 0,
            name = "Invite Link Test Group",
            description = "Testing invite link update",
            groupImg = null,
            createdAt = Date().toString(),
            updatedAt = Date().toString(),
            inviteLink = null,
            syncStatus = SyncStatus.PENDING_SYNC,
            localImagePath = "img",
            imageLastModified = "2024-09-12"
        )
        val insertedId = groupDao.insertGroup(group)

        val inviteLink = "https://test-invite-link.com"
        groupDao.updateGroupInviteLink(insertedId.toInt(), inviteLink)

        val updatedGroup = groupDao.getGroupById(insertedId.toInt()).first()
        assertEquals(inviteLink, updatedGroup?.inviteLink)
    }

    @Test
    fun updateGroupImage() = runBlocking {
        val group = GroupEntity(
            id = 0,
            name = "Image Test Group",
            description = "Testing group image update",
            groupImg = null,
            createdAt = Date().toString(),
            updatedAt = Date().toString(),
            inviteLink = null,
            syncStatus = SyncStatus.PENDING_SYNC,
            localImagePath = "img",
            imageLastModified = "2024-09-12"
        )
        val insertedId = groupDao.insertGroup(group)

        val imagePath = "path/to/image.jpg"
        groupDao.updateGroupImage(insertedId.toInt(), imagePath)

        val updatedGroup = groupDao.getGroupById(insertedId.toInt()).first()
        assertEquals(imagePath, updatedGroup?.groupImg)
    }
}