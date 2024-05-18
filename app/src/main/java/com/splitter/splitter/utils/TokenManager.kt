package com.splitter.splitter.utils

import android.content.Context
import android.content.SharedPreferences

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
        val editor = getPreferences(context).edit()
        editor.remove(KEY_ACCESS_TOKEN)
        editor.remove(KEY_REFRESH_TOKEN)
        editor.apply()
    }
}
