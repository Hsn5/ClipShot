package com.example.clipshot

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors

class ScreenshotAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_VAULT_UPDATED = "com.example.clipshot.ACTION_VAULT_UPDATED"

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

    private val handler = Handler(Looper.getMainLooper())
    private var volumeDownLongPressTriggered = false

    private val longPressRunnable = Runnable {
        volumeDownLongPressTriggered = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeSilentScreenshot()
        }
    }

    private val buttonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                takeSilentScreenshot()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // Clean up any expired 24h screenshots when service starts
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

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
                            ScreenshotVaultManager.saveAndCopyToClipboard(
                                applicationContext,
                                softwareBitmap
                            )
                            softwareBitmap.recycle()

                            sendBroadcast(Intent(ACTION_VAULT_UPDATED).setPackage(packageName))
                            showToast("Copied to clipboard & saved to 24h Vault 📋")
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

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
