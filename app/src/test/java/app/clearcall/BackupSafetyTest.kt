package app.clearcall

import app.clearcall.data.isSafeFileName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Backup archives are untrusted input: only plain file names may ever reach the disk. */
class BackupSafetyTest {

    @Test
    fun acceptsNormalRecordingNames() {
        assertTrue(isSafeFileName("3f2a9c1e-7b4d-4e8a-9c51-0d6f2b7a1e34.m4a"))
        assertTrue(isSafeFileName("3f2a9c1e-7b4d-4e8a-9c51-0d6f2b7a1e34.wav"))
        assertTrue(isSafeFileName("recording_01.m4a"))
    }

    @Test
    fun rejectsPathTraversal() {
        assertFalse(isSafeFileName("../evil.m4a"))
        assertFalse(isSafeFileName("..\\evil.m4a"))
        assertFalse(isSafeFileName("a/b.m4a"))
        assertFalse(isSafeFileName("/etc/passwd"))
        assertFalse(isSafeFileName("a..b.m4a"))
    }

    @Test
    fun rejectsHiddenEmptyAndOddNames() {
        assertFalse(isSafeFileName(""))
        assertFalse(isSafeFileName(".hidden"))
        assertFalse(isSafeFileName("has space.m4a"))
        assertFalse(isSafeFileName("semi;colon.m4a"))
        assertFalse(isSafeFileName("a".repeat(101)))
    }
}
