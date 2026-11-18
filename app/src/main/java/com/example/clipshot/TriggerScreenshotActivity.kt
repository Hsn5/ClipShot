package com.example.clipshot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

class TriggerScreenshotActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val openCrop = intent?.getBooleanExtra("open_crop", true) ?: true

        if (ScreenshotAccessibilityService.instance != null) {
            ScreenshotAccessibilityService.captureNow(delayMs = 100L, openCropOverlay = openCrop)
        } else {
            Toast.makeText(
                applicationContext,
                "Please enable ClipShot Accessibility Service first",
                Toast.LENGTH_LONG
            ).show()
            val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(settingsIntent)
        }

        finish()
        overridePendingTransition(0, 0)
    }
}
