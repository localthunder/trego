package com.splitter.splittr.data.local.repositories

import android.content.Context
import android.net.Uri
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.converters.LocalIdGenerator
import com.splitter.splittr.data.local.dao.GroupDao
import com.splitter.splittr.data.local.dao.GroupMemberDao
import com.splitter.splittr.data.local.dao.PaymentDao
import com.splitter.splittr.data.local.dao.PaymentSplitDao
import com.splitter.splittr.data.local.dao.UserDao
import com.splitter.splittr.data.local.entities.GroupEntity
import com.splitter.splittr.data.local.entities.GroupMemberEntity
import com.splitter.splittr.model.Group
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.model.GroupMember
import com.splitter.splittr.ui.screens.UserBalanceWithCurrency
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.ImageUtils
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
class GroupRepository(
    private val groupDao: GroupDao,
    private val groupMemberDao: GroupMemberDao,
    private val userDao: UserDao,
    private val paymentDao: PaymentDao,
    private val paymentSplitDao: PaymentSplitDao,
    private val apiService: ApiService,
    private val context: Context,
    private val dispatchers: CoroutineDispatchers
) {
    fun getGroupById(groupId: Int): Flow<Group?> = flow {
        val localGroup = groupDao.getGroupById(groupId).first()?.toModel()
        emit(localGroup)

        if (NetworkUtils.isOnline()) {
            try {
                val remoteGroup = apiService.getGroupById(groupId)
                groupDao.insertGroup(remoteGroup.toEntity(SyncStatus.SYNCED))
                val updatedLocalGroup = groupDao.getGroupById(groupId).first()?.toModel()
                if (updatedLocalGroup != localGroup) {
                    emit(updatedLocalGroup)
                }
            } catch (e: Exception) {
                Log.e("GroupRepository", "Error fetching remote group", e)
            }
        }
    }.flowOn(dispatchers.io)

    fun getGroupsByUserId(userId: Int, forceRefresh: Boolean = false) = flow {
        Log.d("GroupRepository", "Fetching groups for user $userId")
        val localGroups = groupDao.getGroupsByUserId(userId).first()
        Log.d("GroupRepository", "Local groups: ${localGroups.size}")
        emit(localGroups)

        if (forceRefresh || SyncUtils.isDataStale("user_groups_$userId")) {
            if (NetworkUtils.isOnline()) {
                try {
                    val remoteGroups = apiService.getGroupsByUserId(userId)
                    Log.d("GroupRepository", "Remote groups: ${remoteGroups.size}")

                    // Insert all groups first
                    remoteGroups.forEach { group ->
                        groupDao.insertGroup(group.toEntity(SyncStatus.SYNCED))
                    }

                    // Fetch all members for all groups
                    val allGroupMembers = remoteGroups.flatMap { group ->
                        try {
                            apiService.getMembersOfGroup(group.id)
                        } catch (e: Exception) {
                            Log.e("GroupRepository", "Error fetching members for group ${group.id}", e)
                            emptyList()
                        }
                    }

                    // Insert all users referenced by group members
                    val userIds = allGroupMembers.map { it.userId }.distinct()
                    userIds.forEach { userId ->
                        try {
                            val user = apiService.getUserById(userId)
                            userDao.insertUser(user.toEntity(SyncStatus.SYNCED))
                        } catch (e: Exception) {
                            Log.e("GroupRepository", "Error fetching user $userId", e)
                        }
                    }

                    // Now insert all group members
                    allGroupMembers.forEach { member ->
                        groupMemberDao.insertGroupMember(member.toEntity(SyncStatus.SYNCED))
                    }

                    Log.d("GroupRepository", "Inserted ${allGroupMembers.size} members for all groups")
                    val updatedGroups = groupDao.getGroupsByUserId(userId).first()
                    Log.d("GroupRepository", "Updated groups: ${updatedGroups.size}")
                    emit(updatedGroups)
                    SyncUtils.updateLastSyncTimestamp("user_groups_$userId")
                } catch (e: Exception) {
                    Log.e("GroupRepository", "Error fetching remote groups for user", e)
                    throw e  // Rethrow the exception to be handled by the caller
                }
            } else {
                Log.w("GroupRepository", "No network connection available")
            }
        }
    }.flowOn(dispatchers.io)

    suspend fun createGroupWithMember(group: Group, userId: Int): Result<Pair<Group, GroupMember>> = withContext(dispatchers.io) {
        try {
            val currentTime = System.currentTimeMillis().toString()

            val localGroupId = LocalIdGenerator.nextId()

            // Create the GroupEntity with the generated ID
            val localGroupEntity = GroupEntity(
                id = localGroupId,
                name = group.name,
                description = group.description,
                groupImg = group.groupImg,
                createdAt = currentTime,
                updatedAt = currentTime,
                inviteLink = group.inviteLink,
                syncStatus = SyncStatus.PENDING_SYNC
            )

            // Insert the group into the database
            groupDao.insertGroup(localGroupEntity)

            // 3. Create the local group member with the sync status PENDING_SYNC
            val localGroupMember = GroupMemberEntity(
                groupId = localGroupId,
                userId = userId,
                createdAt = currentTime,
                updatedAt = currentTime,
                removedAt = null,
                syncStatus = SyncStatus.PENDING_SYNC  // Set the sync status
            )

            // 4. Insert the group member locally
            groupMemberDao.insertGroupMember(localGroupMember)

            // 4. If online, sync the group and group member with the server
            if (NetworkUtils.isOnline()) {
                // Sync group to the server
                val serverGroup = apiService.createGroup(group)

                // Update the local group to mark it as synced
                groupDao.updateGroup(serverGroup.toEntity(SyncStatus.SYNCED))

                // Sync group member to the server
                val serverMember = apiService.addMemberToGroup(serverGroup.id, localGroupMember.toModel())

                // Update the local group member to mark it as synced
                groupMemberDao.updateGroupMember(serverMember.toEntity(SyncStatus.SYNCED))

                return@withContext Result.success(Pair(serverGroup, serverMember))
            } else {
                // Fetch the locally stored group as a non-Flow object
                val localGroupEntity = groupDao.getGroupById(localGroupId).firstOrNull()
                val localGroup = localGroupEntity?.toModel()

                // Ensure that the group exists locally and return the result
                return@withContext if (localGroup != null) {
                    Result.success(Pair(localGroup, localGroupMember.toModel()))
                } else {
                    Result.failure(IllegalStateException("Local group not found"))
                }
            }
        } catch (e: Exception) {
            Log.e("GroupRepository", "Error in createGroupWithMember", e)
            return@withContext Result.failure(e)
        }
    }

    suspend fun updateGroup(group: Group): Result<Group> = withContext(dispatchers.io) {
        try {
            groupDao.updateGroup(group.toEntity(SyncStatus.PENDING_SYNC))
            if (NetworkUtils.isOnline()) {
                val serverGroup = apiService.updateGroup(group.id, group)
                groupDao.updateGroup(serverGroup.toEntity(SyncStatus.SYNCED))
                Result.success(serverGroup)
            } else {
                Result.success(group)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addMemberToGroup(groupId: Int, userId: Int): Result<GroupMember> = withContext(dispatchers.io) {
        try {
            val groupMember = GroupMember(
                id = 0,
                groupId = groupId,
                userId = userId,
                createdAt = System.currentTimeMillis().toString(),
                updatedAt = System.currentTimeMillis().toString(),
                removedAt = null
            )

            val localId = groupMemberDao.insertGroupMember(groupMember.toEntity(SyncStatus.PENDING_SYNC)).toInt()

            if (NetworkUtils.isOnline()) {
                val serverGroupMember = apiService.addMemberToGroup(groupId, groupMember)
                val updatedGroupMember = serverGroupMember.copy(id = localId)
                groupMemberDao.insertGroupMember(updatedGroupMember.toEntity(SyncStatus.SYNCED))
                Result.success(updatedGroupMember)
            } else {
                Result.success(groupMember.copy(id = localId))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getGroupMembers(groupId: Int): Flow<List<GroupMemberEntity>> = flow {
        Log.d("GroupRepository", "getGroupMembers called with groupId: $groupId")

        // Emit local data first
        val localMembers = groupMemberDao.getMembersOfGroup(groupId).first()
        Log.d("GroupRepository", "Fetched ${localMembers.size} local group members")
        emit(localMembers)

        // Fetch remote data if online
        if (NetworkUtils.isOnline()) {
            try {
                val remoteMembers = apiService.getMembersOfGroup(groupId)
                Log.d("GroupRepository", "Fetched ${remoteMembers.size} remote group members")

                // Update local database
                remoteMembers.forEach { member ->
                    groupMemberDao.insertGroupMember(member.toEntity(SyncStatus.SYNCED))
                }

                // Emit updated local data
                val updatedLocalMembers = groupMemberDao.getMembersOfGroup(groupId).first()
                Log.d("GroupRepository", "Emitting ${updatedLocalMembers.size} updated group members")
                emit(updatedLocalMembers)
            } catch (e: Exception) {
                Log.e("GroupRepository", "Error fetching remote group members", e)
            }
        }
    }.flowOn(dispatchers.io)

    suspend fun removeMemberFromGroup(groupId: Int, userId: Int): Result<Unit> = withContext(dispatchers.io) {
        try {
            groupMemberDao.removeGroupMember(groupId, userId)
            if (NetworkUtils.isOnline()) {
                apiService.removeMemberFromGroup(groupId, userId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGroupInviteLink(groupId: Int): Result<String> = withContext(dispatchers.io) {
        try {
            if (NetworkUtils.isOnline()) {
                val linkMap = apiService.getGroupInviteLink(groupId)
                val inviteLink = linkMap["inviteLink"] ?: ""
                groupDao.updateGroupInviteLink(groupId, inviteLink)
                Result.success(inviteLink)
            } else {
                val localGroup = groupDao.getGroupById(groupId).first()
                Result.success(localGroup?.inviteLink ?: "")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncGroups() = withContext(dispatchers.io) {

        val userId = getUserIdFromPreferences(context)

        if (NetworkUtils.isOnline()) {
            // 1. Sync local unsaved changes to the server
            groupDao.getUnsyncedGroups().first().forEach { groupEntity ->
                try {
                    val serverGroup = if (groupEntity.serverId == null) {
                        groupEntity.toModel()?.let { apiService.createGroup(it) }
                    } else {
                        groupEntity.toModel()?.let { apiService.updateGroup(groupEntity.serverId, it) }
                    }
                    if (serverGroup != null) {
                        groupDao.updateGroup(serverGroup.toEntity(SyncStatus.SYNCED))
                    }
                } catch (e: Exception) {
                    groupDao.updateGroupSyncStatus(groupEntity.id, SyncStatus.SYNC_FAILED)
                    Log.e("GroupRepository", "Failed to sync group ${groupEntity.id}", e)
                }
            }

            // 2. Fetch groups from the server
            try {
                val serverGroups = userId?.let { apiService.getGroupsByUserId(it) }
                serverGroups?.forEach { serverGroup ->
                    val localGroup = groupDao.getGroupById(serverGroup.id).first()
                    if (localGroup == null) {
                        // New group from server, insert it
                        groupDao.insertGroup(serverGroup.toEntity(SyncStatus.SYNCED))
                    } else {
                        // Update existing group
                        groupDao.updateGroup(serverGroup.toEntity(SyncStatus.SYNCED))
                    }
                }
            } catch (e: Exception) {
                Log.e("GroupRepository", "Failed to fetch groups from server", e)
            }
        } else {
            Log.e("GroupRepository", "No internet connection available for syncing groups")
        }
    }

    suspend fun syncGroupMembers() = withContext(dispatchers.io) {
        val userId = getUserIdFromPreferences(context)

        if (NetworkUtils.isOnline()) {
            // 1. Sync local unsaved changes to the server
            groupMemberDao.getUnsyncedGroupMembers().first().forEach { groupMemberEntity ->
                try {
                    val serverGroupMember = if (groupMemberEntity.serverId == null) {
                        // New member: Add to the server and return the result
                        groupMemberEntity.toModel()?.let { apiService.addMemberToGroup(groupMemberEntity.groupId, it) }
                    } else {
                        // Existing member: Sync changes to the server (e.g., update existing group member)
                        apiService.updateGroupMember(groupMemberEntity.serverId, groupMemberEntity.toModel())
                    }

                    // Check if the server response is valid and then update the local database
                    serverGroupMember?.let {
                        // Explicitly specify the type of `toEntity` to resolve type inference issues
                        val updatedEntity = it.toEntity(SyncStatus.SYNCED)
                        groupMemberDao.updateGroupMember(updatedEntity)
                    }
                } catch (e: Exception) {
                    groupMemberDao.updateGroupMemberSyncStatus(groupMemberEntity.id, SyncStatus.SYNC_FAILED)
                    Log.e("GroupMemberRepository", "Failed to sync group member ${groupMemberEntity.id}", e)
                }
            }
            // 2. Fetch group members from the server and associated users
            try {
                val serverGroups = userId?.let { apiService.getGroupsByUserId(it) }

                serverGroups?.forEach { serverGroup ->
                    val groupId = serverGroup.id

                    // Fetch group members for this group
                    val serverGroupMembers = apiService.getMembersOfGroup(groupId)

                    serverGroupMembers.forEach { serverGroupMember ->

                        // Sync associated user (if not already present)
                        val associatedUser = apiService.getUserById(serverGroupMember.userId)  // Assuming an API call to fetch user info
                        associatedUser?.let { user ->
                            val localUser = userDao.getUserById(user.userId).firstOrNull()
                            if (localUser == null) {
                                userDao.insertUser(user.toEntity())  // Insert user into associated users table
                            } else {
                                userDao.updateUser(user.toEntity())  // Update existing user
                            }
                        }

                        val localGroupMember = groupMemberDao.getGroupMemberById(serverGroupMember.id).firstOrNull()

                        if (localGroupMember == null) {
                            groupMemberDao.insertGroupMember(serverGroupMember.toEntity(SyncStatus.SYNCED))
                        } else {
                            groupMemberDao.updateGroupMember(serverGroupMember.toEntity(SyncStatus.SYNCED))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GroupMemberRepository", "Failed to fetch group members or users from server", e)
            }
        } else {
            Log.e("GroupMemberRepository", "No internet connection available for syncing group members")
        }
    }

    suspend fun uploadGroupImage(groupId: Int, imageUri: Uri): Result<Pair<String?, String?>> = withContext(dispatchers.io) {
        try {
            if (NetworkUtils.isOnline()) {
                val imageData = ImageUtils.uriToByteArray(context, imageUri)
                    ?: throw IOException("Failed to convert URI to ByteArray")

                val requestFile = imageData.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData(
                    name = "group_img",
                    filename = "image_${System.currentTimeMillis()}.jpg",
                    body = requestFile
                )

                val responseBody = apiService.uploadGroupImage(groupId, body)
                Result.success(Pair(responseBody.imagePath, responseBody.message))
            } else {
                Result.failure(IOException("No internet connection"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGroupImage(groupId: Int, imagePath: String) = withContext(dispatchers.io) {
        groupDao.updateGroupImage(groupId, imagePath)
    }

    suspend fun calculateGroupBalances(groupId: Int): Result<List<UserBalanceWithCurrency>> = withContext(dispatchers.io) {
        try {
            val payments = paymentDao.getNonArchivedPaymentsByGroup(groupId)
            val balances = mutableMapOf<Int, MutableMap<String, Double>>()

            payments.forEach { payment ->
                val paidByUserId = payment.paidByUserId
                val amount = payment.amount * -1

                balances.getOrPut(paidByUserId) { mutableMapOf() }
                balances[paidByUserId]!!.merge(payment.currency!!, amount, Double::plus)

                val splits = paymentSplitDao.getNonArchivedSplitsByPayment(payment.id)
                splits.forEach { split ->
                    val userId = split.userId
                    val splitAmount = split.amount

                    balances.getOrPut(userId) { mutableMapOf() }
                    balances[userId]!!.merge(split.currency, splitAmount, Double::plus)
                }
            }

            val userIds = balances.keys.toList()
            val users = userDao.getUsersByIds(userIds).first()

            val result = users.map { user ->
                UserBalanceWithCurrency(
                    userId = user.userId,
                    username = user.username,
                    balances = balances[user.userId] ?: emptyMap()
                )
            }

            Result.success(result)
        } catch (e: Exception) {
            Log.e("GroupRepository", "Error calculating group balances", e)
            Result.failure(e)
        }
    }
}