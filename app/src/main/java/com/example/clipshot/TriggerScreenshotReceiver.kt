package com.example.clipshot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class TriggerScreenshotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val openCrop = intent.getBooleanExtra("open_crop", true)
        if (ScreenshotAccessibilityService.instance != null) {
            ScreenshotAccessibilityService.captureNow(delayMs = 50L, openCropOverlay = openCrop)
        } else {
            Toast.makeText(
                context,
                "Please enable ClipShot Accessibility Service first",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
