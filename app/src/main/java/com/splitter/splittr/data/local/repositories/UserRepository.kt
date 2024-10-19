package com.splitter.splittr.data.local.repositories

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.UserDao
import com.splitter.splittr.data.local.entities.UserEntity
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.network.AuthResponse
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.ui.screens.LoginRequest
import com.splitter.splittr.ui.screens.RegisterRequest
import com.splitter.splittr.utils.AuthUtils.getLoginState
import com.splitter.splittr.utils.AuthUtils.storeLoginState
import com.splitter.splittr.utils.CoroutineDispatchers
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
    private val dispatchers: CoroutineDispatchers
) {
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
                createdAt = System.currentTimeMillis().toString(), // Using current time as creation time
                updatedAt = System.currentTimeMillis().toString(), // Using current time as update time
                defaultCurrency = "GBP", // Assuming a default currency, adjust as needed
                lastLoginDate = System.currentTimeMillis().toString(),
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
    suspend fun syncUsers() = withContext(dispatchers.io) {
        userDao.getUnsyncedUsers().first().forEach { userEntity ->
            try {
                val user = apiService.getUserById(userEntity.userId)
                userDao.updateUser(user.toEntity())
                userDao.updateUserSyncStatus(userEntity.userId, SyncStatus.SYNCED.name)
            } catch (e: Exception) {
                userDao.updateUserSyncStatus(userEntity.userId, SyncStatus.SYNC_FAILED.name)
            }
        }
        userDao.getAllUsers().first().forEach { userEntity ->
            try {
                // Check if the user exists on the server
                val serverUser = try {
                    apiService.getUserById(userEntity.userId)
                } catch (e: Exception) {
                    null // User doesn't exist on the server
                }

                if (serverUser == null) {
                    // User doesn't exist on the server, so create it
                    apiService.createUser(userEntity.toModel())
                } else {
                    // User exists, so update it
                    apiService.updateUser(userEntity.userId, userEntity.toModel())
                }

                userDao.updateUserSyncStatus(userEntity.userId, SyncStatus.SYNCED.name)
            } catch (e: Exception) {
                Log.e("ReverseSyncUsers", "Failed to sync user ${userEntity.userId}", e)
                userDao.updateUserSyncStatus(userEntity.userId, SyncStatus.SYNC_FAILED.name)
            }
        }
    }
}