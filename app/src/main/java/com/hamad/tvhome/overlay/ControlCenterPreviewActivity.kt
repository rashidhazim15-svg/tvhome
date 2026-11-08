package com.hamad.tvhome.overlay

import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity

/**
 * Shows the Control Center overlay over whatever app is in front. Used by
 * Settings → General → Preview Control Center, and useful for device QA. A
 * transparent, non-exported activity: it asks the running accessibility service
 * to draw its overlay, then gets out of the way.
 */
class ControlCenterPreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TvAccessibilityService.showPreview()) {
            startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        finish()
    }
}
