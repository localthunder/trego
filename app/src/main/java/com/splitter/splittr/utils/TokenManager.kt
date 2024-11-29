package com.splitter.splittr.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONObject
import java.util.Date

object TokenManager {
    private const val PREF_NAME = "splitter_prefs"
    private const val KEY_ACCESS_TOKEN = "jwt_access_token"
    private const val KEY_REFRESH_TOKEN = "jwt_refresh_token"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveAccessToken(context: Context, token: String) {
        val editor = getPreferences(context).edit()
        editor.putString(KEY_ACCESS_TOKEN, token)
        editor.apply()
    }

    fun getAccessToken(context: Context): String? {
        return getPreferences(context).getString(KEY_ACCESS_TOKEN, null)
    }

    fun saveRefreshToken(context: Context, token: String) {
        val editor = getPreferences(context).edit()
        editor.putString(KEY_REFRESH_TOKEN, token)
        editor.apply()
    }

    fun getRefreshToken(context: Context): String? {
        return getPreferences(context).getString(KEY_REFRESH_TOKEN, null)
    }

    fun clearTokens(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            apply()
        }
    }

    fun isTokenExpired(context: Context): Boolean {
        val token = getAccessToken(context) ?: return true

        try {
            val payload = token.split(".")[1]
            val decodedPayload = String(Base64.decode(payload, Base64.URL_SAFE))
            val jsonPayload = JSONObject(decodedPayload)

            val expiration = jsonPayload.getLong("exp")
            val expirationDate = Date(expiration * 1000)

            return Date().after(expirationDate)
        } catch (e: Exception) {
            // If there's any error in decoding, assume the token is expired
            return true
        }
    }
}
