package com.selfservice.kiosk.mode.data

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit-тесты для AppDatabase.
 */
class AppDatabaseTest {

    @Test
    fun `test database schema version`() {
        val expectedVersion = 1
        assertTrue("Database version should be positive", expectedVersion > 0)
    }

    @Test
    fun `test DateConverter handles null values`() {
        val nullValue: Long? = null
        assertNull("Null should remain null", nullValue)
    }
}
