package com.example.clipshot

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            isFillViewport = true
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(64, 64, 64, 64)
        }

        val titleText = TextView(this).apply {
            text = "ClipShot"
            textSize = 28f
            setTextColor(Color.parseColor("#FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        val subTitleText = TextView(this).apply {
            text = "Silent Clipboard-Only Screenshot"
            textSize = 14f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }

        statusText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 48)
        }

        val enableBtn = Button(this).apply {
            text = "1. Enable Accessibility Service"
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#238636"))
            }
            setPadding(32, 32, 32, 32)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val spacer = TextView(this).apply {
            height = 32
        }

        val testBtn = Button(this).apply {
            text = "2. Test Capture Now"
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#1F6FEB"))
            }
            setPadding(32, 32, 32, 32)
            setOnClickListener {
                if (ScreenshotAccessibilityService.instance != null) {
                    ScreenshotAccessibilityService.captureNow(delayMs = 200L)
                } else {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }

        val helpText = TextView(this).apply {
            text = "\nHow to trigger without leaving your app:\n\n" +
                   "• Quick Settings Tile: Tap 'ClipShot' in notification shade.\n" +
                   "• Modes & Routines: Choose Action -> Notifications -> Show custom notification -> enter 'clipshot'.\n" +
                   "• Accessibility Button/Shortcut: Assign ClipShot to Side+Volume keys or Back-Tap."
            textSize = 13f
            setTextColor(Color.parseColor("#9E9E9E"))
            setPadding(0, 48, 0, 0)
        }

        layout.addView(titleText)
        layout.addView(subTitleText)
        layout.addView(statusText)
        layout.addView(enableBtn)
        layout.addView(spacer)
        layout.addView(testBtn)
        layout.addView(helpText)

        scrollView.addView(layout)
        setContentView(scrollView)

        // تلوين شريط الحالة بأمان بعد setContentView فقط
        try {
            window.statusBarColor = Color.parseColor("#121212")
            window.navigationBarColor = Color.parseColor("#121212")
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        val isActive = ScreenshotAccessibilityService.instance != null
        if (isActive) {
            statusText.text = "✅ Service is ACTIVE & Ready!"
            statusText.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            statusText.text = "⚠️ Service is NOT enabled.\nTap button 1 below to turn on ClipShot."
            statusText.setTextColor(Color.parseColor("#FF5252"))
        }
    }
}
