package com.example.clipshot

import android.app.Activity
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var vaultCountText: TextView
    private lateinit var vaultContainer: LinearLayout

    private val thumbExecutor = Executors.newSingleThreadExecutor()

    private val vaultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshVaultList()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
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
            setPadding(dp(18), dp(24), dp(18), dp(24))
        }

        val titleText = TextView(this).apply {
            text = "Cropboard v9.0"
            textSize = 28f
            setTextColor(Color.parseColor("#FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        }

        val subTitleText = TextView(this).apply {
            text = "Smart Crop • Web URL • 24h Private Vault"
            textSize = 14f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }

        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            background = roundedBg("#1E1E1E", "#2D2D2D", dp(12).toFloat())
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val spacer1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(12))
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
            background = roundedBg("#238636", "#2EA043", dp(10).toFloat())
            setPadding(dp(10), dp(12), dp(10), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
            }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val testBtn = Button(this).apply {
            text = "Capture & Crop"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedBg("#1F6FEB", "#388BFD", dp(10).toFloat())
            setPadding(dp(10), dp(12), dp(10), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(6)
            }
            setOnClickListener {
                if (ScreenshotAccessibilityService.instance != null) {
                    ScreenshotAccessibilityService.captureNow(delayMs = 200L, openCropOverlay = true)
                } else {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }

        buttonsRow.addView(enableBtn)
        buttonsRow.addView(testBtn)

        val vaultHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(20), 0, dp(8))
        }

        vaultCountText = TextView(this).apply {
            text = "24-Hour Screenshot Vault"
            textSize = 17f
            setTextColor(Color.parseColor("#FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val clearUnpinnedBtn = Button(this).apply {
            text = "Clear Unpinned"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.parseColor("#FF6B6B"))
            background = roundedBg("#25171A", "#4A252B", dp(8).toFloat())
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                thumbExecutor.execute {
                    ScreenshotVaultManager.clearUnpinned(this@MainActivity)
                    runOnUiThread {
                        refreshVaultList()
                        Toast.makeText(this@MainActivity, "Cleared unpinned screenshots", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        vaultHeaderRow.addView(vaultCountText)
        vaultHeaderRow.addView(clearUnpinnedBtn)

        val vaultHintText = TextView(this).apply {
            text = "Triggers: Double-Tap Vol Down • Quick Tile • Accessibility Button • Launcher Shortcut."
            textSize = 12f
            setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, 0, 0, dp(12))
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

    override fun onDestroy() {
        thumbExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun updateServiceStatus() {
        val isActive = ScreenshotAccessibilityService.instance != null
        if (isActive) {
            statusText.text = "✅ Service Active (Smart Crop • Web URL • Double-Tap Vol Down • Quick Tile)"
            statusText.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            statusText.text = "⚠️ Service NOT enabled. Tap 'Accessibility' to enable Cropboard."
            statusText.setTextColor(Color.parseColor("#FF5252"))
        }
    }

    private fun refreshVaultList() {
        thumbExecutor.execute {
            val items = ScreenshotVaultManager.purgeExpired(this)
            runOnUiThread {
                renderVaultItems(items)
            }
        }
    }

    private fun renderVaultItems(items: List<VaultItem>) {
        vaultContainer.removeAllViews()
        val pinnedCount = items.count { it.isPinned }
        vaultCountText.text = "Vault (${items.size} saved • $pinnedCount pinned)"

        if (items.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No screenshots in vault yet.\nCapture a screenshot and it will appear here for 24 hours."
                textSize = 14f
                setTextColor(Color.parseColor("#6E7681"))
                gravity = Gravity.CENTER
                background = roundedBg("#181818", "#262626", dp(12).toFloat())
                setPadding(dp(20), dp(28), dp(20), dp(28))
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
            background = roundedBg(bgHex, borderHex, dp(12).toFloat())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        val thumbView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(108)).apply {
                rightMargin = dp(12)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBg("#0D1117", "#30363D", dp(8).toFloat())
            setOnClickListener {
                showFullScreenViewer(item, dateFormat)
            }
        }

        // Decode thumbnail asynchronously off the main thread
        val file = ScreenshotVaultManager.getFileForItem(this, item)
        if (file.exists()) {
            thumbExecutor.execute {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val thumbBitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
                if (thumbBitmap != null) {
                    runOnUiThread {
                        thumbView.setImageBitmap(thumbBitmap)
                    }
                }
            }
        }

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
            setPadding(0, dp(3), 0, dp(6))
        }

        infoColumn.addView(timeText)
        infoColumn.addView(expiryText)

        if (!item.sourceUrl.isNullOrBlank()) {
            val urlBtn = Button(this).apply {
                text = "🔗 Copy URL: ${item.sourceUrl.removePrefix("https://").removePrefix("http://").take(26)}..."
                isAllCaps = false
                textSize = 11f
                setTextColor(Color.parseColor("#58A6FF"))
                background = roundedBg("#131D2E", "#1F6FEB", dp(6).toFloat())
                setPadding(dp(8), dp(4), dp(8), dp(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(6)
                }
                setOnClickListener {
                    ScreenshotVaultManager.copyTextToClipboard(this@MainActivity, item.sourceUrl)
                    Toast.makeText(this@MainActivity, "URL copied to clipboard 🔗", Toast.LENGTH_SHORT).show()
                }
            }
            infoColumn.addView(urlBtn)
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
        }

        val viewBtn = Button(this).apply {
            text = "View"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.parseColor("#E0E0E0"))
            background = roundedBg("#262B33", "#3D444D", dp(8).toFloat())
            setPadding(dp(10), dp(5), dp(10), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(6)
            }
            setOnClickListener {
                showFullScreenViewer(item, dateFormat)
            }
        }

        val copyBtn = Button(this).apply {
            text = "Copy"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedBg("#1F6FEB", "#388BFD", dp(8).toFloat())
            setPadding(dp(10), dp(5), dp(10), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(6)
            }
            setOnClickListener {
                val ok = ScreenshotVaultManager.copyItemToClipboard(this@MainActivity, item)
                if (ok) {
                    Toast.makeText(this@MainActivity, "Copied to clipboard 📋", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val saveBtn = Button(this).apply {
            text = "Save"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedBg("#238636", "#2EA043", dp(8).toFloat())
            setPadding(dp(10), dp(5), dp(10), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                thumbExecutor.execute {
                    val saved = ScreenshotVaultManager.saveToGallery(this@MainActivity, item)
                    runOnUiThread {
                        val msg = if (saved) "Saved to Gallery (Pictures/Cropboard) 🖼️" else "Failed to save to Gallery"
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        row1.addView(viewBtn)
        row1.addView(copyBtn)
        row1.addView(saveBtn)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val pinBtn = Button(this).apply {
            text = if (item.isPinned) "Pinned 📌" else "Pin 📌"
            isAllCaps = false
            textSize = 12f
            setTextColor(if (item.isPinned) Color.parseColor("#121212") else Color.parseColor("#E3B341"))
            background = if (item.isPinned) {
                roundedBg("#E3B341", "#F2CC60", dp(8).toFloat())
            } else {
                roundedBg("#2A2415", "#6E561B", dp(8).toFloat())
            }
            setPadding(dp(11), dp(5), dp(11), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(6)
            }
            setOnClickListener {
                val nowPinned = ScreenshotVaultManager.togglePin(this@MainActivity, item.id)
                val msg = if (nowPinned) "Pinned! Will never auto-delete 📌" else "Unpinned (24h timer resumed)"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                refreshVaultList()
            }
        }

        val delBtn = Button(this).apply {
            text = "Delete ✕"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.parseColor("#FF6B6B"))
            background = roundedBg("#25171A", "#4A252B", dp(8).toFloat())
            setPadding(dp(10), dp(5), dp(10), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                ScreenshotVaultManager.deleteItem(this@MainActivity, item.id)
                refreshVaultList()
            }
        }

        row2.addView(pinBtn)
        row2.addView(delBtn)

        infoColumn.addView(row1)
        infoColumn.addView(row2)

        card.addView(thumbView)
        card.addView(infoColumn)
        return card
    }

    private fun showFullScreenViewer(item: VaultItem, dateFormat: SimpleDateFormat) {
        val file = ScreenshotVaultManager.getFileForItem(this, item)
        if (!file.exists()) {
            Toast.makeText(this, "Screenshot file not found", Toast.LENGTH_SHORT).show()
            refreshVaultList()
            return
        }

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        val fullImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(8), dp(60), dp(8), dp(100))
            setOnClickListener { dialog.dismiss() }
        }

        thumbExecutor.execute {
            val fullBmp = BitmapFactory.decodeFile(file.absolutePath)
            if (fullBmp != null) {
                runOnUiThread {
                    fullImage.setImageBitmap(fullBmp)
                }
            }
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#CC121212"))
            setPadding(dp(18), dp(18), dp(18), dp(14))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        }

        val titleLabel = TextView(this).apply {
            text = dateFormat.format(Date(item.timestamp))
            textSize = 15f
            setTextColor(Color.parseColor("#FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = Button(this).apply {
            text = "Close ✕"
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedBg("#262B33", "#3D444D", dp(8).toFloat())
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { dialog.dismiss() }
        }

        topBar.addView(titleLabel)
        topBar.addView(closeBtn)

        val bottomWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC121212"))
            setPadding(dp(12), dp(10), dp(12), dp(18))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        if (!item.sourceUrl.isNullOrBlank()) {
            val urlActionBtn = Button(this).apply {
                text = "🌐 Open / Copy URL: ${item.sourceUrl.take(40)}"
                isAllCaps = false
                textSize = 12f
                setTextColor(Color.parseColor("#58A6FF"))
                background = roundedBg("#131D2E", "#1F6FEB", dp(8).toFloat())
                setPadding(dp(12), dp(6), dp(12), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(8)
                }
                setOnClickListener {
                    ScreenshotVaultManager.copyTextToClipboard(this@MainActivity, item.sourceUrl)
                    Toast.makeText(this@MainActivity, "URL copied 📋", Toast.LENGTH_SHORT).show()
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl)))
                    } catch (_: Exception) {}
                }
            }
            bottomWrapper.addView(urlActionBtn)
        }

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val viewerCopyBtn = Button(this).apply {
            text = "Copy 📋"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedBg("#1F6FEB", "#388BFD", dp(10).toFloat())
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
            }
            setOnClickListener {
                if (ScreenshotVaultManager.copyItemToClipboard(this@MainActivity, item)) {
                    Toast.makeText(this@MainActivity, "Copied to clipboard 📋", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val viewerSaveBtn = Button(this).apply {
            text = "Save to Gallery 🖼️"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedBg("#238636", "#2EA043", dp(10).toFloat())
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f).apply {
                rightMargin = dp(6)
            }
            setOnClickListener {
                thumbExecutor.execute {
                    val saved = ScreenshotVaultManager.saveToGallery(this@MainActivity, item)
                    runOnUiThread {
                        val msg = if (saved) "Saved to Gallery (Pictures/Cropboard) 🖼️" else "Failed to save to Gallery"
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val viewerPinBtn = Button(this).apply {
            text = if (item.isPinned) "Pinned 📌" else "Pin 📌"
            isAllCaps = false
            textSize = 14f
            setTextColor(if (item.isPinned) Color.parseColor("#121212") else Color.parseColor("#E3B341"))
            background = if (item.isPinned) {
                roundedBg("#E3B341", "#F2CC60", dp(10).toFloat())
            } else {
                roundedBg("#2A2415", "#6E561B", dp(10).toFloat())
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f)
            setOnClickListener {
                val nowPinned = ScreenshotVaultManager.togglePin(this@MainActivity, item.id)
                item.isPinned = nowPinned
                text = if (nowPinned) "Pinned 📌" else "Pin 📌"
                setTextColor(if (nowPinned) Color.parseColor("#121212") else Color.parseColor("#E3B341"))
                background = if (nowPinned) {
                    roundedBg("#E3B341", "#F2CC60", dp(10).toFloat())
                } else {
                    roundedBg("#2A2415", "#6E561B", dp(10).toFloat())
                }
                refreshVaultList()
            }
        }

        bottomBar.addView(viewerCopyBtn)
        bottomBar.addView(viewerSaveBtn)
        bottomBar.addView(viewerPinBtn)
        bottomWrapper.addView(bottomBar)

        container.addView(fullImage)
        container.addView(topBar)
        container.addView(bottomWrapper)

        dialog.setContentView(container)
        dialog.show()
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
