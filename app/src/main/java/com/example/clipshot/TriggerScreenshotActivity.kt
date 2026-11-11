package com.example.clipshot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/**
 * Why did Samsung Modes & Routines exit to the Home Screen while Quick Settings Tile worked?
 * Because Samsung Modes & Routines launches App Shortcuts using `Theme` task transition and if
 * MainActivity shares the same default taskAffinity, or if a visible window is created and closed,
 * OneUI's WindowManager transitions back to the Home Screen (`com.sec.android.app.launcher`).
 *
 * How v5.0 solves this completely:
 * 1. `TriggerScreenshotActivity` uses `@android:style/Theme.NoDisplay` — Android's official zero-window
 *    theme that tells WindowManager NOT to allocate a Surface or pause/exit the underlying app at all!
 * 2. `MainActivity` is isolated in `taskAffinity="com.example.clipshot.setup"` so closing it or leaving it
 *    in the background never links to `TriggerScreenshotActivity`.
 * 3. `finish()` is called immediately inside `onCreate()` before `super.onResume()` (required by `Theme.NoDisplay`),
 *    keeping the foreground app 100% untouched on screen!
 */
class TriggerScreenshotActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ScreenshotAccessibilityService.instance != null) {
            ScreenshotAccessibilityService.captureNow(delayMs = 100L)
        } else {
            Toast.makeText(
                applicationContext,
                "Please enable ClipShot Accessibility Service first",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }

        // Theme.NoDisplay requires finish() to be called synchronously inside onCreate()
        finish()
        overridePendingTransition(0, 0)
    }
}
