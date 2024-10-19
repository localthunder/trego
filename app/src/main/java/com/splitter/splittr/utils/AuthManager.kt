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

    fun isUserLoggedIn(context: Context): Boolean {
        val token = AuthUtils.getLoginState(context)
        Log.d(TAG, "isUserLoggedIn: token = $token")
        return token != null
    }

    fun promptForBiometrics(
        activity: ComponentActivity,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        Log.d(TAG, "promptForBiometrics: called")
        val biometricManager = BiometricManager.from(activity)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        )

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            Log.d(TAG, "Device supports biometric authentication")

            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = when (activity) {
                is FragmentActivity -> {
                    Log.d(TAG, "Activity is FragmentActivity, setting up BiometricPrompt")
                    BiometricPrompt(activity, executor, createAuthCallback(onSuccess, onFailure))
                }
                else -> {
                    Log.e(TAG, "Activity is not FragmentActivity. Biometric authentication is not supported.")
                    onFailure()
                    return
                }
            }

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Login to Splitter")
                .setSubtitle("Log in using your biometric credential")
                .setNegativeButtonText("Use app password")
                .build()

            Log.d(TAG, "Starting biometric prompt")
            biometricPrompt.authenticate(promptInfo)
        } else {
            Log.e(TAG, "Device cannot authenticate with biometrics: canAuthenticate = $canAuthenticate")
            onFailure()
        }
    }

    private fun createAuthCallback(onSuccess: () -> Unit, onFailure: () -> Unit): BiometricPrompt.AuthenticationCallback {
        return object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.e(TAG, "Authentication error: $errString")
                onFailure()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "Authentication succeeded!")
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.e(TAG, "Authentication failed")
                onFailure()
            }
        }
    }
}
