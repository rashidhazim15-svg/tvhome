package com.hamad.tvhome.prayer

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

enum class Prayer(val arabic: String, val english: String) {
    FAJR("\u0627\u0644\u0641\u062c\u0631", "Fajr"),
    SUNRISE("\u0627\u0644\u0634\u0631\u0648\u0642", "Sunrise"),
    DHUHR("\u0627\u0644\u0638\u0647\u0631", "Dhuhr"),
    ASR("\u0627\u0644\u0639\u0635\u0631", "Asr"),
    MAGHRIB("\u0627\u0644\u0645\u063a\u0631\u0628", "Maghrib"),
    ISHA("\u0627\u0644\u0639\u0634\u0627\u0621", "Isha"),
}

data class PrayerMethod(
    val key: String,
    val arabic: String,
    val fajrAngle: Double,
    val ishaAngle: Double? = null,
    val ishaMinutes: Int? = null,
)

data class PrayerDay(
    val date: LocalDate,
    val times: Map<Prayer, LocalTime>,
    val zone: ZoneId,
) {
    fun at(prayer: Prayer): ZonedDateTime? = times[prayer]?.let { date.atTime(it).atZone(zone) }
}

/**
 * Standard solar prayer-time math (the same equations used by PrayTimes):
 * declination + equation of time for the date, then hour angles for each prayer.
 * Everything is computed locally; no network is required and nothing is invented
 * when the sun never reaches an angle (the entry is simply omitted).
 */
object PrayerCalculator {

    val methods = listOf(
        PrayerMethod("MWL", "\u0627\u0644\u0631\u0627\u0628\u0637\u0629 \u0627\u0644\u0639\u0627\u0644\u0645\u064a\u0629", 18.0, 17.0),
        PrayerMethod("UMM_AL_QURA", "\u0623\u0645 \u0627\u0644\u0642\u0631\u0649", 18.5, ishaMinutes = 90),
        PrayerMethod("EGYPTIAN", "\u0627\u0644\u0647\u064a\u0626\u0629 \u0627\u0644\u0645\u0635\u0631\u064a\u0629", 19.5, 17.5),
        PrayerMethod("KARACHI", "\u062c\u0627\u0645\u0639\u0629 \u0643\u0631\u0627\u062a\u0634\u064a", 18.0, 18.0),
    )

    fun method(key: String): PrayerMethod = methods.firstOrNull { it.key == key } ?: methods[0]

    fun day(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        methodKey: String,
    ): PrayerDay {
        val method = method(methodKey)
        val noon = date.atTime(12, 0).atZone(zone)
        val tzHours = zone.rules.getOffset(noon.toInstant()).totalSeconds / 3600.0

        val d = date.toEpochDay() + 2440587.5 - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(Math.toRadians(g)) + 0.020 * sin(Math.toRadians(2 * g)))
        val e = 23.439 - 0.00000036 * d
        val ra = Math.toDegrees(
            atan2(
                cos(Math.toRadians(e)) * sin(Math.toRadians(l)),
                cos(Math.toRadians(l)),
            ),
        ) / 15.0
        val eqT = q / 15.0 - fixHour(ra)
        val decl = Math.toDegrees(asin(sin(Math.toRadians(e)) * sin(Math.toRadians(l))))

        val dhuhr = 12 + tzHours - longitude / 15.0 - eqT
        val times = LinkedHashMap<Prayer, LocalTime>()

        fun put(prayer: Prayer, hours: Double?) {
            if (hours == null || hours.isNaN() || hours.isInfinite()) return
            times[prayer] = minutesToTime(hours * 60.0)
        }

        fun hourAngle(altitude: Double): Double? {
            val numerator = sin(Math.toRadians(altitude)) -
                sin(Math.toRadians(decl)) * sin(Math.toRadians(latitude))
            val denominator = cos(Math.toRadians(decl)) * cos(Math.toRadians(latitude))
            val value = numerator / denominator
            if (value < -1.0 || value > 1.0) return null
            return Math.toDegrees(acos(value)) / 15.0
        }

        /** Hour angle for the sun being [angle] degrees below the horizon. */
        fun depression(angle: Double): Double? = hourAngle(-angle)

        val fajrOffset = depression(method.fajrAngle)
        put(Prayer.FAJR, fajrOffset?.let { dhuhr - it })
        put(Prayer.SUNRISE, depression(0.833)?.let { dhuhr - it })
        put(Prayer.DHUHR, dhuhr)
        put(Prayer.MAGHRIB, depression(0.833)?.let { dhuhr + it })

        val asrAngle = Math.toDegrees(atan(1.0 / (1.0 + tan(Math.toRadians(abs(latitude - decl))))))
        put(Prayer.ASR, hourAngle(asrAngle)?.let { dhuhr + it })

        val isha = if (method.ishaMinutes != null) {
            times[Prayer.MAGHRIB]?.let { it.toSecondOfDay() / 60.0 + method.ishaMinutes }
        } else {
            method.ishaAngle?.let { angle -> depression(angle)?.let { dhuhr + it } }
        }
        // The minutes-based method yields minutes, the angle-based one yields hours.
        if (isha != null) {
            times[Prayer.ISHA] = minutesToTime(if (method.ishaMinutes != null) isha else isha * 60.0)
        }

        return PrayerDay(date, times, zone)
    }

    /** The next prayer at or after [now], rolling over to tomorrow's Fajr. */
    fun next(now: ZonedDateTime, latitude: Double, longitude: Double, methodKey: String): Pair<Prayer, ZonedDateTime>? {
        val today = day(now.toLocalDate(), latitude, longitude, now.zone, methodKey)
        val ordered = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
        ordered.forEach { prayer ->
            val at = today.at(prayer) ?: return@forEach
            if (!at.isBefore(now)) return prayer to at
        }
        val tomorrow = day(now.toLocalDate().plusDays(1), latitude, longitude, now.zone, methodKey)
        val fajr = tomorrow.at(Prayer.FAJR) ?: return null
        return Prayer.FAJR to fajr
    }

    private fun minutesToTime(minutes: Double): LocalTime {
        val wrapped = ((minutes % 1440.0) + 1440.0) % 1440.0
        val total = Math.round(wrapped).toInt() % 1440
        return LocalTime.of(total / 60, total % 60)
    }

    private fun fixAngle(angle: Double): Double = ((angle % 360.0) + 360.0) % 360.0

    private fun fixHour(hour: Double): Double = ((hour % 24.0) + 24.0) % 24.0

    private fun atan2(y: Double, x: Double): Double = Math.atan2(y, x)
}
