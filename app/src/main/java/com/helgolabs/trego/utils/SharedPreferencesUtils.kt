package com.helgolabs.trego.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val USER_ID_KEY = "user_id"
private const val SECURE_PREFS_NAME = "trego_secure_preferences"

fun getUserIdFromPreferences(context: Context): Int? {
    return SecurePreferences.getUserId(context)
}

fun storeUserIdInPreferences(context: Context, userId: Int) {
    SecurePreferences.storeUserId(context, userId)
}

fun clearUserIdFromPreferences(context: Context) {
    SecurePreferences.clearUserId(context)
}

fun hasUserIdInPreferences(context: Context): Boolean {
    return SecurePreferences.hasUserId(context)
}

object SecurePreferences {
    private fun getEncryptedPreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getUserId(context: Context): Int? {
        val userId = getEncryptedPreferences(context).getInt(USER_ID_KEY, -1)
        return if (userId == -1) null else userId
    }

    fun storeUserId(context: Context, userId: Int) {
        getEncryptedPreferences(context).edit().apply {
            putInt(USER_ID_KEY, userId)
            apply()
        }
    }

    fun clearUserId(context: Context) {
        getEncryptedPreferences(context).edit().apply {
            remove(USER_ID_KEY)
            apply()
        }
    }

    fun hasUserId(context: Context): Boolean {
        return getEncryptedPreferences(context).contains(USER_ID_KEY)
    }
}
