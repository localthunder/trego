package com.splitter.splitter.utils

import android.content.Context

object AuthUtils {

    private const val PREFS_NAME = "MyAppPrefs"
    private const val KEY_AUTH_TOKEN = "auth_token"

    fun storeLoginState(context: Context, token: String) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(KEY_AUTH_TOKEN, token)
        editor.apply()
    }

    fun getLoginState(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(KEY_AUTH_TOKEN, null)
    }

    fun clearLoginState(context: Context) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.remove(KEY_AUTH_TOKEN)
        editor.apply()
    }
}
