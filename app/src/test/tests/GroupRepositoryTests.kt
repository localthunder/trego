package com.splitter.splitter.tests

import GroupRepository
import android.content.Context
import com.splitter.splitter.data.local.dao.GroupDao
import com.splitter.splitter.data.local.dao.GroupMemberDao
import com.splitter.splitter.data.local.entities.GroupEntity
import com.splitter.splitter.data.local.entities.GroupMemberEntity
import com.splitter.splitter.data.network.ApiService
import com.splitter.splitter.model.Group
import com.splitter.splitter.utils.CoroutineDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test

class GroupRepositoryTest {

    @Mock
    private lateinit var groupDao: GroupDao

    @Mock
    private lateinit var groupMemberDao: GroupMemberDao

    @Mock
    private lateinit var apiService: ApiService

    private lateinit var groupRepository: GroupRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        groupRepository = GroupRepository(groupDao, groupMemberDao, apiService, mockContext(), mockDispatchers())
    }

    @Test
    fun `test getGroupById returns correct group`() = runBlocking {
        // Given
        val groupId = 1
        val groupEntity = GroupEntity(id = groupId, name = "Test Group", /* other fields */)
        `when`(groupDao.getGroupById(groupId)).thenReturn(flowOf(groupEntity))

        // When
        val result = groupRepository.getGroupById(groupId).first()

        // Then
        assert(result != null)
        assert(result?.id == groupId)
        assert(result?.name == "Test Group")
    }

    @Test
    fun `test getGroupMembers returns correct members`() = runBlocking {
        // Given
        val groupId = 1
        val members = listOf(
            GroupMemberEntity(id = 1, groupId = groupId, userId = 101, /* other fields */),
            GroupMemberEntity(id = 2, groupId = groupId, userId = 102, /* other fields */)
        )
        `when`(groupMemberDao.getMembersOfGroup(groupId)).thenReturn(flowOf(members))

        // When
        val result = groupRepository.getGroupMembers(groupId).first()

        // Then
        assert(result.size == 2)
        assert(result[0].userId == 101)
        assert(result[1].userId == 102)
    }

    @Test
    fun `test createGroup success`() = runBlocking {
        // Given
        val group = Group(id = 0, name = "New Group", /* other fields */)
        val createdGroup = group.copy(id = 1)
        `when`(apiService.createGroup(any())).thenReturn(createdGroup)
        `when`(groupDao.insertGroup(any())).thenReturn(1L)

        // When
        val result = groupRepository.createGroup(group)

        // Then
        assert(result.isSuccess)
        assert(result.getOrNull()?.id == 1)
    }

    // Helper functions
    private fun mockContext(): Context = mock(Context::class.java)
    private fun mockDispatchers() = object : CoroutineDispatchers {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
    }
}