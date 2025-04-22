package com.helgolabs.trego.data.repositories

import android.content.ContentValues.TAG
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.extensions.toListItem
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dataClasses.UserGroupListItem
import com.helgolabs.trego.data.local.dao.GroupDao
import com.helgolabs.trego.data.local.dao.GroupDefaultSplitDao
import com.helgolabs.trego.data.local.dao.GroupMemberDao
import com.helgolabs.trego.data.local.dao.PaymentDao
import com.helgolabs.trego.data.local.dao.PaymentSplitDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.dao.UserDao
import com.helgolabs.trego.data.local.dao.UserGroupArchiveDao
import com.helgolabs.trego.data.local.dataClasses.CurrencySettlingInstructions
import com.helgolabs.trego.data.local.dataClasses.GroupImageUploadResult
import com.helgolabs.trego.data.local.dataClasses.SettlingInstruction
import com.helgolabs.trego.data.local.dataClasses.UserBalanceWithCurrency
import com.helgolabs.trego.data.local.entities.GroupDefaultSplitEntity
import com.helgolabs.trego.data.local.entities.GroupEntity
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.data.local.entities.UserGroupArchiveEntity
import com.helgolabs.trego.data.model.Group
import com.helgolabs.trego.data.model.GroupDefaultSplit
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.model.GroupMember
import com.helgolabs.trego.data.sync.GroupSyncManager
import com.helgolabs.trego.data.sync.SyncableRepository
import com.helgolabs.trego.data.sync.managers.GroupDefaultSplitSyncManager
import com.helgolabs.trego.data.sync.managers.GroupMemberSyncManager
import com.helgolabs.trego.data.sync.managers.UserGroupArchiveSyncManager
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.ImageUtils
import com.helgolabs.trego.utils.NetworkUtils
import com.helgolabs.trego.utils.NetworkUtils.isOnline
import com.helgolabs.trego.utils.PlaceholderImageGenerator
import com.helgolabs.trego.utils.ServerIdUtil
import com.helgolabs.trego.utils.SyncUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.Dispatchers
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
import kotlin.math.abs

class GroupRepository(
    private val groupDao: GroupDao,
    private val groupMemberDao: GroupMemberDao,
    private val userDao: UserDao,
    private val paymentDao: PaymentDao,
    private val paymentSplitDao: PaymentSplitDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val userGroupArchiveDao: UserGroupArchiveDao,
    private val groupDefaultSplitDao: GroupDefaultSplitDao,
    private val apiService: ApiService,
    private val context: Context,
    private val dispatchers: CoroutineDispatchers,
    private val groupSyncManager: GroupSyncManager,
    private val groupMemberSyncManager: GroupMemberSyncManager,
    private val userGroupArchiveSyncManager: UserGroupArchiveSyncManager,
    private val groupDefaultSplitSyncManager: GroupDefaultSplitSyncManager
) : SyncableRepository {

    override val entityType = "groups"
    override val syncPriority = 1 // High priority as other entities depend on groups

    val myApplication = context.applicationContext as MyApplication
    val userRepository = myApplication.syncManagerProvider.provideUserRepository()

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

    fun getGroupById(groupId: Int): Flow<GroupEntity?> = flow {
        try {
            val localGroup = groupDao.getGroupById(groupId).first()
            if (localGroup != null) {
                // Check if the group needs a placeholder image
                val groupWithImage = if (localGroup.groupImg == null && localGroup.localImagePath == null) {
                    // Generate placeholder asynchronously
                    withContext(dispatchers.io) {
                        ensurePlaceholderImage(localGroup)
                    }
                } else {
                    localGroup.copy(
                        groupImg = localGroup.localImagePath ?: localGroup.groupImg
                    )
                }

                emit(groupWithImage)
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

                    emit(updatedGroup.copy(
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
        group: GroupEntity,
        userId: Int
    ): Result<Pair<GroupEntity, GroupMemberEntity>> = withContext(dispatchers.io) {
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
                defaultCurrency = existingUser?.defaultCurrency ?: "GBP",
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

            // Generate a placeholder image if no custom image was provided
            val groupWithPlaceholder = ensurePlaceholderImage(createdGroup)

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
                    val serverGroup = apiService.createGroup(groupWithPlaceholder.toModel())
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

                        groupDao.updateGroupDirect(groupWithPlaceholder.copy(
                            serverId = serverGroup.id,
                            syncStatus = SyncStatus.SYNCED
                        ))
                        groupMemberDao.updateGroupMember(createdMember.copy(
                            serverId = serverMember.id,
                            removedAt = serverMember.removedAt,
                            syncStatus = SyncStatus.SYNCED
                        ))
                    }

                    Result.success(Pair(groupWithPlaceholder, createdMember))
                } catch (e: Exception) {
                    Log.e("GroupRepository", "Server sync failed", e)
                    Result.success(Pair(groupWithPlaceholder, createdMember))
                }
            } else {
                Log.d("GroupRepository", "Device offline, returning local versions")
                Result.success(Pair(groupWithPlaceholder, createdMember))
            }
        } catch (e: Exception) {
            Log.e("GroupRepository", "Error in createGroupWithMember", e)
            Log.e("GroupRepository", "Stack trace: ${e.stackTrace.joinToString("\n")}")
            Result.failure(e)
        }
    }

    suspend fun updateGroup(group: GroupEntity): Result<GroupEntity> = withContext(dispatchers.io) {
        try {
            Log.d(TAG, "Updating group ${group.id}")
            // First save locally with pending sync status
            val localEntity = group.copy(syncStatus = SyncStatus.PENDING_SYNC)
            groupDao.updateGroup(localEntity)

            if (NetworkUtils.isOnline()) {
                // Convert the local entity to server model
                val serverModel = myApplication.entityServerConverter.convertGroupToServer(localEntity)
                    .getOrThrow() // This will throw if conversion fails

                // Make API call with converted model
                val serverGroup = apiService.updateGroup(serverModel.id, serverModel)

                // Update local database with server response
                groupDao.updateGroup(serverGroup.toEntity(SyncStatus.SYNCED))
                Result.success(serverGroup.toEntity())
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
    ): Result<GroupMemberEntity> = withContext(dispatchers.io) {
        val currentTime = DateUtils.getCurrentTimestamp()

        try {
            // Get the local group to find its server ID
            val group = groupDao.getGroupByIdSync(groupId)
                ?: return@withContext Result.failure(Exception("Group not found"))

            // Get the local user to find its server ID
            val user = userDao.getUserByIdSync(userId)
                ?: return@withContext Result.failure(Exception("User not found"))


            val groupMember = GroupMemberEntity(
                id = 0,
                groupId = groupId,
                userId = userId,
                createdAt = currentTime,
                updatedAt = currentTime,
                removedAt = null
            )

            try {
                val localId = groupMemberDao.insertGroupMember(
                    groupMember.copy(syncStatus = SyncStatus.PENDING_SYNC)
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
                        val updatedLocalGroupMember = updatedGroupMember.toEntity(SyncStatus.SYNCED)
                        groupMemberDao.insertGroupMember(updatedLocalGroupMember)
                        Result.success(updatedLocalGroupMember)
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
            // First get the member to find their group
            val member = groupMemberDao.getGroupMemberByIdSync(memberId)
                ?: return@withContext Result.failure(Exception("Member not found"))

            // Calculate user's balance in the group
            val balances = calculateGroupBalances(member.groupId).getOrElse {
                return@withContext Result.failure(Exception("Failed to calculate balances: ${it.message}"))
            }

            // Find this member's balance
            val memberBalance = balances.find { it.userId == member.userId }

            // Check if user has any non-zero balances
            val hasNonZeroBalance = memberBalance?.balances?.any { (_, amount) ->
                // Use a small epsilon for floating point comparison
                Math.abs(amount) > 0.01
            } ?: false

            if (hasNonZeroBalance) {
                return@withContext Result.failure(Exception("Cannot remove member with non-zero balance"))
            }

            // If balance is zero, proceed with removal
            groupMemberDao.removeGroupMember(memberId)

            // Sync with server if online
            if (NetworkUtils.isOnline()) {
                val localMember = groupMemberDao.getGroupMemberByIdSync(memberId)
                localMember?.serverId?.let {
                    apiService.removeMemberFromGroup(it)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("GroupRepository", "Failed to remove member", e)
            Result.failure(e)
        }
    }

    suspend fun getGroupInviteLink(groupId: Int): Result<String> = withContext(dispatchers.io) {
        try {
            // First try to get the invite link from local database
            val localGroup = groupDao.getGroupById(groupId).first()
                ?: return@withContext Result.failure(Exception("Group not found"))

            val localInviteLink = localGroup.inviteLink

            // Use direct deep link scheme - this works for sharing between devices with the app
            val appScheme = "trego://groups/invite/"
            val playStoreUrl = "https://play.google.com/store/apps/details?id=com.helgolabs.trego"

            if (!localInviteLink.isNullOrBlank()) {
                return@withContext Result.success(appScheme + localInviteLink)
            }

            // Only if we don't have a local invite link and we're online, try to fetch from API
            if (NetworkUtils.isOnline()) {
                try {
                    val serverGroupId = localGroup.serverId
                        ?: return@withContext Result.failure(Exception("Group has no server ID"))

                    val linkMap = apiService.getGroupInviteLink(serverGroupId)
                    val inviteCode = linkMap["inviteLink"] ?: ""

                    if (inviteCode.isNotBlank()) {
                        // Update local database with the new invite link
                        groupDao.updateGroupInviteLink(groupId, inviteCode)

                        // Return formatted URL with deep link and fallback
                        return@withContext Result.success(
                            "$appScheme$inviteCode?fallback=$playStoreUrl"
                        )
                    } else {
                        return@withContext Result.failure(Exception("Server returned empty invite code"))
                    }
                } catch (e: Exception) {
                    Log.e("GroupRepository", "Error fetching invite link from API", e)
                    return@withContext Result.failure(Exception("Failed to get invite link: ${e.message}"))
                }
            } else {
                return@withContext Result.failure(Exception("No network connection available"))
            }
        } catch (e: Exception) {
            Log.e("GroupRepository", "Error in getGroupInviteLink", e)
            Result.failure(e)
        }
    }

    suspend fun generateProvisionalUserInviteLink(
        provisionalUserId: Int,
        groupId: Int? = null
    ): Result<String> = withContext(dispatchers.io) {
        try {
            // Verify the user exists and is provisional
            val user = userDao.getUserByIdDirect(provisionalUserId)
                ?: return@withContext Result.failure(Exception("User not found"))

            if (!user.isProvisional) {
                return@withContext Result.failure(Exception("User is not provisional"))
            }

            // Get group invite code if groupId is provided
            val groupInviteCode = if (groupId != null) {
                getGroupInviteLink(groupId).getOrNull()
            } else null

            // Generate the invite URL
            val inviteUrl = buildString {
                append("trego://users/invite/")
                append("?userId=$provisionalUserId")
                if (groupInviteCode != null) {
                    append("&groupCode=$groupInviteCode")
                }
            }

            Result.success(inviteUrl)
        } catch (e: Exception) {
            Log.e("GroupRepository", "Error generating provisional user invite link", e)
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

                        // Get the server ID from the local user ID
                        val localGroup = groupDao.getGroupByIdSync(groupId)
                            ?: throw IllegalStateException("User not found in local database")

                        val serverGroupId = localGroup.serverId
                            ?: throw IllegalStateException("No server ID found for group $groupId")

                        // Upload image first
                        val imageResponse = apiService.uploadGroupImage(serverGroupId, imagePart)

                        // Get fresh group data from server
                        val updatedGroup = apiService.getGroupById(serverGroupId)

                        // Update local database
                        groupDao.runInTransaction {
                            val existingGroup = groupDao.getGroupById(groupId).first()
                            if (existingGroup != null) {
                                val newGroup = updatedGroup.toEntity(SyncStatus.SYNCED).copy(
                                    id = existingGroup.id,
                                    localImagePath = localPath,
                                    groupImg = imageResponse.imagePath,
                                    imageLastModified = currentTime,
                                    updatedAt = currentTime
                                )
                                Log.d("GroupRepository", "Updating group with new timestamps - " +
                                        "imageLastModified: ${newGroup.imageLastModified}, " +
                                        "updatedAt: ${newGroup.updatedAt}")
                                groupDao.updateGroup(newGroup)
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

    suspend fun updateGroupImage(groupId: Int, imagePath: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                val userId = getUserIdFromPreferences(context)
                    ?: return@withContext Result.failure(Exception("User not found"))

                // Get the current group
                val group = groupDao.getGroupByIdSync(groupId)
                    ?: return@withContext Result.failure(Exception("Group not found"))
                Log.d("updateGroupImage", "get group by id sync using group id: $groupId")

                // Important: Use the server ID for the API call
                val serverGroupId = group.serverId ?: groupId
                Log.d(TAG, "Updating group image: local ID=$groupId, server ID=$serverGroupId")

                // Update local database
                groupDao.updateGroupImage(groupId, imagePath, userId.toString())
                //I added this in to get it to call the updateLocalImageInfo dao method which should handle local image path
                downloadAndSaveGroupImage(group)

                // Try to update the server if online
                if (isOnline()) {
                    try {
                        // Use the SERVER ID here, not the local ID
                        val updatedGroup = group.toModel().copy(
                            groupImg = imagePath
                        )

                        val serverGroup = apiService.updateGroup(serverGroupId, updatedGroup)

                        // Update local entity with server data
                        val updatedEntity = serverGroup.toEntity()
                        groupDao.insertGroup(updatedEntity)

                        Log.d(TAG, "Group image updated on server: $imagePath")
                    } catch (e: Exception) {
                        Log.w(TAG, "Error updating group image on server", e)
                        // We don't fail the operation for network errors
                    }
                }

                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating group image", e)
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

    /**
     * Creates a placeholder image for a group if it doesn't have a custom image
     */
    private suspend fun ensurePlaceholderImage(group: GroupEntity): GroupEntity = withContext(dispatchers.io) {
        // Skip if group already has an image
        if (!group.groupImg.isNullOrEmpty() || !group.localImagePath.isNullOrEmpty()) {
            return@withContext group
        }

        try {
            // Generate a placeholder image based on group ID and name
            val placeholderPath = PlaceholderImageGenerator.generateForGroup(
                context,
                group.id,
                group.name
            )

            // Update the group with the placeholder image path
            val updatedGroup = group.copy(
                localImagePath = placeholderPath,
                imageLastModified = DateUtils.getCurrentTimestamp()
            )

            // Update the database
            groupDao.updateGroup(updatedGroup)

            Log.d(TAG, "Created placeholder image for group ${group.id}: $placeholderPath")

            return@withContext updatedGroup
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create placeholder image for group ${group.id}", e)
            return@withContext group
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
            // Use chunked processing for large payment datasets
            val balances = mutableMapOf<Int, MutableMap<String, Double>>()

            // Process payments in chunks to reduce memory pressure
            val payments = paymentDao.getNonArchivedPaymentsByGroup(groupId)

            // Process in smaller chunks
            for (payment in payments) {
                val paidByUserId = payment.paidByUserId
                val amount = payment.amount * -1
                val currency = payment.currency ?: continue

                // Update payer balance
                balances.getOrPut(paidByUserId) { mutableMapOf() }
                    .merge(currency, amount, Double::plus)

                // Process splits in smaller chunks too
                val splits = paymentSplitDao.getNonArchivedSplitsByPayment(payment.id)
                for (split in splits) {
                    balances.getOrPut(split.userId) { mutableMapOf() }
                        .merge(split.currency, split.amount, Double::plus)
                }
            }

            // Get user info efficiently
            val userIds = balances.keys.toList()
            val userMap = userDao.getUsersByIds(userIds).first().associateBy { it.userId }

            // Create result objects efficiently
            val result = userIds.mapNotNull { userId ->
                val user = userMap[userId] ?: return@mapNotNull null
                UserBalanceWithCurrency(
                    userId = user.userId,
                    username = user.username,
                    balances = balances[userId] ?: emptyMap()
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
        userGroupArchiveSyncManager.performSync()
        groupDefaultSplitSyncManager.performSync()
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

    fun getNonArchivedGroupsByUserId(userId: Int): Flow<List<UserGroupListItem>> =
        groupDao.getNonArchivedGroupsByUserId(userId)
            .catch { e ->
                Log.e("GroupRepository", "Error fetching non-archived groups", e)
                throw e
            }
            .map { groupEntities ->
                Log.d("GroupRepository", "Found ${groupEntities.size} non-archived groups for user $userId")
                groupEntities.map { it.toListItem() }
            }
            .flowOn(dispatchers.io)

    fun getArchivedGroupsByUserId(userId: Int): Flow<List<UserGroupListItem>> =
        groupDao.getArchivedGroupsByUserId(userId)
            .catch { e ->
                Log.e("GroupRepository", "Error fetching archived groups", e)
                throw e
            }
            .map { groupEntities ->
                Log.d("GroupRepository", "Found ${groupEntities.size} archived groups for user $userId")
                groupEntities.map { it.toListItem() }
            }
            .flowOn(dispatchers.io)

    fun isGroupArchived(groupId: Int, userId: Int): Flow<Boolean> = flow {
        try {
            val archiveEntity = userGroupArchiveDao.getArchive(userId, groupId).first()
            emit(archiveEntity != null)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking group archive status", e)
            emit(false)
        }
    }


    suspend fun calculateSettlingInstructions(groupId: Int): Result<List<CurrencySettlingInstructions>> = withContext(dispatchers.io) {
        try {
            // Get the balances with a more memory-efficient approach
            val balances = calculateGroupBalances(groupId).getOrElse {
                return@withContext Result.failure(it)
            }

            // Build a map of user IDs to usernames
            val usernameLookup = mutableMapOf<Int, String>()
            val userIds = balances.map { it.userId }.toSet()

            // Load user data to get names
            userRepository.getUsersByIds(userIds.toList()).first().forEach { user ->
                usernameLookup[user.userId] = user.username
            }

            // Process one currency at a time to reduce memory usage
            val result = mutableListOf<CurrencySettlingInstructions>()
            val currencies = balances.flatMap { it.balances.keys }.toSet()

            for (currency in currencies) {
                // Extract only balances for this currency
                val currencyBalances = balances.mapNotNull { userBalance ->
                    val amount = userBalance.balances[currency] ?: return@mapNotNull null
                    if (kotlin.math.abs(amount) < 0.01) return@mapNotNull null

                    // Create a triple with userId, username, and amount
                    Triple(
                        userBalance.userId,
                        usernameLookup[userBalance.userId] ?: "Unknown",
                        amount
                    )
                }

                if (currencyBalances.isEmpty()) continue

                // Process settling for this currency
                val currencyInstructions = processSettlingForCurrency(currencyBalances, currency, groupId)
                if (currencyInstructions.isNotEmpty()) {
                    result.add(CurrencySettlingInstructions(currency, currencyInstructions))
                }
            }

            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating settling instructions", e)
            Result.failure(e)
        }
    }


    /**
     * Process balances and generate settling instructions for a specific currency.
     * This creates optimized settling instructions to minimize the number of transactions.
     *
     * @param balances List of user balances with IDs, names, and amounts
     * @param currency The currency code
     * @param groupId The group ID
     * @return List of settling instructions
     */
    private fun processSettlingForCurrency(
        balances: List<Triple<Int, String, Double>>, // userId, username, balance
        currency: String,
        groupId: Int
    ): List<SettlingInstruction> {
        // Sort balances: negative (debtors) first, positive (creditors) last
        val sortedBalances = balances.sortedBy { it.third }.toMutableList()
        val instructions = mutableListOf<SettlingInstruction>()

        // Use a more memory-efficient algorithm
        while (sortedBalances.size >= 2) {
            val debtor = sortedBalances.first()
            val creditor = sortedBalances.last()

            // Both have effectively zero balance - remove them
            if (abs(debtor.third) < 0.01 && abs(creditor.third) < 0.01) {
                sortedBalances.removeAt(sortedBalances.lastIndex)
                sortedBalances.removeAt(0)
                continue
            }

            // Debtor has zero balance - remove it
            if (abs(debtor.third) < 0.01) {
                sortedBalances.removeAt(0)
                continue
            }

            // Creditor has zero balance - remove it
            if (abs(creditor.third) < 0.01) {
                sortedBalances.removeAt(sortedBalances.lastIndex)
                continue
            }

            // Calculate transfer amount
            val transferAmount = kotlin.math.min(abs(debtor.third), creditor.third)

            instructions.add(
                SettlingInstruction(
                    fromId = debtor.first,
                    toId = creditor.first,
                    fromName = debtor.second,
                    toName = creditor.second,
                    amount = transferAmount,
                    currency = currency,
                    groupId = groupId
                )
            )

            // Update balances and remove settled entries
            val updatedDebtorBalance = debtor.third + transferAmount
            val updatedCreditorBalance = creditor.third - transferAmount

            // Replace with updated balances
            sortedBalances[0] = Triple(debtor.first, debtor.second, updatedDebtorBalance)
            sortedBalances[sortedBalances.lastIndex] = Triple(creditor.first, creditor.second, updatedCreditorBalance)

            // Remove settled balances
            if (abs(updatedDebtorBalance) < 0.01) {
                sortedBalances.removeAt(0)
            }
            if (sortedBalances.isNotEmpty() && abs(updatedCreditorBalance) < 0.01) {
                sortedBalances.removeAt(sortedBalances.lastIndex)
            }
        }

        return instructions
    }

    suspend fun joinGroupByInvite(inviteCode: String, userId: Int): Result<Int> = withContext(dispatchers.io) {
        try {
            if (isOnline()) {
                val serverResponse = apiService.joinGroupByInvite(inviteCode)
                val localGroup = serverResponse.toEntity(SyncStatus.SYNCED)

                myApplication.database.withTransaction {
                    // Insert or update the group
                    val groupId = groupDao.insertGroup(localGroup).toInt()

                    // Add user as member
                    val memberEntity = GroupMemberEntity(
                        groupId = groupId,
                        userId = userId,
                        createdAt = DateUtils.getCurrentTimestamp(),
                        updatedAt = DateUtils.getCurrentTimestamp(),
                        removedAt = null,
                        syncStatus = SyncStatus.SYNCED
                    )
                    groupMemberDao.insertGroupMember(memberEntity)

                    Result.success(groupId)
                }
            } else {
                Result.failure(IOException("Internet connection required to join group"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Get default splits for a group - returns a Flow that will update when DB changes
    fun getGroupDefaultSplits(groupId: Int): Flow<List<GroupDefaultSplitEntity>> {
        return groupDefaultSplitDao.getDefaultSplitsByGroup(groupId)
    }

    // Create or update a single default split
    suspend fun createOrUpdateDefaultSplit(defaultSplit: GroupDefaultSplitEntity): Result<GroupDefaultSplitEntity> = withContext(dispatchers.io) {
        try {
            // Save locally first with PENDING_SYNC status
            val localSplit = defaultSplit.copy(syncStatus = SyncStatus.PENDING_SYNC)
            groupDefaultSplitDao.insertOrUpdateDefaultSplit(localSplit)

            // Attempt to sync with server if online
            if (NetworkUtils.isOnline()) {
                try {
                    // Convert to server model
                    val serverModelResult = myApplication.entityServerConverter.convertGroupDefaultSplitToServer(localSplit)
                    val serverModel = serverModelResult.getOrElse { error ->
                        // Handle conversion error
                        Log.e("GroupRepository", "Failed to convert split to server model", error)
                        return@withContext Result.failure(error)
                    }

                    // Use create or update based on if it's a new split or existing one
                    val serverSplit = if (localSplit.serverId == null) {
                        apiService.createGroupDefaultSplit(localSplit.groupId, serverModel)
                    } else {
                        apiService.updateGroupDefaultSplit(localSplit.groupId, localSplit.serverId, serverModel)
                    }

                    // Update local split with server ID and SYNCED status
                    val syncedSplitResult = myApplication.entityServerConverter.convertGroupDefaultSplitFromServer(serverSplit, localSplit)
                    val syncedSplit = syncedSplitResult.getOrElse { error ->
                        // Handle conversion error
                        Log.e("GroupRepository", "Failed to convert server response to local entity", error)
                        return@withContext Result.failure(error)
                    }

                    groupDefaultSplitDao.insertOrUpdateDefaultSplit(syncedSplit.copy(syncStatus = SyncStatus.SYNCED))

                    Result.success(syncedSplit)
                } catch (e: Exception) {
                    Log.e("GroupRepository", "Failed to sync default split with server", e)
                    // Return the local split even if server sync fails
                    Result.success(localSplit)
                }
            } else {
                // When offline, return the local entity
                Result.success(localSplit)
            }
        } catch (e: Exception) {
            Log.e("GroupRepository", "Failed to create/update default split", e)
            Result.failure(e)
        }
    }

    // Create or update multiple default splits for a group
    suspend fun updateGroupDefaultSplits(groupId: Int, splits: List<GroupDefaultSplitEntity>): Result<List<GroupDefaultSplitEntity>> = withContext(dispatchers.io) {
        try {
            // Start a local transaction
            val timestamp = DateUtils.getCurrentTimestamp()

            // Validate total equals 100%
            val totalPercentage = splits.sumOf { it.percentage ?: 0.0 }
            if (abs(totalPercentage - 100.0) > 0.001) { // Use epsilon for floating point comparison
                return@withContext Result.failure(Exception("Total percentage must equal 100%"))
            }

            // Soft delete existing splits
            groupDefaultSplitDao.softDeleteDefaultSplitsByGroupWithStatus(groupId, timestamp)

            // Add new splits locally with PENDING_SYNC status
            val localSplits = splits.map { it.copy(
                syncStatus = SyncStatus.PENDING_SYNC,
                createdAt = timestamp,
                updatedAt = timestamp
            )}
            groupDefaultSplitDao.insertOrUpdateDefaultSplits(localSplits)

            // Attempt to sync with server if online
            if (NetworkUtils.isOnline()) {
                try {
                    // Convert to server models - handling Results properly
                    val serverModelResults = localSplits.map { split ->
                        myApplication.entityServerConverter.convertGroupDefaultSplitToServer(split)
                    }

                    // Check if any conversions failed
                    val hasFailedConversions = serverModelResults.any { it.isFailure }
                    if (hasFailedConversions) {
                        val firstError = serverModelResults.first { it.isFailure }.exceptionOrNull()
                        Log.e("GroupRepository", "Failed to convert splits to server models", firstError)
                        return@withContext Result.success(localSplits) // Return local data anyway
                    }

                    // Extract successful conversions
                    val serverModels = serverModelResults.map { it.getOrThrow() }

                    val serverGroupId = ServerIdUtil.getServerId(groupId, "groups", context) ?: 0

                    // Send batch update to server
                    val serverSplits = apiService.createOrUpdateBatchGroupDefaultSplits(serverGroupId, serverModels)

                    // Update local splits with server IDs and SYNCED status
                    val syncedSplitsList = mutableListOf<GroupDefaultSplitEntity>()

                    for (index in serverSplits.indices) {
                        val serverSplit = serverSplits[index]
                        val localSplit = localSplits.getOrNull(index)

                        val convertResult = myApplication.entityServerConverter.convertGroupDefaultSplitFromServer(
                            serverSplit,
                            localSplit
                        )

                        if (convertResult.isSuccess) {
                            val convertedSplit = convertResult.getOrThrow()
                            syncedSplitsList.add(convertedSplit.copy(syncStatus = SyncStatus.SYNCED))
                        } else {
                            Log.e("GroupRepository", "Failed to convert server split back to local entity",
                                convertResult.exceptionOrNull())
                            // If we can't convert a server response, keep the original local version
                            localSplit?.let { syncedSplitsList.add(it) }
                        }
                    }

                    // Save all successfully converted splits
                    if (syncedSplitsList.isNotEmpty()) {
                        groupDefaultSplitDao.insertOrUpdateDefaultSplits(syncedSplitsList)
                    }

                    Result.success(syncedSplitsList)
                } catch (e: Exception) {
                    Log.e("GroupRepository", "Failed to sync batch default splits with server", e)
                    // Return the local splits even if server sync fails
                    Result.success(localSplits)
                }
            } else {
                // When offline, return the local entities
                Result.success(localSplits)
            }
        } catch (e: Exception) {
            Log.e("GroupRepository", "Failed to update default splits", e)
            Result.failure(e)
        }
    }

    // Delete all default splits for a group
    suspend fun deleteGroupDefaultSplits(groupId: Int): Result<Unit> = withContext(dispatchers.io) {
        try {
            // Soft delete locally first
            val timestamp = DateUtils.getCurrentTimestamp()
            groupDefaultSplitDao.softDeleteDefaultSplitsByGroupWithStatus(groupId, timestamp)

            val serverGroupId = ServerIdUtil.getServerId(groupId, "groups", context) ?: 0

            // Attempt to delete from server if online
            if (NetworkUtils.isOnline()) {
                try {
                    apiService.deleteGroupDefaultSplits(serverGroupId)
                    // Hard deletes happen on the server, so we don't need to update our local DB
                } catch (e: Exception) {
                    Log.e("GroupRepository", "Failed to delete default splits from server", e)
                    // The operation is still considered successful locally
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("GroupRepository", "Failed to delete default splits", e)
            Result.failure(e)
        }
    }

    // Delete a specific default split by ID
    suspend fun deleteGroupDefaultSplit(groupId: Int, splitId: Int): Result<Unit> = withContext(dispatchers.io) {
        try {
            // Get the entity first
            val split = groupDefaultSplitDao.getDefaultSplitById(splitId)
                ?: return@withContext Result.failure(Exception("Default split not found"))

            // Soft delete locally first
            val timestamp = DateUtils.getCurrentTimestamp()
            groupDefaultSplitDao.softDeleteDefaultSplit(splitId, timestamp)

            // Attempt to delete from server if online
            if (NetworkUtils.isOnline() && split.serverId != null) {
                try {
                    apiService.deleteGroupDefaultSplit(groupId, split.serverId)
                    // Hard deletes happen on the server, so we don't need to update our local DB
                } catch (e: Exception) {
                    Log.e("GroupRepository", "Failed to delete default split from server", e)
                    // The operation is still considered successful locally
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("GroupRepository", "Failed to delete default split", e)
            Result.failure(e)
        }
    }

    suspend fun forceRefreshGroup(groupId: Int): Result<GroupEntity> {
        return withContext(dispatchers.io) {
            try {
                val localGroup = groupDao.getGroupByIdSync(groupId)
                    ?: return@withContext Result.failure(Exception("Group not found"))

                // Ensure we have a server ID
                val serverGroupId = localGroup.serverId
                    ?: return@withContext Result.failure(Exception("Group has no server ID"))

                // Force fetch from server
                val remoteGroup = apiService.getGroupById(serverGroupId)
                val updatedGroup = remoteGroup.toEntity(SyncStatus.SYNCED)

                // Update local database
                groupDao.updateGroup(updatedGroup)

                Result.success(updatedGroup)
            } catch (e: Exception) {
                Log.e("GroupRepository", "Error forcing group refresh", e)
                Result.failure(e)
            }
        }
    }

    // Sync pending changes for default splits
    suspend fun syncPendingDefaultSplits(): Result<Unit> = withContext(dispatchers.io) {
        if (!NetworkUtils.isOnline()) {
            return@withContext Result.failure(Exception("No network connection"))
        }

        try {
            // Get all locally modified splits
            val pendingSplits = groupDefaultSplitDao.getUnsyncedDefaultSplits()

            // Handle deleted splits
            val deletedSplits = pendingSplits.filter { it.syncStatus == SyncStatus.LOCALLY_DELETED && it.removedAt != null }
            deletedSplits.forEach { split ->
                try {
                    if (split.serverId != null) {
                        apiService.deleteGroupDefaultSplit(split.groupId, split.serverId)
                    }
                } catch (e: Exception) {
                    Log.e("GroupRepository", "Failed to sync deleted split ${split.id}", e)
                }
            }

            // Handle added/updated splits
            val activeSplits = pendingSplits.filter { it.syncStatus != SyncStatus.LOCALLY_DELETED && it.removedAt == null }

            // Group by group ID for batch operations
            val splitsByGroup = activeSplits.groupBy { it.groupId }

            splitsByGroup.forEach { (groupId, splits) ->
                try {
                    // Convert to server models
                    val serverModels = mutableListOf<GroupDefaultSplit>()

                    // Process each split individually to handle conversion errors
                    for (split in splits) {
                        val convertResult = myApplication.entityServerConverter.convertGroupDefaultSplitToServer(split)
                        if (convertResult.isSuccess) {
                            serverModels.add(convertResult.getOrThrow())
                        } else {
                            Log.e("GroupRepository", "Failed to convert split ${split.id} to server model",
                                convertResult.exceptionOrNull())
                            // Skip this split
                        }
                    }

                    // Skip if no valid conversions
                    if (serverModels.isEmpty()) {
                        Log.w("GroupRepository", "No valid splits to sync for group $groupId")
                        return@forEach  // Use return@forEach instead of continue
                    }

                    // Send the valid models to server
                    val serverSplits = apiService.createOrUpdateBatchGroupDefaultSplits(groupId, serverModels)


                    // Update local splits with server IDs and SYNCED status
                    val syncedSplits = mutableListOf<GroupDefaultSplitEntity>()

                    for (index in serverSplits.indices) {
                        val serverSplit = serverSplits[index]
                        val localSplit = splits.getOrNull(index)

                        val convertResult = myApplication.entityServerConverter.convertGroupDefaultSplitFromServer(
                            serverSplit,
                            localSplit
                        )

                        if (convertResult.isSuccess) {
                            syncedSplits.add(convertResult.getOrThrow().copy(syncStatus = SyncStatus.SYNCED))
                        } else {
                            Log.e("GroupRepository", "Failed to convert server response to local entity",
                                convertResult.exceptionOrNull())
                        }
                    }

                    if (syncedSplits.isNotEmpty()) {
                        groupDefaultSplitDao.insertOrUpdateDefaultSplits(syncedSplits)
                    }
                } catch (e: Exception) {
                    Log.e("GroupRepository", "Failed to sync splits for group $groupId", e)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("GroupRepository", "Failed to sync pending default splits", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "GroupRepository"
    }
}

