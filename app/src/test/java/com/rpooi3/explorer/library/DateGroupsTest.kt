package com.rpooi3.explorer.library

import com.rpooi3.explorer.library.DateGroups.Day
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Day groups of the history and downloads lists. */
class DateGroupsTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    private val now = at(2026, 11, 13, 15)

    @Test
    fun sameDay_isToday_fromMidnightToMidnight() {
        assertEquals(Day.TODAY, DateGroups.dayOf(at(2026, 11, 13, 0, 0), now, utc))
        assertEquals(Day.TODAY, DateGroups.dayOf(at(2026, 11, 13, 23, 59), now, utc))
    }

    @Test
    fun theDayBefore_isYesterday_evenIfLessThan24HoursAgo() {
        assertEquals(Day.YESTERDAY, DateGroups.dayOf(at(2026, 11, 12, 23, 59), now, utc))
        assertEquals(Day.YESTERDAY, DateGroups.dayOf(at(2026, 11, 12, 0, 0), now, utc))
    }

    @Test
    fun anythingOlder_isEarlier() {
        assertEquals(Day.EARLIER, DateGroups.dayOf(at(2026, 11, 11, 23, 59), now, utc))
        assertEquals(Day.EARLIER, DateGroups.dayOf(at(2025, 11, 13), now, utc))
    }

    @Test
    fun yesterday_worksAcrossTheTurnOfTheYear() {
        assertEquals(Day.YESTERDAY, DateGroups.dayOf(at(2025, 12, 31), at(2026, 1, 1), utc))
    }

    @Test
    fun theSameDayOfAnotherYear_isADifferentGroup() {
        assertNotEquals(DateGroups.dayKey(at(2025, 11, 13), utc), DateGroups.dayKey(at(2026, 11, 13), utc))
        assertEquals(DateGroups.dayKey(at(2026, 11, 13, 1), utc), DateGroups.dayKey(at(2026, 11, 13, 22), utc))
    }

    @Test
    fun laterDays_haveLargerKeys() {
        assertTrue(DateGroups.dayKey(at(2026, 1, 1), utc) > DateGroups.dayKey(at(2025, 12, 31), utc))
    }

    @Test
    fun theDay_dependsOnTheTimeZone() {
        // 22:00 UTC on the 12th is already the 13th in Dhaka (UTC+6).
        val dhaka = TimeZone.getTimeZone("Asia/Dhaka")
        val time = at(2026, 11, 12, 22)
        assertEquals(Day.YESTERDAY, DateGroups.dayOf(time, now, utc))
        assertEquals(Day.TODAY, DateGroups.dayOf(time, now, dhaka))
    }

    @Test
    fun headings_nameTodayAndYesterday_andGiveTheFullDate() {
        fun heading(time: Long) = DateGroups.heading(time, now, "Today", "Yesterday", Locale.US, utc)

        assertEquals("Today – Friday, November 13, 2026", heading(at(2026, 11, 13, 9)))
        assertEquals("Yesterday – Thursday, November 12, 2026", heading(at(2026, 11, 12, 9)))
        assertEquals("Monday, November 9, 2026", heading(at(2026, 11, 9, 9)))
    }
}
