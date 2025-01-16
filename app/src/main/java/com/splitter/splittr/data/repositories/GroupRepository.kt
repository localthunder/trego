package com.splitter.splittr.data.repositories

import android.content.ContentValues.TAG
import android.content.Context
import android.net.Uri
import android.util.Log
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toListItem
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dataClasses.UserGroupListItem
import com.splitter.splittr.data.local.dao.GroupDao
import com.splitter.splittr.data.local.dao.GroupMemberDao
import com.splitter.splittr.data.local.dao.PaymentDao
import com.splitter.splittr.data.local.dao.PaymentSplitDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.local.dao.UserDao
import com.splitter.splittr.data.local.dao.UserGroupArchiveDao
import com.splitter.splittr.data.local.entities.GroupEntity
import com.splitter.splittr.data.local.entities.GroupMemberEntity
import com.splitter.splittr.data.local.entities.UserGroupArchiveEntity
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
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    private val userGroupArchiveDao: UserGroupArchiveDao,
    private val apiService: ApiService,
    private val context: Context,
    private val dispatchers: CoroutineDispatchers,
    private val groupSyncManager: GroupSyncManager,
    private val groupMemberSyncManager: GroupMemberSyncManager
    ) : SyncableRepository {

    override val entityType = "groups"
    override val syncPriority = 1 // High priority as other entities depend on groups

    val myApplication = context.applicationContext as MyApplication

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
        try {
            val localGroup = groupDao.getGroupById(groupId).first()
            if (localGroup != null) {
                emit(localGroup.toModel().copy(
                    groupImg = localGroup.localImagePath ?: localGroup.groupImg
                ))
            }

            if (NetworkUtils.isOnline()) {
                try {
                    val remoteGroup = withTimeout(5000) { // Add timeout
                        apiService.getGroupById(localGroup?.serverId ?: 0)
                    }

                    if (remoteGroup.groupImg != null) {
                        try {
                            downloadAndSaveGroupImage(
                                localGroup?.copy(groupImg = remoteGroup.groupImg)
                                    ?: remoteGroup.toEntity(SyncStatus.SYNCED)
                            )
                        } catch (e: Exception) {
                            Log.e("GroupRepository", "Failed to download image", e)
                            // Continue without image
                        }
                    }

                    val updatedGroup = remoteGroup.toEntity(SyncStatus.SYNCED).copy(
                        localImagePath = localGroup?.localImagePath
                    )
                    groupDao.insertGroup(updatedGroup)

                    emit(updatedGroup.toModel().copy(
                        groupImg = localGroup?.localImagePath ?: remoteGroup.groupImg
                    ))
                } catch (e: Exception) {
                    Log.e("GroupRepository", "Server error", e)
                    // Don't throw if we have local data
                    if (localGroup == null) {
                        emit(null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GroupRepository", "Error in getGroupById", e)
            emit(null)
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
                    val localUser = userDao.getUserByIdDirect(userId)
                    val remoteGroups = apiService.getGroupsByUserId(localUser?.serverId ?: 0)
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
    ): Result<Pair<Group, GroupMember>> = withContext(dispatchers.io) {
        try {
            Log.d("GroupRepository", "Starting createGroupWithMember with userId: $userId")

            // Verify user exists
            val existingUser = userDao.getUserByIdSync(userId)
            Log.d("GroupRepository", "Found user in database: ${existingUser != null}, user details: $existingUser")

            val currentTime = DateUtils.getCurrentTimestamp()

            val localGroup = GroupEntity(
                id = 0,
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

            val (createdGroup, createdMember) = groupDao.runInTransaction {
                Log.d("GroupRepository", "Starting transaction for group and member creation")

                val generatedGroupId = groupDao.insertGroup(localGroup).toInt()
                Log.d("GroupRepository", "Generated group ID: $generatedGroupId")

                val groupEntity = localGroup.copy(id = generatedGroupId)

                val localGroupMember = GroupMemberEntity(
                    id = 0,
                    serverId = null,
                    groupId = generatedGroupId,
                    userId = userId,
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    removedAt = null,
                    syncStatus = SyncStatus.PENDING_SYNC
                )
                Log.d("GroupRepository", "Attempting to insert group member with: groupId=$generatedGroupId, userId=$userId",)

                val generatedMemberId = groupMemberDao.insertGroupMember(localGroupMember).toInt()
                Log.d("GroupRepository", "Generated member ID: $generatedMemberId")

                val memberEntity = localGroupMember.copy(id = generatedMemberId)

                Pair(groupEntity, memberEntity)
            }
            Log.d("GroupRepository", "Successfully created local group and member")

            if (NetworkUtils.isOnline()) {
                try {
                    val userEntity = userDao.getUserByIdSync(userId)
                    Log.d("GroupRepository", "Found user for server sync: ${userEntity?.userId}, serverId: ${userEntity?.serverId}")

                    val serverUserId = userEntity?.serverId ?: run {
                        Log.e("GroupRepository", "No server ID found for user $userId")
                        return@withContext Result.failure(Exception("Cannot sync: No server ID found for user"))
                    }

                    Log.d("GroupRepository", "Creating group on server")
                    val serverGroup = apiService.createGroup(createdGroup.toModel())
                    Log.d("GroupRepository", "Server returned group with ID: ${serverGroup.id}")

                    val serverMemberRequest = GroupMember(
                        id = 0,
                        groupId = serverGroup.id,
                        userId = serverUserId,
                        createdAt = currentTime,
                        updatedAt = currentTime,
                        removedAt = null
                    )
                    Log.d("GroupRepository", "Creating member on server with groupId: ${serverGroup.id}, userId: $serverUserId")

                    val serverMember = apiService.addMemberToGroup(serverGroup.id, serverMemberRequest)
                    Log.d("GroupRepository", "Server returned member with ID: ${serverMember.id}")

                    groupDao.runInTransaction {
                        Log.d("GroupRepository", "Updating local entries with server IDs - group: ${serverGroup.id}, member: ${serverMember.id}")

                        groupDao.updateGroupDirect(createdGroup.copy(
                            serverId = serverGroup.id,
                            syncStatus = SyncStatus.SYNCED
                        ))
                        groupMemberDao.updateGroupMember(createdMember.copy(
                            serverId = serverMember.id,
                            removedAt = serverMember.removedAt,
                            syncStatus = SyncStatus.SYNCED
                        ))
                    }

                    Result.success(Pair(createdGroup.toModel(), createdMember.toModel()))
                } catch (e: Exception) {
                    Log.e("GroupRepository", "Server sync failed", e)
                    Result.success(Pair(createdGroup.toModel(), createdMember.toModel()))
                }
            } else {
                Log.d("GroupRepository", "Device offline, returning local versions")
                Result.success(Pair(createdGroup.toModel(), createdMember.toModel()))
            }
        } catch (e: Exception) {
            Log.e("GroupRepository", "Error in createGroupWithMember", e)
            Log.e("GroupRepository", "Stack trace: ${e.stackTrace.joinToString("\n")}")
            Result.failure(e)
        }
    }

    suspend fun updateGroup(group: Group): Result<Group> = withContext(dispatchers.io) {
        try {
            // First save locally with pending sync status
            val localEntity = group.toEntity(SyncStatus.PENDING_SYNC)
            groupDao.updateGroup(localEntity)

            if (NetworkUtils.isOnline()) {
                // Convert the local entity to server model
                val serverModel = myApplication.entityServerConverter.convertGroupToServer(localEntity)
                    .getOrThrow() // This will throw if conversion fails

                // Make API call with converted model
                val serverGroup = apiService.updateGroup(serverModel.id, serverModel)

                // Update local database with server response
                groupDao.updateGroup(serverGroup.toEntity(SyncStatus.SYNCED))
                Result.success(serverGroup)
            } else {
                Result.success(group)
            }
        } catch (e: Exception) {
            Log.e("GroupRepository", "Error updating group", e)
            Result.failure(e)
        }
    }

    suspend fun addMemberToGroup(
        groupId: Int,
        userId: Int
    ): Result<GroupMember> = withContext(dispatchers.io) {
        val currentTime = DateUtils.getCurrentTimestamp()

        try {
            // Get the local group to find its server ID
            val group = groupDao.getGroupByIdSync(groupId)
                ?: return@withContext Result.failure(Exception("Group not found"))

            // Get the local user to find its server ID
            val user = userDao.getUserByIdSync(userId)
                ?: return@withContext Result.failure(Exception("User not found"))


            val groupMember = GroupMember(
                id = 0,
                groupId = groupId,
                userId = userId,
                createdAt = currentTime,
                updatedAt = currentTime,
                removedAt = null
            )

            try {
                val localId = groupMemberDao.insertGroupMember(
                    groupMember.toEntity(SyncStatus.PENDING_SYNC)
                ).toInt()

                if (NetworkUtils.isOnline()) {
                    try {
                        // Create server request using server IDs
                        val serverMemberRequest = GroupMember(
                            id = 0,
                            groupId = group.serverId ?: 0,
                            userId = user.serverId ?: 0,
                            createdAt = currentTime,
                            updatedAt = currentTime,
                            removedAt = null
                        )

                        val serverGroupMember = apiService.addMemberToGroup(group.serverId ?: 0, serverMemberRequest)
                        val updatedGroupMember = serverGroupMember.copy(id = localId)
                        groupMemberDao.insertGroupMember(
                            updatedGroupMember.toEntity(SyncStatus.SYNCED)
                        )
                        Result.success(updatedGroupMember)
                    } catch (e: Exception) {
                        Log.e("GroupRepository", "Server sync failed for group member", e)
                        // Return local version if server sync fails
                        Result.success(groupMember.copy(id = localId))
                    }
                } else {
                    Result.success(groupMember.copy(id = localId))
                }
            } catch (e: Exception) {
                Log.e("GroupRepository", "Database insertion failed", e)
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.e("GroupRepository", "Error in addMemberToGroup", e)
            Result.failure(e)
        }
    }

    fun getGroupMembers(groupId: Int): Flow<List<GroupMemberEntity>> = flow {
        // Emit local data first
        val localMembers = groupMemberDao.getMembersOfGroup(groupId).first()
        emit(localMembers)

        if (NetworkUtils.isOnline()) {
            try {
                // Get the server group ID
                val group = groupDao.getGroupByIdSync(groupId)
                    ?: throw Exception("Group not found")
                val serverGroupId = group.serverId
                    ?: throw Exception("Group has no server ID")

                // Fetch members from server
                val remoteMembers = apiService.getMembersOfGroup(serverGroupId)

                groupMemberDao.runInTransaction {
                    // Process each remote member
                    remoteMembers.forEach { remoteMember ->
                        val convertedMember = myApplication.entityServerConverter
                            .convertGroupMemberFromServer(remoteMember)
                            .getOrElse {
                                Log.e("GroupRepository", "Failed to convert member", it)
                                return@forEach
                            }

                        val existingMember = groupMemberDao.getGroupMemberByServerId(remoteMember.id)

                        if (existingMember == null) {
                            groupMemberDao.insertGroupMember(convertedMember)
                        } else {
                            groupMemberDao.updateGroupMemberDirect(convertedMember.copy(id = existingMember.id))
                        }
                    }
                }

                emit(groupMemberDao.getMembersOfGroup(groupId).first())
            } catch (e: Exception) {
                Log.e("GroupRepository", "Error fetching members", e)
                // Don't throw - we still have valid local data
            }
        }
    }

    suspend fun removeMemberFromGroup(memberId: Int): Result<Unit> = withContext(dispatchers.io) {
        try {
            groupMemberDao.removeGroupMember(memberId)
            if (NetworkUtils.isOnline()) {
                val localMember = groupMemberDao.getGroupMemberByIdSync(memberId)
                (localMember?.serverId)?.let {
                    apiService.removeMemberFromGroup(it)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGroupInviteLink(groupId: Int): Result<String> = withContext(dispatchers.io) {
        try {
            // First try to get the invite link from local database
            val localGroup = groupDao.getGroupById(groupId).first()
            val localInviteLink = localGroup?.inviteLink

            if (!localInviteLink.isNullOrBlank()) {
                // If we have a valid local invite link, return it immediately
                return@withContext Result.success(localInviteLink)
            }

            // Only if we don't have a local invite link and we're online, try to fetch from API
            if (NetworkUtils.isOnline()) {
                try {
                    val localGroup = groupDao.getGroupByIdSync(groupId)
                    val linkMap = localGroup?.serverId?.let { apiService.getGroupInviteLink(it) }
                    val inviteLink = linkMap?.get("inviteLink") ?: ""
                    if (inviteLink.isNotBlank()) {
                        // Update local database with the new invite link
                        groupDao.updateGroupInviteLink(groupId, inviteLink)
                    }
                    Result.success(inviteLink)
                } catch (e: Exception) {
                    // If API call fails, still return empty string rather than failure
                    Result.success("")
                }
            } else {
                // If offline and no local invite link, return empty string
                Result.success("")
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

    suspend fun archiveGroup(groupId: Int): Result<Unit> = withContext(dispatchers.io) {
        try {
            val userId = getUserIdFromPreferences(context) ?:
            return@withContext Result.failure(Exception("User ID not found"))
            val timestamp = DateUtils.getCurrentTimestamp()

            // Create and save local archive entry
            val archiveEntry = UserGroupArchiveEntity(
                userId = userId,
                groupId = groupId,
                archivedAt = timestamp,
                syncStatus = SyncStatus.PENDING_SYNC
            )
            userGroupArchiveDao.insertArchive(archiveEntry)
            Log.d(TAG, "Group $groupId archived locally for user $userId at $timestamp")

            // If online, attempt server sync
            if (NetworkUtils.isOnline()) {
                try {
                    // Convert local IDs to server IDs
                    val serverIds = myApplication.entityServerConverter.convertUserGroupArchiveToServer(archiveEntry)
                        .getOrElse {
                            Log.e(TAG, "Failed to convert IDs for server sync", it)
                            return@withContext Result.success(Unit) // Still succeed as local archive worked
                        }

                    // Attempt server sync
                    apiService.archiveGroup(
                        groupId = serverIds["group_id"] as Int,
                        userId = serverIds["user_id"] as Int
                    )

                    // Update sync status on success
                    userGroupArchiveDao.updateSyncStatus(userId, groupId, SyncStatus.SYNCED)
                    Log.d(TAG, "Group $groupId archived on server successfully for user $userId")
                } catch (e: Exception) {
                    // Handle server sync failure
                    Log.e(TAG, "Failed to sync archived group $groupId with server for user $userId", e)
                    userGroupArchiveDao.updateSyncStatus(userId, groupId, SyncStatus.SYNC_FAILED)
                    // Don't return failure as local archive was successful
                }
            } else {
                Log.d(TAG, "Device offline, group $groupId archived locally only for user $userId")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error archiving group $groupId", e)
            Result.failure(e)
        }
    }

    suspend fun restoreGroup(groupId: Int): Result<Unit> = withContext(dispatchers.io) {
        try {
            val userId = getUserIdFromPreferences(context) ?:
            return@withContext Result.failure(Exception("User ID not found"))

            // Get the existing archive entry before deleting it (for server ID conversion)
            val existingArchive = userGroupArchiveDao.getArchive(userId, groupId).firstOrNull()
                ?: return@withContext Result.failure(Exception("Archive entry not found"))

            // Delete archive entry from local database
            userGroupArchiveDao.deleteArchive(userId, groupId)
            Log.d(TAG, "Group $groupId restored locally for user $userId")

            // If online, attempt server sync
            if (NetworkUtils.isOnline()) {
                try {
                    // Convert local IDs to server IDs using the existing archive entry
                    val serverIds = myApplication.entityServerConverter.convertUserGroupArchiveToServer(existingArchive)
                        .getOrElse {
                            Log.e(TAG, "Failed to convert IDs for server sync", it)
                            return@withContext Result.success(Unit) // Still succeed as local restore worked
                        }

                    // Attempt server sync
                    apiService.restoreGroup(
                        groupId = serverIds["group_id"] as Int,
                        userId = serverIds["user_id"] as Int
                    )

                    Log.d(TAG, "Group $groupId restored on server successfully for user $userId")
                } catch (e: Exception) {
                    // If server sync fails, re-insert the archive entry with SYNC_FAILED status
                    Log.e(TAG, "Failed to sync restored group $groupId with server for user $userId", e)
                    userGroupArchiveDao.insertArchive(
                        existingArchive.copy(
                            archivedAt = DateUtils.getCurrentTimestamp(),
                            syncStatus = SyncStatus.SYNC_FAILED
                        )
                    )
                    return@withContext Result.failure(e)
                }
            } else {
                Log.d(TAG, "Device offline, group $groupId restored locally only for user $userId")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring group $groupId", e)
            Result.failure(e)
        }
    }

    override suspend fun sync() {
        groupSyncManager.performSync()
        groupMemberSyncManager.performSync()
    }
    private suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelay: Long = 1000,
        block: suspend () -> T
    ): T? {
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (attempt == maxAttempts - 1) return null
                delay(initialDelay * (attempt + 1))
            }
        }
        return null
    }

    companion object {
        private const val TAG = "GroupRepository"
    }
}

