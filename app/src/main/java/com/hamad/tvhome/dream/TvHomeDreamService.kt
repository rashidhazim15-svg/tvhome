package com.hamad.tvhome.dream

import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import com.hamad.tvhome.aerial.AerialScreen
import com.hamad.tvhome.data.TvPrefs
import com.hamad.tvhome.prayer.PrayerScreensaverView

/**
 * The single system daydream ("screen saver"). It shows whichever saver the
 * user picked in Settings, so switching between Aerial and the Arabic prayer
 * screen actually changes what the system plays when the TV idles. Keeping one
 * dream component in the system settings avoids a second system picker.
 */
class TvHomeDreamService : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private var aerial: AerialScreen? = null
    private var prayer: PrayerScreensaverView? = null

    private val ticker = object : Runnable {
        override fun run() {
            prayer?.tick()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        setFullscreen(true)
        if (TvPrefs(this).screenSaverId == "aerial") {
            val view = AerialScreen(this)
            aerial = view
            setContentView(view)
            view.start()
        } else {
            val view = PrayerScreensaverView(this)
            prayer = view
            setContentView(view)
            view.tick()
            handler.postDelayed(ticker, 1_000L)
        }
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(ticker)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        aerial?.stop()
        aerial = null
        prayer = null
        super.onDetachedFromWindow()
    }
}
