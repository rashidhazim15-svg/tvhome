package com.hamad.tvhome.launcher

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hamad.tvhome.ui.LocalTvMetrics
import com.hamad.tvhome.ui.LocalTvType
import com.hamad.tvhome.ui.TvMetrics
import com.hamad.tvhome.ui.TvType

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No FLAG_KEEP_SCREEN_ON here: the launcher must be allowed to idle so the
        // system daydream (Aerial / Prayer) can start like the tvOS screen saver.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
                // Authored in tvOS points; one point is 1/1080th of the screen height.
                val metrics = remember(maxHeight) { TvMetrics((maxHeight.value / 1080f).coerceAtLeast(0.35f)) }
                val type = remember(metrics) { TvType(metrics) }
                CompositionLocalProvider(LocalTvMetrics provides metrics, LocalTvType provides type) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        LauncherScreen(vm)
                    }
                }
            }
        }
        hideSystemUi()
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
        hideSystemUi()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.homeTab()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (vm.handleKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun hideSystemUi() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
