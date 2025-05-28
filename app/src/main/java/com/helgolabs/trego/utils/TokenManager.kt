// Modified TokenManager.kt
package com.helgolabs.trego.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.util.Date

object TokenManager {
    private const val TAG = "TokenManager"
    private const val PREF_NAME = "splitter_prefs"
    private const val KEY_REFRESH_TOKEN = "jwt_refresh_token"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveAccessToken(context: Context, token: String) {
        SecureLogger.d(TAG, "Saving access token: ${token.take(15)}...")
        // Use AuthUtils for secure storage of the access token
        AuthUtils.storeLoginState(context, token)
    }

    fun getAccessToken(context: Context): String? {
        // Get the token from AuthUtils secure storage
        val token = AuthUtils.getLoginState(context)
        SecureLogger.d(TAG, "Retrieved access token: ${token?.take(15) ?: "null"}...")
        return token
    }

    fun saveRefreshToken(context: Context, token: String) {
        SecureLogger.d(TAG, "Saving refresh token: ${token.take(15)}...")
        val editor = getPreferences(context).edit()
        editor.putString(KEY_REFRESH_TOKEN, token)
        editor.apply()
    }

    fun getRefreshToken(context: Context): String? {
        val token = getPreferences(context).getString(KEY_REFRESH_TOKEN, null)
        SecureLogger.d(TAG, "Retrieved refresh token: ${token?.take(15) ?: "null"}...")
        return token
    }

    fun clearTokens(context: Context) {
        SecureLogger.d(TAG, "Clearing all tokens")
        // Clear the access token using AuthUtils
        AuthUtils.clearLoginState(context)

        // Clear the refresh token
        val prefs = getPreferences(context)
        prefs.edit().apply {
            remove(KEY_REFRESH_TOKEN)
            apply()
        }

        // Also clear authentication state
        AuthManager.setAuthenticated(context, false)
    }

    fun isTokenExpired(context: Context): Boolean {
        val token = getAccessToken(context) ?: return true

        try {
            val parts = token.split(".")
            if (parts.size < 2) {
                SecureLogger.e(TAG, "Invalid token format")
                return true
            }

            val payload = parts[1]
            val decodedPayload = String(Base64.decode(payload, Base64.URL_SAFE))
            val jsonPayload = JSONObject(decodedPayload)

            val expiration = jsonPayload.getLong("exp")
            val expirationDate = Date(expiration * 1000)
            val now = Date()

            val isExpired = now.after(expirationDate)
            if (isExpired) {
                SecureLogger.d(TAG, "Token is expired. Exp: ${expirationDate}, Now: ${now}")
            }

            return isExpired
        } catch (e: Exception) {
            // If there's any error in decoding, assume the token is expired
            SecureLogger.e(TAG, "Error checking token expiration", e)
            return true
        }
    }

    fun saveTokens(context: Context, accessToken: String, refreshToken: String) {
        saveAccessToken(context, accessToken)
        saveRefreshToken(context, refreshToken)
    }
}