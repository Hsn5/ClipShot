package com.example.clipshot

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors

class ScreenshotAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_VAULT_UPDATED = "com.example.clipshot.ACTION_VAULT_UPDATED"

        var instance: ScreenshotAccessibilityService? = null
            private set

        fun captureNow(delayMs: Long = 0L, openCropOverlay: Boolean = true) {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).postDelayed({
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    service.takeSilentScreenshot(openCropOverlay)
                }
            }, delayMs)
        }

        fun copyUrlNow() {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                val url = service.extractCurrentBrowserUrl()
                if (url != null) {
                    service.copyPlainTextToClipboard(url)
                    service.showToast("URL copied: $url")
                } else {
                    service.showToast("No web URL found on current screen")
                }
            }
        }

        fun collapseShadeAndCapture() {
            val service = instance ?: return
            service.performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            captureNow(delayMs = 380L, openCropOverlay = true)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var volumeDownLongPressTriggered = false
    private var lastNotificationTriggerTime = 0L

    private val longPressRunnable = Runnable {
        volumeDownLongPressTriggered = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeSilentScreenshot(openCropOverlay = true)
        }
    }

    private val buttonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                takeSilentScreenshot(openCropOverlay = true)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ScreenshotVaultManager.purgeExpired(applicationContext)
        try {
            accessibilityButtonController.registerAccessibilityButtonCallback(buttonCallback)
        } catch (_: Exception) {}
    }

    override fun onUnbind(intent: Intent?): Boolean {
        try {
            accessibilityButtonController.unregisterAccessibilityButtonCallback(buttonCallback)
        } catch (_: Exception) {}
        instance = null
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                volumeDownLongPressTriggered = false
                handler.postDelayed(longPressRunnable, 450L)
                return false
            } else if (event.action == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(longPressRunnable)
                if (volumeDownLongPressTriggered) {
                    volumeDownLongPressTriggered = false
                    return true
                }
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        if (event.packageName?.toString() == packageName) return
        if (event.parcelableData !is Notification) return

        val text = event.text?.joinToString(" ")?.lowercase() ?: ""
        val contentDesc = event.contentDescription?.toString()?.lowercase() ?: ""
        val combined = "$text $contentDesc"

        val now = System.currentTimeMillis()
        if (now - lastNotificationTriggerTime <= 2000L) return

        if (combined.contains("clipurl")) {
            lastNotificationTriggerTime = now
            Handler(Looper.getMainLooper()).postDelayed({ copyUrlNow() }, 150L)
        } else if (combined.contains("clipshot")) {
            lastNotificationTriggerTime = now
            captureNow(delayMs = 150L, openCropOverlay = true)
        }
    }

    override fun onInterrupt() {}

    @RequiresApi(Build.VERSION_CODES.R)
    fun takeSilentScreenshot(openCropOverlay: Boolean = true) {
        // Extract browser URL BEFORE displaying any overlay
        val detectedUrl = extractCurrentBrowserUrl()
        val executor = Executors.newSingleThreadExecutor()

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            executor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: ScreenshotResult) {
                    val hardwareBuffer = screenshotResult.hardwareBuffer
                    val colorSpace = screenshotResult.colorSpace

                    try {
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        val softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hardwareBitmap?.recycle()
                        hardwareBuffer.close()

                        if (softwareBitmap != null) {
                            if (openCropOverlay) {
                                handler.post {
                                    CropOverlayManager(
                                        context = this@ScreenshotAccessibilityService,
                                        fullBitmap = softwareBitmap,
                                        detectedUrl = detectedUrl,
                                        onResultBitmap = { finalBitmap ->
                                            saveFinalBitmap(finalBitmap, detectedUrl)
                                        },
                                        onCopyUrlOnly = { url ->
                                            softwareBitmap.recycle()
                                            copyPlainTextToClipboard(url)
                                            showToast("Web URL copied to clipboard 🔗")
                                        },
                                        onCancel = {
                                            softwareBitmap.recycle()
                                        }
                                    ).show()
                                }
                            } else {
                                saveFinalBitmap(softwareBitmap, detectedUrl)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast("Failed to process screenshot")
                    }
                }

                override fun onFailure(errorCode: Int) {
                    showToast("Screenshot failed (Error code: $errorCode)")
                }
            }
        )
    }

    private fun saveFinalBitmap(bitmap: Bitmap, sourceUrl: String?) {
        ScreenshotVaultManager.saveAndCopyToClipboard(
            applicationContext,
            bitmap,
            sourceUrl
        )
        bitmap.recycle()
        sendBroadcast(Intent(ACTION_VAULT_UPDATED).setPackage(packageName))
        val msg = if (sourceUrl != null) {
            "Copied to clipboard & saved with URL 📋🔗"
        } else {
            "Copied to clipboard & saved to 24h Vault 📋"
        }
        showToast(msg)
    }

    /**
     * Scans the active browser window tree for an address bar URL (Chrome, Samsung Internet, Edge, Firefox, Brave).
     */
    fun extractCurrentBrowserUrl(): String? {
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return null

        val knownUrlIds = listOf(
            "com.android.chrome:id/url_bar",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.microsoft.emmx:id/url_bar",
            "com.brave.browser:id/url_bar",
            "com.opera.browser:id/url_field",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"
        )

        for (viewId in knownUrlIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            for (node in nodes) {
                val raw = node.text?.toString()?.trim()
                val normalized = normalizeUrl(raw)
                if (normalized != null) return normalized
            }
        }

        // Fallback recursive scan (bounded depth) for any EditText/TextView containing a web URL
        return scanNodeForUrl(root, depth = 0)
    }

    private fun scanNodeForUrl(node: AccessibilityNodeInfo?, depth: Int): String? {
        if (node == null || depth > 8) return null
        val cls = node.className?.toString() ?: ""
        if (cls.contains("EditText") || (node.viewIdResourceName?.contains("url", ignoreCase = true) == true)) {
            val raw = node.text?.toString()?.trim()
            val normalized = normalizeUrl(raw)
            if (normalized != null) return normalized
        }
        for (i in 0 until node.childCount) {
            val res = scanNodeForUrl(node.getChild(i), depth + 1)
            if (res != null) return res
        }
        return null
    }

    private fun normalizeUrl(raw: String?): String? {
        if (raw.isNullOrBlank() || raw.contains(" ")) return null
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            return raw
        }
        if (Patterns.WEB_URL.matcher(raw).matches() && raw.contains(".")) {
            return "https://$raw"
        }
        return null
    }

    private fun copyPlainTextToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Web URL", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
