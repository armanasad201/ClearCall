package app.clearcall

import app.clearcall.core.Format
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class FormatTest {

    @Test
    fun duration_underAnHour() {
        assertEquals("0:00", Format.duration(0))
        assertEquals("0:09", Format.duration(9_400))
        assertEquals("1:15", Format.duration(75_000))
        assertEquals("59:59", Format.duration(3_599_999))
    }

    @Test
    fun duration_overAnHour() {
        assertEquals("1:02:05", Format.duration(3_725_000))
        assertEquals("10:00:00", Format.duration(36_000_000))
    }

    @Test
    fun duration_negativeIsClampedToZero() {
        assertEquals("0:00", Format.duration(-5_000))
    }

    @Test
    fun totalDuration_picksCompactUnit() {
        assertEquals("45s", Format.totalDuration(45_000))
        assertEquals("14m", Format.totalDuration(14 * 60_000L))
        assertEquals("2h 14m", Format.totalDuration((2 * 3600 + 14 * 60) * 1000L))
    }

    @Test
    fun size_scalesUnits() {
        assertEquals("512 B", Format.size(512))
        assertEquals("2 KB", Format.size(2 * 1024))
        assertEquals("5.0 MB", Format.size(5L * 1024 * 1024))
        assertEquals("3.00 GB", Format.size(3L * 1024 * 1024 * 1024))
    }

    @Test
    fun dayLabel_todayYesterdayThisYearAndOlder() {
        val zone: ZoneId = ZoneOffset.UTC
        val today = LocalDate.of(2026, 11, 18)
        fun at(date: LocalDate) = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        assertEquals("Today", Format.dayLabel(at(today), today, zone, Locale.US))
        assertEquals("Yesterday", Format.dayLabel(at(today.minusDays(1)), today, zone, Locale.US))
        assertEquals("Mon, Nov 16", Format.dayLabel(at(LocalDate.of(2026, 11, 16)), today, zone, Locale.US))
        assertEquals("Mar 2, 2025", Format.dayLabel(at(LocalDate.of(2025, 3, 2)), today, zone, Locale.US))
    }

    @Test
    fun dayLabel_respectsTheGivenTimeZone() {
        // 23:30 UTC on the 17th is already the 18th in Dhaka (UTC+6).
        val instant = LocalDate.of(2026, 11, 17).atTime(23, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        val today = LocalDate.of(2026, 11, 18)
        assertEquals("Today", Format.dayLabel(instant, today, ZoneId.of("Asia/Dhaka"), Locale.US))
        assertEquals("Yesterday", Format.dayLabel(instant, today, ZoneOffset.UTC, Locale.US))
    }
}
