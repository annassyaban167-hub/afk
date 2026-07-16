package com.limitedafk.appblocker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlockedAppDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: BlockedAppDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.blockedAppDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndReadApp_returnsCorrectData() {
        val app = BlockedApp("com.test", "Test App", 30, 1800, "CLOSE_AFTER_TIMEOUT")
        dao.insert(app)

        val result = dao.getApp("com.test")
        assertNotNull(result)
        assertEquals("Test App", result?.appName)
        assertEquals(30, result?.limitMinutes)
        assertEquals(1800, result?.remainingSeconds)
    }

    @Test
    fun insertDuplicate_replacesExisting() {
        val app1 = BlockedApp("com.test", "Test", 30, 1800, "CLOSE_AFTER_TIMEOUT")
        dao.insert(app1)
        val app2 = BlockedApp("com.test", "Updated", 15, 900, "CLOSE_IMMEDIATELY")
        dao.insert(app2)

        val result = dao.getApp("com.test")
        assertEquals("Updated", result?.appName)
        assertEquals(15, result?.limitMinutes)
    }

    @Test
    fun deleteApp_removesIt() {
        val app = BlockedApp("com.test", "Test", 30, 1800, "CLOSE_AFTER_TIMEOUT")
        dao.insert(app)
        dao.delete(app)

        assertNull(dao.getApp("com.test"))
    }

    @Test
    fun getAllApps_returnsAll() {
        dao.insert(BlockedApp("com.a", "A", 10, 600, "CLOSE_AFTER_TIMEOUT"))
        dao.insert(BlockedApp("com.b", "B", 20, 1200, "CLOSE_AFTER_TIMEOUT"))

        val all = dao.getAll()
        assertEquals(2, all.size)
    }

    @Test
    fun updateApp_changesFields() {
        val app = BlockedApp("com.test", "Test", 30, 1800, "CLOSE_AFTER_TIMEOUT")
        dao.insert(app)

        val updated = app.copy(remainingSeconds = 0, isBlocked = true)
        dao.update(updated)

        val result = dao.getApp("com.test")
        assertEquals(0, result?.remainingSeconds)
        assertEquals(true, result?.isBlocked)
    }
}
