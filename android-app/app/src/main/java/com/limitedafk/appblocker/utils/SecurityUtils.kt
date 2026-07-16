package com.limitedafk.appblocker.utils

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

object SecurityUtils {
    private const val PREFS_FILE = "secure_prefs"
    private const val KEY_PIN_HASH = "pin_hash"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(pin.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun hasPin(context: Context): Boolean =
        getPrefs(context).contains(KEY_PIN_HASH)

    fun savePin(context: Context, pin: String): Boolean {
        if (pin.length != 6 || !pin.all { it.isDigit() }) return false
        getPrefs(context).edit().putString(KEY_PIN_HASH, hashPin(pin)).apply()
        return true
    }

    fun verifyPin(context: Context, inputPin: String): Boolean {
        val storedHash = getPrefs(context).getString(KEY_PIN_HASH, null) ?: return false
        return hashPin(inputPin) == storedHash
    }
}
