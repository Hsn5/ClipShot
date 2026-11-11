package com.example.clipshot

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusBadge: TextView
    private lateinit var statusDesc: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#0D1117")
        window.navigationBarColor = Color.parseColor("#0D1117")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D1117"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), dp(32))
        }

        val title = TextView(this).apply {
            text = "ClipShot v5.0"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#F0F6FC"))
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Samsung OneUI Modes & Routines Edition"
            textSize = 14f
            setTextColor(Color.parseColor("#8B949E"))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(28))
        }

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedRect("#161B22", "#30363D", dp(16))
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(20)
            }
        }

        statusBadge = TextView(this).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        }

        statusDesc = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#8B949E"))
            setLineSpacing(0f, 1.25f)
        }

        statusCard.addView(statusBadge)
        statusCard.addView(statusDesc)

        val enableBtn = createStyledButton(
            text = "1. Open Accessibility Settings",
            bgColor = "#238636",
            textColor = "#FFFFFF"
        ) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val testBtn = createStyledButton(
            text = "2. Test Capture to Clipboard Now",
            bgColor = "#1F6FEB",
            textColor = "#FFFFFF"
        ) {
            val intent = Intent(this, TriggerScreenshotActivity::class.java)
            startActivity(intent)
        }

        val infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedRect("#161B22", "#30363D", dp(16))
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        }

        val infoTitle = TextView(this).apply {
            text = "Samsung Modes & Routines Tip"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#F0F6FC"))
            setPadding(0, 0, 0, dp(10))
        }

        val infoBody = TextView(this).apply {
            text = "• In Modes & Routines -> Action -> Apps -> 'Open an app or do an app action', make sure you select the specific action 'Copy Screen' (NOT 'Open ClipShot').\n\n" +
                   "• ClipShot v5.0 now uses Android's hardware 'Theme.NoDisplay' and isolates MainActivity's taskAffinity so OneUI never switches windows or exits your current app."
            textSize = 13f
            setTextColor(Color.parseColor("#8B949E"))
            setLineSpacing(0f, 1.3f)
        }

        infoCard.addView(infoTitle)
        infoCard.addView(infoBody)

        root.addView(title)
        root.addView(subtitle)
        root.addView(statusCard)
        root.addView(enableBtn)
        root.addView(testBtn)
        root.addView(infoCard)

        scrollView.addView(root)
        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        val isActive = ScreenshotAccessibilityService.instance != null
        if (isActive) {
            statusBadge.text = "● Service Active & Ready (v5.0)"
            statusBadge.setTextColor(Color.parseColor("#3FB950"))
            statusDesc.text = "Running silently with Theme.NoDisplay. Triggering 'Copy Screen' from Modes & Routines or Quick Settings will capture without leaving your open app."
        } else {
            statusBadge.text = "● Service Not Enabled"
            statusBadge.setTextColor(Color.parseColor("#F85149"))
            statusDesc.text = "Tap 'Open Accessibility Settings' below, select 'Installed apps', and turn on 'ClipShot Silent Capture'."
        }
    }

    private fun createStyledButton(
        text: String,
        bgColor: String,
        textColor: String,
        onClick: (View) -> Unit
    ): Button {
        return Button(this).apply {
            this.text = text
            this.textSize = 15f
            this.isAllCaps = false
            this.setTypeface(typeface, Typeface.BOLD)
            this.setTextColor(Color.parseColor(textColor))
            this.background = roundedRect(bgColor, bgColor, dp(12))
            this.setPadding(dp(16), dp(14), dp(16), dp(14))
            this.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
            this.setOnClickListener(onClick)
        }
    }

    private fun roundedRect(fillHex: String, strokeHex: String, radiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(Color.parseColor(fillHex))
            setStroke(dp(1), Color.parseColor(strokeHex))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
