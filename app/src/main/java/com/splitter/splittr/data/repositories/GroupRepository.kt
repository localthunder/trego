package com.splitter.splittr.data.repositories

import android.content.Context
import android.net.Uri
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toListItem
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.DataClasses.UserGroupListItem
import com.splitter.splittr.data.local.converters.LocalIdGenerator
import com.splitter.splittr.data.local.dao.GroupDao
import com.splitter.splittr.data.local.dao.GroupMemberDao
import com.splitter.splittr.data.local.dao.PaymentDao
import com.splitter.splittr.data.local.dao.PaymentSplitDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.local.dao.UserDao
import com.splitter.splittr.data.local.entities.GroupEntity
import com.splitter.splittr.data.local.entities.GroupMemberEntity
import com.splitter.splittr.data.model.Group
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.model.GroupMember
import com.splitter.splittr.data.sync.GroupSyncManager
import com.splitter.splittr.data.sync.SyncableRepository
import com.splitter.splittr.ui.screens.UserBalanceWithCurrency
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.ImageUtils
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.SyncUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    private val syncMetadataDao: SyncMetadataDao,
    private val apiService: ApiService,
    private val context: Context,
    private val dispatchers: CoroutineDispatchers,
    private val groupSyncManager: GroupSyncManager
    ) : SyncableRepository {

    override val entityType = "groups"
    override val syncPriority = 1 // High priority as other entities depend on groups

    // Result data class for image upload
    data class GroupImageUploadResult(
        val localFileName: String,
        val serverPath: String?,
        val message: String?,
        val needsSync: Boolean = false
    )

    fun getGroupListItems(userId: Int): Flow<List<UserGroupListItem>> =
        groupDao.getGroupsByUserId(userId)
            .map { groupEntities ->
                groupEntities.map { it.toListItem() }
            }
            .flowOn(dispatchers.io)


    fun getGroupById(groupId: Int): Flow<Group?> = flow {
        val localGroup = groupDao.getGroupById(groupId).first()?.toModel()

        // Try to load from local cache first
        localGroup?.groupImg?.let { serverPath ->
            if (ImageUtils.imageExistsLocally(context, serverPath)) {
                emit(localGroup)
            }
        }

        if (NetworkUtils.isOnline()) {
            try {
                val remoteGroup = apiService.getGroupById(groupId)

                remoteGroup.groupImg?.let { serverPath ->
                    checkAndUpdateLocalImage(groupId, serverPath, remoteGroup.updatedAt)
                }

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
                            Log.e(
                                "GroupRepository",
                                "Error fetching members for group ${group.id}",
                                e
                            )
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

                    Log.d(
                        "GroupRepository",
                        "Inserted ${allGroupMembers.size} members for all groups"
                    )
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

    suspend fun createGroupWithMember(
        group: Group,
        userId: Int
    ): Result<Pair<Group, GroupMember>> =
        withContext(dispatchers.io) {
            try {
                val currentTime = System.currentTimeMillis().toString()
                val localGroupId = LocalIdGenerator.nextId()

                // Create and insert local group
                val localGroupEntity = GroupEntity(
                    id = localGroupId,
                    serverId = null,
                    name = group.name,
                    description = group.description,
                    groupImg = group.groupImg,
                    localImagePath = null,
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    inviteLink = group.inviteLink,
                    syncStatus = SyncStatus.PENDING_SYNC
                )
                groupDao.insertGroup(localGroupEntity)

                // Create and insert local member
                val localGroupMember = GroupMemberEntity(
                    id = LocalIdGenerator.nextId(),
                    serverId = null,
                    groupId = localGroupId,
                    userId = userId,
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    removedAt = null,
                    syncStatus = SyncStatus.PENDING_SYNC
                )
                groupMemberDao.insertGroupMember(localGroupMember)

                if (NetworkUtils.isOnline()) {
                    // Sync with server immediately if online
                    val serverGroup = apiService.createGroup(group)
                    val serverMember =
                        apiService.addMemberToGroup(serverGroup.id, localGroupMember.toModel())

                    // Update local entries with server IDs
                    groupDao.runInTransaction {
                        groupDao.deleteGroup(localGroupId)
                        groupDao.insertGroup(
                            serverGroup.toEntity(SyncStatus.SYNCED).copy(
                                id = serverGroup.id,
                                serverId = serverGroup.id
                            )
                        )

                        groupMemberDao.deleteGroupMember(localGroupMember.id)
                        groupMemberDao.insertGroupMember(
                            serverMember.toEntity(SyncStatus.SYNCED).copy(
                                id = serverMember.id,
                                serverId = serverMember.id,
                                groupId = serverGroup.id
                            )
                        )
                    }

                    Result.success(Pair(serverGroup, serverMember))
                } else {
                    Result.success(Pair(localGroupEntity.toModel(), localGroupMember.toModel()))
                }
            } catch (e: Exception) {
                Log.e("GroupRepository", "Error in createGroupWithMember", e)
                Result.failure(e)
            }
        }

    companion object {
        private const val LOCAL_ID_THRESHOLD = 90000000
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

    private suspend fun checkAndUpdateLocalImage(groupId: Int, serverPath: String, lastModified: String) {
        try {
            val localPath = ImageUtils.getImageWithCaching(context, serverPath, lastModified)
            groupDao.updateLocalImagePath(groupId, localPath)
        } catch (e: Exception) {
            Log.e("GroupRepository", "Error caching image: ${e.message}")
        }
    }

    suspend fun handleGroupImageUpload(groupId: Int, imageUri: Uri): Result<GroupImageUploadResult> =
        withContext(dispatchers.io) {
            try {
                val imageData = ImageUtils.uriToByteArray(context, imageUri)
                    ?: return@withContext Result.failure(IOException("Failed to process image"))

                val localFileName = ImageUtils.saveImage(context, imageData)

                if (NetworkUtils.isOnline()) {
                    try {
                        val requestBody = imageData.toRequestBody(
                            "image/jpeg".toMediaTypeOrNull(),
                            0,
                            imageData.size
                        )

                        val imagePart = MultipartBody.Part.createFormData(
                            name = "group_img",
                            filename = "group_img.jpg",
                            body = requestBody
                        )

                        val response = apiService.uploadGroupImage(groupId, imagePart)

                        // Update local database with the new image path
                        response.imagePath?.let { path ->
                            groupDao.updateGroupImage(groupId, path)
                        }

                        Result.success(
                            GroupImageUploadResult(
                                localFileName = localFileName,
                                serverPath = response.imagePath,
                                message = response.message,
                                needsSync = false
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("GroupRepository", "Server upload failed, saving locally", e)
                        Result.success(
                            GroupImageUploadResult(
                                localFileName = localFileName,
                                serverPath = null,
                                message = "Image saved locally, will sync later",
                                needsSync = true
                            )
                        )
                    }
                } else {
                    Result.success(
                        GroupImageUploadResult(
                            localFileName = localFileName,
                            serverPath = null,
                            message = "Image saved locally, will sync when online",
                            needsSync = true
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    //Syncs pending image uploads to the server
    suspend fun syncPendingImageUploads() = withContext(dispatchers.io) {
        if (!NetworkUtils.isOnline()) {
            return@withContext
        }

        try {
            val groupsWithPendingImages = groupDao.getGroupsWithPendingImageSync()

            groupsWithPendingImages.forEach { group ->
                try {
                    val localImageFile = group.groupImg?.let {
                        ImageUtils.getImageFile(context, it)
                    }

                    if (localImageFile == null) {
                        groupDao.updateGroupImageSyncStatus(group.id, SyncStatus.SYNC_FAILED)
                        return@forEach
                    }

                    // Read the image file into a ByteArray
                    val imageData = localImageFile.readBytes()

                    // Create MultipartBody.Part for the image
                    val requestBody = imageData.toRequestBody(
                        "image/jpeg".toMediaTypeOrNull(),
                        0,
                        imageData.size
                    )
                    val imagePart = MultipartBody.Part.createFormData(
                        name = "image",
                        filename = "image.jpg",
                        body = requestBody
                    )

                    // Make the API call with MultipartBody.Part
                    val response = apiService.uploadGroupImage(group.id, imagePart)

                    // Handle the response from UploadResponse
                    if (response.imagePath != null) {
                        groupDao.updateGroupImage(group.id, response.imagePath)
                        groupDao.updateGroupImageSyncStatus(group.id, SyncStatus.SYNCED)
                    } else {
                        groupDao.updateGroupImageSyncStatus(group.id, SyncStatus.SYNC_FAILED)
                    }
                } catch (e: Exception) {
                    groupDao.updateGroupImageSyncStatus(group.id, SyncStatus.SYNC_FAILED)
                    Log.e("GroupRepository", "Failed to sync image for group ${group.id}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("GroupRepository", "Error syncing pending images", e)
        }
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

    override suspend fun sync() {
        groupSyncManager.performSync()
    }
}