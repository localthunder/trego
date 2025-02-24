// File: AuthManager.kt
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

    fun isUserLoggedIn(context: Context): Boolean {
        val token = AuthUtils.getLoginState(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return token != null && prefs.getBoolean(KEY_AUTHENTICATED, false)
    }

    fun setAuthenticated(context: Context, authenticated: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTHENTICATED, authenticated).apply()
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

        val canAuthenticateResult = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

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
                            if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS) {
                                Log.d(TAG, "No biometrics enrolled, allowing device credentials")
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
                    .setTitle("Unlock Splitter")
                    .setSubtitle("Use your screen lock to continue")
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
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