package com.splitter.splittr.data.repositories

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.local.dao.UserDao
import com.splitter.splittr.data.local.entities.UserEntity
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.network.AuthResponse
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.sync.SyncableRepository
import com.splitter.splittr.data.sync.managers.UserSyncManager
import com.splitter.splittr.ui.screens.LoginRequest
import com.splitter.splittr.ui.screens.RegisterRequest
import com.splitter.splittr.utils.AuthUtils.getLoginState
import com.splitter.splittr.utils.AuthUtils.storeLoginState
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.DateUtils
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.TokenManager
import com.splitter.splittr.utils.TokenManager.getRefreshToken
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.withContext
import java.io.IOException

class UserRepository(
    private val userDao: UserDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val syncMetadataDao: SyncMetadataDao,
    private val userSyncManager: UserSyncManager
) : SyncableRepository {

    override val entityType = "users"
    override val syncPriority = 1

    fun getUserById(userId: Int) = userDao.getUserById(userId)

    fun getUsersByIds(userIds: List<Int>) = userDao.getUsersByIds(userIds)

    suspend fun refreshUser(userId: Int) = withContext(dispatchers.io) {
        try {
            val user = apiService.getUserById(userId)
            userDao.insertUser(user.toEntity())
        } catch (e: Exception) {
            // Handle error
        }
    }

    suspend fun registerUser(registerRequest: RegisterRequest): Result<AuthResponse> = withContext(dispatchers.io) {
        val timestamp = DateUtils.getCurrentTimestamp()
        try {
            val authResponse = apiService.registerUser(registerRequest)
            val userEntity = UserEntity(
                userId = authResponse.userId,
                serverId = null, // Assuming this is set by the server later
                username = registerRequest.username,
                email = registerRequest.email,
                passwordHash = null, // Assuming we don't store the password hash locally
                googleId = null,
                appleId = null,
                createdAt = timestamp,
                updatedAt = timestamp,
                defaultCurrency = "GBP", // Assuming a default currency, adjust as needed
                lastLoginDate = timestamp,
                syncStatus = SyncStatus.PENDING_SYNC // Marking as pending sync
            )
            userDao.insertUser(userEntity)
            Result.success(authResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginUser(context: Context, loginRequest: LoginRequest): Result<AuthResponse> = withContext(dispatchers.io) {
        try {
            // Check if the device is online
            if (NetworkUtils.isOnline()) {
                // Perform online login via the API
                val authResponse = apiService.loginUser(loginRequest)

                // Sync the user details with the server
                refreshUser(authResponse.userId)

                // Save the new access token in encrypted SharedPreferences
                authResponse.token?.let { storeLoginState(context, it) }
                authResponse.refreshToken?.let { TokenManager.saveRefreshToken(context, it) }

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
                        val localAuthResponse = AuthResponse(userId = userId, token = token, success = true, message = null, refreshToken = "" )
                        Result.success(localAuthResponse)
                    } else {
                        Result.failure(Exception("User not found in local database"))
                    }
                } else {
                    // No token and user data available, return failure
                    Result.failure(IOException("No internet connection and no cached login state"))
                }
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error logging in user", e)
            return@withContext Result.failure(e)
        }
    }


    suspend fun refreshToken(context: Context, refreshToken: String): Result<AuthResponse> = withContext(dispatchers.io) {
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
    override suspend fun sync(): Unit = withContext(dispatchers.io) {
        try {
            Log.d(TAG, "Starting user sync")
            userSyncManager.performSync()
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