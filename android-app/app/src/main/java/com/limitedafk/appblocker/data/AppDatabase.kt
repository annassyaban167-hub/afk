package com.limitedafk.appblocker.data

import android.content.Context

class AppDatabase private constructor(context: Context) {
    private val dao = BlockedAppDao(context.applicationContext)

    fun blockedAppDao(): BlockedAppDao = dao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = AppDatabase(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
