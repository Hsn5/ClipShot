package com.example.clipshot

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScreenshotAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_VAULT_UPDATED = "com.example.clipshot.ACTION_VAULT_UPDATED"

        var instance: ScreenshotAccessibilityService? = null
            private set

        @Volatile
        private var isOverlayShowing = false

        private var lastGlobalTriggerTime = 0L

        fun captureNow(delayMs: Long = 0L, openCropOverlay: Boolean = true) {
            val service = instance ?: return
            val now = System.currentTimeMillis()
            if (isOverlayShowing || now - lastGlobalTriggerTime < 1400L) return
            lastGlobalTriggerTime = now

            service.mainHandler.postDelayed({
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isOverlayShowing) {
                    service.takeSilentScreenshot(openCropOverlay)
                }
            }, delayMs)
        }

        fun collapseShadeAndCapture() {
            val service = instance ?: return
            service.performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            captureNow(delayMs = 380L, openCropOverlay = true)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var activeOverlay: CropOverlayManager? = null

    private var pendingSingleTap = false
    private var isVolumeDownHeld = false

    private val holdRepeatRunnable = object : Runnable {
        override fun run() {
            if (isVolumeDownHeld) {
                lowerVolume()
                mainHandler.postDelayed(this, 80L)
            }
        }
    }

    private val singleTapRunnable = Runnable {
        pendingSingleTap = false
        lowerVolume()
        if (isVolumeDownHeld) {
            mainHandler.postDelayed(holdRepeatRunnable, 120L)
        }
    }

    private val buttonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController?) {
            captureNow(delayMs = 0L, openCropOverlay = true)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ioExecutor.execute {
            ScreenshotVaultManager.purgeExpired(applicationContext)
        }
        try {
            accessibilityButtonController.registerAccessibilityButtonCallback(buttonCallback)
        } catch (_: Exception) {}
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanupService()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanupService()
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun cleanupService() {
        mainHandler.removeCallbacksAndMessages(null)
        activeOverlay?.cancel()
        activeOverlay = null
        try {
            accessibilityButtonController.unregisterAccessibilityButtonCallback(buttonCallback)
        } catch (_: Exception) {}
        instance = null
        isOverlayShowing = false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && !isOverlayShowing) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    if (pendingSingleTap) {
                        mainHandler.removeCallbacks(singleTapRunnable)
                        mainHandler.removeCallbacks(holdRepeatRunnable)
                        pendingSingleTap = false
                        isVolumeDownHeld = false
                        captureNow(delayMs = 0L, openCropOverlay = true)
                        return true
                    } else {
                        isVolumeDownHeld = true
                        pendingSingleTap = true
                        mainHandler.postDelayed(singleTapRunnable, 260L)
                        return true
                    }
                }
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                isVolumeDownHeld = false
                mainHandler.removeCallbacks(holdRepeatRunnable)
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

    /**
     * Dismisses the active crop overlay if another window (e.g. Home/Launcher, Recents, or another app)
     * comes to the foreground while the overlay is visible.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isOverlayShowing || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return

        mainHandler.post {
            activeOverlay?.cancel()
        }
    }

    override fun onInterrupt() {}

    @RequiresApi(Build.VERSION_CODES.R)
    fun takeSilentScreenshot(openCropOverlay: Boolean = true) {
        val detectedUrl = extractCurrentBrowserUrl()
        val smartBounds = if (openCropOverlay) extractSmartElementBounds() else emptyList()

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            ioExecutor,
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
                                mainHandler.post {
                                    isOverlayShowing = true
                                    try {
                                        val overlay = CropOverlayManager(
                                            context = this@ScreenshotAccessibilityService,
                                            fullBitmap = softwareBitmap,
                                            detectedUrl = detectedUrl,
                                            smartBounds = smartBounds,
                                            onResultBitmap = { finalBitmap ->
                                                activeOverlay = null
                                                isOverlayShowing = false
                                                saveFinalBitmapAsync(finalBitmap, detectedUrl)
                                            },
                                            onCopyUrlOnly = { url ->
                                                activeOverlay = null
                                                isOverlayShowing = false
                                                if (!softwareBitmap.isRecycled) softwareBitmap.recycle()
                                                copyPlainTextToClipboard(url)
                                                showToast("Web URL copied to clipboard 🔗")
                                            },
                                            onCancel = {
                                                activeOverlay = null
                                                isOverlayShowing = false
                                                if (!softwareBitmap.isRecycled) softwareBitmap.recycle()
                                            }
                                        )
                                        activeOverlay = overlay
                                        overlay.show()
                                    } catch (e: Exception) {
                                        activeOverlay = null
                                        isOverlayShowing = false
                                        if (!softwareBitmap.isRecycled) softwareBitmap.recycle()
                                        e.printStackTrace()
                                        showToast("Couldn't open crop overlay")
                                    }
                                }
                            } else {
                                saveFinalBitmapAsync(softwareBitmap, detectedUrl)
                            }
                        }
                    } catch (e: Exception) {
                        activeOverlay = null
                        isOverlayShowing = false
                        e.printStackTrace()
                        showToast("Failed to process screenshot")
                    }
                }

                override fun onFailure(errorCode: Int) {
                    activeOverlay = null
                    isOverlayShowing = false
                    showToast("Screenshot failed (Error code: $errorCode)")
                }
            }
        )
    }

    private fun saveFinalBitmapAsync(bitmap: Bitmap, sourceUrl: String?) {
        ioExecutor.execute {
            try {
                ScreenshotVaultManager.saveAndCopyToClipboard(
                    applicationContext,
                    bitmap,
                    sourceUrl
                )
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            mainHandler.post {
                sendBroadcast(Intent(ACTION_VAULT_UPDATED).setPackage(packageName))
                val msg = if (sourceUrl != null) {
                    "Copied to clipboard & saved with URL 📋🔗"
                } else {
                    "Copied to clipboard & saved to 24h Vault 📋"
                }
                showToast(msg)
            }
        }
    }

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
        if (node == null || depth > 12 || out.size >= 250) return

        if (node.isVisibleToUser) {
            val cls = node.className?.toString() ?: ""
            val isMeaningful = node.isClickable ||
                cls.contains("Image", ignoreCase = true) ||
                cls.contains("TextView", ignoreCase = true) ||
                cls.contains("Card", ignoreCase = true) ||
                cls.contains("WebView", ignoreCase = true) ||
                cls.contains("Video", ignoreCase = true) ||
                !node.text.isNullOrBlank() ||
                !node.contentDescription.isNullOrBlank() ||
                node.childCount == 0

            if (isMeaningful) {
                val r = Rect()
                node.getBoundsInScreen(r)
                val w = r.width()
                val h = r.height()
                if (w >= 110 && h >= 80 && !(w > screenW * 0.93f && h > screenH * 0.78f)) {
                    val clamped = RectF(
                        r.left.toFloat().coerceIn(0f, screenW.toFloat()),
                        r.top.toFloat().coerceIn(0f, screenH.toFloat()),
                        r.right.toFloat().coerceIn(0f, screenW.toFloat()),
                        r.bottom.toFloat().coerceIn(0f, screenH.toFloat())
                    )
                    if (clamped.width() >= 110f && clamped.height() >= 80f) {
                        val key = "${(clamped.left / 16).toInt()}_${(clamped.top / 16).toInt()}_${(clamped.right / 16).toInt()}_${(clamped.bottom / 16).toInt()}"
                        if (seen.add(key)) {
                            out.add(clamped)
                        }
                    }
                }
            }
        }

        for (i in 0 until node.childCount) {
            collectBounds(node.getChild(i), depth + 1, screenW, screenH, out, seen)
        }
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
                val normalized = normalizeUrl(raw, strict = false)
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
            val normalized = normalizeUrl(raw, strict = true)
            if (normalized != null) return normalized
        }
        for (i in 0 until node.childCount) {
            val res = scanNodeForUrl(node.getChild(i), depth + 1)
            if (res != null) return res
        }
        return null
    }

    private fun normalizeUrl(raw: String?, strict: Boolean): String? {
        if (raw.isNullOrBlank() || raw.contains(" ")) return null
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            return raw
        }
        if (Patterns.WEB_URL.matcher(raw).matches() && raw.contains(".")) {
            if (!strict) return "https://$raw"
            val commonWebTlds = listOf(".com", ".org", ".net", ".io", ".dev", ".edu", ".gov", ".co", ".me", ".app", ".ai", "/")
            if (commonWebTlds.any { raw.contains(it, ignoreCase = true) }) {
                return "https://$raw"
            }
        }
        return null
    }

    private fun copyPlainTextToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Web URL", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
