package com.example.clipshot

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast

class TriggerScreenshotService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ScreenshotAccessibilityService.instance != null) {
            ScreenshotAccessibilityService.captureNow(delayMs = 50L)
        } else {
            Toast.makeText(
                applicationContext,
                "Please enable ClipShot Accessibility Service first",
                Toast.LENGTH_SHORT
            ).show()
        }
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
