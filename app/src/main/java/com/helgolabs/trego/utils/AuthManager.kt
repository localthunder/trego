package com.helgolabs.trego.utils

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.fragment.app.FragmentActivity
import com.helgolabs.trego.data.local.dao.UserDao
import com.helgolabs.trego.data.repositories.UserRepository
import com.helgolabs.trego.data.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AuthManager {
    private const val TAG = "AuthManager"
    private const val PREFS_NAME = "AuthPrefs"
    private const val KEY_AUTHENTICATED = "is_authenticated"
    private const val KEY_AUTH_TIMESTAMP = "auth_timestamp"
    private const val SESSION_TIMEOUT_MS = 5 * 60 * 1000 // 5 minutes

    // Add this method to check if authentication has timed out
    fun hasSessionTimedOut(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isAuthenticated = prefs.getBoolean(KEY_AUTHENTICATED, false)

        if (!isAuthenticated) return true

        val lastAuthTime = prefs.getLong(KEY_AUTH_TIMESTAMP, 0)
        val currentTime = System.currentTimeMillis()

        return (currentTime - lastAuthTime) > SESSION_TIMEOUT_MS
    }

    // Update setAuthenticated to also store the timestamp
    fun setAuthenticated(context: Context, authenticated: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putBoolean(KEY_AUTHENTICATED, authenticated)

        if (authenticated) {
            editor.putLong(KEY_AUTH_TIMESTAMP, System.currentTimeMillis())
        }

        editor.apply()
    }

    // Add a logout method
    fun logout(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_AUTHENTICATED, false)
            .remove(KEY_AUTH_TIMESTAMP)
            .apply()

        // Clear tokens
        TokenManager.clearTokens(context)

        // Clear user ID
        clearUserIdFromPreferences(context)
    }

    // Update isUserLoggedIn to check for timeout
    fun isUserLoggedIn(context: Context): Boolean {
        val token = AuthUtils.getLoginState(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isAuthenticated = prefs.getBoolean(KEY_AUTHENTICATED, false)

        // Enhanced validation
        if (token.isNullOrBlank() || !isAuthenticated) {
            // Clear state if token is invalid
            logout(context)
            return false
        }

        // Validate the user ID exists
        val userId = getUserIdFromPreferences(context)
        if (userId == null || userId == -1) {
            // No valid user ID stored
            logout(context)
            return false
        }

        // Check for session timeout
        if (hasSessionTimedOut(context)) {
            setAuthenticated(context, false)
            return false
        }

        return true
    }

    private fun updateLastLoginDate(userRepository: UserRepository, userId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            userRepository.updateLastLoginDate(userId)
                .onFailure { e ->
                    Log.e(TAG, "Failed to update last login date", e)
                }
        }
    }

    fun promptForBiometrics(
        activity: FragmentActivity,
        userRepository: UserRepository,
        userId: Int,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        Log.d(TAG, "Starting biometric prompt")
        val biometricManager = BiometricManager.from(activity)

        // Update authenticator types to include both biometrics and device credential
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val canAuthenticateResult = biometricManager.canAuthenticate(authenticators)

        Log.d(TAG, "Authentication capability result: $canAuthenticateResult")

        when (canAuthenticateResult) {
            BiometricManager.BIOMETRIC_SUCCESS,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                val executor = ContextCompat.getMainExecutor(activity)
                val biometricPrompt = BiometricPrompt(activity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            Log.d(TAG, "Authentication succeeded")
                            setAuthenticated(activity, true)
                            updateLastLoginDate(userRepository, userId)
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            Log.e(TAG, "Authentication error $errorCode: $errString")
                            if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                                errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL) {
                                Log.d(TAG, "No biometrics or device credentials enrolled, allowing access")
                                setAuthenticated(activity, true)
                                updateLastLoginDate(userRepository, userId)
                                onSuccess()
                            } else {
                                setAuthenticated(activity, false)
                                onFailure()
                            }
                        }

                        override fun onAuthenticationFailed() {
                            Log.e(TAG, "Authentication failed")
                            setAuthenticated(activity, false)
                            onFailure()
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Trego")
                    .setSubtitle("Use your biometrics or screen lock to continue")
                    .setAllowedAuthenticators(authenticators)
                    .build()

                try {
                    biometricPrompt.authenticate(promptInfo)
                    Log.d(TAG, "Authentication prompt shown")
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing authentication prompt", e)
                    onFailure()
                }
            }
            else -> {
                Log.e(TAG, "Device cannot authenticate: $canAuthenticateResult")
                onFailure()
            }
        }
    }
}