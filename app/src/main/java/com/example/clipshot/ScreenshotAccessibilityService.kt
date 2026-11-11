package com.example.clipshot

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class ScreenshotAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ScreenshotAccessibilityService? = null
            private set

        fun captureNow(delayMs: Long = 0L) {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).postDelayed({
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    service.takeSilentScreenshot()
                }
            }, delayMs)
        }

        fun collapseShadeAndCapture() {
            val service = instance ?: return
            service.performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            captureNow(delayMs = 380L)
        }
    }

    private val buttonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController?) {
            // يشتغل فوراً عند الضغط على اختصار Accessibility بدون أي نافذة أو خروج من التطبيق!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                takeSilentScreenshot()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // تفعيل استقبال ضغطة اختصار إمكانية الوصول (Accessibility Shortcut / Button)
        accessibilityButtonController.registerAccessibilityButtonCallback(buttonCallback)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        try {
            accessibilityButtonController.unregisterAccessibilityButtonCallback(buttonCallback)
        } catch (_: Exception) {}
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // لو عملت في Modes & Routines أكشن "Show custom notification" مكتوب فيه كلمة clipshot
        if (event?.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val text = event.text?.joinToString(" ")?.lowercase() ?: ""
            if (text.contains("clipshot")) {
                captureNow(delayMs = 150L)
            }
        }
    }

    override fun onInterrupt() {}

    @RequiresApi(Build.VERSION_CODES.R)
    fun takeSilentScreenshot() {
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
                            copyBitmapToClipboard(softwareBitmap)
                            softwareBitmap.recycle()
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

    private fun copyBitmapToClipboard(bitmap: Bitmap) {
        val cacheFolder = File(cacheDir, "screenshots")
        if (!cacheFolder.exists()) cacheFolder.mkdirs()

        File(cacheFolder, ".nomedia").createNewFile()

        val tempFile = File(cacheFolder, "clipboard_latest.png")
        FileOutputStream(tempFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }

        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            tempFile
        )

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newUri(contentResolver, "Screenshot", uri)
        clipboard.setPrimaryClip(clip)

        showToast("Screenshot copied to clipboard")
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
