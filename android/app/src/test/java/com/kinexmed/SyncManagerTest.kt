package com.kinexmed

import com.kinexmed.sync.SyncManager
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncManagerTest {

    @Test
    fun testExponentialBackoffGrowthAndBounds() {
        val delay0 = SyncManager.computeBackoffDelayMs(0, initialDelayMs = 1000L, maxDelayMs = 30000L)
        val delay1 = SyncManager.computeBackoffDelayMs(1, initialDelayMs = 1000L, maxDelayMs = 30000L)
        val delay2 = SyncManager.computeBackoffDelayMs(2, initialDelayMs = 1000L, maxDelayMs = 30000L)
        val delayMax = SyncManager.computeBackoffDelayMs(10, initialDelayMs = 1000L, maxDelayMs = 30000L)

        assertTrue("Attempt 0 should be at least 1000ms", delay0 >= 1000L)
        assertTrue("Attempt 1 should be at least 2000ms", delay1 >= 2000L)
        assertTrue("Attempt 2 should be at least 4000ms", delay2 >= 4000L)
        assertTrue("Attempt 10 should be capped at maxDelayMs", delayMax <= 30000L)
    }

    @Test
    fun testBackoffNeverExceedsMaxCap() {
        for (attempt in 0..15) {
            val delay = SyncManager.computeBackoffDelayMs(attempt, initialDelayMs = 500L, maxDelayMs = 10000L)
            assertTrue("Delay must not exceed max cap of 10000ms", delay <= 10000L)
            assertTrue("Delay must be at least initial delay", delay >= 500L)
        }
    }
}

