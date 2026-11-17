package com.example.clipshot

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.media.AudioManager
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
    private var pendingSingleTap = false
    private var isVolumeDownHeld = false
    private var lastNotificationTriggerTime = 0L

    private val holdRepeatRunnable = object : Runnable {
        override fun run() {
            if (isVolumeDownHeld) {
                lowerVolume()
                handler.postDelayed(this, 80L)
            }
        }
    }

    private val singleTapRunnable = Runnable {
        pendingSingleTap = false
        lowerVolume()
        if (isVolumeDownHeld) {
            handler.postDelayed(holdRepeatRunnable, 120L)
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
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    if (pendingSingleTap) {
                        handler.removeCallbacks(singleTapRunnable)
                        handler.removeCallbacks(holdRepeatRunnable)
                        pendingSingleTap = false
                        isVolumeDownHeld = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            takeSilentScreenshot(openCropOverlay = true)
                        }
                        return true
                    } else {
                        isVolumeDownHeld = true
                        pendingSingleTap = true
                        handler.postDelayed(singleTapRunnable, 260L)
                        return true
                    }
                }
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                isVolumeDownHeld = false
                handler.removeCallbacks(holdRepeatRunnable)
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    private fun lowerVolume() {
        try {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustSuggestedStreamVolume(
                AudioManager.ADJUST_LOWER,
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (_: Exception) {}
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
        val detectedUrl = extractCurrentBrowserUrl()
        val smartBounds = if (openCropOverlay) extractSmartElementBounds() else emptyList()
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
                                        smartBounds = smartBounds,
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

    /**
     * Collects visible UI element bounding boxes from the active screen so the crop tool
     * can snap smartly to cards, images, posts, and containers on tap or edge resize.
     */
    private fun extractSmartElementBounds(): List<RectF> {
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        val results = mutableListOf<RectF>()
        val seenKeys = HashSet<String>()

        collectBounds(root, depth = 0, screenW = screenW, screenH = screenH, out = results, seen = seenKeys)
        return results
    }

    private fun collectBounds(
        node: AccessibilityNodeInfo?,
        depth: Int,
        screenW: Int,
        screenH: Int,
        out: MutableList<RectF>,
        seen: HashSet<String>
    ) {
        if (node == null || depth > 12 || out.size >= 140) return

        if (node.isVisibleToUser) {
            val r = Rect()
            node.getBoundsInScreen(r)
            val w = r.width()
            val h = r.height()
            // Keep meaningful UI blocks (ignore tiny icons and full-screen root containers)
            if (w >= 90 && h >= 70 && (w < screenW * 0.97f || h < screenH * 0.90f)) {
                val clamped = RectF(
                    r.left.toFloat().coerceIn(0f, screenW.toFloat()),
                    r.top.toFloat().coerceIn(0f, screenH.toFloat()),
                    r.right.toFloat().coerceIn(0f, screenW.toFloat()),
                    r.bottom.toFloat().coerceIn(0f, screenH.toFloat())
                )
                if (clamped.width() >= 90f && clamped.height() >= 70f) {
                    val key = "${clamped.left.toInt()}_${clamped.top.toInt()}_${clamped.right.toInt()}_${clamped.bottom.toInt()}"
                    if (seen.add(key)) {
                        out.add(clamped)
                    }
                }
            }
        }

        for (i in 0 until node.childCount) {
            collectBounds(node.getChild(i), depth + 1, screenW, screenH, out, seen)
        }
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
