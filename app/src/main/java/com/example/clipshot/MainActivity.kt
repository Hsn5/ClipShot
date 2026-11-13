package com.example.clipshot

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var vaultCountText: TextView
    private lateinit var vaultContainer: LinearLayout

    private val vaultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshVaultList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(48, 56, 48, 64)
        }

        val titleText = TextView(this).apply {
            text = "ClipShot"
            textSize = 28f
            setTextColor(Color.parseColor("#FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        val subTitleText = TextView(this).apply {
            text = "Silent Clipboard & 24-Hour Private Vault"
            textSize = 14f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 36)
        }

        statusText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            background = roundedBg("#1E1E1E", "#2D2D2D", 24f)
            setPadding(32, 28, 32, 28)
        }

        val spacer1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, 24)
        }

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }

        val enableBtn = Button(this).apply {
            text = "Accessibility"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedBg("#238636", "#2EA043", 20f)
            setPadding(20, 24, 20, 24)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 12
            }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val testBtn = Button(this).apply {
            text = "Capture Now"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedBg("#1F6FEB", "#388BFD", 20f)
            setPadding(20, 24, 20, 24)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 12
            }
            setOnClickListener {
                if (ScreenshotAccessibilityService.instance != null) {
                    ScreenshotAccessibilityService.captureNow(delayMs = 200L)
                } else {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }

        buttonsRow.addView(enableBtn)
        buttonsRow.addView(testBtn)

        // Vault Header Row
        val vaultHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 48, 0, 20)
        }

        vaultCountText = TextView(this).apply {
            text = "24-Hour Screenshot Vault"
            textSize = 18f
            setTextColor(Color.parseColor("#FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val clearUnpinnedBtn = Button(this).apply {
            text = "Clear Unpinned"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.parseColor("#FF6B6B"))
            background = roundedBg("#25171A", "#4A252B", 16f)
            setPadding(24, 12, 24, 12)
            setOnClickListener {
                ScreenshotVaultManager.clearUnpinned(this@MainActivity)
                refreshVaultList()
                Toast.makeText(this@MainActivity, "Cleared unpinned screenshots", Toast.LENGTH_SHORT).show()
            }
        }

        vaultHeaderRow.addView(vaultCountText)
        vaultHeaderRow.addView(clearUnpinnedBtn)

        val vaultHintText = TextView(this).apply {
            text = "Unpinned screenshots auto-delete after 24 hours. Pin 📌 important shots to keep them forever. None of these ever appear in your phone's Gallery."
            textSize = 12f
            setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, 0, 0, 24)
        }

        vaultContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(titleText)
        root.addView(subTitleText)
        root.addView(statusText)
        root.addView(spacer1)
        root.addView(buttonsRow)
        root.addView(vaultHeaderRow)
        root.addView(vaultHintText)
        root.addView(vaultContainer)

        scrollView.addView(root)
        setContentView(scrollView)

        try {
            window.statusBarColor = Color.parseColor("#121212")
            window.navigationBarColor = Color.parseColor("#121212")
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        refreshVaultList()
        val filter = IntentFilter(ScreenshotAccessibilityService.ACTION_VAULT_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(vaultReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(vaultReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(vaultReceiver)
        } catch (_: Exception) {}
    }

    private fun updateServiceStatus() {
        val isActive = ScreenshotAccessibilityService.instance != null
        if (isActive) {
            statusText.text = "✅ Service Active (Hold Volume Down 0.5s or use Quick Tile)"
            statusText.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            statusText.text = "⚠️ Service NOT enabled. Tap 'Accessibility' to enable ClipShot."
            statusText.setTextColor(Color.parseColor("#FF5252"))
        }
    }

    private fun refreshVaultList() {
        vaultContainer.removeAllViews()
        val items = ScreenshotVaultManager.purgeExpired(this)
        val pinnedCount = items.count { it.isPinned }
        vaultCountText.text = "Vault (${items.size} saved • $pinnedCount pinned)"

        if (items.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No screenshots in vault yet.\nCapture a screenshot and it will appear here for 24 hours."
                textSize = 14f
                setTextColor(Color.parseColor("#6E7681"))
                gravity = Gravity.CENTER
                background = roundedBg("#181818", "#262626", 24f)
                setPadding(40, 64, 40, 64)
            }
            vaultContainer.addView(emptyView)
            return
        }

        val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.US)

        for (item in items) {
            val card = buildVaultCard(item, dateFormat)
            vaultContainer.addView(card)
        }
    }

    private fun buildVaultCard(item: VaultItem, dateFormat: SimpleDateFormat): View {
        val borderHex = if (item.isPinned) "#D29922" else "#2D2D2D"
        val bgHex = if (item.isPinned) "#1C1914" else "#1A1A1A"

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(bgHex, borderHex, 24f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24
            }
        }

        // Thumbnail Preview
        val thumbView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(160, 240).apply {
                rightMargin = 28
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBg("#0D1117", "#30363D", 14f)
        }

        val file = ScreenshotVaultManager.getFileForItem(this, item)
        if (file.exists()) {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 4 // Fast lightweight thumbnail decoding
            }
            val thumbBitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
            thumbView.setImageBitmap(thumbBitmap)
        }

        // Right Column: Metadata + Action Buttons
        val infoColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val timeText = TextView(this).apply {
            text = dateFormat.format(Date(item.timestamp))
            textSize = 15f
            setTextColor(Color.parseColor("#F0F6FC"))
        }

        val expiryText = TextView(this).apply {
            text = item.getFormattedStatus()
            textSize = 13f
            setTextColor(
                if (item.isPinned) Color.parseColor("#E3B341")
                else Color.parseColor("#8B949E")
            )
            setPadding(0, 6, 0, 20)
        }

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // 1. Copy Button
        val copyBtn = Button(this).apply {
            text = "Copy"
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedBg("#1F6FEB", "#388BFD", 16f)
            setPadding(24, 12, 24, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = 14
            }
            setOnClickListener {
                val ok = ScreenshotVaultManager.copyItemToClipboard(this@MainActivity, item)
                if (ok) {
                    Toast.makeText(this@MainActivity, "Copied to clipboard 📋", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 2. Pin / Unpin Button (Prevents 24h Auto-Delete)
        val pinBtn = Button(this).apply {
            text = if (item.isPinned) "Pinned 📌" else "Pin 📌"
            isAllCaps = false
            textSize = 13f
            setTextColor(if (item.isPinned) Color.parseColor("#121212") else Color.parseColor("#E3B341"))
            background = if (item.isPinned) {
                roundedBg("#E3B341", "#F2CC60", 16f)
            } else {
                roundedBg("#2A2415", "#6E561B", 16f)
            }
            setPadding(24, 12, 24, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = 14
            }
            setOnClickListener {
                val nowPinned = ScreenshotVaultManager.togglePin(this@MainActivity, item.id)
                val msg = if (nowPinned) "Pinned! Will never auto-delete 📌" else "Unpinned (24h timer resumed)"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                refreshVaultList()
            }
        }

        // 3. Delete Button
        val delBtn = Button(this).apply {
            text = "✕"
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.parseColor("#FF6B6B"))
            background = roundedBg("#25171A", "#4A252B", 16f)
            setPadding(20, 12, 20, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                ScreenshotVaultManager.deleteItem(this@MainActivity, item.id)
                refreshVaultList()
            }
        }

        actionsRow.addView(copyBtn)
        actionsRow.addView(pinBtn)
        actionsRow.addView(delBtn)

        infoColumn.addView(timeText)
        infoColumn.addView(expiryText)
        infoColumn.addView(actionsRow)

        card.addView(thumbView)
        card.addView(infoColumn)
        return card
    }

    private fun roundedBg(fillHex: String, strokeHex: String, radiusPx: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(Color.parseColor(fillHex))
            setStroke(2, Color.parseColor(strokeHex))
        }
    }
}
