package com.limitedafk.appblocker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class BlockedAppDao(context: Context) {

    private val dbHelper = DbHelper(context)

    companion object {
        private const val TABLE = "blocked_apps"
        private const val COL_PKG = "packageName"
        private const val COL_NAME = "appName"
        private const val COL_LIMIT = "limitMinutes"
        private const val COL_REMAINING = "remainingSeconds"
        private const val COL_MODE = "mode"
        private const val COL_BLOCKED = "isBlocked"
    }

    private class DbHelper(context: Context) : SQLiteOpenHelper(
        context, "app_blocker_db", null, 2
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE $TABLE (
                    $COL_PKG TEXT PRIMARY KEY,
                    $COL_NAME TEXT NOT NULL,
                    $COL_LIMIT INTEGER NOT NULL,
                    $COL_REMAINING INTEGER NOT NULL,
                    $COL_MODE TEXT NOT NULL DEFAULT 'CLOSE_AFTER_TIMEOUT',
                    $COL_BLOCKED INTEGER NOT NULL DEFAULT 0
                )
            """)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            db.setForeignKeyConstraintsEnabled(true)
        }
    }

    private fun <T> read(block: (SQLiteDatabase) -> T): T {
        val db = dbHelper.readableDatabase
        try { return block(db) } finally { db.close() }
    }

    private fun <T> write(block: (SQLiteDatabase) -> T): T {
        val db = dbHelper.writableDatabase
        try { return block(db) } finally { db.close() }
    }

    // add enabled column support
    private fun cursorToApp(c: android.database.Cursor): BlockedApp {
        val colEnabled = try { c.getColumnIndexOrThrow("enabled") } catch (_: Exception) { -1 }
        return BlockedApp(
            packageName = c.getString(c.getColumnIndexOrThrow(COL_PKG)),
            appName = c.getString(c.getColumnIndexOrThrow(COL_NAME)),
            limitMinutes = c.getInt(c.getColumnIndexOrThrow(COL_LIMIT)),
            remainingSeconds = c.getInt(c.getColumnIndexOrThrow(COL_REMAINING)),
            mode = c.getString(c.getColumnIndexOrThrow(COL_MODE)),
            isBlocked = c.getInt(c.getColumnIndexOrThrow(COL_BLOCKED)) != 0,
            enabled = if (colEnabled >= 0) c.getInt(colEnabled) != 0 else true
        )
    }

    fun getAll(): List<BlockedApp> = read { db ->
        val list = mutableListOf<BlockedApp>()
        val c = db.query(TABLE, null, null, null, null, null, null)
        while (c.moveToNext()) list.add(cursorToApp(c))
        c.close()
        list
    }

    fun getApp(packageName: String): BlockedApp? = read { db ->
        val c = db.query(TABLE, null, "$COL_PKG = ?", arrayOf(packageName), null, null, null)
        val app = if (c.moveToFirst()) cursorToApp(c) else null
        c.close()
        app
    }

    fun insert(app: BlockedApp) = write { db ->
        db.insertWithOnConflict(TABLE, null, toValues(app), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun update(app: BlockedApp) = write { db ->
        db.update(TABLE, toValues(app), "$COL_PKG = ?", arrayOf(app.packageName))
    }

    fun delete(app: BlockedApp) = write { db ->
        db.delete(TABLE, "$COL_PKG = ?", arrayOf(app.packageName))
    }

    fun resetAllRemainingTime() = write { db ->
        db.execSQL("UPDATE $TABLE SET $COL_REMAINING = $COL_LIMIT * 60, $COL_BLOCKED = 0")
    }

    fun getBlockedCount(): Int = read { db ->
        val c = db.rawQuery("SELECT COUNT(*) FROM $TABLE WHERE $COL_BLOCKED = 1", null)
        val count = if (c.moveToFirst()) c.getInt(0) else 0
        c.close()
        count
    }

    private fun toValues(app: BlockedApp): ContentValues = ContentValues().apply {
        put(COL_PKG, app.packageName)
        put(COL_NAME, app.appName)
        put(COL_LIMIT, app.limitMinutes)
        put(COL_REMAINING, app.remainingSeconds)
        put(COL_MODE, app.mode)
        put(COL_BLOCKED, if (app.isBlocked) 1 else 0)
        put("enabled", if (app.enabled) 1 else 0)
    }
}
