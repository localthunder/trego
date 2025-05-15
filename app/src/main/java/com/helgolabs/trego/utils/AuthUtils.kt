package com.helgolabs.trego.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import android.util.Log

object AuthUtils {

    private const val KEY_ALIAS = "my_app_key_alias"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "MyAppPrefs"
    private const val KEY_AUTH_TOKEN = "auth_token"

    // Initialize the KeyStore
    private fun getKeyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    }

    // Generate a new key if one doesn't exist
    private fun createKey() {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = getKeyStore()
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                createKey()
            }
            keyStore.getKey(KEY_ALIAS, null) as SecretKey
        } catch (e: Exception) {
            Log.e("AuthUtils", "Error getting secret key", e)
            null
        }
    }

    fun storeLoginState(context: Context, token: String) {
        try {
            val secretKey = getSecretKey() ?: return
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedData = cipher.doFinal(token.toByteArray(Charsets.UTF_8))

            Log.d("AuthUtils", "Storing token: $token")

            val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putString(KEY_AUTH_TOKEN, Base64.encodeToString(encryptedData, Base64.DEFAULT))
            editor.putString("iv", Base64.encodeToString(iv, Base64.DEFAULT))
            editor.commit()  // Use commit() for synchronous storage
            Log.d("AuthUtils", "Token stored successfully")
        } catch (e: Exception) {
            Log.e("AuthUtils", "Error storing login state", e)
            // If there's an error storing, clear any partial data
            clearLoginState(context)
        }
    }

    fun getLoginState(context: Context): String? {
        return try {
            val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val encryptedData = sharedPreferences.getString(KEY_AUTH_TOKEN, null)
            val iv = sharedPreferences.getString("iv", null)?.let { Base64.decode(it, Base64.DEFAULT) }

            if (encryptedData == null || iv == null) {
                Log.e("AuthUtils", "No token found or IV missing")
                // Clear any partial data
                clearLoginState(context)
                return null
            }

            val secretKey = getSecretKey() ?: run {
                Log.e("AuthUtils", "Unable to get secret key")
                clearLoginState(context)
                return null
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decodedData = Base64.decode(encryptedData, Base64.DEFAULT)
            val token = String(cipher.doFinal(decodedData), Charsets.UTF_8)

            // Validate that the token is not empty
            if (token.isBlank()) {
                Log.e("AuthUtils", "Retrieved token is empty")
                clearLoginState(context)
                return null
            }

            Log.d("AuthUtils", "Retrieved token: $token")
            return token
        } catch (e: Exception) {
            Log.e("AuthUtils", "Error retrieving login state", e)
            // On any decryption error, clear the state
            clearLoginState(context)
            return null
        }
    }


    fun clearLoginState(context: Context) {
        try {
            val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.remove(KEY_AUTH_TOKEN)
            editor.remove("iv")
            editor.commit()  // Use commit() for immediate persistence
            Log.d("AuthUtils", "Login state cleared")
        } catch (e: Exception) {
            Log.e("AuthUtils", "Error clearing login state", e)
        }
    }

    fun isPasswordValid(password: String): Boolean {
        if (password.length < 8) return false
        // Check for at least one number or special character
        val hasNumberOrSpecial = password.any { char ->
            char.isDigit() || !char.isLetterOrDigit()
        }
        return hasNumberOrSpecial
    }
}