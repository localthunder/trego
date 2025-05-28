package com.helgolabs.trego.data.repositories

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dao.GroupDao
import com.helgolabs.trego.data.local.dao.GroupMemberDao
import com.helgolabs.trego.data.local.dao.PaymentDao
import com.helgolabs.trego.data.local.dao.PaymentSplitDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.dao.UserDao
import com.helgolabs.trego.data.local.entities.UserEntity
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.local.dataClasses.AuthResponse
import com.helgolabs.trego.data.local.dataClasses.CreateInviteTokenRequest
import com.helgolabs.trego.data.local.dataClasses.LoginRequest
import com.helgolabs.trego.data.local.dataClasses.MergeUsersRequest
import com.helgolabs.trego.data.local.dataClasses.RegisterRequest
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.data.model.GroupMember
import com.helgolabs.trego.data.model.User
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.sync.SyncableRepository
import com.helgolabs.trego.data.sync.managers.DeviceTokenSyncManager
import com.helgolabs.trego.data.sync.managers.UserPreferencesSyncManager
import com.helgolabs.trego.data.sync.managers.UserSyncManager
import com.helgolabs.trego.utils.AuthUtils.getLoginState
import com.helgolabs.trego.utils.AuthUtils.storeLoginState
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.EntityServerConverter
import com.helgolabs.trego.utils.NetworkUtils
import com.helgolabs.trego.utils.TokenManager
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.withContext
import java.io.IOException

class UserRepository(
    private val userDao: UserDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val syncMetadataDao: SyncMetadataDao,
    private val groupMemberDao: GroupMemberDao,
    private val groupDao: GroupDao,
    private val paymentDao: PaymentDao,
    private val paymentSplitDao: PaymentSplitDao,
    private val userSyncManager: UserSyncManager,
    private val deviceTokenSyncManager: DeviceTokenSyncManager,
    private val context: Context
) : SyncableRepository {

    override val entityType = "users"
    override val syncPriority = 1

    val myApplication = context.applicationContext as MyApplication
    val database = myApplication.database

    fun getUserById(userId: Int) = userDao.getUserById(userId)

    suspend fun getUserByServerId(serverId: Int): Result<UserEntity> = withContext(dispatchers.io) {
        try {
            // First check if we have a valid token
            val token = TokenManager.getAccessToken(context)
            if (token.isNullOrBlank()) {
                Log.e(TAG, "No valid token available for getUserByServerId")
                return@withContext Result.failure(IllegalStateException("No valid token available"))
            }

            Log.d(TAG, "Fetching user from server with ID: $serverId")

            // Get user from API
            val serverUser = apiService.getUserById(serverId)

            // Convert to entity
            val userEntity = myApplication.entityServerConverter
                .convertUserFromServer(serverUser, null, true)
                .getOrThrow()

            // Check if user already exists in database
            val existingUser = userDao.getUserByServerIdSync(serverId)

            if (existingUser != null) {
                // Update existing user
                val updatedUser = userEntity.copy(
                    userId = existingUser.userId,
                    syncStatus = SyncStatus.SYNCED
                )
                userDao.updateUserDirect(updatedUser)

                Log.d(TAG, "Updated existing user with userId: ${updatedUser.userId}")
                Result.success(updatedUser)
            } else {
                // Insert new user
                val insertedId = userDao.insertUser(userEntity)
                val insertedUser = userDao.getUserByIdDirect(insertedId.toInt())
                    ?: throw IllegalStateException("Failed to retrieve inserted user")

                Log.d(TAG, "Inserted new user with userId: ${insertedUser.userId}")
                Result.success(insertedUser)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user by server ID", e)
            Result.failure(e)
        }
    }

    fun getUserByServerIdFlow(serverId: Int) = userDao.getUserByServerId(serverId)

    fun getUsersByIds(userIds: List<Int>) = userDao.getUsersByIds(userIds)

    suspend fun refreshUser(userId: Int) = withContext(dispatchers.io) {
        try {
            val user = apiService.getUserById(userId)
            userDao.insertUser(user.toEntity())
        } catch (e: Exception) {
            // Handle error
        }
    }

    suspend fun registerUser(registerRequest: RegisterRequest): Result<AuthResponse> =
        withContext(dispatchers.io) {
            if (!NetworkUtils.isOnline()) {
                return@withContext Result.failure(IOException("Internet connection required for registration"))
            }

            val timestamp = DateUtils.getCurrentTimestamp()
            try {
                val authResponse = apiService.registerUser(registerRequest)
                Log.d("UserRepository", "Server returned user ID: ${authResponse.userId}")

                val userEntity = UserEntity(
                    serverId = authResponse.userId,
                    username = registerRequest.username,
                    email = registerRequest.email,
                    passwordHash = null,
                    googleId = null,
                    appleId = null,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    defaultCurrency = "GBP",
                    lastLoginDate = timestamp,
                    syncStatus = SyncStatus.SYNCED
                )
                Log.d(
                    "UserRepository",
                    "Created UserEntity with userId: ${userEntity.userId}, serverId: ${userEntity.serverId}"
                )

                val insertedId = userDao.insertUser(userEntity)
                Log.d("UserRepository", "Room returned inserted ID: $insertedId")

                // Verify what was actually inserted
                val insertedUser = userDao.getUserByIdSync(insertedId.toInt())
                Log.d(
                    "UserRepository",
                    "Verified inserted user: userId=${insertedUser?.userId}, serverId=${insertedUser?.serverId}"
                )

                Result.success(authResponse)
            } catch (e: Exception) {
                Log.e("UserRepository", "Error registering user", e)
                Result.failure(e)
            }
        }

    suspend fun loginUser(context: Context, loginRequest: LoginRequest): Result<AuthResponse> =
        withContext(dispatchers.io) {
            try {
                // Check if the device is online
                if (NetworkUtils.isOnline()) {
                    // Perform online login via the API
                    val authResponse = apiService.loginUser(loginRequest)

                    // Sync the user details with the server
                    refreshUser(authResponse.userId)

                    // Save the new access token in encrypted SharedPreferences
                    authResponse.token?.let { TokenManager.saveAccessToken(context, it) }
                    authResponse.refreshToken?.let { TokenManager.saveRefreshToken(context, it) }

                    // Add debug logging
                    Log.d("AuthRepository", "Login successful. Access token: ${authResponse.token?.take(15)}...")
                    Log.d("AuthRepository", "Refresh token saved: ${authResponse.refreshToken?.take(15) ?: "NULL"}...")

                    // Verify tokens are saved correctly
                    val savedAccessToken = TokenManager.getAccessToken(context)
                    val savedRefreshToken = TokenManager.getRefreshToken(context)
                    Log.d("AuthRepository", "Verified access token saved: ${savedAccessToken != null}")
                    Log.d("AuthRepository", "Verified refresh token saved: ${savedRefreshToken != null}")

                    // Optionally refresh the token immediately after login
                    val refreshToken = TokenManager.getRefreshToken(context)
                    if (refreshToken != null) {
                        val refreshResult = refreshToken(context, refreshToken)
                        if (refreshResult.isSuccess) {
                            // If refresh was successful, return the new AuthResponse
                            return@withContext Result.success(refreshResult.getOrNull()!!)
                        }
                        // If refresh fails, continue with the original authResponse
                    }
                    return@withContext Result.success(authResponse)
                } else {
                    // Offline scenario: Retrieve stored login state and user data from the local database
                    val token = getLoginState(context)
                    return@withContext if (token != null) {
                        // Retrieve user details from the local database (for example, by userId or saved session)
                        val userId = getUserIdFromPreferences(context)
                        val user = userId?.let { userDao.getUserById(it) }

                        if (user != null) {
                            // Create an AuthResponse from local data
                            val localAuthResponse = AuthResponse(
                                userId = userId,
                                token = token,
                                success = true,
                                message = null,
                                refreshToken = ""
                            )
                            Result.success(localAuthResponse)
                        } else {
                            Result.failure(Exception("User not found in local database"))
                        }
                    } else {
                        // No token and user data available, return failure
                        Result.failure(IOException("You need an internet connection to login for the first time"))
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthRepository", "Error logging in user", e)
                return@withContext Result.failure(e)
            }
        }

    suspend fun createProvisionalUser(
        username: String,
        email: String?,
        inviteLater: Boolean,
        groupId: Int
    ): Result<Int> = withContext(dispatchers.io) {
        Log.d("UserRepository", "Starting provisional user creation")
        try {
            val timestamp = DateUtils.getCurrentTimestamp()
            val invitedBy = getUserIdFromPreferences(context)

            val generatedUserId = database.withTransaction {
                val localUser = UserEntity(
                    serverId = null,
                    username = username,
                    email = "$username.$timestamp.provisional@trego.temp",
                    passwordHash = null,
                    googleId = null,
                    appleId = null,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    defaultCurrency = "GBP",
                    lastLoginDate = null,
                    syncStatus = SyncStatus.PENDING_SYNC,
                    isProvisional = true,
                    invitedBy = invitedBy,
                    invitationEmail = if (!inviteLater) email else null,
                    mergedIntoUserId = null
                )

                Log.d("UserRepository", "Attempting to insert user into local database")
                val userId = userDao.insertUser(localUser).toInt()

                if (userId == -1) {
                    throw Exception("Failed to insert user")
                }

                val groupMemberEntity = GroupMemberEntity(
                    id = 0,  // Let Room generate local ID
                    serverId = null,  // No server ID yet
                    groupId = groupId,  // This is already the local group ID
                    userId = userId,  // This is already the local user ID
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    removedAt = null,
                    syncStatus = SyncStatus.PENDING_SYNC
                )

                groupMemberDao.insertGroupMember(groupMemberEntity)

                // Update group's updated_at timestamp
                val group = groupDao.getGroupByIdSync(groupId)
                if (group != null) {
                    groupDao.updateGroup(
                        group.copy(
                            updatedAt = timestamp,
                            syncStatus = SyncStatus.PENDING_SYNC
                        )
                    )
                }

                userId
            }

            // Move server sync outside transaction since it's a network call
            if (NetworkUtils.isOnline()) {
                try {
                    val userEntity = userDao.getUserByIdDirect(generatedUserId)!!
                    val temporaryEmail = "$username.$timestamp.provisional@trego.temp"
                    val serverUserModel = myApplication.entityServerConverter
                        .convertUserToServer(
                            user = userEntity,
                            emailOverride = temporaryEmail
                        ).getOrThrow()

                    val serverUser = apiService.createUser(serverUserModel)
                    val updatedUser = userEntity.copy(
                        serverId = serverUser.userId,
                        syncStatus = SyncStatus.SYNCED
                    )
                    userDao.updateUser(updatedUser)

                    // Sync group member
                    val groupMember = groupMemberDao.getGroupMemberByUserIdSync(generatedUserId)!!
                    val serverGroupMemberModel = myApplication.entityServerConverter
                        .convertGroupMemberToServer(groupMember)
                        .getOrThrow()

                    // Update the member model with the new server user ID
                    val serverGroupMember = apiService.addMemberToGroup(
                        groupId = serverGroupMemberModel.groupId,
                        groupMember = serverGroupMemberModel.copy(userId = serverUser.userId)
                    )

                    // Convert server response back to local entity
                    val updatedLocalGroupMember = myApplication.entityServerConverter
                        .convertGroupMemberFromServer(serverGroupMember, groupMember)
                        .getOrThrow()

                    // Update the local group member with the server information
                    groupMemberDao.updateGroupMember(
                        updatedLocalGroupMember.copy(
                            syncStatus = SyncStatus.SYNCED
                        )
                    )

                    Log.d("UserRepository", "Server sync successful")
                } catch (e: Exception) {
                    Log.e("UserRepository", "Server sync failed", e)
                    // Continue with local ID even if sync fails
                }
            }

            Result.success(generatedUserId)
        } catch (e: Exception) {
            Log.e("UserRepository", "Failed to create provisional user:", e)
            Result.failure(e)
        }
    }

    suspend fun updateInvitationEmail(userId: Int, email: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            val user = userDao.getUserByIdDirect(userId)
                ?: return@withContext Result.failure(Exception("User not found"))

            if (!user.isProvisional) {
                return@withContext Result.failure(Exception("Can only update invitation email for provisional users"))
            }

            // Update local database
            userDao.updateInvitationEmail(userId, email)

            // If online, sync with server
            if (NetworkUtils.isOnline()) {
                try {
                    val serverUser = myApplication.entityServerConverter
                        .convertUserToServer(user.copy(invitationEmail = email))
                        .getOrThrow()

                    apiService.updateUser(user.serverId ?: 0, serverUser)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync invitation email update", e)
                    // Continue since local update succeeded
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating invitation email", e)
            Result.failure(e)
        }
    }

    suspend fun refreshToken(context: Context, refreshToken: String): Result<AuthResponse> =
        withContext(dispatchers.io) {
            try {
                val authResponse = apiService.refreshToken(mapOf("refreshToken" to refreshToken))

                // Save the new access token
                authResponse.token?.let { TokenManager.saveAccessToken(context, it) }

                // Save the new refresh token if provided
                authResponse.refreshToken?.let {
                    TokenManager.saveRefreshToken(context, it)
                }

                Result.success(authResponse)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun updateLastLoginDate(userId: Int): Result<Unit> = withContext(dispatchers.io) {
        try {
            val currentTime = DateUtils.getCurrentTimestamp()

            // Get current user
            val user = userDao.getUserByIdDirect(userId) ?: return@withContext Result.failure(
                Exception("User not found")
            )

            // Update local database
            val updatedUser = user.copy(
                lastLoginDate = currentTime,
                updatedAt = currentTime,
                syncStatus = SyncStatus.PENDING_SYNC
            )
            userDao.updateUserDirect(updatedUser)

            // If online, sync to server
            if (NetworkUtils.isOnline()) {
                try {
                    // Convert to server model
                    val serverUser = myApplication.entityServerConverter
                        .convertUserToServer(updatedUser)
                        .getOrElse {
                            Log.e(TAG, "Failed to convert user for server update", it)
                            return@withContext Result.failure(it)
                        }

                    // Update server
                    val response = apiService.updateUser(serverUser.userId, serverUser)

                    // Update local entity with server response
                    myApplication.entityServerConverter
                        .convertUserFromServer(response, updatedUser, true)
                        .onSuccess { syncedUser ->
                            userDao.updateUserDirect(syncedUser.copy(syncStatus = SyncStatus.SYNCED))
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync last login date to server", e)
                    // Don't fail the operation since local update succeeded
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating last login date", e)
            Result.failure(e)
        }
    }


    suspend fun mergeProvisionalUser(provisionalUserId: Int, targetUserId: Int): Result<Unit> =
        withContext(dispatchers.io) {
            try {
                val provisionalUser = userDao.getUserByIdSync(provisionalUserId)
                    ?: return@withContext Result.failure(Exception("Provisional user not found"))

                if (!provisionalUser.isProvisional) {
                    return@withContext Result.failure(Exception("Source user is not a provisional user"))
                }

                val targetUser = userDao.getUserByIdSync(targetUserId)
                    ?: return@withContext Result.failure(Exception("Target user not found"))

                if (targetUser.isProvisional) {
                    return@withContext Result.failure(Exception("Cannot merge into a provisional user"))
                }

                database.withTransaction {
                    // Update group memberships
                    // Get ALL memberships for the provisional user, including soft-deleted ones
                    val provisionalMemberships = groupMemberDao.getGroupMembersByUserId(provisionalUserId)

                    provisionalMemberships.forEach { provisionalMember ->
                        val targetMembership = groupMemberDao.getGroupMemberByGroupAndUserId(
                            provisionalMember.groupId,
                            targetUserId
                        )

                        when {
                            // Case 1: Target user has no membership
                            targetMembership == null -> {
                                // Transfer the membership to target user
                                groupMemberDao.updateGroupMemberDirect(
                                    provisionalMember.copy(
                                        userId = targetUserId,
                                        updatedAt = DateUtils.getCurrentTimestamp(),
                                        syncStatus = SyncStatus.PENDING_SYNC
                                    )
                                )
                            }

                            // Case 2: Target user has a soft-deleted membership
                            targetMembership.removedAt != null -> {
                                // Reactivate target user's membership
                                groupMemberDao.updateGroupMemberDirect(
                                    targetMembership.copy(
                                        removedAt = null,
                                        updatedAt = DateUtils.getCurrentTimestamp(),
                                        syncStatus = SyncStatus.PENDING_SYNC
                                    )
                                )
                                // Delete provisional user's membership
                                groupMemberDao.deleteGroupMember(provisionalMember.id)
                            }

                            // Case 3: Target user has an active membership
                            else -> {
                                // Simply delete provisional user's membership
                                groupMemberDao.deleteGroupMember(provisionalMember.id)
                            }
                        }
                    }

                    // Update payments
                    paymentDao.getPaymentsByUser(provisionalUserId).forEach { payment ->
                        paymentDao.updatePaymentDirect(
                            payment.copy(
                                paidByUserId = targetUserId,
                                updatedAt = DateUtils.getCurrentTimestamp(),
                                syncStatus = SyncStatus.PENDING_SYNC
                            )
                        )
                    }

                    // Update payment splits
                    paymentSplitDao.getPaymentSplitsByUser(provisionalUserId).forEach { split ->
                        paymentSplitDao.updatePaymentSplitDirect(
                            split.copy(
                                userId = targetUserId,
                                updatedAt = DateUtils.getCurrentTimestamp(),
                                syncStatus = SyncStatus.PENDING_SYNC
                            )
                        )
                    }

                    // Mark the provisional user as merged
                    userDao.updateUserDirect(
                        provisionalUser.copy(
                            mergedIntoUserId = targetUserId,
                            updatedAt = DateUtils.getCurrentTimestamp(),
                            syncStatus = SyncStatus.PENDING_SYNC
                        )
                    )
                }

                if (NetworkUtils.isOnline()) {
                    try {
                        val mergeUsersRequest = MergeUsersRequest(
                            provisionalUserId = provisionalUser.serverId ?: 0,
                            targetUserId = targetUser.serverId ?: 0
                        )

                        val response = apiService.mergeUsers(mergeUsersRequest)

                        if (response.success) {
                            database.withTransaction {
                                // Update provisional user sync status
                                userDao.updateUserSyncStatus(
                                    userId = provisionalUserId,
                                    status = SyncStatus.SYNCED
                                )

                                // Only update sync status for affected records
                                response.affectedGroups?.forEach { groupId ->
                                    groupMemberDao.updateGroupMemberSyncStatusByGroup(
                                        groupId = groupId,
                                        userId = provisionalUserId,
                                        status = SyncStatus.SYNCED
                                    )
                                }

                                response.affectedPayments?.forEach { paymentId ->
                                    // Update payment sync status
                                    paymentDao.updatePaymentSyncStatus(
                                        paymentId = paymentId,
                                        status = SyncStatus.SYNCED
                                    )

                                    // Update associated splits sync status
                                    paymentSplitDao.updatePaymentSplitSyncStatusByPayment(
                                        paymentId = paymentId,
                                        status = SyncStatus.SYNCED
                                    )
                                }
                            }
                        } else {
                            Log.e(TAG, "Server merge failed: ${response.message}")
                            // Mark affected records for retry
                            database.withTransaction {
                                userDao.updateUserSyncStatus(
                                    userId = provisionalUserId,
                                    status = SyncStatus.SYNC_FAILED
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync user merge to server", e)
                        // Mark for retry on next sync
                        database.withTransaction {
                            userDao.updateUserSyncStatus(
                                userId = provisionalUserId,
                                status = SyncStatus.SYNC_FAILED
                            )
                        }
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error merging users", e)
                Result.failure(e)
            }
        }

    suspend fun createInviteToken(token: String, userId: Int?): Result<String> = withContext(dispatchers.io) {
        try {
            if (userId == null) {
                return@withContext Result.failure(IllegalArgumentException("User ID cannot be null"))
            }

            // If offline, store locally and sync later
            if (!NetworkUtils.isOnline()) {
                // Store token mapping in local database for later sync
                // This would require adding a table for token mappings
                return@withContext Result.success(token)
            }

            // Send to server to create the token mapping
            val request = CreateInviteTokenRequest(token = token, userId = userId)
            val response = apiService.createInviteToken(request)

            if (response.success) {
                Result.success(token)
            } else {
                Result.failure(Exception(response.message ?: "Failed to create invite token"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating invite token", e)
            Result.failure(e)
        }
    }

    suspend fun resolveInviteToken(token: String): Result<Int> = withContext(dispatchers.io) {
        try {
            if (!NetworkUtils.isOnline()) {
                return@withContext Result.failure(IOException("Internet connection required"))
            }

            val response = apiService.resolveInviteToken(token)

            if (response.success && response.userId != null) {
                Result.success(response.userId)
            } else {
                Result.failure(Exception(response.message ?: "Failed to resolve invite token"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving invite token", e)
            Result.failure(e)
        }
    }

    suspend fun requestPasswordReset(email: String): Result<AuthResponse> = withContext(dispatchers.io) {
        try {
            if (!NetworkUtils.isOnline()) {
                return@withContext Result.failure(IOException("Internet connection required for password reset"))
            }

            val response = apiService.requestPasswordReset(mapOf("email" to email))

            if (response.success) {
                Result.success(response)
            } else {
                Result.failure(Exception(response.message ?: "Password reset request failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting password reset", e)
            Result.failure(e)
        }
    }

    suspend fun resetPassword(token: String, newPassword: String): Result<AuthResponse> = withContext(dispatchers.io) {
        try {
            if (!NetworkUtils.isOnline()) {
                return@withContext Result.failure(IOException("Internet connection required for password reset"))
            }

            val response = apiService.resetPassword(
                mapOf(
                    "token" to token,
                    "newPassword" to newPassword
                )
            )

            if (response.success) {
                Result.success(response)
            } else {
                Result.failure(Exception(response.message ?: "Password reset failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting password", e)
            Result.failure(e)
        }
    }

    suspend fun initiatePasswordChange(): Result<String> = withContext(dispatchers.io) {
        try {
            // Get current user ID
            val userId = getUserIdFromPreferences(context)
                ?: return@withContext Result.failure(Exception("No user logged in"))

            // Get current user
            val user = userDao.getUserByIdDirect(userId)
                ?: return@withContext Result.failure(Exception("User not found"))

            if (!NetworkUtils.isOnline()) {
                return@withContext Result.failure(IOException("Internet connection required"))
            }

            // Request immediate reset token for authenticated user
            val response = apiService.getAuthenticatedResetToken()

            if (response.success && response.token != null) {
                Result.success(response.token)
            } else {
                Result.failure(Exception(response.message ?: "Failed to get reset token"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating password change", e)
            Result.failure(e)
        }
    }

    suspend fun updateUsername(userId: Int, newUsername: String): Result<Unit> =
        withContext(dispatchers.io) {
            try {
                if (!NetworkUtils.hasNetworkCapabilities()) {
                    return@withContext Result.failure(IOException("Internet connection required"))
                }

                val response = apiService.updateUsername(request = mapOf("username" to newUsername))

                // Update local database
                userDao.updateUsername(
                    userId = userId,
                    newUsername = newUsername,
                    timestamp = DateUtils.getCurrentTimestamp(),
                    syncStatus = SyncStatus.SYNCED
                )

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating username", e)
                Result.failure(e)
            }
        }

    override suspend fun sync(): Unit = withContext(dispatchers.io) {
        try {
            Log.d(TAG, "Starting user sync")
            userSyncManager.performSync()
            deviceTokenSyncManager.performSync()
            Log.d(TAG, "User sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during user sync", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "UserRepository"
    }
}