package com.hamad.tvhome.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

class PrayerCalculatorTest {

    private val riyadh = ZoneId.of("Asia/Riyadh")
    private val latitude = 24.7136
    private val longitude = 46.6753
    private val date = LocalDate.of(2026, 11, 8)

    private fun day(method: String) =
        PrayerCalculator.day(date, latitude, longitude, riyadh, method)

    private fun assertNear(expected: String, actual: LocalTime?, toleranceSeconds: Long = 120) {
        assertNotNull(actual)
        val delta = abs(
            actual!!.toSecondOfDay().toLong() -
                LocalTime.parse(expected).toSecondOfDay().toLong(),
        )
        assertTrue(
            "expected $expected, got $actual (delta ${delta}s)",
            delta <= toleranceSeconds,
        )
    }

    @Test
    fun `riyadh mwl matches the aladhan reference`() {
        val day = day("MWL")
        assertNear("04:47", day.times[Prayer.FAJR])
        assertNear("06:05", day.times[Prayer.SUNRISE])
        assertNear("11:37", day.times[Prayer.DHUHR])
        assertNear("14:47", day.times[Prayer.ASR])
        assertNear("17:09", day.times[Prayer.MAGHRIB])
        assertNear("18:23", day.times[Prayer.ISHA])
    }

    @Test
    fun `prayers are ordered through the day`() {
        val times = day("MWL").times
        val order = listOf(
            Prayer.FAJR,
            Prayer.SUNRISE,
            Prayer.DHUHR,
            Prayer.ASR,
            Prayer.MAGHRIB,
            Prayer.ISHA,
        )
        order.zipWithNext().forEach { (first, second) ->
            assertTrue(
                "$first ${times[first]} must precede $second ${times[second]}",
                times.getValue(first) < times.getValue(second),
            )
        }
    }

    @Test
    fun `umm al qura uses ninety minutes after maghrib for isha`() {
        val times = day("UMM_AL_QURA").times
        val gap = Duration.between(times.getValue(Prayer.MAGHRIB), times.getValue(Prayer.ISHA))
        assertTrue(
            "isha-maghrib was ${gap.toMinutes()} minutes",
            abs(gap.toMinutes() - 90) <= 1,
        )
    }

    @Test
    fun `next prayer rolls over to tomorrow fajr`() {
        val onDate = date.atTime(23, 0).atZone(riyadh)
        val next = PrayerCalculator.next(onDate, latitude, longitude, "MWL")
        assertNotNull(next)
        assertEquals(Prayer.FAJR, next!!.first)
        assertEquals(date.plusDays(1), next.second.toLocalDate())

        val early = PrayerCalculator.next(onDate.withHour(3), latitude, longitude, "MWL")
        assertEquals(Prayer.FAJR, early!!.first)
        assertEquals(date, early.second.toLocalDate())
    }

    @Test
    fun `next prayer returns the current prayer exactly at its time`() {
        val day = day("MWL")
        val fajr = day.at(Prayer.FAJR)!!
        val next = PrayerCalculator.next(fajr, latitude, longitude, "MWL")
        assertEquals(Prayer.FAJR, next!!.first)
        assertEquals(fajr, next.second)
    }

    @Test
    fun `no times are invented when the sun never reaches the angle`() {
        // Midsummer above the Arctic circle: the sun never sets.
        val tromso = PrayerCalculator.day(
            LocalDate.of(2026, 6, 21),
            69.6492,
            18.9553,
            ZoneId.of("Europe/Oslo"),
            "MWL",
        )
        assertNull(tromso.times[Prayer.FAJR])
        assertNull(tromso.times[Prayer.MAGHRIB])
        assertNull(tromso.times[Prayer.ISHA])
        assertNotNull(tromso.times[Prayer.DHUHR])
    }

    @Test
    fun `unknown method falls back to mwl`() {
        assertEquals("MWL", PrayerCalculator.method("UNKNOWN").key)
    }

    @Test
    fun `day exposes zoned datetime per prayer`() {
        val day = day("MWL")
        val asr = day.at(Prayer.ASR)
        assertNotNull(asr)
        assertEquals(riyadh, asr!!.zone)
        assertNotNull(ZonedDateTime.of(date, asr.toLocalTime(), riyadh))
    }
}
