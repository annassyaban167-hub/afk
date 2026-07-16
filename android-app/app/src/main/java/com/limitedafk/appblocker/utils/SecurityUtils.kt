package com.limitedafk.appblocker.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest

object SecurityUtils {
    private const val PREFS_FILE = "secure_prefs"
    private const val KEY_PIN_HASH = "pin_hash"

    private fun getEncryptedPrefs(context: Context) = EncryptedSharedPreferences.create(
        PREFS_FILE,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun hashPin(pin: String): String {
        val bytes = pin.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun hasPin(context: Context): Boolean {
        return getEncryptedPrefs(context).contains(KEY_PIN_HASH)
    }

    fun savePin(context: Context, pin: String): Boolean {
        if (pin.length != 6 || !pin.all { it.isDigit() }) return false
        val hash = hashPin(pin)
        getEncryptedPrefs(context).edit().putString(KEY_PIN_HASH, hash).apply()
        return true
    }

    fun verifyPin(context: Context, inputPin: String): Boolean {
        val storedHash = getEncryptedPrefs(context).getString(KEY_PIN_HASH, null) ?: return false
        return hashPin(inputPin) == storedHash
    }
}
