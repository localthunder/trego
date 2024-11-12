package com.splitter.splittr.data.local.repositories

import SyncUtils
import android.content.Context
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.*
import com.splitter.splittr.data.local.entities.GroupEntity
import com.splitter.splittr.data.local.entities.GroupMemberEntity
import com.splitter.splittr.data.local.entities.PaymentEntity
import com.splitter.splittr.data.local.entities.PaymentSplitEntity
import com.splitter.splittr.data.local.entities.UserEntity
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.network.UploadResponsed
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.model.Group
import com.splitter.splittr.data.model.GroupMember
import com.splitter.splittr.data.model.User
import com.splitter.splittr.data.repositories.GroupRepository
import com.splitter.splittr.ui.screens.UserBalanceWithCurrency
import com.splitter.splittr.utils.AppCoroutineDispatchers
import com.splitter.splittr.utils.ImageUtils
import com.splitter.splittr.utils.NetworkUtils
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class GroupRepositoryTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var groupDao: GroupDao
    private lateinit var groupMemberDao: GroupMemberDao
    private lateinit var userDao: UserDao
    private lateinit var paymentDao: PaymentDao
    private lateinit var paymentSplitDao: PaymentSplitDao
    private lateinit var apiService: ApiService
    private lateinit var context: Context
    private lateinit var groupRepository: GroupRepository

    private val testDispatchers = AppCoroutineDispatchers()

    @Before
    fun setup() {
        groupDao = mockk()
        groupMemberDao = mockk()
        userDao = mockk()
        paymentDao = mockk()
        paymentSplitDao = mockk()
        apiService = mockk()
        context = mockk()

        mockkObject(NetworkUtils)
        mockkObject(ImageUtils)

        groupRepository = GroupRepository(groupDao, groupMemberDao, userDao, paymentDao, paymentSplitDao, apiService, context, testDispatchers)
    }

    @Test
    fun `getGroupById returns flow of specific group`() = runTest {
        val groupId = 1
        val mockGroup = GroupEntity(id = groupId, name = "Test Group", description = "Test Description", groupImg = null, createdAt = "2023-01-01", updatedAt = "2023-01-01", inviteLink = null)

        coEvery { groupDao.getGroupById(groupId) } returns flowOf(mockGroup)
        every { NetworkUtils.isOnline() } returns false

        val result = groupRepository.getGroupById(groupId)

        result.collect { group ->
            assertEquals(mockGroup.toModel(), group)
        }

        coVerify { groupDao.getGroupById(groupId) }
    }

    @Test
    fun `getGroupsByUserId returns flow of groups`() = runTest {
        val userId = 1
        val mockGroups = listOf(
            GroupEntity(id = 1, serverId = null, name = "Group 1", description = "Description 1", groupImg = null, createdAt = "2023-01-01", updatedAt = "2023-01-01", inviteLink = null, syncStatus = SyncStatus.PENDING_SYNC),
            GroupEntity(id = 2, serverId = null, name = "Group 2", description = "Description 2", groupImg = null, createdAt = "2023-01-02", updatedAt = "2023-01-02", inviteLink = null, syncStatus = SyncStatus.PENDING_SYNC)
        )

        coEvery { groupDao.getGroupsByUserId(userId) } returns flowOf(mockGroups)
        every { NetworkUtils.isOnline() } returns false

        // Mock SyncUtils
        mockkObject(SyncUtils)
        every { SyncUtils.isDataStale(any()) } returns false

        val result = groupRepository.getGroupsByUserId(userId)

        result.collect { groups ->
            assertEquals(mockGroups, groups)
        }

        coVerify { groupDao.getGroupsByUserId(userId) }
        verify { SyncUtils.isDataStale(any()) }
    }

    @Test
    fun `updateGroup updates group successfully`() = runTest {
        val group = Group(id = 1, name = "Updated Group", description = "Updated Description", groupImg = null, createdAt = "2023-01-01", updatedAt = "2023-01-02", inviteLink = null)

        every { NetworkUtils.isOnline() } returns true
        coEvery { groupDao.updateGroup(any()) } just Runs
        coEvery { apiService.updateGroup(1, group) } returns group

        val result = groupRepository.updateGroup(group)

        assertTrue(result.isSuccess)
        assertEquals(group, result.getOrNull())

        coVerify { groupDao.updateGroup(any()) }
        coVerify { apiService.updateGroup(1, group) }
    }

    @Test
    fun `createGroupWithMember creates group and adds member successfully`() = runTest {
        val userId = 1
        val group = Group(id = 0, name = "New Group", description = "New Description", groupImg = null, createdAt = "2023-01-01", updatedAt = "2023-01-01", inviteLink = null)
        val serverGroup = group.copy(id = 1)
        val groupMember = GroupMember(id = 1, groupId = 1, userId = userId, createdAt = "2023-01-01", updatedAt = "2023-01-01", removedAt = null)

        every { NetworkUtils.isOnline() } returns true
        coEvery { groupDao.insertGroup(any()) } returns 1L
        coEvery { groupMemberDao.insertGroupMember(any()) } returns 1L
        coEvery { apiService.createGroup(any()) } returns serverGroup
        coEvery { apiService.addMemberToGroup(1, any()) } returns groupMember
        coEvery { groupDao.updateGroup(any()) } just Runs
        coEvery { groupMemberDao.updateGroupMember(any()) } just Runs

        val result = groupRepository.createGroupWithMember(group, userId)

        assertTrue(result.isSuccess)
        val (resultGroup, resultMember) = result.getOrNull()!!
        assertEquals(serverGroup, resultGroup)
        assertEquals(groupMember, resultMember)

        coVerify { groupDao.insertGroup(any()) }
        coVerify { groupMemberDao.insertGroupMember(any()) }
        coVerify { apiService.createGroup(any()) }
        coVerify { apiService.addMemberToGroup(1, any()) }
    }

    @Test
    fun `addMemberToGroup adds member successfully`() = runTest {
        val groupId = 1
        val userId = 2
        val groupMember = GroupMember(id = 1, groupId = groupId, userId = userId, createdAt = "2023-01-01", updatedAt = "2023-01-01", removedAt = null)

        every { NetworkUtils.isOnline() } returns true
        coEvery { groupMemberDao.insertGroupMember(any()) } returns 1L
        coEvery { apiService.addMemberToGroup(groupId, any()) } returns groupMember
        coEvery { groupMemberDao.insertGroupMember(any()) } returns 1L

        val result = groupRepository.addMemberToGroup(groupId, userId)

        assertTrue(result.isSuccess)
        assertEquals(groupMember, result.getOrNull())

        coVerify { groupMemberDao.insertGroupMember(any()) }
        coVerify { apiService.addMemberToGroup(groupId, any()) }
    }

    @Test
    fun `getGroupMembers returns flow of group members`() = runTest {
        val groupId = 1
        val mockMembers = listOf(
            GroupMemberEntity(id = 1, groupId = groupId, userId = 1, createdAt = "2023-01-01", updatedAt = "2023-01-01", removedAt = null),
            GroupMemberEntity(id = 2, groupId = groupId, userId = 2, createdAt = "2023-01-02", updatedAt = "2023-01-02", removedAt = null)
        )

        coEvery { groupMemberDao.getMembersOfGroup(groupId) } returns flowOf(mockMembers)
        every { NetworkUtils.isOnline() } returns false

        val result = groupRepository.getGroupMembers(groupId)

        result.collect { members ->
            assertEquals(mockMembers, members)
        }

        coVerify { groupMemberDao.getMembersOfGroup(groupId) }
    }

    @Test
    fun `removeMemberFromGroup removes member successfully`() = runTest {
        val groupId = 1
        val userId = 2

        every { NetworkUtils.isOnline() } returns true
        coEvery { groupMemberDao.removeGroupMember(groupId, userId) } just Runs
        coEvery { apiService.removeMemberFromGroup(groupId, userId) } returns mockk()

        val result = groupRepository.removeMemberFromGroup(groupId, userId)

        assertTrue(result.isSuccess)

        coVerify { groupMemberDao.removeGroupMember(groupId, userId) }
        coVerify { apiService.removeMemberFromGroup(groupId, userId) }
    }

    @Test
    fun `getGroupInviteLink returns invite link successfully`() = runTest {
        val groupId = 1
        val inviteLink = "https://example.com/invite/123"

        every { NetworkUtils.isOnline() } returns true
        coEvery { apiService.getGroupInviteLink(groupId) } returns mapOf("inviteLink" to inviteLink)
        coEvery { groupDao.updateGroupInviteLink(groupId, inviteLink) } just Runs

        val result = groupRepository.getGroupInviteLink(groupId)

        assertTrue(result.isSuccess)
        assertEquals(inviteLink, result.getOrNull())

        coVerify { apiService.getGroupInviteLink(groupId) }
        coVerify { groupDao.updateGroupInviteLink(groupId, inviteLink) }
    }

    @Test
    fun `uploadGroupImage uploads image successfully`() = runTest {
        val groupId = 1
        val imagePath = "/path/to/image.jpg"
        val uri = mockk<Uri>()

        every { NetworkUtils.isOnline() } returns true
        every { ImageUtils.uriToByteArray(context, uri) } returns byteArrayOf(1, 2, 3)
        coEvery { apiService.uploadGroupImage(eq(groupId), any()) } returns UploadResponsed("Success", imagePath)

        val result = groupRepository.uploadGroupImage(groupId, uri)

        assertTrue(result.isSuccess)
        assertEquals(Pair(imagePath, "Success"), result.getOrNull())

        coVerify { apiService.uploadGroupImage(eq(groupId), any()) }
    }

    @Test
    fun `updateGroupImage updates group image successfully`() = runTest {
        val groupId = 1
        val imagePath = "/path/to/new_image.jpg"

        coEvery { groupDao.updateGroupImage(groupId, imagePath) } just Runs

        groupRepository.updateGroupImage(groupId, imagePath)

        coVerify { groupDao.updateGroupImage(groupId, imagePath) }
    }

    @Test
    fun `calculateGroupBalances calculates balances correctly`() = runTest {
        val groupId = 1
        val payments = listOf(
            mockk<PaymentEntity> {
                every { id } returns 1
                every { paidByUserId } returns 1
                every { amount } returns 100.0
                every { currency } returns "USD"
            }
        )
        val splits = listOf(
            mockk<PaymentSplitEntity> {
                every { userId } returns 2
                every { amount } returns 50.0
                every { currency } returns "USD"
            }
        )
        val users = listOf(
            UserEntity(userId = 1, username = "User1", email = "user1@example.com", passwordHash = "hash1", createdAt = "2023-01-01", updatedAt = "2023-01-01", appleId = "sdc", googleId = "sddsf", defaultCurrency = "USD", lastLoginDate = "2024-08-12"),
            UserEntity(userId = 2, username = "User2", email = "user2@example.com", passwordHash = "hash2", createdAt = "2023-01-01", updatedAt = "2023-01-01", appleId = "sdc", googleId = "sddsf", defaultCurrency = "USD", lastLoginDate = "2024-08-12")
        )

        coEvery { paymentDao.getNonArchivedPaymentsByGroup(groupId) } returns payments
        coEvery { paymentSplitDao.getNonArchivedSplitsByPayment(1) } returns splits
        coEvery { userDao.getUsersByIds(any()) } returns flowOf(users)

        val result = groupRepository.calculateGroupBalances(groupId)

        assertTrue(result.isSuccess)
        val balances = result.getOrNull()!!
        assertEquals(2, balances.size)
        assertEquals(-100.0, balances.find { it.userId == 1 }?.balances?.get("USD"))
        assertEquals(50.0, balances.find { it.userId == 2 }?.balances?.get("USD"))

        coVerify { paymentDao.getNonArchivedPaymentsByGroup(groupId) }
        coVerify { paymentSplitDao.getNonArchivedSplitsByPayment(1) }
        coVerify { userDao.getUsersByIds(any()) }
    }
}