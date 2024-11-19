package com.splitter.splittr.data.repositories

import android.content.Context
import android.net.Uri
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toListItem
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dataClasses.UserGroupListItem
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
import com.splitter.splittr.data.sync.managers.GroupMemberSyncManager
import com.splitter.splittr.ui.screens.UserBalanceWithCurrency
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.DateUtils
import com.splitter.splittr.utils.ImageUtils
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.SyncUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
    private val groupSyncManager: GroupSyncManager,
    private val groupMemberSyncManager: GroupMemberSyncManager
    ) : SyncableRepository {

    override val entityType = "groups"
    override val syncPriority = 1 // High priority as other entities depend on groups

    // Result data class for image upload
    data class GroupImageUploadResult(
        val localFileName: String,
        val serverPath: String?,
        val localPath: String?,
        val message: String?,
        val needsSync: Boolean = false
    )

    fun getGroupListItems(userId: Int): Flow<List<UserGroupListItem>> =
        groupDao.getGroupsByUserId(userId)
            .catch { e ->
                Log.e("GroupRepository", "Error in getGroupListItems", e)
                throw e
            }
            .map { groupEntities ->
                Log.d("GroupRepository", "Found ${groupEntities.size} groups for user $userId")
                groupEntities.map { it.toListItem() }
            }
            .flowOn(dispatchers.io)

    fun getGroupById(groupId: Int): Flow<Group?> = flow {
        val localGroup = groupDao.getGroupById(groupId).first()

        // If we have a local copy, emit it immediately
        if (localGroup != null) {
            emit(
                localGroup.toModel().copy(
                    groupImg = localGroup.localImagePath ?: localGroup.groupImg
                )
            )
        }

        if (NetworkUtils.isOnline()) {
            try {
                val remoteGroup = apiService.getGroupById(groupId)

                // Download the image if needed
                if (remoteGroup.groupImg != null && (localGroup?.localImagePath == null ||
                            localGroup.imageLastModified != remoteGroup.updatedAt)
                ) {
                    downloadAndSaveGroupImage(
                        localGroup?.copy(groupImg = remoteGroup.groupImg)
                            ?: remoteGroup.toEntity(SyncStatus.SYNCED)
                    )
                }

                val updatedGroup = remoteGroup.toEntity(SyncStatus.SYNCED).copy(
                    localImagePath = localGroup?.localImagePath
                )
                groupDao.insertGroup(updatedGroup)

                val finalGroup = groupDao.getGroupById(groupId).first()?.toModel()?.copy(
                    groupImg = localGroup?.localImagePath ?: remoteGroup.groupImg
                )

                if (finalGroup != null) {
                    emit(finalGroup)
                }
            } catch (e: Exception) {
                Log.e("GroupRepository", "Error fetching remote group", e)
                if (localGroup == null) throw e
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

    suspend fun createGroupWithMember(group: Group, userId: Int): Result<Pair<Group, GroupMember>> =
        withContext(dispatchers.io) {
            try {
                val currentTime = DateUtils.getCurrentTimestamp()

                if (NetworkUtils.isOnline()) {
                    // Create group on server first
                    val serverGroup = apiService.createGroup(group)
                    val serverMember = apiService.addMemberToGroup(
                        serverGroup.id, GroupMember(
                            id = 0,
                            groupId = serverGroup.id,
                            userId = userId,
                            createdAt = currentTime,
                            updatedAt = currentTime,
                            removedAt = null
                        )
                    )

                    // Insert into local database
                    groupDao.insertGroup(serverGroup.toEntity(SyncStatus.SYNCED))
                    groupMemberDao.insertGroupMember(serverMember.toEntity(SyncStatus.SYNCED))

                    Result.success(Pair(serverGroup, serverMember))
                } else {
                    // Handle offline case
                    val localGroupId = LocalIdGenerator.nextId()
                    val localGroup = GroupEntity(
                        id = localGroupId,
                        serverId = null,
                        name = group.name,
                        description = group.description,
                        groupImg = group.groupImg,
                        localImagePath = null,
                        imageLastModified = null,
                        createdAt = currentTime,
                        updatedAt = currentTime,
                        inviteLink = group.inviteLink,
                        syncStatus = SyncStatus.PENDING_SYNC
                    )

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

                    groupDao.insertGroup(localGroup)
                    groupMemberDao.insertGroupMember(localGroupMember)

                    Result.success(Pair(localGroup.toModel(), localGroupMember.toModel()))
                }
            } catch (e: Exception) {
                Log.e("GroupRepository", "Error in createGroupWithMember", e)
                Result.failure(e)
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

    suspend fun addMemberToGroup(groupId: Int, userId: Int): Result<GroupMember> =
        withContext(dispatchers.io) {
            val currentTime = DateUtils.getCurrentTimestamp()
            try {
                val groupMember = GroupMember(
                    id = 0,
                    groupId = groupId,
                    userId = userId,
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    removedAt = null
                )

                val localId =
                    groupMemberDao.insertGroupMember(groupMember.toEntity(SyncStatus.PENDING_SYNC))
                        .toInt()

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
        // Emit local data first
        val localMembers = groupMemberDao.getMembersOfGroup(groupId).first()
        emit(localMembers)

        if (NetworkUtils.isOnline()) {
            try {
                val remoteMembers = apiService.getMembersOfGroup(groupId)

                groupMemberDao.runInTransaction {
                    // Get all existing members for this group
                    val existingMembers = groupMemberDao.getMembersOfGroup(groupId).first()
                    val existingMemberIds = existingMembers.mapNotNull { it.serverId }.toSet()

                    // Process remote members
                    remoteMembers.forEach { remoteMember ->
                        val existingMember = groupMemberDao.getGroupMemberByServerId(remoteMember.id)

                        when {
                            existingMember == null -> {
                                // New member
                                groupMemberDao.insertGroupMember(remoteMember.toEntity(SyncStatus.SYNCED))
                            }
                            DateUtils.isUpdateNeeded(
                                serverTimestamp = remoteMember.updatedAt,
                                localTimestamp = existingMember.updatedAt,
                                entityId = "GroupMember-${remoteMember.id}"
                            ) -> {
                                // Update existing member
                                val updatedMember = remoteMember.toEntity(SyncStatus.SYNCED).copy(
                                    id = existingMember.id
                                )
                                groupMemberDao.updateGroupMemberDirect(updatedMember)
                            }
                        }
                    }

                    // Don't remove local members that are pending sync
                    val remoteIds = remoteMembers.map { it.id }.toSet()
                    existingMembers
                        .filter { it.syncStatus == SyncStatus.SYNCED && it.serverId !in remoteIds }
                        .forEach { member ->
                            // Optional: Instead of deleting, you could mark as removed
                            groupMemberDao.removeGroupMember(member.id)
                        }
                }

                // Emit updated members
                emit(groupMemberDao.getMembersOfGroup(groupId).first())
            } catch (e: Exception) {
                Log.e("GroupRepository", "Error fetching remote group members", e)
                throw e
            }
        }
    }

    suspend fun removeMemberFromGroup(memberId: Int): Result<Unit> = withContext(dispatchers.io) {
        try {
            groupMemberDao.removeGroupMember(memberId)
            if (NetworkUtils.isOnline()) {
                apiService.removeMemberFromGroup(memberId)
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

    private suspend fun checkAndUpdateLocalImage(
        groupId: Int,
        serverPath: String,
        lastModified: String
    ) {
        try {
            val localPath = ImageUtils.getImageWithCaching(context, serverPath, lastModified)
            groupDao.updateLocalImageInfo(groupId, localPath, lastModified)
        } catch (e: Exception) {
            Log.e("GroupRepository", "Error caching image: ${e.message}")
        }
    }


    suspend fun handleGroupImageUpload(groupId: Int, imageUri: Uri): Result<GroupImageUploadResult> =
        withContext(dispatchers.io) {
            try {
                val imageData = ImageUtils.uriToByteArray(context, imageUri)
                    ?: return@withContext Result.failure(IOException("Failed to process image"))

                // Save locally first
                val localFileName = ImageUtils.saveImage(context, imageData)
                val localPath = ImageUtils.getLocalImagePath(context, localFileName)
                val currentTime = DateUtils.getCurrentTimestamp()

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

                        // Upload image first
                        val imageResponse = apiService.uploadGroupImage(groupId, imagePart)

                        // Get fresh group data from server
                        val updatedGroup = apiService.getGroupById(groupId)

                        // Update local database
                        groupDao.runInTransaction {
                            val existingGroup = groupDao.getGroupById(groupId).first()
                            if (existingGroup != null) {
                                val newGroup = updatedGroup.toEntity(SyncStatus.SYNCED).copy(
                                    id = existingGroup.id,
                                    localImagePath = localPath,
                                    imageLastModified = currentTime,
                                    updatedAt = currentTime
                                )
                                groupDao.insertGroup(newGroup)
                            }
                        }

                        // Trigger sync managers to handle any related data
                        groupSyncManager.performSync()

                        Result.success(
                            GroupImageUploadResult(
                                localFileName = localFileName,
                                serverPath = imageResponse.imagePath,
                                localPath = localPath,
                                message = imageResponse.message,
                                needsSync = false
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("GroupRepository", "Server upload failed, saving locally", e)
                        // Mark for sync
                        groupDao.runInTransaction {
                            val currentGroup = groupDao.getGroupById(groupId).first()
                            if (currentGroup != null) {
                                groupDao.updateGroup(currentGroup.copy(
                                    syncStatus = SyncStatus.PENDING_SYNC,
                                    localImagePath = localPath,
                                    imageLastModified = currentTime,
                                    updatedAt = currentTime
                                ))
                            }
                        }

                        Result.success(
                            GroupImageUploadResult(
                                localFileName = localFileName,
                                serverPath = null,
                                localPath = localPath,
                                message = "Image saved locally, will sync later",
                                needsSync = true
                            )
                        )
                    }
                } else {
                    // Handle offline case - mark for sync
                    groupDao.runInTransaction {
                        val currentGroup = groupDao.getGroupById(groupId).first()
                        if (currentGroup != null) {
                            groupDao.updateGroup(currentGroup.copy(
                                syncStatus = SyncStatus.PENDING_SYNC,
                                localImagePath = localPath,
                                imageLastModified = currentTime,
                                updatedAt = currentTime
                            ))
                        }
                    }

                    Result.success(
                        GroupImageUploadResult(
                            localFileName = localFileName,
                            serverPath = null,
                            localPath = localPath,
                            message = "Image saved locally, will sync when online",
                            needsSync = true
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun ensureGroupImagesDownloaded() {
        if (!NetworkUtils.isOnline()) return

        try {
            val groups = groupDao.getAllGroups().first()
            for (group in groups) {
                if (group.groupImg != null && (group.localImagePath == null || group.imageLastModified == null)) {
                    downloadAndSaveGroupImage(group)
                }
            }
        } catch (e: Exception) {
            Log.e("GroupRepository", "Error ensuring group images downloaded", e)
        }
    }

    private suspend fun downloadAndSaveGroupImage(group: GroupEntity) {
        try {
            val imageUrl = ImageUtils.getFullImageUrl(group.groupImg) ?: return
            val localFileName = ImageUtils.downloadAndSaveImage(context, imageUrl) ?: return
            val localPath = ImageUtils.getLocalImagePath(context, localFileName)

            groupDao.updateLocalImageInfo(
                groupId = group.id,
                localPath = localPath,
                lastModified = DateUtils.getCurrentTimestamp()
            )
        } catch (e: Exception) {
            Log.e("GroupRepository", "Error downloading group image", e)
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
        groupMemberSyncManager.performSync()
    }
}