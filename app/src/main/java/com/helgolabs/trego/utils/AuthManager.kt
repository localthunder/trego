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
    private const val KEY_NEEDS_BIOMETRIC = "needs_biometric"
    private const val SESSION_TIMEOUT_MS = 5 * 60 * 1000 // 5 minutes

    // Add this method to check if user needs biometric authentication
    fun needsBiometricAuthentication(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val needsBiometric = prefs.getBoolean(KEY_NEEDS_BIOMETRIC, false)
        SecureLogger.d("AuthManager", "needsBiometricAuthentication: $needsBiometric")
        return needsBiometric
    }

    // Set whether user needs biometric on next app open
    fun setNeedsBiometric(context: Context, needsBiometric: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_NEEDS_BIOMETRIC, needsBiometric)
            .commit()
    }

    // Check if authentication has timed out
    fun hasSessionTimedOut(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isAuthenticated = prefs.getBoolean(KEY_AUTHENTICATED, false)
        val lastAuthTime = prefs.getLong(KEY_AUTH_TIMESTAMP, 0)
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastAuthTime
        val timedOut = !isAuthenticated || timeDiff > SESSION_TIMEOUT_MS

        SecureLogger.d("AuthManager", "hasSessionTimedOut: $timedOut (isAuth: $isAuthenticated, timeDiff: ${timeDiff}ms)")
        return timedOut
    }

    // Update setAuthenticated to also store the timestamp
    fun setAuthenticated(context: Context, authenticated: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putBoolean(KEY_AUTHENTICATED, authenticated)

        if (authenticated) {
            editor.putLong(KEY_AUTH_TIMESTAMP, System.currentTimeMillis())
            editor.putBoolean(KEY_NEEDS_BIOMETRIC, false) // Reset biometric need when authenticated
        }

        editor.commit()
    }

    // Updated logout method
    fun logout(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_AUTHENTICATED, false)
            .remove(KEY_AUTH_TIMESTAMP)
            .putBoolean(KEY_NEEDS_BIOMETRIC, false)
            .commit()

        // Clear tokens
        TokenManager.clearTokens(context)

        // Clear user ID
        clearUserIdFromPreferences(context)

        // Log completion
        SecureLogger.d(TAG, "Logout completed - all authentication state cleared")
    }

    // Updated isUserLoggedIn to be more lenient
    fun isUserLoggedIn(context: Context): Boolean {
        val token = AuthUtils.getLoginState(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isAuthenticated = prefs.getBoolean(KEY_AUTHENTICATED, false)

        // If no token, user is not logged in
        if (token.isNullOrBlank()) {
            return false
        }

        // Validate the user ID exists
        val userId = getUserIdFromPreferences(context)
        if (userId == null || userId == -1) {
            return false
        }

        // If we have a token and user ID, user is logged in
        // They may need biometric authentication, but they're still "logged in"
        return true
    }

    private fun updateLastLoginDate(userRepository: UserRepository, userId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            userRepository.updateLastLoginDate(userId)
                .onFailure { e ->
                    SecureLogger.e(TAG, "Failed to update last login date", e)
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
        SecureLogger.d(TAG, "Starting biometric prompt")
        val biometricManager = BiometricManager.from(activity)

        // Update authenticator types to include both biometrics and device credential
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val canAuthenticateResult = biometricManager.canAuthenticate(authenticators)

        SecureLogger.d(TAG, "Authentication capability result: $canAuthenticateResult")

        when (canAuthenticateResult) {
            BiometricManager.BIOMETRIC_SUCCESS,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                val executor = ContextCompat.getMainExecutor(activity)
                val biometricPrompt = BiometricPrompt(activity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            SecureLogger.d(TAG, "Authentication succeeded")
                            setAuthenticated(activity, true)
                            setNeedsBiometric(activity, false)
                            updateLastLoginDate(userRepository, userId)
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            SecureLogger.e(TAG, "Authentication error $errorCode: $errString")
                            if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                                errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL) {
                                SecureLogger.d(TAG, "No biometrics or device credentials enrolled, allowing access")
                                setAuthenticated(activity, true)
                                setNeedsBiometric(activity, false)
                                updateLastLoginDate(userRepository, userId)
                                onSuccess()
                            } else {
                                setAuthenticated(activity, false)
                                onFailure()
                            }
                        }

                        override fun onAuthenticationFailed() {
                            SecureLogger.e(TAG, "Authentication failed")
                            // Don't change authentication state on failure - let user retry
                            // setAuthenticated(activity, false)
                            // onFailure()
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Trego")
                    .setSubtitle("Use your biometrics or screen lock to continue")
                    .setAllowedAuthenticators(authenticators)
                    .build()

                try {
                    biometricPrompt.authenticate(promptInfo)
                    SecureLogger.d(TAG, "Authentication prompt shown")
                } catch (e: Exception) {
                    SecureLogger.e(TAG, "Error showing authentication prompt", e)
                    onFailure()
                }
            }
            else -> {
                SecureLogger.e(TAG, "Device cannot authenticate: $canAuthenticateResult")
                // If device can't authenticate, just let them in
                setAuthenticated(activity, true)
                setNeedsBiometric(activity, false)
                onSuccess()
            }
        }
    }
}