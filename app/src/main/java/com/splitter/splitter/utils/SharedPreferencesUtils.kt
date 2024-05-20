package com.splitter.splitter.utils

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log

private const val USER_ID_KEY = "user_id"

fun getUserIdFromPreferences(context: Context): Int? {
    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val userId = prefs.getInt(USER_ID_KEY, -1)
    Log.d("SharedPreferences", "Retrieved user ID: $userId")
    return if (userId == -1) null else userId
}

fun storeUserIdInPreferences(context: Context, userId: Int) {
    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val editor = prefs.edit()
    editor.putInt(USER_ID_KEY, userId)
    editor.apply()
    Log.d("SharedPreferences", "Stored user ID: $userId")
}
