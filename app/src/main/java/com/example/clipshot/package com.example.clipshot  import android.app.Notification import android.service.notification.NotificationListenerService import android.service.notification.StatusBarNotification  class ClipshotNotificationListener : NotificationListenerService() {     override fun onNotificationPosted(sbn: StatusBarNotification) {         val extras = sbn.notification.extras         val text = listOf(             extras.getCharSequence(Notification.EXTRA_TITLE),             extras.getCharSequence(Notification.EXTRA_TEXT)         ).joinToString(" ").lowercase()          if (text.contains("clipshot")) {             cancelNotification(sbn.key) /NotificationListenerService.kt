package com.example.clipshot

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class ClipshotNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val text = listOf(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TEXT)
        ).joinToString(" ").lowercase()

        if (text.contains("clipshot")) {
            cancelNotification(sbn.key) // يشيل الإشعار عشان ميظهرش في اللقطة
            ScreenshotAccessibilityService.captureNow(delayMs = 150L)
        }
    }
}
