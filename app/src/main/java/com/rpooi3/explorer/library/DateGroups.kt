package com.rpooi3.explorer.library

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Sorts timestamps into the day groups the history and downloads lists show:
 * today, yesterday, and one group per earlier day.
 */
object DateGroups {

    /** Which day a timestamp falls on, relative to "now". */
    enum class Day { TODAY, YESTERDAY, EARLIER }

    /** A number that is the same for all timestamps of one calendar day, and larger for later days. */
    @JvmStatic
    @JvmOverloads
    fun dayKey(time: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = time }
        return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
    }

    @JvmStatic
    @JvmOverloads
    fun dayOf(time: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): Day {
        val yesterday = Calendar.getInstance(zone).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        return when (dayKey(time, zone)) {
            dayKey(now, zone) -> Day.TODAY
            dayKey(yesterday, zone) -> Day.YESTERDAY
            else -> Day.EARLIER
        }
    }

    /**
     * The heading of a day group, e.g. "Today – Friday, November 13, 2026".
     *
     * @param today the word for today in the user's language
     * @param yesterday the word for yesterday in the user's language
     */
    @JvmStatic
    @JvmOverloads
    fun heading(
        time: Long,
        now: Long,
        today: String,
        yesterday: String,
        locale: Locale = Locale.getDefault(),
        zone: TimeZone = TimeZone.getDefault(),
    ): String {
        val date = DateFormat.getDateInstance(DateFormat.FULL, locale).apply { timeZone = zone }.format(Date(time))
        return when (dayOf(time, now, zone)) {
            Day.TODAY -> "$today – $date"
            Day.YESTERDAY -> "$yesterday – $date"
            Day.EARLIER -> date
        }
    }
}
