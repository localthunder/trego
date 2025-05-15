package com.helgolabs.trego.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

private const val USER_ID_KEY = "user_id"
private const val PREFS_NAME = "splitter_preferences"

fun getUserIdFromPreferences(context: Context): Int? {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val userId = prefs.getInt(USER_ID_KEY, -1)  // Use USER_ID_KEY consistently
    Log.d("SharedPreferences", "Retrieved user ID: $userId")
    return if (userId == -1) null else userId
}

fun storeUserIdInPreferences(context: Context, userId: Int) {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val editor = prefs.edit()
    editor.putInt(USER_ID_KEY, userId)  // Use USER_ID_KEY consistently
    editor.apply()
    Log.d("SharedPreferences", "Stored user ID: $userId")
}

fun clearUserIdFromPreferences(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().remove(USER_ID_KEY).commit()  // Use commit() for immediate persistence
    Log.d("SharedPreferences", "Cleared user ID")  // Changed to match the tag used elsewhere
}

// Add a helper to check if user ID exists without logging
fun hasUserIdInPreferences(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.contains(USER_ID_KEY)
}

