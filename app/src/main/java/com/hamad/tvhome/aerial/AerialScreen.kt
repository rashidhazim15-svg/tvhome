package com.hamad.tvhome.aerial

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Full-screen Aerial surface shared by the daydream and its preview. Real clips
 * come from Apple's catalogue (fetched through [AerialTls]); when the catalogue
 * cannot be reached the screen says so instead of faking content.
 */
class AerialScreen(context: Context) : FrameLayout(context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = AerialRepository(context)

    private val playerView = PlayerView(context).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        setShutterBackgroundColor(Color.BLACK)
        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        setKeepContentOnPlayerReset(true)
    }

    private val fade = View(context).apply { setBackgroundColor(Color.BLACK) }

    private val status = TextView(context).apply {
        visibility = GONE
        gravity = Gravity.CENTER
        setTextColor(Color.argb(220, 255, 255, 255))
        textSize = 16f
        setLineSpacing(0f, 1.25f)
        letterSpacing = 0.02f
        maxWidth = (640 * resources.displayMetrics.density).toInt()
    }

    private var player: ExoPlayer? = null

    init {
        setBackgroundColor(Color.BLACK)
        contentDescription = "Aerial"
        addView(playerView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(fade, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(
            status,
            LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                leftMargin = (80 * resources.displayMetrics.density).toInt()
                rightMargin = leftMargin
            },
        )
    }

    fun start() {
        scope.launch {
            val clips = runCatching { repository.clips() }
            clips.onFailure { error ->
                Log.w("Aerial", "catalogue failed", error)
                showStatus(
                    "Aerial is unavailable",
                    "The Apple screensaver catalogue could not be reached. " +
                        "Check the network connection and try again.",
                )
            }
            clips.onSuccess { list ->
                Log.i("Aerial", "catalogue ready: ${list.size} clips")
                play(list)
                if (repository.needsRefresh()) {
                    scope.launch {
                        val updated = repository.refresh()
                        Log.i("Aerial", "catalogue refresh: ${updated?.size ?: "failed"}")
                    }
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
        player?.release()
        player = null
    }

    private fun play(clips: List<AerialClip>) {
        val http = OkHttpDataSource.Factory(repository.http)
        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        player = exo

        playerView.player = exo
        exo.repeatMode = Player.REPEAT_MODE_ALL
        exo.shuffleModeEnabled = true
        exo.volume = 0f
        exo.setMediaItems(
            clips.map { clip ->
                MediaItem.Builder()
                    .setMediaId(clip.id)
                    .setUri(clip.videoUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(clip.label)
                            .build(),
                    )
                    .build()
            },
        )
        exo.currentMediaItem?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }?.let { label ->
            // setMediaItems already dispatches the first transition, before addListener.
            contentDescription = "Aerial \u2014 $label"
            Log.i("Aerial", "playing $label")
        }
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val label = mediaItem?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
                contentDescription = if (label != null) "Aerial \u2014 $label" else "Aerial"
                if (label != null) Log.i("Aerial", "playing $label")
                fadeIn()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) fadeIn()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w("Aerial", "clip failed (${error.errorCodeName})", error)
                if (exo.mediaItemCount <= 1) {
                    showStatus(
                        "Aerial can't play on this device",
                        "The screensaver catalogue was reached, but the video codec " +
                            "was rejected (${error.errorCodeName}).",
                    )
                    return
                }
                // Drop the broken clip so the loop never retries it.
                exo.removeMediaItem(exo.currentMediaItemIndex)
                exo.prepare()
                exo.play()
            }
        })

        exo.prepare()
        exo.play()
        fadeIn()
    }

    private fun fadeIn() {
        fade.animate().cancel()
        fade.alpha = 1f
        fade.animate().alpha(0f).setDuration(700L).start()
    }

    private fun showStatus(title: String, body: String) {
        status.text = "$title\n$body"
        status.visibility = VISIBLE
        contentDescription = "$title. $body"
        fade.animate().cancel()
        fade.alpha = 0.55f
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }
}
