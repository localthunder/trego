// File: AuthManager.kt
package com.splitter.splittr.utils

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.fragment.app.FragmentActivity

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

    fun promptForBiometrics(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        Log.d(TAG, "Starting biometric prompt")
        val biometricManager = BiometricManager.from(activity)

        // Check what authentication methods are available
        val canAuthenticateResult = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or DEVICE_CREDENTIAL
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
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            Log.e(TAG, "Authentication error $errorCode: $errString")
                            if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS) {
                                Log.d(TAG, "No biometrics enrolled, allowing device credentials")
                                // Continue with device credentials
                                setAuthenticated(activity, true)
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
                // If device has no security set up, we might want to handle this differently
                onFailure()
            }
        }
    }

//    private fun createAuthCallback(onSuccess: () -> Unit, onFailure: () -> Unit): BiometricPrompt.AuthenticationCallback {
//        return object : BiometricPrompt.AuthenticationCallback() {
//            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
//                super.onAuthenticationError(errorCode, errString)
//                Log.e(TAG, "Authentication error: $errString")
//                onFailure()
//            }
//
//            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
//                super.onAuthenticationSucceeded(result)
//                Log.d(TAG, "Authentication succeeded!")
//                onSuccess()
//            }
//
//            override fun onAuthenticationFailed() {
//                super.onAuthenticationFailed()
//                Log.e(TAG, "Authentication failed")
//                onFailure()
//            }
//        }
//    }
}
