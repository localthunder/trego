package com.helgolabs.trego.data.local.repositories

import android.content.Context
import android.net.ConnectivityManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.dao.UserDao
import com.helgolabs.trego.data.local.entities.UserEntity
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.network.AuthResponse
import com.helgolabs.trego.data.repositories.UserRepository
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.sync.managers.PaymentSyncManager
import com.helgolabs.trego.data.sync.managers.UserSyncManager
import com.helgolabs.trego.ui.screens.LoginRequest
import com.helgolabs.trego.ui.screens.RegisterRequest
import com.helgolabs.trego.utils.AppCoroutineDispatchers
import com.helgolabs.trego.utils.AuthUtils
import com.helgolabs.trego.utils.NetworkUtils
import com.helgolabs.trego.utils.TokenManager
import com.helgolabs.trego.utils.getUserIdFromPreferences
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class UserRepositoryTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var userDao: UserDao
    private lateinit var apiService: ApiService
    private lateinit var context: Context
    private lateinit var userRepository: UserRepository
    private lateinit var syncMetadataDao: SyncMetadataDao
    private lateinit var userSyncManager: UserSyncManager

    private val testDispatchers = AppCoroutineDispatchers()

    @Before
    fun setup() {
        userDao = mockk()
        apiService = mockk()
        context = mockk()

        userRepository = UserRepository(userDao, apiService, testDispatchers, syncMetadataDao, userSyncManager)
    }

    @Test
    fun `getUserById returns flow of specific user`() = runTest {
        val userId = 1
        val mockUser = UserEntity(
            userId = userId,
            username = "testuser",
            email = "test@example.com",
            passwordHash = "hash",
            createdAt = "2023-01-01",
            updatedAt = "2023-01-01",
            appleId = "sdfvdfb",
            googleId = "svdsd",
            defaultCurrency = "USD",
            lastLoginDate = "2024-08-12"
        )

        // Mock the DAO method to return a flow with the mocked user
        coEvery { userDao.getUserById(userId) } returns flowOf(mockUser)

        // Collect the flow and assert that the values match
        val result = userRepository.getUserById(userId).first()

        // Verify that the mocked DAO method was called
        coVerify { userDao.getUserById(userId) }

        // Assert that the collected value matches the expected user entity
        assertEquals(mockUser, result)
    }


    @Test
    fun `registerUser registers user successfully`() = runTest {
        val registerRequest = RegisterRequest(username = "newuser", email = "new@example.com", password = "password123")
        val authResponse = AuthResponse(userId = 1, token = "token123", success = true, message = null, refreshToken = "refresh123")

        coEvery { apiService.registerUser(registerRequest) } returns authResponse
        coEvery { userDao.insertUser(any()) } just Runs

        val result = userRepository.registerUser(registerRequest)

        assertTrue(result.isSuccess)
        assertEquals(authResponse, result.getOrNull())

        coVerify { apiService.registerUser(registerRequest) }
        coVerify { userDao.insertUser(any()) }
    }

    @Test
    fun `loginUser performs online login successfully`() = runTest {
        // Mock necessary objects and behavior
        val loginRequest = LoginRequest(email = "user@example.com", password = "password123")
        val authResponse = AuthResponse(
            userId = 1,
            token = "token123",
            success = true,
            message = null,
            refreshToken = "refresh123"
        )

        // Mock NetworkUtils
        mockkObject(NetworkUtils)
        coEvery { NetworkUtils.isOnline() } returns true

        // Mock API service
        coEvery { apiService.loginUser(loginRequest) } returns authResponse
        coEvery { apiService.getUserById(1) } returns mockk()

        // Mock DAO behavior
        coEvery { userDao.insertUser(any()) } just Runs

        // Mock AuthUtils and TokenManager
        mockkObject(AuthUtils)
        mockkObject(TokenManager)
        every { AuthUtils.storeLoginState(context, any()) } just Runs
        every { TokenManager.saveRefreshToken(context, any()) } just Runs
        every { TokenManager.getRefreshToken(context) } returns null

        // Call the repository method
        val result = userRepository.loginUser(context, loginRequest)

        // Assertions
        assertTrue(result.isSuccess)
        assertEquals(authResponse, result.getOrNull())

        // Verify that mocks were called correctly
        coVerify { apiService.loginUser(loginRequest) }
        coVerify { apiService.getUserById(1) }
        verify { AuthUtils.storeLoginState(context, "token123") }
        verify { TokenManager.saveRefreshToken(context, "refresh123") }
    }


    @Test
    fun `loginUser performs offline login successfully`() = runTest {
        // Mock necessary objects and behavior
        val loginRequest = LoginRequest(email = "user@example.com", password = "password123")
        val userId = 1
        val token = "cachedToken123"
        val mockUser = UserEntity(
            userId = userId,
            username = "testuser",
            email = "test@example.com",
            passwordHash = "hash",
            createdAt = "2023-01-01",
            updatedAt = "2023-01-01",
            appleId = "sdfvdfb",
            googleId = "svdsd",
            defaultCurrency = "USD",
            lastLoginDate = "2024-08-12"
        )

        // Mock NetworkUtils
        mockkObject(NetworkUtils)
        coEvery { NetworkUtils.isOnline() } returns false

        // Mock AuthUtils and SharedPreferencesUtils
        mockkObject(AuthUtils)
        mockkStatic("com.helgolabs.trego.utils.SharedPreferencesUtilsKt")

        every { AuthUtils.getLoginState(context) } returns token
        every { getUserIdFromPreferences(context) } returns userId

        // Mock DAO behavior
        coEvery { userDao.getUserById(userId) } returns flowOf(mockUser)

        // Call the repository method
        val result = userRepository.loginUser(context, loginRequest)

        // Assertions
        assertTrue(result.isSuccess)
        val authResponse = result.getOrNull()
        assertNotNull(authResponse)
        assertEquals(userId, authResponse?.userId)
        assertEquals(token, authResponse?.token)

        // Verify that mocks were called correctly
        verify { AuthUtils.getLoginState(context) }
        coVerify { userDao.getUserById(userId) }
    }


    @Test
    fun `refreshToken refreshes token successfully`() = runTest {
        val refreshToken = "oldRefreshToken"
        val newAuthResponse = AuthResponse(userId = 1, token = "newToken123", success = true, message = null, refreshToken = "newRefreshToken")

        coEvery { apiService.refreshToken(any()) } returns newAuthResponse
        every { TokenManager.saveAccessToken(context, any()) } just Runs
        every { TokenManager.saveRefreshToken(context, any()) } just Runs

        val result = userRepository.refreshToken(context, refreshToken)

        assertTrue(result.isSuccess)
        assertEquals(newAuthResponse, result.getOrNull())

        coVerify { apiService.refreshToken(mapOf("refreshToken" to refreshToken)) }
        verify { TokenManager.saveAccessToken(context, "newToken123") }
        verify { TokenManager.saveRefreshToken(context, "newRefreshToken") }
    }

    @Test
    fun `syncUsers syncs unsynced users successfully`() = runTest {
        val unsyncedUser1 = UserEntity(userId = 1, username = "user1", email = "user1@example.com", passwordHash = "hash1", createdAt = "2023-01-01", updatedAt = "2023-01-01", defaultCurrency = "USD", appleId = "sdv", googleId = "dfvd", syncStatus = SyncStatus.PENDING_SYNC, lastLoginDate = "2024-08-12")
        val unsyncedUser2 = UserEntity(userId = 2, username = "user2", email = "user2@example.com", passwordHash = "hash2", createdAt = "2023-01-02", updatedAt = "2023-01-02", defaultCurrency = "EUR", appleId = "sdv", googleId = "dfvd", syncStatus = SyncStatus.PENDING_SYNC, lastLoginDate = "2024-08-12")

        coEvery { userDao.getUnsyncedUsers() } returns flowOf(listOf(unsyncedUser1, unsyncedUser2))
        coEvery { apiService.getUserById(1) } returns mockk(relaxed = true)
        coEvery { apiService.getUserById(2) } returns mockk(relaxed = true)
        coEvery { userDao.updateUser(any()) } just Runs
        coEvery { userDao.updateUserSyncStatus(any(), any()) } just Runs

        userRepository.sync()

        coVerify { userDao.getUnsyncedUsers() }
        coVerify { apiService.getUserById(1) }
        coVerify { apiService.getUserById(2) }
        coVerify(exactly = 2) { userDao.updateUser(any()) }
        coVerify(exactly = 2) { userDao.updateUserSyncStatus(any(), SyncStatus.SYNCED) }
    }
}