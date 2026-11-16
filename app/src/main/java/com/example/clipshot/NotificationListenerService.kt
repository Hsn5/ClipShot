package com.example.clipshot

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class ClipShotNotificationListener : NotificationListenerService() {

    private var lastTriggerTime = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName == packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.lowercase() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.lowercase() ?: ""
        val combined = "$title $text"

        val isUrlTrigger = combined.contains("clipurl")
        val isShotTrigger = combined.contains("clipshot")
        if (!isUrlTrigger && !isShotTrigger) return

        val now = System.currentTimeMillis()
        if (now - lastTriggerTime <= 2000L) return
        lastTriggerTime = now

        // Dismiss the trigger notification immediately so it doesn't appear in the screenshot
        try {
            cancelNotification(sbn.key)
        } catch (_: Exception) {}

        if (isUrlTrigger) {
            ScreenshotAccessibilityService.copyUrlNow()
        } else {
            ScreenshotAccessibilityService.captureNow(delayMs = 200L, openCropOverlay = true)
        }
    }
}
