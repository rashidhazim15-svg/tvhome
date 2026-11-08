package com.hamad.tvhome.prayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.hamad.tvhome.R
import com.hamad.tvhome.data.TvPrefs
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Typographic, purely Arabic prayer screen: city and dates at the top right,
 * the next prayer in the centre, and the full day's times along the bottom.
 * Everything is drawn from the device's own clock and the configured method.
 */
class PrayerScreensaverView(context: Context) : View(context) {

    private val scale: Float get() = height / 1080f

    private val nameFace: Typeface? =
        ResourcesCompat.getFont(context, R.font.ibm_plex_sans_arabic_medium)
    private val strongFace: Typeface? =
        ResourcesCompat.getFont(context, R.font.ibm_plex_sans_arabic_semibold)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = nameFace
        textAlign = Paint.Align.RIGHT
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
    }

    private var city = ""
    private var gregorian = ""
    private var hijri = ""
    private var methodArabic = ""
    private var day: PrayerDay? = null
    private var nextPrayer: Prayer? = null
    private var nextAt: ZonedDateTime? = null
    private var remaining: Duration? = null

    private val arabicLocale = Locale("ar", "SA")

    fun tick() {
        val prefs = TvPrefs(context)
        val zone = runCatching { ZoneId.of(prefs.timezone) }.getOrDefault(ZoneId.systemDefault())
        val now = ZonedDateTime.now(zone)
        val date = now.toLocalDate()
        city = arabicCity(prefs.city)
        methodArabic = PrayerCalculator.method(prefs.prayerMethod).arabic
        gregorian = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", arabicLocale))
        hijri = runCatching {
            HijrahDate.from(date).format(DateTimeFormatter.ofPattern("d MMMM yyyy", arabicLocale))
        }.getOrDefault("")

        val today = PrayerCalculator.day(
            date,
            prefs.latitude.toDouble(),
            prefs.longitude.toDouble(),
            zone,
            prefs.prayerMethod,
        )
        day = today
        val pair = PrayerCalculator.next(now, prefs.latitude.toDouble(), prefs.longitude.toDouble(), prefs.prayerMethod)
        nextPrayer = pair?.first
        nextAt = pair?.second
        remaining = pair?.second?.let { Duration.between(now, it) }?.takeIf { !it.isNegative }
        contentDescription = buildString {
            append(city)
            if (pair != null) {
                append("\u060c \u0627\u0644\u0635\u0644\u0627\u0629 \u0627\u0644\u0642\u0627\u062f\u0645\u0629 ")
                append(pair.first.arabic)
                append(' ')
                append(arabicTime(pair.second.toLocalTime()))
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = scale
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(
            0f, 0f, w, h,
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, h,
                    Color.rgb(9, 14, 17),
                    Color.rgb(3, 4, 6),
                    Shader.TileMode.CLAMP,
                )
            },
        )
        canvas.drawRect(
            0f, 0f, w, h,
            Paint().apply {
                shader = RadialGradient(
                    w * 0.72f, h * 0.34f, w * 0.55f,
                    intArrayOf(Color.argb(70, 26, 74, 78), Color.TRANSPARENT),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )

        val margin = 80f * s
        val right = w - margin

        // City + dates (top right, RTL).
        paint.typeface = strongFace
        text(canvas, city, right, 92f * s, 38f * s, 1f)
        paint.typeface = nameFace
        text(canvas, gregorian, right, 140f * s, 25f * s, 0.66f)
        text(canvas, hijri, right, 176f * s, 25f * s, 0.52f)

        val today = day
        if (today == null || today.times.isEmpty()) {
            text(canvas, "\u062a\u0639\u0630\u0631 \u062d\u0633\u0627\u0628 \u0627\u0644\u0645\u0648\u0627\u0642\u064a\u062a", right, h * 0.5f, 31f * s, 0.7f)
            drawFooter(canvas, s, w, h)
            return
        }

        // Next prayer, centred as one typographic block.
        val centerX = w * 0.72f
        paint.textAlign = Paint.Align.CENTER
        text(canvas, "\u0627\u0644\u0635\u0644\u0627\u0629 \u0627\u0644\u0642\u0627\u062f\u0645\u0629", centerX, h * 0.34f, 25f * s, 0.6f)
        val next = nextPrayer ?: Prayer.FAJR
        paint.typeface = strongFace
        text(canvas, next.arabic, centerX, h * 0.45f, 76f * s, 1f)
        paint.typeface = nameFace
        val at = nextAt ?: today.at(next)
        text(canvas, at?.toLocalTime()?.let(::arabicTime).orEmpty(), centerX, h * 0.55f, 57f * s, 0.92f)

        remaining?.let { left ->
            val text = "\u0628\u0627\u0642\u064a " + arabicCountdown(left)
            text(canvas, text, centerX, h * 0.625f, 31f * s, 0.62f)
        }

        // Full day, Fajr at the right edge, flowing leftwards.
        val rowRight = w - margin
        val itemWidth = (w - margin * 2f) / 6f
        val hairlineY = h - 205f * s
        canvas.drawRect(
            margin, hairlineY, w - margin, hairlineY + 1.2f * s,
            Paint().apply { color = Color.argb(46, 255, 255, 255) },
        )

        val order = listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
        order.forEachIndexed { index, prayer ->
            val x = rowRight - index * itemWidth
            val isNext = prayer == nextPrayer
            paint.textAlign = Paint.Align.RIGHT
            paint.typeface = if (isNext) strongFace else nameFace
            val alpha = if (isNext) 1f else 0.58f
            text(canvas, prayer.arabic, x, h - 148f * s, 31f * s, alpha)
            paint.typeface = nameFace
            text(
                canvas,
                today.times[prayer]?.let(::arabicTime).orEmpty(),
                x,
                h - 96f * s,
                if (isNext) 31f * s else 29f * s,
                alpha,
            )
        }

        drawFooter(canvas, s, w, h)
    }

    private fun drawFooter(canvas: Canvas, s: Float, w: Float, h: Float) {
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = nameFace
        if (methodArabic.isNotBlank()) {
            text(canvas, methodArabic, 80f * s, h - 40f * s, 23f * s, 0.38f)
        }
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, alpha: Float) {
        if (value.isBlank()) return
        paint.textSize = size
        paint.color = Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), 255, 255, 255)
        canvas.drawText(value, x, y, paint)
    }

    private fun arabicTime(time: LocalTime): String =
        arabicDigits(String.format(Locale.US, "%02d:%02d", time.hour, time.minute))

    private fun arabicCountdown(duration: Duration): String {
        val total = duration.seconds.coerceAtLeast(0)
        return arabicDigits(
            String.format(Locale.US, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60),
        )
    }

    private fun arabicDigits(value: String): String = buildString {
        value.forEach { c ->
            append(if (c in '0'..'9') '\u0660' + (c - '0') else c)
        }
    }

    private fun arabicCity(city: String): String = when (city.lowercase(Locale.US)) {
        "riyadh" -> "\u0627\u0644\u0631\u064a\u0627\u0636"
        "makkah", "mecca" -> "\u0645\u0643\u0629 \u0627\u0644\u0645\u0643\u0631\u0645\u0629"
        "cairo" -> "\u0627\u0644\u0642\u0627\u0647\u0631\u0629"
        "dubai" -> "\u062f\u0628\u064a"
        "istanbul" -> "\u0625\u0633\u0637\u0646\u0628\u0648\u0644"
        else -> city
    }
}
