package com.hamad.tvhome.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.PixelFormat
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Hosts the Control Center overlay. A launcher cannot draw over other apps, but
 * an accessibility service can (TYPE_ACCESSIBILITY_OVERLAY), so the overlay is
 * opt-in through system accessibility settings and can be revoked at any time.
 *
 * While the overlay is open all D-pad keys are consumed here, which traps focus;
 * Menu or Back closes it. A 500 ms press of Home opens it.
 */
class TvAccessibilityService : AccessibilityService() {

    private var overlay: ControlCenterView? = null
    private var homeDownAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        active = this
        Log.d(TAG, "connected, filterKeyEvents=" + (serviceInfo.flags and
            AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_HOME) return handleHome(event)

        val view = overlay ?: return false
        if (event.action != KeyEvent.ACTION_DOWN) return isNavigation(event.keyCode)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                view.move(ControlCenterView.Move.Left)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                view.move(ControlCenterView.Move.Right)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                view.move(ControlCenterView.Move.Up)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                view.move(ControlCenterView.Move.Down)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> {
                Log.d(TAG, "activate focused item")
                if (view.activate()) hideOverlay()
                true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> {
                Log.d(TAG, "close via key ${event.keyCode}")
                hideOverlay()
                true
            }
            else -> false
        }
    }

    private fun handleHome(event: KeyEvent): Boolean {
        if (overlay != null) {
            if (event.action == KeyEvent.ACTION_DOWN) hideOverlay()
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            val now = SystemClock.uptimeMillis()
            if (event.repeatCount == 0) homeDownAt = now
            val held = now - homeDownAt
            if (event.isLongPress || event.repeatCount > 0 ||
                ControlCenterNavigation.isLongPress(homeDownAt, now)
            ) {
                open()
                return true
            }
            return false
        }
        if (event.action == KeyEvent.ACTION_UP) {
            val upAt = SystemClock.uptimeMillis()
            val long = ControlCenterNavigation.isLongPress(homeDownAt, upAt)
            homeDownAt = 0L
            if (long) {
                open()
                return true
            }
        }
        return false
    }

    private fun isNavigation(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
        keyCode == KeyEvent.KEYCODE_DPAD_UP ||
        keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun open() {
        if (overlay != null) return
        val manager = getSystemService(WindowManager::class.java) ?: return
        val view = ControlCenterView(this).apply {
            onRequestClose = { hideOverlay() }
            onOpenSystemSettings = { id ->
                when (id) {
                    "wifi" -> ControlCenterData.openWifiSettings(this@TvAccessibilityService)
                    "bluetooth" -> ControlCenterData.openBluetoothSettings(this@TvAccessibilityService)
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = WINDOW_TITLE
        }
        runCatching { manager.addView(view, params) }
            .onSuccess {
                view.requestFocus()
                overlay = view
                Log.d(TAG, "overlay open: " + view.contentDescription)
            }
            .onFailure { Log.w(TAG, "overlay failed", it) }
    }

    private fun hideOverlay() {
        val view = overlay ?: return
        overlay = null
        runCatching { getSystemService(WindowManager::class.java)?.removeView(view) }
        Log.d(TAG, "overlay closed")
    }

    override fun onDestroy() {
        if (active === this) active = null
        hideOverlay()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TvAccessibility"
        private const val WINDOW_TITLE = "tvhome Control Center"

        @Volatile
        private var active: TvAccessibilityService? = null

        /** True when the user has enabled the service and it is bound. */
        fun isConnected(): Boolean = active != null

        /**
         * Opens the overlay over the current app for the Settings preview. Returns
         * false when the accessibility service is not enabled.
         */
        fun showPreview(): Boolean {
            val service = active ?: return false
            service.open()
            return service.overlay != null
        }
    }
}
