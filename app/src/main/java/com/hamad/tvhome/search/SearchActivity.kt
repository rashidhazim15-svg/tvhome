package com.hamad.tvhome.search

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamad.tvhome.detail.DetailActivity
import com.hamad.tvhome.launcher.MediaItem
import com.hamad.tvhome.launcher.MediaType
import com.hamad.tvhome.ui.LocalTvMetrics
import com.hamad.tvhome.ui.LocalTvType
import com.hamad.tvhome.ui.TvMetrics
import com.hamad.tvhome.ui.TvType

class SearchActivity : ComponentActivity() {

    private val vm: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Deep link / launcher "open details" path.
        val tmdbId = intent.getIntExtra("tmdb_id", 0)
        if (tmdbId > 0) {
            openDetail(
                tmdbId = tmdbId,
                type = intent.getStringExtra("tmdb_type").orEmpty(),
                title = intent.getStringExtra("title").orEmpty(),
            )
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
                val metrics = remember(maxHeight) {
                    TvMetrics((maxHeight.value / 1080f).coerceAtLeast(0.35f))
                }
                val type = remember(metrics) { TvType(metrics) }
                CompositionLocalProvider(LocalTvMetrics provides metrics, LocalTvType provides type) {
                    // tvOS UI is LTR even on an Arabic system locale.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        val state by vm.state.collectAsStateWithLifecycle()
                        SearchScreen(state = state)
                    }
                }
            }
        }
        hideSystemUi()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (down) vm.move(SearchDir.Left)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (down) vm.move(SearchDir.Right)
            KeyEvent.KEYCODE_DPAD_UP -> if (down) vm.move(SearchDir.Up)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (down) vm.move(SearchDir.Down)

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> {
                if (down) vm.activate()?.let { item ->
                    startActivity(
                        Intent(this, DetailActivity::class.java)
                            .putExtra("tmdb_id", item.tmdbId)
                            .putExtra("tmdb_type", item.type.key)
                            .putExtra("title", item.title),
                    )
                }
                return true
            }

            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> {
                if (down && vm.back()) finish()
                return true
            }

            else -> {
                if (down && !event.isCtrlPressed && !event.isAltPressed) {
                    val char = event.unicodeChar.toChar()
                    if (char.isLetterOrDigit()) {
                        vm.append(char.toString())
                        return true
                    }
                }
            }
        }
        return true
    }

    private fun openDetail(tmdbId: Int, type: String, title: String) {
        startActivity(
            Intent(this, DetailActivity::class.java)
                .putExtra("tmdb_id", tmdbId)
                .putExtra("tmdb_type", type.ifBlank { "movie" })
                .putExtra("title", title),
        )
    }

    private fun hideSystemUi() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

val MediaType.key: String
    get() = if (this == MediaType.MOVIE) "movie" else "tv"
