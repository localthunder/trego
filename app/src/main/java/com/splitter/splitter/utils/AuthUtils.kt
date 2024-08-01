package com.splitter.splitter.utils

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

    private fun getSecretKey(): SecretKey {
        val keyStore = getKeyStore()
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            createKey()
        }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    fun storeLoginState(context: Context, token: String) {
        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(token.toByteArray(Charsets.UTF_8))

        Log.d("AuthUtils", "Storing token: $token")

        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(KEY_AUTH_TOKEN, Base64.encodeToString(encryptedData, Base64.DEFAULT))
        editor.putString("iv", Base64.encodeToString(iv, Base64.DEFAULT))
        editor.apply()
        Log.d("AuthUtils", "Token stored successfully")
    }

    fun getLoginState(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedData = sharedPreferences.getString(KEY_AUTH_TOKEN, null)
        val iv = sharedPreferences.getString("iv", null)?.let { Base64.decode(it, Base64.DEFAULT) }

        if (encryptedData == null || iv == null) {
            Log.e("AuthUtils", "No token found or IV missing")
            return null
        }

        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val decodedData = Base64.decode(encryptedData, Base64.DEFAULT)
        val token = String(cipher.doFinal(decodedData), Charsets.UTF_8)
        Log.d("AuthUtils", "Retrieved token: $token")
        return token
    }


    fun clearLoginState(context: Context) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.remove(KEY_AUTH_TOKEN)
        editor.remove("iv")
        editor.apply()
    }
}
