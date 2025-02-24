package com.helgolabs.trego.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

private const val USER_ID_KEY = "user_id"

fun getUserIdFromPreferences(context: Context): Int? {
    val prefs: SharedPreferences = context.getSharedPreferences("splitter_preferences", Context.MODE_PRIVATE)
    val userId = prefs.getInt("userId", -1)  // Default to -1 if not found
    Log.d("SharedPreferences", "Retrieved user ID: $userId")
    return if (userId == -1) null else userId
}

fun storeUserIdInPreferences(context: Context, userId: Int) {
    val prefs: SharedPreferences = context.getSharedPreferences("splitter_preferences", Context.MODE_PRIVATE)
    val editor = prefs.edit()
    editor.putInt("userId", userId)  // Make sure userId here is correct and not 0
    editor.apply()
    Log.d("SharedPreferences", "Stored user ID: $userId")
}


