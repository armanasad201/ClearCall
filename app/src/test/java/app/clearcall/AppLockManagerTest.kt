package app.clearcall

import app.clearcall.security.AppLockManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockManagerTest {

    private var now = 1_000L
    private fun manager() = AppLockManager(clock = { now })

    @Test
    fun startsLocked() {
        assertFalse(manager().unlocked.value)
    }

    @Test
    fun staysUnlockedForAShortTrip() {
        val m = manager()
        m.markUnlocked()
        m.onBackgrounded()
        now += 5_000 // share sheet, file picker, rotation
        m.onForegrounded()
        assertTrue(m.unlocked.value)
    }

    @Test
    fun locksAfterTheGracePeriod() {
        val m = manager()
        m.markUnlocked()
        m.onBackgrounded()
        now += AppLockManager.GRACE_MS + 1
        m.onForegrounded()
        assertFalse(m.unlocked.value)
    }

    @Test
    fun returningWhileLockedNeverUnlocks() {
        val m = manager()
        m.onBackgrounded()
        now += 1_000
        m.onForegrounded()
        assertFalse(m.unlocked.value)
    }

    @Test
    fun foregroundWithoutAPriorBackgroundDoesNotLock() {
        val m = manager()
        m.markUnlocked()
        now += 10 * AppLockManager.GRACE_MS
        m.onForegrounded()
        assertTrue(m.unlocked.value)
    }

    @Test
    fun explicitLockWins() {
        val m = manager()
        m.markUnlocked()
        m.lock()
        assertFalse(m.unlocked.value)
    }
}
